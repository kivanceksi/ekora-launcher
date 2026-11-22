"""Minecraft servers.dat (sıkıştırılmamış NBT) okuyup yazar.

EkoraMC girdisinde "Sunucu kaynak paketleri: Açık" (acceptTextures=1) ayarlanır; böylece Ekora'nın menü/eşya paketi
oyun içinde soru sormadan yüklenir. Quick Play aynı adresli girdiyi kullandığı için doğrudan bağlanışta da geçerlidir.
Oyuncunun diğer sunucu girdilerine dokunulmaz.
"""
import os
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BARR, STRING, LIST, COMPOUND, IARR, LARR = range(13)


class NbtHatasi(ValueError):
    pass


def _mutf8_coz(b):
    return b.replace(b'\xc0\x80', b'\x00').decode('utf-8', 'surrogatepass')


def _mutf8_kodla(s):
    return s.encode('utf-8', 'surrogatepass').replace(b'\x00', b'\xc0\x80')


class _Okuyucu:
    def __init__(self, b):
        self.b, self.i = b, 0

    def al(self, n):
        if self.i + n > len(self.b):
            raise NbtHatasi('servers.dat bozuk')
        v = self.b[self.i:self.i + n]
        self.i += n
        return v

    def un(self, f):
        return struct.unpack('>' + f, self.al(struct.calcsize('>' + f)))[0]

    def yazi(self):
        return _mutf8_coz(self.al(self.un('H')))

    def deger(self, t, derinlik=0):
        if derinlik > 64:
            raise NbtHatasi('servers.dat çok derin')
        if t == BYTE: return self.un('b')
        if t == SHORT: return self.un('h')
        if t == INT: return self.un('i')
        if t == LONG: return self.un('q')
        if t == FLOAT: return self.un('f')
        if t == DOUBLE: return self.un('d')
        if t == BARR: return self.al(max(0, self.un('i')))
        if t == STRING: return self.yazi()
        if t == LIST:
            et, n = self.un('b'), self.un('i')
            if n > 100000:
                raise NbtHatasi('servers.dat bozuk')
            return (et, [self.deger(et, derinlik + 1) for _ in range(max(0, n))])
        if t == COMPOUND:
            out = []
            while True:
                tt = self.un('b')
                if tt == END:
                    return out
                ad = self.yazi()
                out.append([tt, ad, self.deger(tt, derinlik + 1)])
        if t == IARR: return [self.un('i') for _ in range(max(0, self.un('i')))]
        if t == LARR: return [self.un('q') for _ in range(max(0, self.un('i')))]
        raise NbtHatasi('servers.dat bilinmeyen etiket')


def _yaz(t, v, out):
    if t == BYTE: out += struct.pack('>b', v)
    elif t == SHORT: out += struct.pack('>h', v)
    elif t == INT: out += struct.pack('>i', v)
    elif t == LONG: out += struct.pack('>q', v)
    elif t == FLOAT: out += struct.pack('>f', v)
    elif t == DOUBLE: out += struct.pack('>d', v)
    elif t == BARR: out += struct.pack('>i', len(v)) + v
    elif t == STRING:
        b = _mutf8_kodla(v)
        out += struct.pack('>H', len(b)) + b
    elif t == LIST:
        et, items = v
        out += struct.pack('>bi', et if items else (et or END), len(items))
        for x in items:
            _yaz(et, x, out)
    elif t == COMPOUND:
        for tt, ad, vv in v:
            out += struct.pack('>b', tt)
            _yaz(STRING, ad, out)
            _yaz(tt, vv, out)
        out += b'\x00'
    elif t == IARR:
        out += struct.pack('>i', len(v)) + b''.join(struct.pack('>i', x) for x in v)
    elif t == LARR:
        out += struct.pack('>i', len(v)) + b''.join(struct.pack('>q', x) for x in v)


def oku(b):
    r = _Okuyucu(b)
    if r.un('b') != COMPOUND:
        raise NbtHatasi('servers.dat bozuk')
    ad = r.yazi()
    return ad, r.deger(COMPOUND)


def yaz(ad, kok):
    out = bytearray(b'\x0a')
    _yaz(STRING, ad, out)
    _yaz(COMPOUND, kok, out)
    return bytes(out)


def _alan(comp, ad):
    for e in comp:
        if e[1] == ad:
            return e
    return None


def ekora_girdisi(yol, adres, isim='EkoraMC'):
    """servers.dat içinde `adres` girdisini bulur ya da en üste ekler; kaynak paketini otomatik kabule çevirir.
    Dönüş: 'eklendi' | 'guncellendi' | 'zaten'."""
    kok_ad, kok = '', []
    if os.path.exists(yol):
        try:
            with open(yol, 'rb') as f:
                kok_ad, kok = oku(f.read())
        except (OSError, NbtHatasi):
            os.replace(yol, yol + '.bozuk')  # bozuk dosyayı saklayıp yenisini yaz
            kok_ad, kok = '', []
    liste = _alan(kok, 'servers')
    if liste is None or liste[0] != LIST:
        liste = [LIST, 'servers', (COMPOUND, [])]
        kok = [e for e in kok if e[1] != 'servers'] + [liste]
    et, ogeler = liste[2]
    if ogeler and et != COMPOUND:
        raise NbtHatasi('servers.dat beklenmeyen biçim')
    liste[2] = (COMPOUND, ogeler)
    sonuc = 'zaten'
    hedef = None
    for g in ogeler:
        ip = _alan(g, 'ip')
        if ip and ip[0] == STRING and ip[2].strip().lower() == adres.lower():
            hedef = g
            break
    if hedef is None:
        hedef = [[STRING, 'name', isim], [STRING, 'ip', adres]]
        ogeler.insert(0, hedef)
        sonuc = 'eklendi'
    kabul = _alan(hedef, 'acceptTextures')
    if kabul is None:
        hedef.append([BYTE, 'acceptTextures', 1])
        sonuc = 'guncellendi' if sonuc == 'zaten' else sonuc
    elif kabul[0] != BYTE or kabul[2] != 1:
        kabul[0], kabul[2] = BYTE, 1
        sonuc = 'guncellendi' if sonuc == 'zaten' else sonuc
    if sonuc != 'zaten':
        tmp = yol + '.tmp'
        with open(tmp, 'wb') as f:
            f.write(yaz(kok_ad, kok))
        os.replace(tmp, yol)
    return sonuc
