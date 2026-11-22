package tr.ekora.istemci.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import tr.ekora.istemci.ag.EkoraIstek;
import tr.ekora.istemci.client.ekran.CarkEkrani;
import tr.ekora.istemci.client.ekran.DolapEkrani;
import tr.ekora.istemci.client.ekran.KasaEkrani;
import tr.ekora.istemci.client.ekran.MenuEkrani;

/** Sunucu bağlantısı durumu, gelen veriyi ekranlara dağıtma ve istek gönderme. */
public final class Ekora {
	private Ekora() { }

	/** Sunucu "merhaba" dedi mi (Ekora İstemci'yi destekleyen bir EkoraMC sunucusunda mıyız)? */
	static volatile boolean destek;
	static volatile boolean merhabaGitti;
	static volatile String surum = "1.0.0";

	public static boolean destekli() { return destek; }

	public static void gonder(JsonObject o) {
		if (!ClientPlayNetworking.canSend(EkoraIstek.TYPE)) return;
		ClientPlayNetworking.send(new EkoraIstek(o.toString()));
	}

	public static void istek(String t) {
		JsonObject o = new JsonObject();
		o.addProperty("t", t);
		gonder(o);
	}

	public static void istek(String t, String anahtar, String deger) {
		JsonObject o = new JsonObject();
		o.addProperty("t", t);
		o.addProperty(anahtar, deger);
		gonder(o);
	}

	public static void ses(SoundEvent ses, float ton, float hacim) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ses, ton, hacim));
	}

	/** Sunucudan gelen JSON'u işler (istemci iş parçacığında). */
	static void isle(String json) {
		JsonObject o;
		try {
			o = JsonParser.parseString(json).getAsJsonObject();
		} catch (RuntimeException e) {
			return;
		}
		String t = Json.yazi(o, "t", "");
		Minecraft mc = Minecraft.getInstance();
		Screen acik = mc.gui.screen();
		switch (t) {
			case "merhaba" -> destek = true;
			case "menu" -> {
				if (acik instanceof MenuEkrani m) m.guncelle(o);
				else mc.gui.setScreen(new MenuEkrani(o));
			}
			case "cark" -> {
				if (acik instanceof CarkEkrani c) c.guncelle(o);
				else mc.gui.setScreen(new CarkEkrani(o));
			}
			case "cark_sonuc" -> {
				if (acik instanceof CarkEkrani c) c.sonuc(o);
			}
			case "dolap" -> {
				if (acik instanceof DolapEkrani d) d.guncelle(o);
				else mc.gui.setScreen(new DolapEkrani(o));
			}
			case "kasa" -> mc.gui.setScreen(new KasaEkrani(o));
			case "hata" -> {
				if (acik instanceof CarkEkrani c) c.hata(Json.yazi(o, "mesaj", ""));
				Bildirimler.ekle(Json.yazi(o, "baslik", "Olmadı"), Json.yazi(o, "mesaj", ""), "minecraft:barrier", Cizim0.KIRMIZI);
			}
			case "bildirim" -> Bildirimler.ekle(Json.yazi(o, "baslik", ""), Json.yazi(o, "metin", ""), Json.yazi(o, "ikon", ""),
					tr.ekora.istemci.client.ekran.Cizim.renk(Json.yazi(o, "renk", null), Cizim0.MOR));
			case "kapat" -> {
				if (acik instanceof MenuEkrani || acik instanceof CarkEkrani || acik instanceof DolapEkrani || acik instanceof KasaEkrani) mc.gui.setScreen(null);
			}
			default -> { }
		}
	}

	/** Renk sabitleri (ekran paketine bağımlı olmadan). */
	static final class Cizim0 {
		static final int MOR = 0xFFA855F7, KIRMIZI = 0xFFF87171;
	}
}
