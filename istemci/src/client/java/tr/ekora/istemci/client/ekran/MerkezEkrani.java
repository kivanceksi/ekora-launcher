package tr.ekora.istemci.client.ekran;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import tr.ekora.istemci.client.Ayarlar;
import tr.ekora.istemci.client.Ekora;
import tr.ekora.istemci.client.Json;

/**
 * Ekora Merkezi (K): oyun içi her şeye tek yerden erişim. Solda sekmeler, sağda içerik.
 * Sekmeler: Ana Sayfa, Oyunlar, Ödüller, Sezon Kartı, Kozmetik (dolap), Şans Çarkı, Kasa, Profil, Ayarlar.
 * Veriyi sunucu tek pakette yollar ("menu"); ödül alma vb. istekten sonra sunucu yeni paketi yollar ve ekran yerinde güncellenir.
 */
public final class MerkezEkrani extends EkoraEkran {
	private record Sekme(String id, String ad, ItemStack ikon, int renk, boolean baglanti) { }

	private static final List<Sekme> SEKMELER = List.of(
			new Sekme("ana", "Ana Sayfa", Cizim.esya("minecraft:beacon"), Cizim.MOR, false),
			new Sekme("oyunlar", "Oyunlar", Cizim.esya("minecraft:diamond_sword"), 0xFFF87171, false),
			new Sekme("oduller", "Ödüller", Cizim.esya("minecraft:sunflower"), Cizim.ALTIN, false),
			new Sekme("sezon", "Sezon Kartı", Cizim.esya("minecraft:experience_bottle"), Cizim.YESIL, false),
			new Sekme("kozmetik", "Kozmetik", Cizim.esya("minecraft:leather_chestplate"), 0xFFEC4899, true),
			new Sekme("cark", "Şans Çarkı", Cizim.esya("minecraft:clock"), 0xFFFACC15, true),
			new Sekme("kasa", "Kıvılcım Kasası", Cizim.esya("minecraft:ender_chest"), 0xFFA855F7, true),
			new Sekme("profil", "Profil", Cizim.esya("minecraft:name_tag"), Cizim.CAMGOBEGI, false),
			new Sekme("ayarlar", "Ayarlar", Cizim.esya("minecraft:comparator"), 0xFF94A3B8, false));

	private JsonObject v = new JsonObject();
	private String sekme = "ana";
	private final Map<String, Float> kaydirma = new HashMap<>();
	private final Map<String, Dugme> havuz = new HashMap<>();
	private int icX, icY, icW, icH;
	private int icerikYuksekligi;
	private final Map<String, ItemStack> ikonOnbellek = new HashMap<>();

	public MerkezEkrani(JsonObject veri, String ilkSekme) {
		super("Merkez");
		guncelle(veri, ilkSekme);
	}

	public void guncelle(JsonObject veri, String yeniSekme) {
		this.v = veri;
		ikonOnbellek.clear();
		if (yeniSekme != null && !yeniSekme.isEmpty() && SEKMELER.stream().anyMatch(s -> s.id().equals(yeniSekme))) {
			if (!yeniSekme.equals(sekme)) kaydirma.remove(yeniSekme);
			sekme = yeniSekme;
		}
	}

	@Override
	protected boolean merkezDugmesi() { return false; }

	@Override
	protected int genislik() { return 470; }

	@Override
	protected int yukseklik() { return 262; }

	private ItemStack ikon(String id) {
		return ikonOnbellek.computeIfAbsent(id == null ? "" : id, Cizim::esya);
	}

	/** Kalıcı kimlikli düğme: vurgu animasyonu kareler arasında korunur. */
	private Dugme dg(String anahtar, Runnable eylem) {
		return havuz.computeIfAbsent(anahtar, k -> dugme(eylem));
	}

	/** İçerik alanındaki düğme: kaydırılmış konum, görünmüyorsa tıklanmaz. */
	private Dugme ic(String anahtar, Runnable eylem, int x, int y, int w, int h) {
		Dugme d = dg(anahtar, eylem).yer(x, y, w, h);
		d.aktif = y + h > icY && y < icY + icH;
		return d;
	}

	private float kay() { return kaydirma.getOrDefault(sekme, 0f); }

	// ================================================================== çizim
	@Override
	protected void icerik(GuiGraphicsExtractor g, int mx, int my, float delta) {
		for (Dugme d : havuz.values()) d.aktif = false;
		// üst sağ: çevrimiçi
		String cev = "● " + Json.sayi(v, "cevrimici", 0) + " çevrimiçi";
		g.text(this.font, cev, px + pw - 26 - this.font.width(cev), py + 9, Cizim.YESIL);

		// sol: sekmeler
		int sx = px + 8, sy = py + 28, sw = Math.min(104, pw / 4 + 4);
		Cizim.panel(g, sx, sy, sw, ph - 36, 0xC0101425, Cizim.KENAR);
		int ty = sy + 4;
		int sh = Math.max(16, Math.min(20, (ph - 44) / SEKMELER.size()));
		for (Sekme s : SEKMELER) {
			Dugme d = dg("sekme:" + s.id(), () -> sekmeSec(s));
			d.yer(sx + 3, ty, sw - 6, sh - 2);
			d.aktif = true;
			boolean sec = s.id().equals(sekme);
			boolean ust = d.ustunde(mx, my);
			d.vurgu += ((ust ? 1f : 0f) - d.vurgu) * 0.3f;
			int arka = sec ? Cizim.koyu(s.renk(), 0.72f) : Cizim.karis(0x00000000, 0x30FFFFFF, d.vurgu);
			Cizim.panel(g, d.x, d.y, d.w, d.h, arka, sec ? Cizim.alfa(s.renk(), 0.8f) : 0);
			if (sec) g.fill(d.x + 1, d.y + 2, d.x + 3, d.y + d.h - 2, s.renk());
			g.pose().pushMatrix();
			g.pose().translate(d.x + 5, d.y + (d.h - 12) / 2f);
			g.pose().scale(0.75f, 0.75f);
			g.fakeItem(s.ikon(), 0, 0);
			g.pose().popMatrix();
			String rozet = rozetMetni(s.id());
			int rw = rozet == null ? 0 : this.font.width(rozet) + 6;
			sigdirarakYaz(g, s.ad() + (s.baglanti() ? " ›" : ""), d.x + 20, d.y + (d.h - 8) / 2, d.w - 24 - rw, sec ? 0xFFFFFFFF : Cizim.YAZI, 0.7f, false);
			if (rozet != null) {
				int bx = d.x + d.w - rw - 2, by = d.y + (d.h - 10) / 2;
				Cizim.panel(g, bx, by, rw, 10, s.renk(), 0);
				g.text(this.font, rozet, bx + 3, by + 1, 0xFF101322);
			}
			ty += sh;
		}

		// sağ: içerik
		icX = sx + sw + 8;
		icY = py + 28;
		icW = px + pw - 8 - icX;
		icH = ph - 36;
		g.enableScissor(icX, icY, icX + icW, icY + icH);
		int y0 = icY - Math.round(kay());
		icerikYuksekligi = switch (sekme) {
			case "oyunlar" -> oyunlar(g, mx, my, y0);
			case "oduller" -> oduller(g, mx, my, y0);
			case "sezon" -> sezon(g, mx, my, icY);
			case "profil" -> profil(g, mx, my, y0);
			case "ayarlar" -> ayarlar(g, mx, my, y0);
			default -> ana(g, mx, my, y0);
		};
		g.disableScissor();
		// kaydırma çubuğu (dikey sekmeler)
		if (!"sezon".equals(sekme) && icerikYuksekligi > icH) {
			float oran = icH / (float) icerikYuksekligi;
			int bh = Math.max(14, (int) (icH * oran));
			int by = icY + (int) ((icH - bh) * (kay() / Math.max(1f, icerikYuksekligi - icH)));
			g.fill(icX + icW + 2, icY, icX + icW + 4, icY + icH, 0x40FFFFFF);
			g.fill(icX + icW + 2, by, icX + icW + 4, by + bh, Cizim.MOR);
		}
	}

	private String rozetMetni(String id) {
		return switch (id) {
			case "oduller" -> Json.mantik(v, "gunlukHazir", false) ? "!" : null;
			case "sezon" -> { long n = Json.sayi(alt("sezon"), "bekleyen", 0); yield n > 0 ? String.valueOf(n) : null; }
			case "cark" -> { long n = Json.sayi(v, "cark", 0); yield n > 0 ? String.valueOf(n) : null; }
			case "kasa" -> { long n = Json.sayi(v, "kasa", 0); yield n > 0 ? String.valueOf(n) : null; }
			default -> null;
		};
	}

	private JsonObject alt(String k) {
		return v.has(k) && v.get(k).isJsonObject() ? v.getAsJsonObject(k) : new JsonObject();
	}

	private void sekmeSec(Sekme s) {
		switch (s.id()) {
			case "kozmetik" -> Ekora.istek("dolap");
			case "cark" -> Ekora.istek("cark");
			case "kasa" -> Ekora.istek("kasa");
			default -> sekme = s.id();
		}
	}

	private void bolumBasligi(GuiGraphicsExtractor g, String yazi, int x, int y, int renk) {
		g.fill(x, y + 1, x + 2, y + 8, renk);
		g.text(this.font, "§l" + yazi, x + 6, y, Cizim.YAZI);
	}

	private void cubuk(GuiGraphicsExtractor g, int x, int y, int w, int h, double oran, int c1, int c2) {
		g.fill(x, y, x + w, y + h, 0xFF22263A);
		int f = (int) Math.round(w * Math.max(0, Math.min(1, oran)));
		if (f > 0) Cizim.degradeX(g, x, y, f, h, c1, c2);
	}

	private String modAdi() {
		for (JsonObject o : Json.liste(v, "oyunlar")) if (Json.mantik(o, "burada", false)) return Json.yazi(o, "ad", "");
		String m = Json.yazi(v, "mod", "hub");
		return "hub".equals(m) ? "Lobi" : m;
	}

	// ------------------------------------------------------------------ Ana Sayfa
	private int ana(GuiGraphicsExtractor g, int mx, int my, int y0) {
		int x = icX, w = icW - 6, y = y0;
		int sagW = 76;
		int solW = w - sagW - 6;
		String ad = Json.yazi(v, "ad", this.minecraft.player != null ? this.minecraft.player.getName().getString() : "");
		g.pose().pushMatrix();
		g.pose().translate(x, y + 2);
		g.pose().scale(1.35f, 1.35f);
		g.text(this.font, "Merhaba, " + ad + "!", 0, 0, 0xFFFFFFFF);
		g.pose().popMatrix();
		int cy = y + 17;
		int cx = x;
		String rutbe = Json.yazi(v, "rutbe", "");
		if (!rutbe.isEmpty()) cx += rozet(g, cx, cy, rutbe, Cizim.renk(Json.yazi(v, "rutbeRenk", null), Cizim.ALTIN)) + 4;
		rozet(g, cx, cy, "◉ " + modAdi(), Cizim.CAMGOBEGI);

		// sağ: oyuncu
		int ox = x + w - sagW, oy = y;
		Cizim.panel(g, ox, oy, sagW, 92, 0xC0141828, Cizim.KENAR);
		Cizim.degradeY(g, ox + 1, oy + 40, sagW - 2, 51, 0x00000000, Cizim.alfa(Cizim.MOR, 0.28f));
		if (this.minecraft.player != null && oy + 92 > icY && oy < icY + icH) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, ox + 4, oy + 4, ox + sagW - 4, oy + 90, 32, 0.0625F, mx, my, this.minecraft.player);
		}

		// bakiyeler
		y += 34;
		int tw = (solW - 8) / 3;
		JsonObject sz = alt("sezon");
		karo(g, x, y, tw, "minecraft:blaze_powder", "Kıvılcım", "✦ " + Cizim.sayi(Json.sayi(v, "kivilcim", 0)), Cizim.ALTIN);
		karo(g, x + tw + 4, y, tw, "minecraft:emerald", "Ekora Puanı", Cizim.sayi(Json.sayi(v, "ep", 0)), Cizim.CAMGOBEGI);
		long sv = Json.sayi(sz, "seviye", Json.sayi(v, "sezonSeviye", 0));
		karo(g, x + (tw + 4) * 2, y, tw, "minecraft:experience_bottle", "Sezon", "Sv " + sv + (Json.mantik(sz, "elit", false) ? " ★" : ""), Cizim.YESIL);

		// bugün
		y += 34;
		JsonObject od = alt("odul");
		Cizim.panel(g, x, y, solW, 42, 0xC0141828, Json.mantik(v, "gunlukHazir", false) ? Cizim.alfa(Cizim.YESIL, 0.7f) : Cizim.KENAR);
		boolean hazir = Json.mantik(v, "gunlukHazir", false);
		long siradaki = Json.sayi(od, "siradaki", 1);
		g.text(this.font, hazir ? "§a★ Günlük ödülün hazır!" : "§7Günlük ödül alındı", x + 6, y + 4, Cizim.YAZI);
		String alt = hazir ? siradaki + ". gün • seri " + Json.sayi(od, "seri", 0) + " 🔥" : "Yarın " + (siradaki + 1) + ". gün • seriyi bozma!";
		sigdirarakYaz(g, alt, x + 6, y + 14, solW - 80, Cizim.SOLUK, 0.75f, false);
		Dugme al = ic("ana:gunluk", () -> { if (Json.mantik(v, "gunlukHazir", false)) Ekora.istek("gunluk_al"); else sekme = "oduller"; }, x + solW - 70, y + 6, 64, 18);
		dugmeCiz(g, al, hazir ? "AL!" : "Ödüller", hazir ? Cizim.YESIL : 0xFF4B5563, mx, my);
		// oynama ilerlemesi
		long dk = Json.sayi(od, "dakika", 0);
		JsonObject sonraki = null;
		for (JsonObject t : Json.liste(od, "taslar")) if (!"alindi".equals(Json.yazi(t, "durum", "")) && dk < Json.sayi(t, "dk", 0)) { sonraki = t; break; }
		if (sonraki != null) {
			long hedef = Json.sayi(sonraki, "dk", 1);
			cubuk(g, x + 6, y + 26, solW - 12, 3, dk / (double) hedef, Cizim.CAMGOBEGI, Cizim.MOR);
			sigdirarakYaz(g, "Bugün " + dk + " dk • " + hedef + " dk'da: " + Json.yazi(sonraki, "metin", ""), x + 6, y + 31, solW - 12, Cizim.SOLUK, 0.6f, false);
		} else {
			sigdirarakYaz(g, "Bugün " + dk + " dk oynadın • bütün taşlar tamam ✔", x + 6, y + 28, solW - 12, Cizim.YESIL, 0.7f, false);
		}

		// hızlı erişim
		y = Math.max(y + 50, oy + 98);
		bolumBasligi(g, "Hızlı erişim", x, y, Cizim.MOR);
		y += 13;
		int sutun = w >= 300 ? 3 : 2;
		int kw = (w - (sutun - 1) * 5) / sutun, kh = 30;
		List<Object[]> kartlar = new ArrayList<>();
		long cark = Json.sayi(v, "cark", 0), kasa = Json.sayi(v, "kasa", 0);
		kartlar.add(new Object[]{"k:cark", (Runnable) () -> Ekora.istek("cark"), "minecraft:clock", "Şans Çarkı", cark > 0 ? cark + " çevirme hakkı" : "Hakkını kontrol et", 0xFFFACC15});
		kartlar.add(new Object[]{"k:kasa", (Runnable) () -> Ekora.istek("kasa"), "minecraft:ender_chest", "Kıvılcım Kasası", kasa > 0 ? kasa + " bedava açma" : "Rastgele kozmetik", Cizim.MOR});
		kartlar.add(new Object[]{"k:dolap", (Runnable) () -> Ekora.istek("dolap"), "minecraft:leather_chestplate", "Kozmetik Dolabı", Json.sayi(v, "kozmetik", 0) + " kozmetik", 0xFFEC4899});
		kartlar.add(new Object[]{"k:oyunlar", (Runnable) () -> sekme = "oyunlar", "minecraft:diamond_sword", "Oyunlar", Json.liste(v, "oyunlar").size() + " mod", 0xFFF87171});
		long bek = Json.sayi(sz, "bekleyen", 0);
		kartlar.add(new Object[]{"k:sezon", (Runnable) () -> sekme = "sezon", "minecraft:experience_bottle", "Sezon Kartı", bek > 0 ? bek + " ödül seni bekliyor" : Json.yazi(sz, "ad", "Sezon"), Cizim.YESIL});
		for (JsonObject k : Json.liste(v, "kisayollar")) {
			String id = Json.yazi(k, "id", "");
			if (id.isEmpty()) continue;
			kartlar.add(new Object[]{"ks:" + id, (Runnable) () -> Ekora.istek("kisayol", "id", id), Json.yazi(k, "ikon", "minecraft:compass"), Json.yazi(k, "ad", id),
					Json.yazi(k, "aciklama", ""), Cizim.renk(Json.yazi(k, "renk", null), Cizim.CAMGOBEGI)});
		}
		for (int i = 0; i < kartlar.size(); i++) {
			Object[] k = kartlar.get(i);
			int kx = x + (i % sutun) * (kw + 5), ky = y + (i / sutun) * (kh + 5);
			Dugme d = ic((String) k[0], (Runnable) k[1], kx, ky, kw, kh);
			kartCiz(g, d, ikon((String) k[2]), (String) k[3], (String) k[4], (int) k[5], mx, my);
		}
		y += ((kartlar.size() + sutun - 1) / sutun) * (kh + 5);
		return y - y0 + 4;
	}

	private void karo(GuiGraphicsExtractor g, int x, int y, int w, String ikon, String etiket, String deger, int renk) {
		Cizim.panel(g, x, y, w, 28, 0xC0141828, Cizim.koyu(renk, 0.45f));
		Cizim.degradeX(g, x + 1, y + 1, w - 2, 26, Cizim.alfa(renk, 0.14f), 0x00000000);
		g.fakeItem(ikon(ikon), x + 4, y + 6);
		sigdirarakYaz(g, etiket, x + 23, y + 5, w - 26, Cizim.SOLUK, 0.65f, false);
		sigdirarakYaz(g, deger, x + 23, y + 15, w - 26, renk, 0.6f, false);
	}

	// ------------------------------------------------------------------ Oyunlar
	private int oyunlar(GuiGraphicsExtractor g, int mx, int my, int y0) {
		int x = icX, w = icW - 6, y = y0;
		bolumBasligi(g, "Oyun modları", x, y, 0xFFF87171);
		String sag = "Tıkla ve ışınlan";
		g.text(this.font, sag, x + w - this.font.width(sag), y, Cizim.SOLUK);
		y += 14;
		List<JsonObject> l = Json.liste(v, "oyunlar");
		if (l.isEmpty()) { g.text(this.font, "Oyun listesi yüklenemedi.", x, y + 10, Cizim.SOLUK); return 40; }
		int sutun = w >= 280 ? 2 : 1;
		int kw = (w - (sutun - 1) * 6) / sutun, kh = 50;
		for (int i = 0; i < l.size(); i++) {
			JsonObject o = l.get(i);
			String id = Json.yazi(o, "id", "");
			int kx = x + (i % sutun) * (kw + 6), ky = y + (i / sutun) * (kh + 6);
			Dugme d = ic("oyun:" + id, () -> Ekora.istek("oyun", "id", id), kx, ky, kw, kh);
			int renk = Cizim.renk(Json.yazi(o, "renk", null), Cizim.CAMGOBEGI);
			boolean burada = Json.mantik(o, "burada", false);
			boolean ust = d.aktif && d.ustunde(mx, my);
			d.vurgu += ((ust ? 1f : 0f) - d.vurgu) * 0.3f;
			int yk = (int) (d.vurgu * 2);
			Cizim.panel(g, kx, ky - yk, kw, kh, Cizim.karis(0xE0141828, Cizim.koyu(renk, 0.72f), d.vurgu * 0.8f), burada ? renk : Cizim.karis(Cizim.KENAR, renk, d.vurgu));
			Cizim.degradeX(g, kx + 1, ky - yk + 1, kw / 2, kh - 2, Cizim.alfa(renk, 0.2f), 0x00000000);
			g.fill(kx + 1, ky - yk + 3, kx + 3, ky - yk + kh - 3, renk);
			g.pose().pushMatrix();
			g.pose().translate(kx + 8, ky - yk + 8);
			g.pose().scale(1.5f, 1.5f);
			g.fakeItem(ikon(Json.yazi(o, "ikon", "minecraft:grass_block")), 0, 0);
			g.pose().popMatrix();
			int tx = kx + 36, tw = kw - 40;
			String ad = Json.yazi(o, "ad", id);
			String etiket = Json.yazi(o, "etiket", "");
			int ew = etiket.isEmpty() ? 0 : this.font.width(etiket) + 8;
			sigdirarakYaz(g, "§l" + ad, tx, ky - yk + 6, tw - ew, renk, 0.75f, false);
			if (!etiket.isEmpty()) {
				Cizim.panel(g, kx + kw - ew - 4, ky - yk + 4, ew, 11, Cizim.ALTIN, 0);
				g.text(this.font, etiket, kx + kw - ew, ky - yk + 6, 0xFF101322);
			}
			List<String> ac = Json.yazilar(o, "aciklama");
			for (int j = 0; j < Math.min(2, ac.size()); j++) sigdirarakYaz(g, ac.get(j), tx, ky - yk + 17 + j * 9, tw, Cizim.SOLUK, 0.6f, false);
			long n = Json.sayi(o, "oynayan", 0);
			String alt = burada ? "◉ Buradasın" : "● " + n + " oynuyor";
			g.text(this.font, alt, tx, ky - yk + kh - 11, burada ? renk : n > 0 ? Cizim.YESIL : 0xFF6B7280);
			if (ust) g.text(this.font, "▶", kx + kw - 10, ky - yk + kh - 11, renk);
		}
		y += ((l.size() + sutun - 1) / sutun) * (kh + 6);
		return y - y0;
	}

	// ------------------------------------------------------------------ Ödüller
	private int oduller(GuiGraphicsExtractor g, int mx, int my, int y0) {
		int x = icX, w = icW - 6, y = y0;
		JsonObject od = alt("odul");
		bolumBasligi(g, "Günlük giriş serisi", x, y, Cizim.ALTIN);
		String seri = "🔥 " + Json.sayi(od, "seri", 0) + " gün";
		g.text(this.font, seri, x + w - this.font.width(seri), y, 0xFFFB923C);
		y += 13;
		List<JsonObject> gunler = Json.liste(od, "gunler");
		int gw = gunler.isEmpty() ? 40 : (w - (gunler.size() - 1) * 4) / gunler.size();
		long simdi = System.currentTimeMillis();
		for (int i = 0; i < gunler.size(); i++) {
			JsonObject gn = gunler.get(i);
			String durum = Json.yazi(gn, "durum", "kilitli");
			boolean buyuk = Json.mantik(gn, "buyuk", false);
			int gx = x + i * (gw + 4);
			int renk = buyuk ? 0xFFA855F7 : Cizim.ALTIN;
			Dugme d = ic("gun:" + i, () -> { if ("hazir".equals(durum)) Ekora.istek("gunluk_al"); }, gx, y, gw, 58);
			boolean hazir = "hazir".equals(durum), alindi = "alindi".equals(durum);
			float nabiz = hazir ? 0.5f + 0.5f * (float) Math.sin(simdi / 260.0) : 0f;
			int kenar = hazir ? Cizim.karis(renk, 0xFFFFFFFF, nabiz * 0.5f) : alindi ? Cizim.koyu(Cizim.YESIL, 0.3f) : Cizim.KENAR;
			Cizim.panel(g, gx, y, gw, 58, alindi ? 0xC0132A1F : hazir ? Cizim.koyu(renk, 0.7f) : 0xC0141828, kenar);
			if (buyuk) Cizim.degradeY(g, gx + 1, y + 1, gw - 2, 56, Cizim.alfa(0xFFA855F7, 0.25f), 0x00000000);
			g.centeredText(this.font, Json.sayi(gn, "gun", i + 1) + ". gün", gx + gw / 2, y + 4, hazir ? 0xFFFFFFFF : Cizim.SOLUK);
			g.pose().pushMatrix();
			g.pose().translate(gx + gw / 2f - (buyuk ? 12 : 8), y + 15);
			if (buyuk) g.pose().scale(1.5f, 1.5f);
			g.fakeItem(ikon(Json.yazi(gn, "ikon", "minecraft:gold_nugget")), 0, 0);
			g.pose().popMatrix();
			sigdirarakYaz(g, Json.yazi(gn, "metin", ""), gx + 2, y + 39, gw - 4, Cizim.YAZI, 0.5f, true);
			if (alindi) {
				g.fill(gx + 1, y + 1, gx + gw - 1, y + 57, 0x70101322);
				g.centeredText(this.font, "§a✔", gx + gw / 2, y + 20, Cizim.YESIL);
			}
			if (hazir) g.centeredText(this.font, "§lAL", gx + gw / 2, y + 49, Cizim.karis(Cizim.YESIL, 0xFFFFFFFF, nabiz));
			if (d.aktif && d.ustunde(mx, my)) g.setTooltipForNextFrame(this.font, Component.literal(Json.yazi(gn, "metin", "") + (hazir ? " §a(Tıkla: al)" : alindi ? " §7(alındı)" : " §8(seriyi bozmadan gel)")), mx, my);
		}
		y += 66;

		// oynadıkça ödül
		long dk = Json.sayi(od, "dakika", 0);
		bolumBasligi(g, "Oynadıkça ödül", x, y, Cizim.CAMGOBEGI);
		String bugun = "Bugün " + dk + " dk";
		g.text(this.font, bugun, x + w - this.font.width(bugun), y, Cizim.CAMGOBEGI);
		y += 14;
		List<JsonObject> taslar = Json.liste(od, "taslar");
		long enCok = taslar.isEmpty() ? 180 : Json.sayi(taslar.get(taslar.size() - 1), "dk", 180);
		int bx = x + 8, bw = w - 16;
		Cizim.panel(g, x, y, w, 52, 0xC0141828, Cizim.KENAR);
		cubuk(g, bx, y + 24, bw, 4, dk / (double) enCok, Cizim.CAMGOBEGI, Cizim.MOR);
		for (JsonObject t : taslar) {
			long tdk = Json.sayi(t, "dk", 0);
			int tx = bx + (int) (bw * Math.min(1.0, tdk / (double) enCok));
			boolean alindi = "alindi".equals(Json.yazi(t, "durum", ""));
			Cizim.panel(g, tx - 9, y + 4, 18, 18, alindi ? 0xFF14532D : dk >= tdk ? Cizim.koyu(Cizim.ALTIN, 0.6f) : 0xFF1C2033, alindi ? Cizim.YESIL : Cizim.KENAR);
			g.fakeItem(ikon(Json.yazi(t, "ikon", "minecraft:gold_nugget")), tx - 8, y + 5);
			g.centeredText(this.font, (alindi ? "§a" : "§7") + tdk + "dk", tx, y + 32, Cizim.YAZI);
			if (Cizim.icinde(mx, my, tx - 9, y + 4, 18, 18) && my >= icY && my < icY + icH)
				g.setTooltipForNextFrame(this.font, Component.literal(tdk + " dk: " + Json.yazi(t, "metin", "") + (alindi ? " §a✔" : "")), mx, my);
		}
		sigdirarakYaz(g, "Her " + Json.sayi(od, "aralikDk", 10) + " dk +" + Json.sayi(od, "aralikKivilcim", 200) + " ✦ • her " + Json.sayi(od, "carkBonusDk", 60) + " dk +1 çark (günde 3) • AFK sayılmaz",
				x + 6, y + 42, w - 12, Cizim.SOLUK, 0.6f, true);
		y += 60;

		// çark ve kasa
		int hw = (w - 6) / 2;
		long cark = Json.sayi(v, "cark", 0), kasa = Json.sayi(v, "kasa", 0);
		kartCiz(g, ic("od:cark", () -> Ekora.istek("cark"), x, y, hw, 30), ikon("minecraft:clock"), "Şans Çarkı", cark > 0 ? cark + " çevirme hakkın var" : "Kıvılcım ile çevir", 0xFFFACC15, mx, my);
		kartCiz(g, ic("od:kasa", () -> Ekora.istek("kasa"), x + hw + 6, y, hw, 30), ikon("minecraft:ender_chest"), "Kıvılcım Kasası", kasa > 0 ? kasa + " bedava açma" : "Rastgele kozmetik", Cizim.MOR, mx, my);
		y += 36;
		return y - y0;
	}

	// ------------------------------------------------------------------ Sezon Kartı (yatay kaydırma)
	private static final int SV_W = 46;

	private int sezon(GuiGraphicsExtractor g, int mx, int my, int yUst) {
		int x = icX, w = icW, y = yUst;
		JsonObject sz = alt("sezon");
		long sv = Json.sayi(sz, "seviye", 0), maks = Json.sayi(sz, "maks", 30), top = Json.sayi(sz, "toplamXp", 0), per = Math.max(1, Json.sayi(sz, "seviyeXp", 150));
		boolean elit = Json.mantik(sz, "elit", false);
		// başlık şeridi
		Cizim.panel(g, x, y, w, 40, 0xC0141828, Cizim.KENAR);
		Cizim.degradeX(g, x + 1, y + 1, w - 2, 38, Cizim.alfa(Cizim.CAMGOBEGI, 0.16f), Cizim.alfa(Cizim.MOR, 0.16f));
		g.text(this.font, "§l" + Json.yazi(sz, "ad", "Sezon"), x + 6, y + 5, 0xFFFFFFFF);
		String svy = "Seviye " + sv + "/" + maks;
		g.text(this.font, svy, x + 6, y + 16, Cizim.CAMGOBEGI);
		long icXp = sv >= maks ? per : top % per;
		cubuk(g, x + 10 + this.font.width(svy), y + 18, Math.max(30, w / 3), 4, icXp / (double) per, Cizim.CAMGOBEGI, Cizim.MOR);
		g.text(this.font, "§7" + icXp + "/" + per + " XP", x + 14 + this.font.width(svy) + Math.max(30, w / 3), y + 16, Cizim.SOLUK);
		sigdirarakYaz(g, "Oynadıkça (dk başına 1), günlük ödülle, galibiyetle ve çarkla XP kazan.", x + 6, y + 28, w - 120, Cizim.SOLUK, 0.6f, false);
		long bek = Json.sayi(sz, "bekleyen", 0);
		Dugme hepsi = ic("sz:hepsi", () -> Ekora.istek("sezon_hepsi"), x + w - 108, y + 4, 102, 14);
		hepsi.aktif = bek > 0;
		dugmeCiz(g, hepsi, bek > 0 ? "Hepsini al (" + bek + ")" : "Bekleyen yok", Cizim.YESIL, mx, my);
		if (elit) rozet(g, x + w - 108, y + 22, "★ Elit Kart", Cizim.ALTIN);
		else {
			Dugme ek = ic("sz:elit", () -> Ekora.istek("kisayol", "id", "magaza"), x + w - 108, y + 22, 102, 14);
			dugmeCiz(g, ek, "★ Elit Kart al", Cizim.ALTIN, mx, my);
			if (ek.ustunde(mx, my)) g.setTooltipForNextFrame(this.font, Component.literal("Elit yolun bütün ödülleri + sezona özel kozmetikler" + (Json.yazi(sz, "elitFiyat", "").isEmpty() ? "" : " • " + Json.yazi(sz, "elitFiyat", ""))), mx, my);
		}

		// yol
		int ty = y + 46, th = icH - 46 - 10;
		int etiketW = 46;
		Cizim.panel(g, x, ty, etiketW, th, 0xC0101425, Cizim.KENAR);
		int sat = (th - 22) / 2;
		g.centeredText(this.font, "§bÜCRETSİZ", x + etiketW / 2, ty + 22 + sat / 2 - 4, Cizim.CAMGOBEGI);
		g.centeredText(this.font, "§6ELİT", x + etiketW / 2, ty + 22 + sat + sat / 2 - 4, Cizim.ALTIN);
		int yx = x + etiketW + 4, yw = w - etiketW - 4;
		List<JsonObject> svl = Json.liste(sz, "seviyeler");
		int toplamW = svl.size() * SV_W;
		icerikYuksekligi = toplamW;
		if (!kaydirma.containsKey("sezon")) kaydirma.put("sezon", (float) Math.max(0, Math.min(toplamW - yw, (sv - 2) * SV_W)));
		float k = Math.max(0, Math.min(Math.max(0, toplamW - yw), kay()));
		kaydirma.put("sezon", k);
		g.enableScissor(yx, ty, yx + yw, ty + th);
		Cizim.panel(g, yx, ty, yw, th, 0xA0101425, Cizim.KENAR);
		// seviye çizgisi
		int cizgiY = ty + 11;
		for (int i = 0; i < svl.size(); i++) {
			JsonObject s = svl.get(i);
			long n = Json.sayi(s, "sv", i + 1);
			int cx = yx + i * SV_W - Math.round(k);
			if (cx + SV_W < yx || cx > yx + yw) continue;
			boolean ulasti = sv >= n;
			g.fill(cx, cizgiY - 1, cx + SV_W, cizgiY + 1, ulasti ? Cizim.CAMGOBEGI : 0xFF2A2F48);
			int dx = cx + SV_W / 2;
			Cizim.panel(g, dx - 9, cizgiY - 8, 18, 16, ulasti ? Cizim.koyu(Cizim.CAMGOBEGI, 0.45f) : 0xFF1C2033, n == sv ? 0xFFFFFFFF : ulasti ? Cizim.CAMGOBEGI : Cizim.KENAR);
			g.centeredText(this.font, String.valueOf(n), dx, cizgiY - 4, ulasti ? 0xFFFFFFFF : Cizim.SOLUK);
			odulCipi(g, mx, my, s.has("ucretsiz") ? s.getAsJsonObject("ucretsiz") : new JsonObject(), (int) n, false, cx + 3, ty + 22, SV_W - 6, sat - 4, yx, yw, ty, th);
			odulCipi(g, mx, my, s.has("elit") ? s.getAsJsonObject("elit") : new JsonObject(), (int) n, true, cx + 3, ty + 22 + sat, SV_W - 6, sat - 4, yx, yw, ty, th);
		}
		g.disableScissor(); // iç kırpma kapanır, dış (içerik) kırpması sürer
		// yatay kaydırma çubuğu
		if (toplamW > yw) {
			int bw = Math.max(20, yw * yw / toplamW);
			int bx = yx + (int) ((yw - bw) * (k / Math.max(1f, toplamW - yw)));
			g.fill(yx, ty + th + 3, yx + yw, ty + th + 5, 0x40FFFFFF);
			g.fill(bx, ty + th + 3, bx + bw, ty + th + 5, Cizim.CAMGOBEGI);
			sigdirarakYaz(g, "fare tekerleği: kaydır", yx, ty + th + 6, yw, 0xFF6B7280, 0.55f, true);
		}
		return toplamW;
	}

	private void odulCipi(GuiGraphicsExtractor g, int mx, int my, JsonObject o, int sv, boolean elitYol, int x, int y, int w, int h, int gx, int gw, int gy, int gh) {
		String durum = Json.yazi(o, "durum", "kilitli");
		boolean hazir = "hazir".equals(durum), alindi = "alindi".equals(durum), kilit = "elit".equals(durum);
		int renk = elitYol ? Cizim.ALTIN : Cizim.CAMGOBEGI;
		Dugme d = dg("sv:" + sv + (elitYol ? "e" : "u"), () -> Ekora.istekCok("sezon_al", "sv", String.valueOf(sv), "elit", elitYol ? "1" : "0"));
		d.yer(x, y, w, h);
		d.aktif = hazir && x + w > gx && x < gx + gw;
		boolean ust = Cizim.icinde(mx, my, x, y, w, h) && mx >= gx && mx < gx + gw && my >= gy && my < gy + gh;
		float nabiz = hazir ? 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 260.0) : 0f;
		Cizim.panel(g, x, y, w, h, alindi ? 0xC0132A1F : hazir ? Cizim.koyu(renk, 0.65f) : 0xC0171A2B,
				hazir ? Cizim.karis(renk, 0xFFFFFFFF, nabiz * 0.6f) : ust ? renk : Cizim.koyu(renk, 0.55f));
		g.fakeItem(ikon(Json.yazi(o, "ikon", "minecraft:gold_nugget")), x + w / 2 - 8, y + Math.max(2, h / 2 - 12));
		if (alindi) { g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x70101322); g.text(this.font, "§a✔", x + w - 8, y + 2, Cizim.YESIL); }
		if (kilit) g.text(this.font, "§6🔒", x + w - 9, y + 2, Cizim.ALTIN);
		if (hazir) g.centeredText(this.font, "§lAL", x + w / 2, y + h - 10, 0xFFFFFFFF);
		else sigdirarakYaz(g, Json.yazi(o, "metin", ""), x + 1, y + h - 10, w - 2, alindi ? Cizim.SOLUK : Cizim.YAZI, 0.45f, true);
		if (ust) g.setTooltipForNextFrame(this.font, Component.literal((elitYol ? "§6Elit" : "§bÜcretsiz") + " §7• Sv." + sv + "§r  " + Json.yazi(o, "metin", "")
				+ (hazir ? " §a(Tıkla: al)" : alindi ? " §7(alındı)" : kilit ? " §6(Elit Kart gerekir)" : " §8(seviyeye ulaş)")), mx, my);
	}

	// ------------------------------------------------------------------ Profil
	private int profil(GuiGraphicsExtractor g, int mx, int my, int y0) {
		int x = icX, w = icW - 6, y = y0;
		int ow = Math.min(110, w / 3);
		Cizim.panel(g, x, y, ow, 150, 0xC0141828, Cizim.KENAR);
		Cizim.degradeY(g, x + 1, y + 60, ow - 2, 89, 0x00000000, Cizim.alfa(Cizim.CAMGOBEGI, 0.22f));
		if (this.minecraft.player != null && y + 150 > icY && y < icY + icH) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 6, y + 18, x + ow - 6, y + 146, 48, 0.0625F, mx, my, this.minecraft.player);
		}
		String ad = Json.yazi(v, "ad", "");
		sigdirarakYaz(g, "§l" + ad, x + 4, y + 5, ow - 8, 0xFFFFFFFF, 0.7f, true);
		int rx = x + ow + 8, rw = w - ow - 8;
		bolumBasligi(g, "İstatistikler", rx, y, Cizim.CAMGOBEGI);
		JsonObject pr = alt("profil"), sz = alt("sezon");
		long top = Json.sayi(pr, "toplamDakika", 0);
		String[][] satirlar = {
				{"Rütbe", Json.yazi(v, "rutbe", "Oyuncu")},
				{"Şu an", modAdi()},
				{"Toplam oynama", (top / 60) + " sa " + (top % 60) + " dk"},
				{"Bugün", Json.sayi(pr, "bugunDakika", 0) + " dk"},
				{"Giriş serisi", Json.sayi(pr, "seri", 0) + " gün 🔥"},
				{"Sezon", "Sv " + Json.sayi(sz, "seviye", 0) + (Json.mantik(sz, "elit", false) ? " • Elit ★" : "")},
				{"Kozmetik", String.valueOf(Json.sayi(v, "kozmetik", 0))},
				{"Kıvılcım", "✦ " + Cizim.sayi(Json.sayi(v, "kivilcim", 0))},
				{"Ekora Puanı", Cizim.sayi(Json.sayi(v, "ep", 0))}};
		int sy = y + 13;
		for (int i = 0; i < satirlar.length; i++) {
			Cizim.panel(g, rx, sy, rw, 13, i % 2 == 0 ? 0xA0161A2B : 0xA0121526, 0);
			g.text(this.font, satirlar[i][0], rx + 5, sy + 3, Cizim.SOLUK);
			String d = satirlar[i][1];
			sigdirarakYaz(g, d, rx + rw / 2, sy + 3, rw / 2 - 5, Cizim.YAZI, 0.7f, false);
			sy += 14;
		}
		y = Math.max(sy + 6, y + 156);
		List<JsonObject> ks = Json.liste(v, "kisayollar");
		if (!ks.isEmpty()) {
			bolumBasligi(g, "Bağlantılar", x, y, Cizim.MOR);
			y += 13;
			int sutun = w >= 300 ? 4 : 2;
			int bw = (w - (sutun - 1) * 5) / sutun;
			for (int i = 0; i < ks.size(); i++) {
				JsonObject k = ks.get(i);
				String id = Json.yazi(k, "id", "");
				Dugme d = ic("pr:" + id, () -> Ekora.istek("kisayol", "id", id), x + (i % sutun) * (bw + 5), y + (i / sutun) * 23, bw, 18);
				dugmeCiz(g, d, Json.yazi(k, "ad", id), Cizim.renk(Json.yazi(k, "renk", null), Cizim.MOR), mx, my);
			}
			y += ((ks.size() + sutun - 1) / sutun) * 23;
		}
		return y - y0;
	}

	// ------------------------------------------------------------------ Ayarlar (bu bilgisayarda)
	private int ayarlar(GuiGraphicsExtractor g, int mx, int my, int y0) {
		int x = icX, w = icW - 6, y = y0;
		bolumBasligi(g, "Ekora İstemci ayarları", x, y, 0xFF94A3B8);
		y += 14;
		y = anahtar(g, mx, my, "a:sandik", x, y, w, "Sandık menüleri Ekora görünümünde", "Sunucudaki bütün menüler (oyunlar, mağaza, kasaba…) Ekora tasarımıyla açılır.",
				Ayarlar.sandikGorunumu, () -> Ayarlar.sandikGorunumu = !Ayarlar.sandikGorunumu);
		y = anahtar(g, mx, my, "a:cam", x, y, w, "Süs camlarını gizle", "Menülerdeki boş renkli cam dolgular yerine temiz panel.",
				Ayarlar.camGizle, () -> Ayarlar.camGizle = !Ayarlar.camGizle);
		y = anahtar(g, mx, my, "a:bildirim", x, y, w, "Köşe bildirimleri", "Ödül, kozmetik ve etkinlik bildirimleri sağ üstte.",
				Ayarlar.bildirimler, () -> Ayarlar.bildirimler = !Ayarlar.bildirimler);
		y = anahtar(g, mx, my, "a:ses", x, y, w, "Ekora sesleri", "Menü tıkları, çark ve kasa sesleri.",
				Ayarlar.sesler, () -> Ayarlar.sesler = !Ayarlar.sesler);
		y = anahtar(g, mx, my, "a:anim", x, y, w, "Açılış animasyonu", "Menüler hafifçe büyüyerek açılır.",
				Ayarlar.animasyon, () -> Ayarlar.animasyon = !Ayarlar.animasyon);
		y += 4;
		sigdirarakYaz(g, "K tuşunu Seçenekler → Kontroller → Tuş Atamaları → Ekora bölümünden değiştirebilirsin.", x, y, w, Cizim.SOLUK, 0.65f, false);
		y += 12;
		sigdirarakYaz(g, "Ekora İstemci " + Ekora.surum() + " • Ekora Launcher ile gelir", x, y, w, 0xFF6B7280, 0.65f, false);
		return y - y0 + 12;
	}

	private int anahtar(GuiGraphicsExtractor g, int mx, int my, String id, int x, int y, int w, String baslik, String aciklama, boolean acik, Runnable degistir) {
		Dugme d = ic(id, () -> { degistir.run(); Ayarlar.kaydet(); }, x, y, w, 26);
		boolean ust = d.aktif && d.ustunde(mx, my);
		Cizim.panel(g, x, y, w, 26, ust ? 0xC01C2036 : 0xC0141828, Cizim.KENAR);
		g.text(this.font, baslik, x + 6, y + 4, Cizim.YAZI);
		sigdirarakYaz(g, aciklama, x + 6, y + 15, w - 50, Cizim.SOLUK, 0.6f, false);
		int sx = x + w - 34, sy = y + 8;
		Cizim.panel(g, sx, sy, 26, 11, acik ? Cizim.koyu(Cizim.YESIL, 0.4f) : 0xFF2A2D3A, acik ? Cizim.YESIL : Cizim.KENAR);
		int top = acik ? sx + 15 : sx + 2;
		g.fill(top, sy + 2, top + 9, sy + 9, acik ? 0xFFFFFFFF : Cizim.SOLUK);
		return y + 30;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double yatay, double dikey) {
		if (Cizim.icinde(mx, my, icX, icY, icW + 6, icH)) {
			float maks = "sezon".equals(sekme) ? Math.max(0, icerikYuksekligi - (icW - 50)) : Math.max(0, icerikYuksekligi - icH);
			float adim = "sezon".equals(sekme) ? SV_W * 1.5f : 18f;
			float yeni = Math.max(0, Math.min(maks, kay() - (float) (dikey + yatay) * adim));
			kaydirma.put(sekme, yeni);
			return true;
		}
		return super.mouseScrolled(mx, my, yatay, dikey);
	}
}
