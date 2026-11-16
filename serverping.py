"""Minimal Minecraft Server List Ping (status) client. No dependencies."""
import json
import socket
import time


def _varint(n):
    out = b''
    n &= 0xFFFFFFFF
    while True:
        b = n & 0x7F
        n >>= 7
        out += bytes([b | (0x80 if n else 0)])
        if not n:
            return out


def _read_varint(sock):
    n = 0
    for i in range(5):
        c = sock.recv(1)
        if not c:
            raise OSError('bağlantı kapandı')
        n |= (c[0] & 0x7F) << (7 * i)
        if not c[0] & 0x80:
            return n
    raise OSError('geçersiz yanıt')


def _varint_from(buf, i):
    n = 0
    for k in range(5):
        c = buf[i]
        i += 1
        n |= (c & 0x7F) << (7 * k)
        if not c & 0x80:
            return n, i
    raise ValueError('geçersiz sayı')


def _plain(desc):
    if isinstance(desc, str):
        return desc
    if isinstance(desc, dict):
        return _plain(desc.get('text', '')) + ''.join(_plain(x) for x in desc.get('extra', []) or [])
    if isinstance(desc, list):
        return ''.join(_plain(x) for x in desc)
    return ''


def ping(host, port=25565, timeout=6.0, protocol=767):
    """Returns {'online', 'players':{'online','max'}, 'version', 'motd', 'latency'}."""
    try:
        start = time.monotonic()
        with socket.create_connection((host, int(port)), timeout=timeout) as s:
            s.settimeout(timeout)
            h = host.encode('utf-8')
            pkt = _varint(0) + _varint(protocol) + _varint(len(h)) + h + int(port).to_bytes(2, 'big') + _varint(1)
            s.sendall(_varint(len(pkt)) + pkt + _varint(1) + _varint(0))
            length = _read_varint(s)
            if length <= 0 or length > 2_000_000:
                raise OSError('geçersiz uzunluk')
            data = b''
            while len(data) < length:
                part = s.recv(min(65536, length - len(data)))
                if not part:
                    break
                data += part
        latency = int((time.monotonic() - start) * 1000)
        _, i = _varint_from(data, 0)
        n, i = _varint_from(data, i)
        d = json.loads(data[i:i + n].decode('utf-8', 'replace'))
        pl = d.get('players') if isinstance(d.get('players'), dict) else {}
        try:
            online, mx = max(0, int(pl.get('online', 0))), max(0, int(pl.get('max', 0)))
        except (TypeError, ValueError):
            online, mx = 0, 0
        ver = d.get('version') if isinstance(d.get('version'), dict) else {}
        motd = ' '.join(_plain(d.get('description')).split())
        import re
        motd = re.sub(r'§.', '', motd)[:160]
        return {'online': True, 'players': {'online': online, 'max': mx}, 'version': str(ver.get('name', ''))[:60],
                'motd': motd, 'latency': latency}
    except (OSError, ValueError, IndexError, UnicodeError):
        return {'online': False, 'players': {'online': 0, 'max': 0}, 'version': '', 'motd': '', 'latency': None}
