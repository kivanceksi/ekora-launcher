"""0.7: Ekora hesabı (sahte HTTP ile) ve servers.dat."""
import json
import os
import struct

import pytest

import ekora_hesap
import sunucu_listesi as sl
from ekora_hesap import Hesap, HesapHatasi


class R:
    def __init__(self, code, data=None, content=b''):
        self.status_code, self._d, self.content = code, data, content

    def json(self):
        if self._d is None:
            raise ValueError
        return self._d


class SahteHttp:
    def __init__(self):
        self.calls = []
        self.refresh = 'r1'

    def get(self, url, params=None, timeout=None, headers=None):
        self.calls.append(('GET', url, params, headers))
        if url.endswith('/api/public/launcher/config'):
            return R(200, {'ok': True, 'supabaseUrl': 'https://abc.supabase.co', 'supabaseKey': 'sb_publishable_x'})
        if '/rest/v1/' in url:
            t = url.split('/rest/v1/')[1]
            if t in ('profiles', 'player_links', 'notifications'):
                assert headers['Authorization'] == 'Bearer tok1'
            else:
                assert 'Authorization' not in headers  # publishable anahtar: anonim istekte Bearer yok
            return R(200, {'profiles': [{'display_name': 'Ali'}], 'player_links': [{'mc_name': 'Ali_Ekora', 'mc_uuid': 'u'}],
                           'notifications': [{'id': 1}, {'id': 2}], 'news_posts': [{'slug': 'a', 'title': 'Başlık', 'kind': 'duyuru'}],
                           'events': [], 'leaderboard_meta': [{'mode': 'genel', 'metric': 'playtime', 'oyuncu': 3}, {'mode': 'x;drop', 'metric': 'y'}],
                           'player_stats': [{'mc_name': 'Ali_Ekora', 'value': 5, 'mode': 'genel', 'metric': 'playtime'}],
                           'punishments': []}[t])
        if 'mc-heads.net' in url:
            return R(200, content=b'\x89PNG\r\n\x1a\nxx')
        raise AssertionError(url)

    def post(self, url, json=None, timeout=None, headers=None, data=None):
        self.calls.append(('POST', url, json, headers))
        if 'grant_type=password' in url:
            if json['password'] != 'dogru-sifre':
                return R(400, {'error_code': 'invalid_credentials', 'msg': 'Invalid login credentials'})
            return R(200, {'access_token': 'tok1', 'refresh_token': 'r1', 'expires_in': 3600, 'user': {'id': 'uid1', 'email': 'a@b.co'}})
        if 'grant_type=refresh_token' in url:
            assert json['refresh_token'] == self.refresh
            return R(200, {'access_token': 'tok1', 'refresh_token': 'r2', 'expires_in': 3600, 'user': {'id': 'uid1', 'email': 'a@b.co'}})
        if url.endswith('/auth/v1/logout'):
            return R(204)
        raise AssertionError(url)


def test_giris_durum_ve_kalicilik(tmp_path):
    http = SahteHttp()
    h = Hesap(tmp_path, http=http)
    assert not h.girisli()
    with pytest.raises(HesapHatasi, match='hatalı'):
        h.giris('a@b.co', 'yanlis-sifre')
    d = h.giris('a@b.co', 'dogru-sifre')
    assert d['girisli'] and d['mcAd'] == 'Ali_Ekora' and d['ad'] == 'Ali' and d['bildirim'] == 2
    dosya = (tmp_path / 'hesap.dat').read_bytes()
    assert b'r1' not in dosya[:3] and b'dogru-sifre' not in dosya  # şifre asla yazılmaz
    # yeni oturum: dosyadan yüklenir, erişim anahtarı yenilenir (refresh döner)
    h2 = Hesap(tmp_path, http=http)
    assert h2.girisli()
    assert h2.erisim() == 'tok1'
    assert json.loads(ekora_hesap.ac((tmp_path / 'hesap.dat').read_bytes()))['refresh'] == 'r2'
    h2.cikis()
    assert not (tmp_path / 'hesap.dat').exists() and not h2.girisli()


def test_herkese_acik_veriler(tmp_path):
    h = Hesap(tmp_path, http=SahteHttp())
    assert h.haberler()[0]['tur'] == 'duyuru'
    meta = h.siralama_meta()
    assert meta == [{'mod': 'genel', 'olcu': 'playtime', 'oyuncu': 3}]  # geçersiz mod adı süzülür
    with pytest.raises(HesapHatasi):
        h.siralama('genel;drop', 'x')
    with pytest.raises(HesapHatasi):
        h.oyuncu('a b')
    assert h.oyuncu('Ali_Ekora')[0]['deger'] == 5
    assert h.avatar('Ali_Ekora').startswith('data:image/png;base64,')
    assert h.site_adresi('magaza') == 'https://ekoramc.fun/magaza'
    with pytest.raises(HesapHatasi):
        h.site_adresi('https://kotu.site')
    with pytest.raises(HesapHatasi):
        h.site_adresi('haber/../x')


def test_gecersiz_site_yapilandirmasi(tmp_path):
    class Kotu(SahteHttp):
        def get(self, url, **kw):
            if url.endswith('/config'):
                return R(200, {'supabaseUrl': 'https://abc.supabase.co', 'supabaseKey': 'sb_secret_GIZLI'})
            return super().get(url, **kw)
    with pytest.raises(HesapHatasi):
        Hesap(tmp_path, http=Kotu()).haberler()


def test_servers_dat_ekle_ve_koru(tmp_path):
    yol = str(tmp_path / 'servers.dat')
    assert sl.ekora_girdisi(yol, 'play.ekoramc.fun:25567') == 'eklendi'
    assert sl.ekora_girdisi(yol, 'play.ekoramc.fun:25567') == 'zaten'
    ad, kok = sl.oku(open(yol, 'rb').read())
    sv = [e for e in kok if e[1] == 'servers'][0][2][1]
    assert len(sv) == 1 and ['BYTE', 0] and sv[0][-1] == [sl.BYTE, 'acceptTextures', 1]


def test_servers_dat_baska_girdilere_dokunmaz(tmp_path):
    # elle yazılmış: iki sunucu, Ekora girdisi paket reddedilmiş (acceptTextures=0) ve simgeli
    kok = [[sl.LIST, 'servers', (sl.COMPOUND, [
        [[sl.STRING, 'name', 'Başka Sunucu'], [sl.STRING, 'ip', 'mc.ornek.com'], [sl.STRING, 'icon', 'QUJD']],
        [[sl.STRING, 'name', 'Ekora'], [sl.STRING, 'ip', 'PLAY.ekoramc.fun:25567'], [sl.BYTE, 'acceptTextures', 0], [sl.BYTE, 'hidden', 0]],
    ])]]
    yol = str(tmp_path / 'servers.dat')
    open(yol, 'wb').write(sl.yaz('', kok))
    assert sl.ekora_girdisi(yol, 'play.ekoramc.fun:25567') == 'guncellendi'
    _, k2 = sl.oku(open(yol, 'rb').read())
    sv = k2[0][2][1]
    assert sv[0] == kok[0][2][1][0]  # diğer sunucu aynen
    assert [sl.BYTE, 'acceptTextures', 1] in sv[1] and [sl.STRING, 'name', 'Ekora'] in sv[1]


def test_servers_dat_bozuksa_yedeklenir(tmp_path):
    yol = tmp_path / 'servers.dat'
    yol.write_bytes(b'\x0a\x00\x00\x09\xff')
    assert sl.ekora_girdisi(str(yol), 'play.ekoramc.fun') == 'eklendi'
    assert (tmp_path / 'servers.dat.bozuk').exists()
