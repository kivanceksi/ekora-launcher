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
import tr.ekora.istemci.client.ekran.MenuEkrani;

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
			{"t":"menu","ad":"EkoraOyuncu","rutbe":"✦ ELİT","rutbeRenk":"#F5B83D","kivilcim":12500,"ep":340,"cark":2,"kasa":1,"kozmetik":14,
			"gunlukHazir":true,"sezonSeviye":7,"sezonXp":620,"sezonGerek":1000,"kisayollar":[
			{"id":"odul","ad":"Ödül Merkezi","aciklama":"Günlük ödül, sezon kartı","ikon":"minecraft:sunflower","renk":"#22C55E"},
			{"id":"magaza","ad":"Mağaza","aciklama":"Rütbe ve kozmetikler","ikon":"minecraft:emerald","renk":"#10B981"},
			{"id":"oyunlar","ad":"Oyunlar","aciklama":"BedWars, BoxPvP, RPG…","ikon":"minecraft:diamond_sword","renk":"#3B82F6"},
			{"id":"lobi","ad":"Lobiye dön","aciklama":"Ekora Kalesi","ikon":"minecraft:compass","renk":"#94A3B8"}]}""";

	static final String DOLAP = """
			{"t":"dolap","kivilcim":12500,"yuvalar":[{"kod":"sapka","ad":"Şapkalar","ikon":"minecraft:golden_helmet"},{"kod":"kostum","ad":"Kostümler","ikon":"minecraft:leather_chestplate"},
			{"kod":"pet","ad":"Omuz Dostları","ikon":"minecraft:parrot_spawn_egg"},{"kod":"iz","ad":"Yürüme İzleri","ikon":"minecraft:blaze_powder"},{"kod":"unvan","ad":"Unvanlar","ikon":"minecraft:name_tag"}],
			"urunler":[
			{"id":"a","ad":"Taç","yuva":"sapka","ikon":"minecraft:golden_helmet","nadirlik":"efsanevi","sahip":true,"takili":true,"fiyat":20000,"aciklama":["Kralların tacı.","Lobide başında parlar."]},
			{"id":"b","ad":"Balkabağı","yuva":"sapka","ikon":"minecraft:carved_pumpkin","nadirlik":"yaygin","sahip":true,"takili":false,"fiyat":1500},
			{"id":"c","ad":"Elmas Miğfer","yuva":"sapka","ikon":"minecraft:diamond_helmet","nadirlik":"destansi","sahip":false,"takili":false,"fiyat":9000},
			{"id":"d","ad":"Kafatası","yuva":"sapka","ikon":"minecraft:skeleton_skull","nadirlik":"nadir","sahip":false,"takili":false,"fiyat":4000},
			{"id":"e","ad":"Ejder Başı","yuva":"sapka","ikon":"minecraft:dragon_head","nadirlik":"efsanevi","sahip":false,"takili":false,"fiyat":25000},
			{"id":"f","ad":"Mantar","yuva":"sapka","ikon":"minecraft:red_mushroom","nadirlik":"yaygin","sahip":true,"takili":false,"fiyat":1000},
			{"id":"g","ad":"Cam Kubbe","yuva":"sapka","ikon":"minecraft:glass","nadirlik":"nadir","sahip":false,"takili":false,"fiyat":3500},
			{"id":"h","ad":"Kar Topu","yuva":"sapka","ikon":"minecraft:snow_block","nadirlik":"yaygin","sahip":false,"takili":false,"fiyat":1200}]}""";

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
			context.setScreen(() -> new MenuEkrani(j(MENU)));
			context.waitTicks(10);
			context.takeScreenshot("ekora_menu");

			context.setScreen(() -> new DolapEkrani(j(DOLAP)));
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
