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
import tr.ekora.istemci.client.ekran.MerkezEkrani;

/** Sunucu bağlantısı durumu, gelen veriyi ekranlara dağıtma ve istek gönderme. */
public final class Ekora {
	private Ekora() { }

	/** Sunucu "merhaba" dedi mi (Ekora İstemci'yi destekleyen bir EkoraMC sunucusunda mıyız)? */
	static volatile boolean destek;
	static volatile boolean merhabaGitti;
	static volatile String surum = "1.1.0";
	private static boolean selamGosterildi;

	/** Sunucunun "şimdi açılan sandık bir eklenti menüsü" ipucu (Akıllı Menü için). */
	public record GuiIpucu(String baslik, String eklenti, boolean akilli, String arama, long zaman) { }

	private static volatile GuiIpucu ipucu;

	/** Başlığı eşleşen ve 5 sn içinde gelmiş ipucu; yoksa null. Bir kez kullanılır. */
	public static GuiIpucu ipucuAl(String baslik) {
		GuiIpucu i = ipucu;
		if (i == null || System.currentTimeMillis() - i.zaman() > 5000 || !i.baslik().equals(baslik)) return null;
		ipucu = null;
		return i;
	}

	/** Testler için. */
	public static void ipucuAyarla(String baslik, String eklenti, boolean akilli) {
		ipucu = new GuiIpucu(baslik, eklenti, akilli, "", System.currentTimeMillis());
	}

	/** Testler için: sunucu aramalı menü ipucu. */
	public static void ipucuAyarla(String baslik, String eklenti, boolean akilli, String arama) {
		ipucu = new GuiIpucu(baslik, eklenti, akilli, arama, System.currentTimeMillis());
	}

	public static boolean destekli() { return destek; }

	public static String surum() { return surum; }

	/** Testler için: sunucu desteğini elle aç/kapat. */
	public static void destekAyarla(boolean d) { destek = d; }

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

	/** Birden çok alanlı istek: istekCok("sezon_al", "sv", "3", "elit", "1"). */
	public static void istekCok(String t, String... anahtarDeger) {
		JsonObject o = new JsonObject();
		o.addProperty("t", t);
		for (int i = 0; i + 1 < anahtarDeger.length; i += 2) o.addProperty(anahtarDeger[i], anahtarDeger[i + 1]);
		gonder(o);
	}

	public static void ses(SoundEvent ses, float ton, float hacim) {
		if (!Ayarlar.sesler) return;
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
			case "merhaba" -> {
				boolean ilk = !destek;
				destek = true;
				if (ilk && !selamGosterildi) {
					selamGosterildi = true;
					Bildirimler.ekle("Ekora İstemci hazır", "K: Ekora Merkezi", "minecraft:nether_star", Cizim0.MOR);
				}
			}
			case "menu" -> {
				String sekme = Json.yazi(o, "sekme", "");
				if (acik instanceof MerkezEkrani m) m.guncelle(o, sekme);
				else mc.gui.setScreen(new MerkezEkrani(o, sekme));
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
			case "gui" -> ipucu = new GuiIpucu(Json.yazi(o, "baslik", ""), Json.yazi(o, "eklenti", ""), Json.mantik(o, "akilli", false), Json.yazi(o, "arama", ""), System.currentTimeMillis());
			case "hata" -> {
				if (acik instanceof CarkEkrani c) c.hata(Json.yazi(o, "mesaj", ""));
				Bildirimler.ekle(Json.yazi(o, "baslik", "Olmadı"), Json.yazi(o, "mesaj", ""), "minecraft:barrier", Cizim0.KIRMIZI);
			}
			case "bildirim" -> Bildirimler.ekle(Json.yazi(o, "baslik", ""), Json.yazi(o, "metin", ""), Json.yazi(o, "ikon", ""),
					tr.ekora.istemci.client.ekran.Cizim.renk(Json.yazi(o, "renk", null), Cizim0.MOR));
			case "kapat" -> {
				if (acik instanceof MerkezEkrani || acik instanceof CarkEkrani || acik instanceof DolapEkrani || acik instanceof KasaEkrani) mc.gui.setScreen(null);
			}
			default -> { }
		}
	}

	/** Renk sabitleri (ekran paketine bağımlı olmadan). */
	static final class Cizim0 {
		static final int MOR = 0xFFA855F7, KIRMIZI = 0xFFF87171;
	}
}
