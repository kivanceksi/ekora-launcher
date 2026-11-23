package tr.ekora.istemci.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import tr.ekora.istemci.client.ekran.CarkEkrani;
import tr.ekora.istemci.client.ekran.DolapEkrani;
import tr.ekora.istemci.client.ekran.KasaEkrani;
import tr.ekora.istemci.client.ekran.MerkezEkrani;
import tr.ekora.istemci.client.Ekora;

/** Ekranları örnek verilerle açar ve ekran görüntüsü alır (CI'da xvfb ile). Sunucu gerekmez. */
public class IstemciEkranTesti implements FabricClientGameTest {
	static JsonObject j(String s) { return JsonParser.parseString(s).getAsJsonObject(); }

	static final String CARK = """
			{"t":"cark","hak":2,"bedava":true,"ucret":4000,"kivilcim":12500,"dilimler":[
			{"ad":"250 Kıvılcım","kisa":"250 ✦","renk":"#64748B","ikon":"minecraft:gold_nugget","yuzde":26.0},
			{"ad":"500 Kıvılcım","kisa":"500 ✦","renk":"#0EA5E9","ikon":"minecraft:gold_nugget","yuzde":22.0},
			{"ad":"1.000 Kıvılcım","kisa":"1.000 ✦","renk":"#14B8A6","ikon":"minecraft:gold_ingot","yuzde":16.0},
			{"ad":"2.500 Kıvılcım","kisa":"2.500 ✦","renk":"#A855F7","ikon":"minecraft:gold_block","yuzde":8.0},
			{"ad":"10.000 Kıvılcım","kisa":"10.000 ✦","renk":"#F59E0B","ikon":"minecraft:blaze_powder","yuzde":1.5},
			{"ad":"+120 Sezon XP","kisa":"+120 XP","renk":"#22C55E","ikon":"minecraft:experience_bottle","yuzde":11.0},
			{"ad":"Kasa Açma Hakkı","kisa":"Kasa","renk":"#EA580C","ikon":"minecraft:ender_chest","yuzde":3.5},
			{"ad":"Rastgele Kozmetik","kisa":"Kozmetik","renk":"#EC4899","ikon":"minecraft:nether_star","yuzde":2.0},
			{"ad":"Tekrar Çevir!","kisa":"Tekrar!","renk":"#EAB308","ikon":"minecraft:clock","yuzde":10.0}]}""";

	static final String MENU = """
			{"t": "menu", "sekme": "ana", "ad": "EkoraOyuncu", "rutbe": "✦ ELİT", "rutbeRenk": "#F5B83D", "kivilcim": 12500, "ep": 340, "cark": 2, "kasa": 1, "kozmetik": 14, "gunlukHazir": true, "sezonSeviye": 7, "cevrimici": 64, "mod": "hub", "oyunlar": [{"id": "smp", "ad": "Survival SMP", "aciklama": ["Ekonomi, ev, ekip ve pazar.", "Ayrı envanter • SMP Coin."], "ikon": "minecraft:grass_block", "renk": "#4ADE80", "oynayan": 12, "burada": false}, {"id": "skyblock", "ad": "SkyBlock", "aciklama": ["Kendi adanı kur ve büyüt.", "60 görev • Ada Coin."], "ikon": "minecraft:oak_sapling", "renk": "#A3E635", "oynayan": 5, "burada": false}, {"id": "kasaba", "ad": "Kasaba", "aciklama": ["Kasabanı kur, parsel al, ulus kur.", "Ayrı dünya • Kasaba Altını."], "ikon": "minecraft:bell", "renk": "#FACC15", "oynayan": 8, "burada": false}, {"id": "rpg", "ad": "Ekora RPG", "aciklama": ["4 sınıf • 32 yetenek • 250+ özel eşya", "Dünya boss'ları, zindanlar, evciller."], "ikon": "minecraft:netherite_axe", "renk": "#F97316", "oynayan": 17, "burada": false, "etiket": "YENİ"}, {"id": "box", "ad": "BoxPvP", "aciklama": ["Madenleri kaz, cevheri sat, güçlen.", "İlerlemen kalıcı, her an gir-çık."], "ikon": "minecraft:diamond_pickaxe", "renk": "#FB923C", "oynayan": 9, "burada": false, "etiket": "YENİ"}, {"id": "bedwars", "ad": "BedWars", "aciklama": ["4 takım, 5 harita teması.", "Yatağını koru, rakibinkini kır."], "ikon": "minecraft:red_bed", "renk": "#F87171", "oynayan": 4, "burada": false}, {"id": "oyunlar", "ad": "Mini Oyunlar", "aciklama": ["SkyWars, Köprü, Katil Kim?, Saklambaç…", "Günün Oyunu ×2 • Günlük görevler."], "ikon": "minecraft:firework_star", "renk": "#F472B6", "oynayan": 6, "burada": false}, {"id": "pvp", "ad": "PvP Düello", "aciklama": ["1v1 • 14 kit.", "1/5/10 tur • her tur taze arena."], "ikon": "minecraft:diamond_sword", "renk": "#EF4444", "oynayan": 3, "burada": false}, {"id": "parkur", "ad": "Parkur", "aciklama": ["25 parkur, 5 zorluk.", "Rekorlar ve sıralama."], "ikon": "minecraft:feather", "renk": "#C084FC", "oynayan": 0, "burada": true}], "odul": {"seri": 2, "bugunAlindi": false, "siradaki": 3, "gunler": [{"gun": 1, "metin": "1.000 ✦ • +30 XP", "ikon": "minecraft:gold_nugget", "durum": "alindi", "buyuk": false}, {"gun": 2, "metin": "1.500 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "alindi", "buyuk": false}, {"gun": 3, "metin": "2.000 ✦ • +40 XP", "ikon": "minecraft:gold_nugget", "durum": "hazir", "buyuk": false}, {"gun": 4, "metin": "2.500 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "kilitli", "buyuk": false}, {"gun": 5, "metin": "3.500 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "kilitli", "buyuk": false}, {"gun": 6, "metin": "4.500 ✦ • +2 çark", "ikon": "minecraft:gold_block", "durum": "kilitli", "buyuk": false}, {"gun": 7, "metin": "7.500 ✦ • +1 kasa", "ikon": "minecraft:ender_chest", "durum": "kilitli", "buyuk": true}], "dakika": 42, "taslar": [{"dk": 15, "metin": "500 ✦", "ikon": "minecraft:gold_nugget", "durum": "alindi"}, {"dk": 30, "metin": "1.000 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "alindi"}, {"dk": 60, "metin": "2.000 ✦", "ikon": "minecraft:gold_ingot", "durum": "kilitli"}, {"dk": 120, "metin": "+1 kasa", "ikon": "minecraft:ender_chest", "durum": "kilitli"}, {"dk": 180, "metin": "5.000 ✦", "ikon": "minecraft:gold_block", "durum": "kilitli"}], "aralikDk": 10, "aralikKivilcim": 200, "carkBonusDk": 60}, "sezon": {"ad": "Açılış Sezonu", "seviye": 7, "maks": 30, "toplamXp": 1110, "seviyeXp": 150, "elit": false, "bekleyen": 2, "elitFiyat": "250 EP", "seviyeler": [{"sv": 1, "ucretsiz": {"metin": "700 ✦", "ikon": "minecraft:gold_nugget", "durum": "alindi"}, "elit": {"metin": "1150 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 2, "ucretsiz": {"metin": "800 ✦", "ikon": "minecraft:gold_nugget", "durum": "alindi"}, "elit": {"metin": "1300 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 3, "ucretsiz": {"metin": "900 ✦", "ikon": "minecraft:gold_nugget", "durum": "alindi"}, "elit": {"metin": "1450 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 4, "ucretsiz": {"metin": "1000 ✦", "ikon": "minecraft:gold_nugget", "durum": "alindi"}, "elit": {"metin": "1600 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 5, "ucretsiz": {"metin": "2.500 ✦ • +1 kasa", "ikon": "minecraft:ender_chest", "durum": "alindi"}, "elit": {"metin": "1750 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 6, "ucretsiz": {"metin": "1200 ✦", "ikon": "minecraft:gold_nugget", "durum": "hazir"}, "elit": {"metin": "1900 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 7, "ucretsiz": {"metin": "1300 ✦", "ikon": "minecraft:gold_nugget", "durum": "hazir"}, "elit": {"metin": "2050 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 8, "ucretsiz": {"metin": "1400 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "2200 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 9, "ucretsiz": {"metin": "1500 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "2350 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 10, "ucretsiz": {"metin": "2.500 ✦ • +1 kasa", "ikon": "minecraft:ender_chest", "durum": "kilitli"}, "elit": {"metin": "Sezon Tacı", "ikon": "minecraft:nether_star", "durum": "elit"}}, {"sv": 11, "ucretsiz": {"metin": "1700 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "2650 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 12, "ucretsiz": {"metin": "1800 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "2800 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 13, "ucretsiz": {"metin": "1900 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "2950 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 14, "ucretsiz": {"metin": "2000 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "3100 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 15, "ucretsiz": {"metin": "2.500 ✦ • +1 kasa", "ikon": "minecraft:ender_chest", "durum": "kilitli"}, "elit": {"metin": "3250 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 16, "ucretsiz": {"metin": "2200 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "3400 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 17, "ucretsiz": {"metin": "2300 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "3550 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 18, "ucretsiz": {"metin": "2400 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "3700 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 19, "ucretsiz": {"metin": "2500 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "3850 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 20, "ucretsiz": {"metin": "2.500 ✦ • +1 kasa", "ikon": "minecraft:ender_chest", "durum": "kilitli"}, "elit": {"metin": "4000 ✦ • +1 çark", "ikon": "minecraft:nether_star", "durum": "elit"}}, {"sv": 21, "ucretsiz": {"metin": "2700 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "4150 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 22, "ucretsiz": {"metin": "2800 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "4300 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 23, "ucretsiz": {"metin": "2900 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "4450 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 24, "ucretsiz": {"metin": "3000 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "4600 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 25, "ucretsiz": {"metin": "2.500 ✦ • +1 kasa", "ikon": "minecraft:ender_chest", "durum": "kilitli"}, "elit": {"metin": "4750 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 26, "ucretsiz": {"metin": "3200 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "4900 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 27, "ucretsiz": {"metin": "3300 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "5050 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 28, "ucretsiz": {"metin": "3400 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "5200 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 29, "ucretsiz": {"metin": "3500 ✦", "ikon": "minecraft:gold_nugget", "durum": "kilitli"}, "elit": {"metin": "5350 ✦ • +1 çark", "ikon": "minecraft:sunflower", "durum": "elit"}}, {"sv": 30, "ucretsiz": {"metin": "2.500 ✦ • +1 kasa", "ikon": "minecraft:ender_chest", "durum": "kilitli"}, "elit": {"metin": "5500 ✦ • +1 çark", "ikon": "minecraft:nether_star", "durum": "elit"}}]}, "profil": {"toplamDakika": 1934, "bugunDakika": 42, "seri": 2}, "kisayollar": [{"id": "magaza", "ad": "Mağaza", "aciklama": "Rütbeler, Ekora Puanı, paketler", "ikon": "minecraft:emerald", "renk": "#22D3EE"}, {"id": "oy", "ad": "Oy Ver", "aciklama": "Oy ver, Kıvılcım kazan", "ikon": "minecraft:writable_book", "renk": "#4ADE80"}, {"id": "lobi", "ad": "Lobiye Dön", "aciklama": "Ekora lobisine ışınlan", "ikon": "minecraft:recovery_compass", "renk": "#A78BFA"}, {"id": "discord", "ad": "Discord", "aciklama": "Topluluk ve destek", "ikon": "minecraft:music_disc_cat", "renk": "#818CF8"}], "sosyal": {"var": true, "sistem": true, "istekler": [{"ad": "KasabaReisi"}], "arkadaslar": [{"ad": "Deniz_42", "cevrimici": true, "modAd": "Ekora RPG"}, {"ad": "MertPvP", "cevrimici": true, "modAd": "BoxPvP"}, {"ad": "ElifYapar", "cevrimici": false, "son": 7200000}, {"ad": "Kaan", "cevrimici": false, "son": 259200000}], "oyuncular": [{"ad": "Ahmet_TR", "modAd": "Kasaba", "arkadas": false}, {"ad": "Deniz_42", "modAd": "Ekora RPG", "arkadas": true}, {"ad": "EkoraOyuncu", "modAd": "Parkur", "arkadas": false, "ben": true}, {"ad": "MertPvP", "modAd": "BoxPvP", "arkadas": true}, {"ad": "SuperSteve", "modAd": "Lobi", "arkadas": false}, {"ad": "Zeynep", "modAd": "SkyBlock", "arkadas": false}, {"ad": "xX_Can_Xx", "modAd": "BedWars", "arkadas": false}, {"ad": "Yusuf07", "modAd": "Lobi", "arkadas": false}]}, "gorevler": {"gunluk": [{"metin": "45 dakika oyna", "ikon": "minecraft:clock", "ilerleme": 42, "hedef": 45, "odul": "+600 ✦ • +50 sezon XP", "bitti": false}, {"metin": "20 yaratık yen", "ikon": "minecraft:iron_sword", "ilerleme": 20, "hedef": 20, "odul": "+450 ✦ • +40 sezon XP", "bitti": true}, {"metin": "3 farklı oyun modunda oyna", "ikon": "minecraft:compass", "ilerleme": 1, "hedef": 3, "odul": "+700 ✦ • +55 sezon XP", "bitti": false}], "biten": 1, "bonus": {"metin": "+1 çark • +100 sezon XP", "bitti": false}, "haftalik": {"metin": "Bu hafta 1500 blok kaz", "ikon": "minecraft:diamond_pickaxe", "ilerleme": 640, "hedef": 1500, "odul": "+4000 ✦ • +1 kasa hakkı • +250 sezon XP", "bitti": false}, "kalanSn": 22512, "haftaKalanSn": 301000}, "siralama": {"listeler": [{"id": "sezon", "ad": "Sezon XP", "ikon": "minecraft:experience_bottle", "renk": "#22D3EE", "ilk": [{"ad": "Deniz_42", "deger": "4200 XP"}, {"ad": "MertPvP", "deger": "3890 XP"}, {"ad": "KasabaReisi", "deger": "3580 XP"}, {"ad": "ElifYapar", "deger": "3270 XP"}, {"ad": "EkoraOyuncu", "deger": "2960 XP", "ben": true}, {"ad": "Zeynep", "deger": "2650 XP"}, {"ad": "Kaan", "deger": "2340 XP"}, {"ad": "Yusuf07", "deger": "2030 XP"}, {"ad": "Ahmet_TR", "deger": "1720 XP"}, {"ad": "SuperSteve", "deger": "1410 XP"}], "sira": 5, "deger": "2960 XP", "toplam": 250}, {"id": "oyna", "ad": "Oynama süresi", "ikon": "minecraft:clock", "renk": "#A855F7", "ilk": [{"ad": "SuperSteve", "deger": "120 sa 0 dk"}, {"ad": "Ahmet_TR", "deger": "111 sa 7 dk"}, {"ad": "Yusuf07", "deger": "102 sa 14 dk"}, {"ad": "Kaan", "deger": "93 sa 21 dk"}, {"ad": "Zeynep", "deger": "84 sa 28 dk"}, {"ad": "EkoraOyuncu", "deger": "75 sa 35 dk", "ben": true}, {"ad": "ElifYapar", "deger": "66 sa 42 dk"}, {"ad": "KasabaReisi", "deger": "57 sa 49 dk"}, {"ad": "MertPvP", "deger": "48 sa 56 dk"}, {"ad": "Deniz_42", "deger": "39 sa 3 dk"}], "sira": 6, "deger": "75 sa 35 dk", "toplam": 250}, {"id": "seri", "ad": "Giriş serisi", "ikon": "minecraft:blaze_powder", "renk": "#FB923C", "ilk": [{"ad": "KasabaReisi", "deger": "40 gün"}, {"ad": "ElifYapar", "deger": "37 gün"}, {"ad": "EkoraOyuncu", "deger": "34 gün", "ben": true}, {"ad": "Zeynep", "deger": "31 gün"}, {"ad": "Kaan", "deger": "28 gün"}, {"ad": "Yusuf07", "deger": "25 gün"}, {"ad": "Ahmet_TR", "deger": "22 gün"}, {"ad": "SuperSteve", "deger": "19 gün"}, {"ad": "Deniz_42", "deger": "16 gün"}, {"ad": "MertPvP", "deger": "13 gün"}], "sira": 3, "deger": "34 gün", "toplam": 250}]}}""";

	static final String DOLAP = """
			{"t":"dolap","ep":340,"epBirim":"EP","kivilcim":12500,"yuvalar":[{"kod":"sapka","ad":"Şapkalar","ikon":"minecraft:golden_helmet"},{"kod":"kostum","ad":"Kostümler","ikon":"minecraft:leather_chestplate"},
			{"kod":"pet","ad":"Omuz Dostları","ikon":"minecraft:parrot_spawn_egg"},{"kod":"iz","ad":"Yürüme İzleri","ikon":"minecraft:blaze_powder"},{"kod":"unvan","ad":"Unvanlar","ikon":"minecraft:name_tag"}],
			"urunler":[
			{"id":"a","ad":"Taç","yuva":"sapka","ikon":"minecraft:golden_helmet","nadirlik":"efsanevi","sahip":true,"takili":true,"fiyat":20000,"aciklama":["Kralların tacı.","Lobide başında parlar."]},
			{"id":"b","ad":"Balkabağı","yuva":"sapka","ikon":"minecraft:carved_pumpkin","nadirlik":"yaygin","sahip":true,"takili":false,"fiyat":7500,"epFiyat":150},
			{"id":"c","ad":"Elmas Miğfer","yuva":"sapka","ikon":"minecraft:diamond_helmet","nadirlik":"destansi","sahip":false,"takili":false,"fiyat":45000,"epFiyat":900},
			{"id":"d","ad":"Kafatası","yuva":"sapka","ikon":"minecraft:skeleton_skull","nadirlik":"nadir","sahip":false,"takili":false,"fiyat":20000,"epFiyat":400},
			{"id":"e","ad":"Ejder Başı","yuva":"sapka","ikon":"minecraft:dragon_head","nadirlik":"efsanevi","sahip":false,"takili":false,"fiyat":125000,"epFiyat":2500},
			{"id":"f","ad":"Mantar","yuva":"sapka","ikon":"minecraft:red_mushroom","nadirlik":"yaygin","sahip":true,"takili":false,"fiyat":5000,"epFiyat":100},
			{"id":"g","ad":"Cam Kubbe","yuva":"sapka","ikon":"minecraft:glass","nadirlik":"nadir","sahip":false,"takili":false,"fiyat":17500,"epFiyat":350},
			{"id":"h","ad":"Kar Topu","yuva":"sapka","ikon":"minecraft:snow_block","nadirlik":"yaygin","sahip":false,"takili":false,"fiyat":6000,"epFiyat":120}]}""";

	static final String KASA = """
			{"t":"kasa","hak":2,"fiyat":3500,"kivilcim":12500,"kopya":false,"odul":{"id":"ejder","ad":"Ejder Başı","ikon":"minecraft:dragon_head","nadirlik":"efsanevi"},"havuz":[
			{"ad":"Balkabağı","ikon":"minecraft:carved_pumpkin","nadirlik":"yaygin"},{"ad":"Mantar","ikon":"minecraft:red_mushroom","nadirlik":"yaygin"},
			{"ad":"Kafatası","ikon":"minecraft:skeleton_skull","nadirlik":"nadir"},{"ad":"Elmas Miğfer","ikon":"minecraft:diamond_helmet","nadirlik":"destansi"},
			{"ad":"Kar Topu","ikon":"minecraft:snow_block","nadirlik":"yaygin"},{"ad":"Cam Kubbe","ikon":"minecraft:glass","nadirlik":"nadir"}]}""";

	static final String KASA_ONAY = """
			{"t":"kasa","onay":true,"hak":0,"fiyat":3500,"kivilcim":12500,"kalan":23,"etkin":true,"oranlar":[
			{"nadirlik":"yaygin","yuzde":52,"adet":11},{"nadirlik":"nadir","yuzde":30,"adet":7},{"nadirlik":"destansi","yuzde":14,"adet":4},{"nadirlik":"efsanevi","yuzde":4,"adet":1}],"havuz":[
			{"ad":"Balkabağı","ikon":"minecraft:carved_pumpkin","nadirlik":"yaygin"},{"ad":"Mantar","ikon":"minecraft:red_mushroom","nadirlik":"yaygin"},
			{"ad":"Kafatası","ikon":"minecraft:skeleton_skull","nadirlik":"nadir"},{"ad":"Elmas Miğfer","ikon":"minecraft:diamond_helmet","nadirlik":"destansi"},
			{"ad":"Ejder Başı","ikon":"minecraft:dragon_head","nadirlik":"efsanevi"},{"ad":"Kar Topu","ikon":"minecraft:snow_block","nadirlik":"yaygin"},
			{"ad":"Cam Kubbe","ikon":"minecraft:glass","nadirlik":"nadir"},{"ad":"Taç","ikon":"minecraft:golden_helmet","nadirlik":"destansi"}]}""";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			sp.getConnection().waitForChunksRender();
			MerkezEkrani[] merkez = new MerkezEkrani[1];
			context.setScreen(() -> merkez[0] = new MerkezEkrani(j(MENU), "ana"));
			context.waitTicks(10);
			context.takeScreenshot("ekora_merkez_ana");
			for (String s : new String[]{"oyunlar", "gorevler", "sosyal", "oduller", "sezon", "siralama", "profil", "ayarlar"}) {
				context.runOnClient(c -> merkez[0].guncelle(j(MENU), s));
				context.waitTicks(6);
				context.takeScreenshot("ekora_merkez_" + s);
			}

			// evrensel sandık görünümü: EkoraHub "oyunlar" menüsüne benzer bir sandık
			context.runOnClient(c -> Ekora.destekAyarla(true));
			context.setScreen(SandikOrnegi::ac);
			context.waitTicks(10);
			context.takeScreenshot("ekora_sandik_gorunumu");
			context.runOnClient(c -> { tr.ekora.istemci.client.Ayarlar.sandikGorunumu = false; });
			context.setScreen(SandikOrnegi::ac);
			context.waitTicks(10);
			context.takeScreenshot("ekora_sandik_vanilla");
			context.runOnClient(c -> { tr.ekora.istemci.client.Ayarlar.sandikGorunumu = true; });

			// Akıllı Menü: eklenti menüleri kartlı Ekora ekranıyla
			Object[][] akilli = {{"mezat", (java.util.function.Supplier<net.minecraft.client.gui.screens.Screen>) OrnekMenuler::mezat},
					{"market", (java.util.function.Supplier<net.minecraft.client.gui.screens.Screen>) OrnekMenuler::market},
					{"onay", (java.util.function.Supplier<net.minecraft.client.gui.screens.Screen>) OrnekMenuler::onay},
					{"roller", (java.util.function.Supplier<net.minecraft.client.gui.screens.Screen>) OrnekMenuler::roller}};
			for (Object[] a : akilli) {
				@SuppressWarnings("unchecked") var sup = (java.util.function.Supplier<net.minecraft.client.gui.screens.Screen>) a[1];
				context.runOnClient(c -> c.gui.setScreen(sup.get()));
				context.waitTicks(8);
				context.takeScreenshot("ekora_akilli_" + a[0]);
			}
			context.runOnClient(c -> { c.gui.setScreen(OrnekMenuler.mezat()); });
			context.waitTicks(4);
			context.runOnClient(c -> { if (c.gui.screen() instanceof tr.ekora.istemci.client.menu.AkilliMenu m) m.testAramaYaz("elmas"); });
			context.waitTicks(4);
			context.takeScreenshot("ekora_akilli_mezat_arama");
			context.runOnClient(c -> { tr.ekora.istemci.client.Ayarlar.menuEnvanter = true; c.gui.setScreen(OrnekMenuler.market()); });
			context.waitTicks(8);
			context.takeScreenshot("ekora_akilli_market_envanter");
			context.runOnClient(c -> { tr.ekora.istemci.client.Ayarlar.menuEnvanter = false; });
			// depo güvenlik ağı: ipucu "menü" dese de adsız ganimet → klasik sandık görünümü
			context.runOnClient(c -> c.gui.setScreen(OrnekMenuler.depo()));
			context.waitTicks(12);
			context.takeScreenshot("ekora_akilli_depo_klasige_dondu");
			context.runOnClient(c -> {
				if (!(c.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen))
					throw new AssertionError("depo menüsü klasik görünüme geçmedi: " + c.gui.screen());
			});

			context.setScreen(() -> { DolapEkrani d = new DolapEkrani(j(DOLAP)); d.sec("h"); return d; });
			context.waitTicks(10);
			context.takeScreenshot("ekora_dolap");

			CarkEkrani[] cark = new CarkEkrani[1];
			context.setScreen(() -> cark[0] = new CarkEkrani(j(CARK)));
			context.waitTicks(10);
			context.takeScreenshot("ekora_cark");
			context.runOnClient(c -> cark[0].sonuc(j("{\"t\":\"cark_sonuc\",\"dilim\":7,\"hak\":1,\"kivilcim\":12500,\"mesaj\":\"Rastgele Kozmetik: Ejder Başı\"}")));
			context.waitTicks(30);
			context.takeScreenshot("ekora_cark_donuyor");
			context.waitTicks(170);
			context.takeScreenshot("ekora_cark_sonuc");

			context.setScreen(() -> new KasaEkrani(j(KASA_ONAY)));
			context.waitTicks(10);
			context.takeScreenshot("ekora_kasa_onay");
			context.setScreen(() -> new KasaEkrani(j(KASA)));
			context.waitTicks(40);
			context.takeScreenshot("ekora_kasa_donuyor");
			context.waitTicks(110);
			context.takeScreenshot("ekora_kasa_sonuc");
			context.setScreen(() -> null);
		}
	}
}
