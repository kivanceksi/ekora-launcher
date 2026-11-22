"""Ekora hesabı: ekoramc.fun site hesabıyla giriş, profil, haberler, sıralamalar, cezalar ve otomatik giriş bileti.

Site, launcher'a yalnızca herkese açık Supabase adresini ve publishable anahtarını verir (/api/public/launcher/config).
Kullanıcı verisi Supabase'in satır güvenliğiyle (RLS) korunur; launcher yalnızca oturum açan kişinin kendi verisini okur.
Yenileme anahtarı (refresh token) Windows'ta DPAPI ile, yalnızca bu Windows kullanıcısının açabileceği şekilde saklanır.
"""
import base64
import json
import os
import re
import socket
import threading
import time
from urllib.parse import quote

import requests

from security import scoped

SITE = 'https://ekoramc.fun'
_AD = re.compile(r'^[A-Za-z0-9_]{3,16}$')
_SLUG = re.compile(r'^[a-z0-9_]{1,40}$')
_EPOSTA = re.compile(r'^[^@\s]{1,64}@[^@\s]{1,190}\.[^@\s]{2,}$')
_IPV4_KILIT = threading.Lock()


class HesapHatasi(ValueError):
    pass


# ---------------------------------------------------------------- yerel koruma (DPAPI)
def _dpapi(veri, coz):
    import ctypes
    from ctypes import wintypes

    class BLOB(ctypes.Structure):
        _fields_ = [('cbData', wintypes.DWORD), ('pbData', ctypes.POINTER(ctypes.c_char))]

    giris = BLOB(len(veri), ctypes.cast(ctypes.create_string_buffer(veri, len(veri)), ctypes.POINTER(ctypes.c_char)))
    cikis = BLOB()
    entropi_b = b'EkoraLauncher/hesap'
    entropi = BLOB(len(entropi_b), ctypes.cast(ctypes.create_string_buffer(entropi_b, len(entropi_b)), ctypes.POINTER(ctypes.c_char)))
    crypt = ctypes.windll.crypt32
    fn = crypt.CryptUnprotectData if coz else crypt.CryptProtectData
    ok = fn(ctypes.byref(giris), None, ctypes.byref(entropi), None, None, 0x01, ctypes.byref(cikis))  # UI_FORBIDDEN
    if not ok:
        raise OSError('DPAPI')
    try:
        return ctypes.string_at(cikis.pbData, cikis.cbData)
    finally:
        ctypes.windll.kernel32.LocalFree(cikis.pbData)


def koru(veri: bytes) -> bytes:
    if os.name == 'nt':
        return b'DP1' + _dpapi(veri, False)
    return b'PL1' + base64.b64encode(veri)  # yalnızca test/Linux


def ac(veri: bytes) -> bytes:
    if veri.startswith(b'DP1') and os.name == 'nt':
        return _dpapi(veri[3:], True)
    if veri.startswith(b'PL1'):
        return base64.b64decode(veri[3:])
    raise OSError('Tanınmayan hesap dosyası')


# ---------------------------------------------------------------- yardımcılar
def _ipv4_post(url, **kw):
    """Bileti IPv4 üzerinden ister: Minecraft sunucusuna da IPv4 ile bağlanılır, IP'ler eşleşmeli."""
    import urllib3.util.connection as uc
    with _IPV4_KILIT:
        eski = uc.allowed_gai_family
        uc.allowed_gai_family = lambda: socket.AF_INET
        try:
            with requests.Session() as s:  # yeni bağlantı havuzu: eski IPv6 bağlantısı yeniden kullanılmasın
                return s.post(url, **kw)
        finally:
            uc.allowed_gai_family = eski


def _hata_metni(r):
    try:
        d = r.json()
    except ValueError:
        d = {}
    kod = str(d.get('error_code') or d.get('error') or d.get('hata') or '')
    msg = str(d.get('msg') or d.get('error_description') or d.get('message') or '')
    tablo = {
        'invalid_credentials': 'E-posta veya şifre hatalı.', 'invalid_grant': 'E-posta veya şifre hatalı.',
        'email_not_confirmed': 'Önce e-posta adresini doğrula (sana gelen bağlantı).',
        'user_banned': 'Bu site hesabı askıya alınmış.', 'over_request_rate_limit': 'Çok fazla deneme. Biraz bekleyip tekrar dene.',
        'hesap_bagli_degil': 'Minecraft hesabın site hesabına bağlı değil. Oyunda /webkod yaz, kodu sitede Hesap Bağla sayfasına gir.',
        'ipv4_gerekli': 'İnternet bağlantın yalnız IPv6 kullanıyor; otomatik giriş için IPv4 gerekiyor.',
        'hiz_siniri': 'Kısa sürede çok fazla deneme oldu. Birkaç dakika bekle.',
        'oturum_gecersiz': 'Oturumun sona erdi. Tekrar giriş yap.', 'yetkisiz': 'Oturumun sona erdi. Tekrar giriş yap.',
        'yapilandirilmamis': 'Sitede launcher bağlantısı henüz açılmamış.',
    }
    if kod in tablo:
        return tablo[kod]
    if 'Invalid login credentials' in msg:
        return tablo['invalid_credentials']
    if r.status_code == 404:
        return 'Sitede launcher bağlantısı henüz açılmamış.'
    return 'Site yanıt vermedi (' + str(r.status_code) + ').'


class Hesap:
    """İş parçacığı güvenli; ağ çağrıları çağıranın iş parçacığında yapılır."""

    def __init__(self, root, site=SITE, ua='EkoraLauncher', http=None):
        self.root = root
        self.site = site.rstrip('/')
        self.ua = ua
        self.http = http or requests
        self._kilit = threading.RLock()
        self._cfg = None
        self._cfg_zaman = 0
        self._erisim = None       # (token, bitis)
        self._oturum = None       # {'refresh', 'uid', 'eposta'}
        self._ben = None
        self._avatar = {}
        self._yukle()

    # ---------- yapılandırma ----------
    def cfg(self):
        with self._kilit:
            if self._cfg and time.time() - self._cfg_zaman < 1800:
                return self._cfg
        onbellek = scoped(self.root, 'cache', 'site.json')
        try:
            r = self.http.get(self.site + '/api/public/launcher/config', timeout=10, headers={'User-Agent': self.ua})
            if r.status_code != 200:
                raise HesapHatasi(_hata_metni(r))
            d = r.json()
            url, key = str(d.get('supabaseUrl', '')), str(d.get('supabaseKey', ''))
            if not re.fullmatch(r'https://[A-Za-z0-9.-]+(:\d+)?', url.rstrip('/')) or not key or key.startswith('sb_secret_') or len(key) > 400:
                raise HesapHatasi('Site yapılandırması geçersiz.')
            cfg = {'url': url.rstrip('/'), 'key': key}
            try:
                onbellek.parent.mkdir(parents=True, exist_ok=True)
                onbellek.write_text(json.dumps(cfg), encoding='utf-8')
            except OSError:
                pass
        except (requests.RequestException, ValueError) as e:
            try:
                cfg = json.loads(onbellek.read_text(encoding='utf-8'))
            except (OSError, ValueError):
                if isinstance(e, HesapHatasi):
                    raise e from None
                raise HesapHatasi('Siteye ulaşılamadı. İnternet bağlantını kontrol et.') from None
        with self._kilit:
            self._cfg, self._cfg_zaman = cfg, time.time()
        return cfg

    def _baslik(self, kullanici=False):
        c = self.cfg()
        h = {'apikey': c['key'], 'User-Agent': self.ua, 'Accept': 'application/json'}
        if kullanici:
            h['Authorization'] = 'Bearer ' + self.erisim()
        elif not c['key'].startswith('sb_publishable_'):
            h['Authorization'] = 'Bearer ' + c['key']  # eski tip anon JWT anahtarı
        return h

    # ---------- oturum ----------
    def _dosya(self):
        return scoped(self.root, 'hesap.dat')

    def _yukle(self):
        try:
            d = json.loads(ac(self._dosya().read_bytes()).decode('utf-8'))
            if isinstance(d, dict) and d.get('refresh'):
                self._oturum = {'refresh': str(d['refresh']), 'uid': str(d.get('uid', '')), 'eposta': str(d.get('eposta', ''))}
        except (OSError, ValueError):
            self._oturum = None

    def _kaydet(self):
        p = self._dosya()
        if not self._oturum:
            try:
                p.unlink()
            except OSError:
                pass
            return
        tmp = scoped(self.root, 'hesap.dat.tmp')
        tmp.write_bytes(koru(json.dumps(self._oturum).encode('utf-8')))
        os.replace(tmp, p)

    def girisli(self):
        return self._oturum is not None

    def _token_al(self, govde, tur):
        c = self.cfg()
        r = self.http.post(c['url'] + '/auth/v1/token?grant_type=' + tur, json=govde, timeout=15,
                           headers={'apikey': c['key'], 'User-Agent': self.ua, 'Content-Type': 'application/json'})
        if r.status_code != 200:
            raise HesapHatasi(_hata_metni(r))
        d = r.json()
        tok, ref = d.get('access_token'), d.get('refresh_token')
        if not tok or not ref:
            raise HesapHatasi('Site beklenmeyen bir yanıt verdi.')
        u = d.get('user') or {}
        with self._kilit:
            self._erisim = (tok, time.time() + max(60, int(d.get('expires_in', 3600))) - 60)
            self._oturum = {'refresh': ref, 'uid': str(u.get('id', self._oturum and self._oturum.get('uid') or '')),
                            'eposta': str(u.get('email', self._oturum and self._oturum.get('eposta') or ''))}
            self._kaydet()

    def giris(self, eposta, sifre):
        eposta = str(eposta or '').strip()
        sifre = str(sifre or '')
        if not _EPOSTA.match(eposta) or len(eposta) > 254:
            raise HesapHatasi('Geçerli bir e-posta adresi yaz.')
        if not 6 <= len(sifre) <= 200:
            raise HesapHatasi('Şifre en az 6 karakter olmalı.')
        self._token_al({'email': eposta, 'password': sifre}, 'password')
        self._ben = None
        return self.durum(zorla=True)

    def erisim(self):
        with self._kilit:
            if self._erisim and time.time() < self._erisim[1]:
                return self._erisim[0]
            if not self._oturum:
                raise HesapHatasi('Önce Ekora hesabınla giriş yap.')
            ref = self._oturum['refresh']
        try:
            self._token_al({'refresh_token': ref}, 'refresh_token')
        except HesapHatasi:
            with self._kilit:
                self._oturum, self._erisim, self._ben = None, None, None
                self._kaydet()
            raise HesapHatasi('Oturumun sona erdi. Tekrar giriş yap.') from None
        return self._erisim[0]

    def cikis(self):
        tok = None
        with self._kilit:
            if self._erisim and time.time() < self._erisim[1]:
                tok = self._erisim[0]
            self._oturum, self._erisim, self._ben = None, None, None
            self._kaydet()
        if tok:
            try:
                c = self.cfg()
                self.http.post(c['url'] + '/auth/v1/logout', timeout=8,
                               headers={'apikey': c['key'], 'Authorization': 'Bearer ' + tok, 'User-Agent': self.ua})
            except Exception:
                pass

    # ---------- veri ----------
    def rest(self, tablo, params, kullanici=False):
        c = self.cfg()
        r = self.http.get(c['url'] + '/rest/v1/' + tablo, params=params, timeout=15, headers=self._baslik(kullanici))
        if r.status_code == 401 and kullanici:
            with self._kilit:
                self._erisim = None
            r = self.http.get(c['url'] + '/rest/v1/' + tablo, params=params, timeout=15, headers=self._baslik(True))
        if r.status_code != 200:
            raise HesapHatasi('Site verisi alınamadı (' + str(r.status_code) + ').')
        d = r.json()
        return d if isinstance(d, list) else []

    def durum(self, zorla=False):
        if not self._oturum:
            return {'girisli': False}
        if self._ben and not zorla:
            return self._ben
        uid = self._oturum.get('uid', '')
        ben = {'girisli': True, 'eposta': self._oturum.get('eposta', ''), 'ad': '', 'mcAd': '', 'mcUuid': '', 'bildirim': 0}
        try:
            prof = self.rest('profiles', {'select': 'display_name', 'id': 'eq.' + uid}, True) if uid else []
            if prof:
                ben['ad'] = str(prof[0].get('display_name') or '')[:40]
            link = self.rest('player_links', {'select': 'mc_name,mc_uuid', 'user_id': 'eq.' + uid}, True) if uid else []
            if link and _AD.match(str(link[0].get('mc_name', ''))):
                ben['mcAd'] = link[0]['mc_name']
                ben['mcUuid'] = str(link[0].get('mc_uuid', ''))
            try:
                bil = self.rest('notifications', {'select': 'id', 'read_at': 'is.null', 'limit': '50'}, True)
                ben['bildirim'] = len(bil)
            except HesapHatasi:
                pass
        except HesapHatasi as e:
            if 'giriş yap' in str(e):
                return {'girisli': False, 'hata': str(e)}
            ben['hata'] = str(e)
        self._ben = ben
        return ben

    def haberler(self, adet=8):
        rows = self.rest('news_posts', {'select': 'slug,title,excerpt,kind,pinned,published_at', 'order': 'pinned.desc,published_at.desc', 'limit': str(adet)})
        return [{'slug': str(r.get('slug', ''))[:120], 'baslik': str(r.get('title', ''))[:160], 'ozet': str(r.get('excerpt') or '')[:400],
                 'tur': 'duyuru' if r.get('kind') == 'duyuru' else 'haber', 'sabit': bool(r.get('pinned')), 'tarih': r.get('published_at')} for r in rows]

    def etkinlikler(self, adet=6):
        simdi = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(time.time() - 6 * 3600))
        rows = self.rest('events', {'select': 'id,title,description,mode_slug,starts_at,ends_at,registration_open',
                                    'starts_at': 'gte.' + simdi, 'order': 'starts_at.asc', 'limit': str(adet)})
        return [{'baslik': str(r.get('title', ''))[:160], 'aciklama': str(r.get('description') or '')[:300], 'mod': str(r.get('mode_slug') or ''),
                 'bas': r.get('starts_at'), 'bit': r.get('ends_at'), 'kayit': bool(r.get('registration_open'))} for r in rows]

    def siralama_meta(self):
        rows = self.rest('leaderboard_meta', {'select': 'mode,metric,oyuncu'})
        return [{'mod': str(r.get('mode', '')), 'olcu': str(r.get('metric', '')), 'oyuncu': int(r.get('oyuncu') or 0)}
                for r in rows if _SLUG.match(str(r.get('mode', ''))) and _SLUG.match(str(r.get('metric', '')))]

    def siralama(self, mod, olcu, adet=50):
        if not _SLUG.match(str(mod)) or not _SLUG.match(str(olcu)):
            raise HesapHatasi('Geçersiz sıralama.')
        rows = self.rest('player_stats', {'select': 'mc_name,value,extra', 'mode': 'eq.' + mod, 'metric': 'eq.' + olcu,
                                          'order': 'value.desc', 'limit': str(min(100, int(adet)))})
        return [{'ad': str(r.get('mc_name', '')), 'deger': r.get('value', 0),
                 'ek': (r.get('extra') or {}).get('kasaba') if isinstance(r.get('extra'), dict) else None} for r in rows]

    def oyuncu(self, ad):
        if not _AD.match(str(ad)):
            raise HesapHatasi('Geçersiz oyuncu adı.')
        rows = self.rest('player_stats', {'select': 'mode,metric,value', 'mc_name': 'ilike.' + ad.replace('_', '\\_'), 'limit': '200'})
        return [{'mod': str(r.get('mode', '')), 'olcu': str(r.get('metric', '')), 'deger': r.get('value', 0)} for r in rows]

    def cezalar(self, ad=None, adet=60):
        p = {'select': 'mc_name,type,reason,issued_at,expires_at,active', 'order': 'issued_at.desc', 'limit': str(adet)}
        if ad:
            if not _AD.match(str(ad)):
                raise HesapHatasi('Geçersiz oyuncu adı.')
            p['mc_name'] = 'ilike.' + ad.replace('_', '\\_')
        rows = self.rest('punishments', p)
        return [{'ad': str(r.get('mc_name', '')), 'tur': str(r.get('type', '')), 'sebep': str(r.get('reason', ''))[:300],
                 'bas': r.get('issued_at'), 'bit': r.get('expires_at'), 'aktif': bool(r.get('active'))} for r in rows]

    def bilet(self):
        tok = self.erisim()
        r = _ipv4_post(self.site + '/api/public/launcher/bilet', timeout=12,
                       headers={'Authorization': 'Bearer ' + tok, 'User-Agent': self.ua, 'Content-Type': 'application/json'}, data='{}')
        if r.status_code != 200:
            raise HesapHatasi(_hata_metni(r))
        d = r.json()
        if not d.get('ok') or not _AD.match(str(d.get('mcName', ''))):
            raise HesapHatasi(_hata_metni(r))
        return d['mcName']

    def avatar(self, ad):
        if not _AD.match(str(ad)):
            return None
        if ad in self._avatar:
            return self._avatar[ad]
        try:
            r = self.http.get('https://mc-heads.net/avatar/' + quote(ad) + '/64', timeout=8, headers={'User-Agent': self.ua})
            if r.status_code == 200 and r.content[:8] == b'\x89PNG\r\n\x1a\n' and len(r.content) < 200_000:
                self._avatar[ad] = 'data:image/png;base64,' + base64.b64encode(r.content).decode('ascii')
                return self._avatar[ad]
        except requests.RequestException:
            pass
        return None

    def site_adresi(self, yol):
        yollar = {'magaza': '/magaza', 'destek': '/destek', 'hesap-bagla': '/hesap-bagla', 'kayit': '/kayit', 'sifre': '/sifre-sifirla',
                  'profil': '/profil', 'haberler': '/haberler', 'siralamalar': '/siralamalar', 'cezalar': '/cezalar',
                  'itiraz': '/ceza-itiraz', 'etkinlikler': '/etkinlikler', 'kurallar': '/kurallar', 'forum': '/forum'}
        if yol.startswith('haber/'):
            slug = yol[6:]
            if re.fullmatch(r'[a-z0-9-]{1,120}', slug):
                return self.site + '/haber/' + slug
            raise HesapHatasi('Geçersiz bağlantı.')
        if yol not in yollar:
            raise HesapHatasi('Geçersiz bağlantı.')
        return self.site + yollar[yol]
