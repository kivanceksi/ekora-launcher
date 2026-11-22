package tr.ekora.istemci.client.ekran;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;

import tr.ekora.istemci.client.Ekora;
import tr.ekora.istemci.client.Json;

/**
 * Ekora Menüsü (K tuşu): solda oyuncunun 3B görünümü ve bakiyeleri, sağda kartlar.
 * Sabit kartlar (dolap, çark, kasa) özel ekranları açar; diğer kartları sunucu gönderir (komut kısayolları).
 */
public final class MenuEkrani extends EkoraEkran {
	private record Kart(Dugme d, ItemStack ikon, String baslik, String alt, int renk) { }

	private JsonObject veri;
	private final List<Kart> kartlar = new ArrayList<>();

	public MenuEkrani(JsonObject veri) {
		super("Menü");
		guncelle(veri);
	}

	public void guncelle(JsonObject v) {
		this.veri = v;
		dugmeler.clear();
		kartlar.clear();
		long cark = Json.sayi(v, "cark", 0), kasa = Json.sayi(v, "kasa", 0);
		kart(() -> Ekora.istek("dolap"), "minecraft:leather_chestplate", "Kozmetik Dolabı", Json.sayi(v, "kozmetik", 0) + " kozmetik", Cizim.renk("#EC4899", Cizim.MOR));
		kart(() -> Ekora.istek("cark"), "minecraft:sunflower", "Şans Çarkı", cark > 0 ? cark + " çevirme hakkı!" : "Hakkını kontrol et", Cizim.ALTIN);
		kart(() -> Ekora.istek("kasa"), "minecraft:ender_chest", "Kıvılcım Kasası", kasa > 0 ? kasa + " bedava açma" : "Rastgele kozmetik", Cizim.MOR);
		for (JsonObject k : Json.liste(v, "kisayollar")) {
			String komut = Json.yazi(k, "id", "");
			if (komut.isEmpty()) continue;
			kart(() -> Ekora.istek("kisayol", "id", komut), Json.yazi(k, "ikon", "minecraft:compass"), Json.yazi(k, "ad", komut),
					Json.yazi(k, "aciklama", ""), Cizim.renk(Json.yazi(k, "renk", null), Cizim.CAMGOBEGI));
		}
	}

	private void kart(Runnable r, String ikon, String baslik, String alt, int renk) {
		kartlar.add(new Kart(dugme(r), Cizim.esya(ikon), baslik, alt, renk));
	}

	@Override
	protected int genislik() { return 400; }

	@Override
	protected int yukseklik() { return 236; }

	@Override
	protected void icerik(GuiGraphicsExtractor g, int mx, int my, float delta) {
		// sol sütun: oyuncu
		int sx = px + 10, sy = py + 30, sw = 112, sh = ph - 40;
		Cizim.panel(g, sx, sy, sw, sh, Cizim.ARKA2, Cizim.KENAR);
		Cizim.degradeY(g, sx + 1, sy + 1, sw - 2, 50, Cizim.alfa(Cizim.MOR, 0.25f), 0x00000000);
		String ad = Json.yazi(veri, "ad", this.minecraft.player != null ? this.minecraft.player.getName().getString() : "");
		g.centeredText(this.font, ad, sx + sw / 2, sy + 6, Cizim.YAZI);
		String rutbe = Json.yazi(veri, "rutbe", "");
		if (!rutbe.isEmpty()) g.centeredText(this.font, rutbe, sx + sw / 2, sy + 16, Cizim.renk(Json.yazi(veri, "rutbeRenk", null), Cizim.ALTIN));
		if (this.minecraft.player != null) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, sx + 8, sy + 26, sx + sw - 8, sy + sh - 52, 34, 0.0625F, mx, my, this.minecraft.player);
		}
		int by = sy + sh - 48;
		satir(g, sx + 6, by, sw - 12, "✦ Kıvılcım", Cizim.sayi(Json.sayi(veri, "kivilcim", 0)), Cizim.ALTIN);
		satir(g, sx + 6, by + 15, sw - 12, "◆ Ekora Puanı", Cizim.sayi(Json.sayi(veri, "ep", 0)), Cizim.CAMGOBEGI);
		long seviye = Json.sayi(veri, "sezonSeviye", -1);
		if (seviye >= 0) {
			long xp = Json.sayi(veri, "sezonXp", 0), gerek = Math.max(1, Json.sayi(veri, "sezonGerek", 1));
			g.text(this.font, "Sezon Sv " + seviye, sx + 6, by + 31, Cizim.SOLUK);
			g.fill(sx + 6, by + 41, sx + sw - 6, by + 44, 0xFF262A40);
			Cizim.degradeX(g, sx + 6, by + 41, (int) ((sw - 12) * Math.min(1.0, (double) xp / gerek)), 3, Cizim.MOR, Cizim.CAMGOBEGI);
		}
		// sağ: kartlar (2 sütun)
		int gx = sx + sw + 8, gy = sy, gw = px + pw - 10 - gx;
		int kw = (gw - 6) / 2, kh = 34;
		boolean gunluk = Json.mantik(veri, "gunlukHazir", false);
		if (gunluk) {
			Cizim.panel(g, gx, gy, gw, 16, Cizim.koyu(Cizim.YESIL, 0.75f), Cizim.koyu(Cizim.YESIL, 0.3f));
			g.text(this.font, "★ Günlük ödülün hazır! Ödül Merkezi'nden al.", gx + 6, gy + 4, Cizim.YESIL);
			gy += 20;
		}
		for (int i = 0; i < kartlar.size(); i++) {
			Kart k = kartlar.get(i);
			int x = gx + (i % 2) * (kw + 6), y = gy + (i / 2) * (kh + 6);
			if (y + kh > py + ph - 8) { k.d().aktif = false; continue; }
			k.d().aktif = true;
			k.d().yer(x, y, kw, kh);
			kartCiz(g, k.d(), k.ikon(), k.baslik(), k.alt(), k.renk(), mx, my);
		}
		g.text(this.font, "§8K: Ekora menüsü  •  ESC: kapat", px + pw - 26 - this.font.width("K: Ekora menüsü  •  ESC: kapat"), py + 9, Cizim.SOLUK);
	}

	private void satir(GuiGraphicsExtractor g, int x, int y, int w, String etiket, String deger, int renk) {
		Cizim.panel(g, x, y, w, 13, 0xFF161A2B, 0);
		g.text(this.font, etiket, x + 4, y + 3, Cizim.SOLUK);
		g.text(this.font, deger, x + w - 4 - this.font.width(deger), y + 3, renk);
	}
}
