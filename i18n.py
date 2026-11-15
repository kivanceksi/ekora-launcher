import re
import urllib.error

MESSAGES={
 'Install libraries':'Oyun kütüphaneleri hazırlanıyor…',
 'Install assets':'Oyun içerikleri hazırlanıyor…',
 'Install java runtime':'Oyuna uygun Java hazırlanıyor…',
 'Installation complete':'Kurulum tamamlandı.',
 'Running fabric installer':'Fabric kuruluyor…',
 'Download assets':'Oyun içerikleri indiriliyor…',
 'Download libraries':'Oyun kütüphaneleri indiriliyor…',
}
def status_tr(text):
    text=str(text)
    if text in MESSAGES:return MESSAGES[text]
    if text.lower().startswith('download'):
        name=text.split('/')[-1].split('\\')[-1]
        if ' ' in name or len(name)>90:return 'Gerekli oyun dosyaları indiriliyor…'
        return 'İndiriliyor: '+name
    if text.lower().startswith(('install','check','extract','copy','verify','unpack','extracting','downloading','running')):
        return 'Oyun dosyaları hazırlanıyor ve kontrol ediliyor…'
    if any(x in text for x in ('…','ı','ş','ğ','ü','ö','ç','İ')) or text.startswith(('Minecraft','Fabric','EkoraMC')):return text
    return 'Oyun dosyaları hazırlanıyor…'

def error_tr(exc):
    if isinstance(exc,(ValueError,RuntimeError)) and any(x in str(exc) for x in ('ı','ş','ğ','ü','ö','ç','İ')):return str(exc)
    if isinstance(exc,FileNotFoundError):return 'Gerekli dosya bulunamadı. Dosya kontrolünü çalıştır veya Java seçimini kontrol et.'
    if isinstance(exc,PermissionError):return 'Uygulama kendi klasörüne yazamadı. Klasörün başka bir program tarafından kullanılmadığını kontrol et.'
    name=type(exc).__name__
    if name in ('VersionNotFound','UnsupportedVersion'):return 'Bu oyun sürümü veya Fabric seçimi desteklenmiyor. Ayarlardan başka sürüm seç.'
    if isinstance(exc,(urllib.error.URLError,TimeoutError,ConnectionError)) or name in ('ConnectionError','ReadTimeout','ConnectTimeout','HTTPError','SSLError'):
        return 'İndirme sunucusuna ulaşılamadı. İnternet bağlantını kontrol edip yeniden dene.'
    return 'İşlem tamamlanamadı. Günlükler bölümündeki hata kaydını incele. Kod: '+name
