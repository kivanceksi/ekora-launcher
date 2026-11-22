package tr.ekora.istemci.client.ekran;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.gson.JsonObject;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import tr.ekora.istemci.client.Ekora;
import tr.ekora.istemci.client.Json;

/**
 * Kıvılcım Kasası. İki görünüm:
 * <ul>
 * <li><b>Onay</b> (veride "odul" yok): kasa, oranlar (nadirlik başına kesin yüzde), içindeki ürünlerden kayan bir vitrin,
 * bedava/Kıvılcım ile aç düğmeleri. Kıvılcım harcayan düğme iki tık ister.</li>
 * <li><b>Açılış</b> (veride "odul" var): yatay kart şeridi ortadaki çizgide yavaşlayıp ödülde durur.
 * Ödülü sunucu seçer ve verir; bu ekran yalnızca gösterir (kapatmak ödülü etkilemez).</li>
 * </ul>
 */
public final class KasaEkrani extends EkoraEkran {
	private record Kart(String ad, ItemStack ikon, String nadirlik) { }

	private record Oran(String nadirlik, double yuzde, int adet) { }

	private static final int KW = 46, KH = 58, ARA = 4, KAZANAN = 42;
	private final boolean onayGorunumu;
	private final List<Kart> havuz = new ArrayList<>();
	private final List<Kart> serit = new ArrayList<>();
	private final List<Oran> oranlar = new ArrayList<>();
	private final Kart odul;
	private final String odulId;
	private final boolean kopya, etkin;
	private final long hak, fiyat, kivilcim, kalan;
	private final long bas = System.currentTimeMillis();
	private final long sure = 6200;
	private final float kaydirmaSon;
	private int sonKart = -1;
	private boolean bitti, sesCaldi, giyildi;
	private final Dugme bedavaAc, kivilcimAc, giy, kapat;

	public KasaEkrani(JsonObject v) {
		super("Kıvılcım Kasası");
		JsonObject o = v.has("odul") && v.get("odul").isJsonObject() ? v.getAsJsonObject("odul") : null;
		onayGorunumu = o == null;
		hak = Json.sayi(v, "hak", Json.sayi(v, "kasa", 0));
		fiyat = Json.sayi(v, "fiyat", 0);
		kivilcim = Json.sayi(v, "kivilcim", 0);
		kalan = Json.sayi(v, "kalan", -1);
		etkin = Json.mantik(v, "etkin", true);
		kopya = Json.mantik(v, "kopya", false);
		for (JsonObject h : Json.liste(v, "havuz")) havuz.add(new Kart(Json.yazi(h, "ad", ""), Cizim.esya(Json.yazi(h, "ikon", "minecraft:paper")), Json.yazi(h, "nadirlik", "yaygin")));
		for (JsonObject r : Json.liste(v, "oranlar")) oranlar.add(new Oran(Json.yazi(r, "nadirlik", "yaygin"), Json.ondalik(r, "yuzde", 0), (int) Json.sayi(r, "adet", 0)));
		Random r = new Random();
		if (o != null) {
			odul = new Kart(Json.yazi(o, "ad", "?"), Cizim.esya(Json.yazi(o, "ikon", "minecraft:nether_star")), Json.yazi(o, "nadirlik", "nadir"));
			odulId = Json.yazi(o, "id", "");
			List<Kart> h = havuz.isEmpty() ? List.of(odul) : havuz;
			for (int i = 0; i < KAZANAN + 6; i++) serit.add(i == KAZANAN ? odul : agirlikli(h, r));
			kaydirmaSon = KAZANAN * (KW + ARA) + (r.nextFloat() - 0.5f) * (KW - 10);
		} else {
			odul = null;
			odulId = "";
			kaydirmaSon = 0;
		}
		bedavaAc = dugme(() -> Ekora.istek("kasa_ac", "bedava", "1"));
		kivilcimAc = dugme(this::kivilcimIleAc);
		giy = dugme(() -> { if (!odulId.isEmpty() && !giyildi) { giyildi = true; Ekora.istek("giy", "id", odulId); } });
		kapat = dugme(this::onClose);
	}

	/** Kıvılcım harcar: iki tık ister. */
	private void kivilcimIleAc() {
		if (onay(kivilcimAc)) Ekora.istek("kasa_ac", "bedava", "0");
	}

	/** Şeritte yaygınlar daha sık görünsün (gerçek olasılık sunucudadır; bu yalnızca görsel). */
	private static Kart agirlikli(List<Kart> h, Random r) {
		for (int deneme = 0; deneme < 4; deneme++) {
			Kart k = h.get(r.nextInt(h.size()));
			float sans = switch (k.nadirlik()) { case "efsanevi" -> 0.15f; case "destansi" -> 0.35f; case "nadir" -> 0.7f; default -> 1f; };
			if (r.nextFloat() < sans) return k;
		}
		return h.get(r.nextInt(h.size()));
	}

	@Override
	protected int genislik() { return 380; }

	@Override
	protected int yukseklik() { return onayGorunumu ? 214 : 196; }

	@Override
	protected void icerik(GuiGraphicsExtractor g, int mx, int my, float delta) {
		rozet(g, px + pw - 26 - this.font.width("✦ " + Cizim.sayi(kivilcim)) - 10, py + 6, "✦ " + Cizim.sayi(kivilcim), Cizim.ALTIN);
		if (onayGorunumu) onay(g, mx, my);
		else acilis(g, mx, my);
	}

	// ------------------------------------------------------------------ onay görünümü
	private void onay(GuiGraphicsExtractor g, int mx, int my) {
		long simdi = System.currentTimeMillis();
		// sol: parlayan kasa
		int kx = px + 10, ky = py + 30, kw = 118, kh = 118;
		Cizim.panel(g, kx, ky, kw, kh, 0xFF0D0F1A, Cizim.KENAR);
		float nabiz = 0.5f + 0.5f * (float) Math.sin(simdi / 380.0);
		Cizim.degradeY(g, kx + 1, ky + kh / 3, kw - 2, kh * 2 / 3 - 1, 0x00000000, Cizim.alfa(Cizim.MOR, 0.25f + 0.25f * nabiz));
		for (int i = 0; i < 10; i++) { // dönen kıvılcımlar
			double a = simdi / 900.0 + i * Math.PI / 5;
			int sx = (int) (kx + kw / 2 + Math.cos(a) * (36 + 6 * Math.sin(simdi / 500.0 + i)));
			int sy = (int) (ky + kh / 2 + 4 + Math.sin(a) * 26);
			g.fill(sx, sy, sx + 2, sy + 2, Cizim.alfa(i % 2 == 0 ? Cizim.ALTIN : Cizim.MOR, 0.5f + 0.5f * nabiz));
		}
		g.pose().pushMatrix();
		float bob = (float) Math.sin(simdi / 450.0) * 3;
		g.pose().translate(kx + kw / 2f - 24, ky + kh / 2f - 26 + bob);
		g.pose().scale(3f, 3f);
		g.fakeItem(Cizim.esya("minecraft:ender_chest"), 0, 0);
		g.pose().popMatrix();
		g.centeredText(this.font, "§lKIVILCIM KASASI", kx + kw / 2, ky + 6, Cizim.ALTIN);
		sigdirarakYaz(g, kalan >= 0 ? kalan + " kozmetik seni bekliyor" : "Rastgele kozmetik", kx + 4, ky + kh - 13, kw - 8, Cizim.SOLUK, 0.7f, true);

		// sağ: oranlar
		int rx = kx + kw + 8, ry = ky, rw = px + pw - 10 - rx;
		g.text(this.font, "Sahip olmadığın, kalıcı bir kozmetik çıkar.", rx, ry + 2, Cizim.YAZI);
		g.text(this.font, "§8Boş çıkmaz • sahip olduğun tekrar çıkmaz", rx, ry + 12, Cizim.SOLUK);
		int oy = ry + 26;
		for (Oran o : oranlar) {
			int nr = Cizim.nadirlikRengi(o.nadirlik());
			g.text(this.font, Cizim.nadirlikAdi(o.nadirlik()), rx, oy, nr);
			String yz = "%" + String.format(java.util.Locale.ROOT, o.yuzde() < 10 ? "%.1f" : "%.0f", o.yuzde()) + " §8(" + o.adet() + ")";
			g.text(this.font, yz, rx + rw - this.font.width(yz), oy, Cizim.YAZI);
			g.fill(rx, oy + 10, rx + rw, oy + 13, 0xFF262A40);
			Cizim.degradeX(g, rx, oy + 10, (int) Math.max(2, rw * Math.min(1.0, o.yuzde() / 100.0)), 3, Cizim.koyu(nr, 0.3f), nr);
			oy += 18;
		}
		if (oranlar.isEmpty()) g.text(this.font, kalan == 0 ? "§aKasadaki her şeye sahipsin!" : "§7Oranlar yükleniyor…", rx, oy, Cizim.SOLUK);

		// alt: içindekilerden kayan vitrin
		int vy = ky + kh + 8, vh = 26, vx = px + 10, vw = pw - 20;
		Cizim.panel(g, vx, vy, vw, vh, 0xFF0D0F1A, Cizim.KENAR);
		if (!havuz.isEmpty()) {
			g.enableScissor(vx + 1, vy + 1, vx + vw - 1, vy + vh - 1);
			float kay = (simdi / 28f) % (havuz.size() * 24f);
			for (int i = -1; i <= vw / 24 + 1; i++) {
				int idx = Math.floorMod((int) Math.floor(kay / 24f) + i, havuz.size());
				Kart k = havuz.get(idx);
				int x = (int) (vx + 4 + i * 24 - (kay % 24f));
				g.fill(x, vy + vh - 3, x + 20, vy + vh - 2, Cizim.nadirlikRengi(k.nadirlik()));
				g.fakeItem(k.ikon(), x + 2, vy + 4);
				if (Cizim.icinde(mx, my, x, vy, 22, vh)) {
					g.setTooltipForNextFrame(this.font, net.minecraft.network.chat.Component.literal(k.ad() + " §8• " + Cizim.nadirlikAdi(k.nadirlik())), mx, my);
				}
			}
			g.disableScissor();
			Cizim.degradeX(g, vx + 1, vy + 1, 20, vh - 2, 0xF00D0F1A, 0x000D0F1A);
			Cizim.degradeX(g, vx + vw - 21, vy + 1, 20, vh - 2, 0x000D0F1A, 0xF00D0F1A);
		}

		// düğmeler
		int by = py + ph - 28, bw = (pw - 20 - 8) / 2;
		boolean dolu = kalan != 0 && etkin;
		bedavaAc.aktif = dolu && hak > 0;
		bedavaAc.yer(px + 10, by, bw, 20);
		dugmeCiz(g, bedavaAc, hak > 0 ? "Bedava aç (" + hak + " hak)" : "Bedava hakkın yok", Cizim.YESIL, mx, my);
		kivilcimAc.aktif = dolu && fiyat > 0 && kivilcim >= fiyat;
		kivilcimAc.yer(px + 10 + bw + 8, by, bw, 20);
		String et = !etkin ? "Şu an kapalı" : kivilcim < fiyat ? "Eksik: ✦ " + Cizim.sayi(fiyat - kivilcim)
				: onayda(kivilcimAc) ? "Emin misin? Tekrar tıkla" : "✦ " + Cizim.sayi(fiyat) + " ile aç";
		dugmeCiz(g, kivilcimAc, et, onayda(kivilcimAc) ? Cizim.KIRMIZI : Cizim.ALTIN, mx, my);
	}

	// ------------------------------------------------------------------ açılış görünümü
	private void acilis(GuiGraphicsExtractor g, int mx, int my) {
		float t = Math.min(1f, (System.currentTimeMillis() - bas) / (float) sure);
		float e = 1 - (float) Math.pow(1 - t, 4.2);
		float kay = kaydirmaSon * e;
		int sx = px + 10, sw = pw - 20, sy = py + 46;
		int orta = sx + sw / 2;
		Cizim.panel(g, sx, sy - 6, sw, KH + 12, 0xFF0D0F1A, Cizim.KENAR);
		g.enableScissor(sx + 1, sy - 5, sx + sw - 1, sy + KH + 5);
		for (int i = 0; i < serit.size(); i++) {
			float x = orta - KW / 2f + i * (KW + ARA) - kay;
			if (x + KW < sx || x > sx + sw) continue;
			Kart k = serit.get(i);
			int nr = Cizim.nadirlikRengi(k.nadirlik());
			boolean kaz = bitti && i == KAZANAN;
			int ix = Math.round(x);
			Cizim.panel(g, ix, sy, KW, KH, kaz ? Cizim.koyu(nr, 0.55f) : 0xFF171A2B, kaz ? 0xFFFFFFFF : Cizim.koyu(nr, 0.3f));
			Cizim.degradeY(g, ix + 1, sy + KH / 2, KW - 2, KH / 2 - 1, 0x00000000, Cizim.alfa(nr, 0.5f));
			g.fill(ix + 2, sy + KH - 3, ix + KW - 2, sy + KH - 1, nr);
			g.pose().pushMatrix();
			g.pose().translate(ix + KW / 2f - 12, sy + 8);
			g.pose().scale(1.5f, 1.5f);
			g.fakeItem(k.ikon(), 0, 0);
			g.pose().popMatrix();
			g.pose().pushMatrix();
			g.pose().translate(ix + KW / 2f, sy + 38);
			g.pose().scale(0.62f, 0.62f);
			g.centeredText(this.font, Cizim.sigdir(this.font, k.ad(), (int) ((KW - 4) / 0.62f)), 0, 0, 0xFFFFFFFF);
			g.pose().popMatrix();
		}
		g.disableScissor();
		Cizim.degradeX(g, sx + 1, sy - 5, 30, KH + 10, 0xF00D0F1A, 0x000D0F1A);
		Cizim.degradeX(g, sx + sw - 31, sy - 5, 30, KH + 10, 0x000D0F1A, 0xF00D0F1A);
		// orta işaret: kartların üstünü kapatmasın diye yalnızca şeridin üstünde ve altında
		g.fill(orta - 1, sy - 9, orta + 1, sy - 2, Cizim.ALTIN);
		g.fill(orta - 1, sy + KH + 2, orta + 1, sy + KH + 9, Cizim.ALTIN);
		Cizim.degradeY(g, orta - KW / 2 - 2, sy - 2, KW + 4, 6, Cizim.alfa(Cizim.ALTIN, 0.35f), 0x00000000);
		Cizim.degradeY(g, orta - KW / 2 - 2, sy + KH - 4, KW + 4, 6, 0x00000000, Cizim.alfa(Cizim.ALTIN, 0.35f));
		for (int r = 0; r < 5; r++) {
			g.fill(orta - 5 + r, sy - 9 - 5 + r, orta + 5 - r, sy - 8 - 5 + r, Cizim.ALTIN);
			g.fill(orta - 5 + r, sy + KH + 13 - r, orta + 5 - r, sy + KH + 14 - r, Cizim.ALTIN);
		}
		int ortadaki = Math.round(kay / (KW + ARA));
		if (ortadaki != sonKart && !bitti) {
			sonKart = ortadaki;
			try {
				Ekora.ses(SoundEvents.NOTE_BLOCK_HAT.value(), 1.0f + 0.5f * (1 - t), 0.4f);
			} catch (RuntimeException ignored) { }
		}
		if (t >= 1f && !bitti) bitti = true;
		int ay = sy + KH + 18;
		int bw = 112, by = py + ph - 26;
		if (bitti) {
			if (!sesCaldi) {
				sesCaldi = true;
				try {
					Ekora.ses(SoundEvents.PLAYER_LEVELUP, 1.0f, 0.7f);
				} catch (RuntimeException ignored) { }
			}
			int nr = Cizim.nadirlikRengi(odul.nadirlik());
			Cizim.degradeX(g, px + 40, ay - 3, (pw - 80) / 2, 24, 0x00000000, Cizim.alfa(nr, 0.22f));
			Cizim.degradeX(g, px + pw / 2, ay - 3, (pw - 80) / 2, 24, Cizim.alfa(nr, 0.22f), 0x00000000);
			g.centeredText(this.font, "§l" + odul.ad(), px + pw / 2, ay, nr);
			g.centeredText(this.font, Cizim.nadirlikAdi(odul.nadirlik()) + (kopya ? " §7• zaten sahipsin, Kıvılcım olarak iade edildi" : " §7• dolabına eklendi"), px + pw / 2, ay + 11, Cizim.SOLUK);
			int x0 = px + (pw - (bw * 3 + 8)) / 2;
			giy.aktif = !kopya && !odulId.isEmpty() && !giyildi;
			giy.yer(x0, by, bw, 18);
			dugmeCiz(g, giy, giyildi ? "✔ Giyildi" : "Hemen giy", Cizim.YESIL, mx, my);
			boolean bedava = hak > 0;
			Dugme tekrar = bedava ? bedavaAc : kivilcimAc;
			(bedava ? kivilcimAc : bedavaAc).aktif = false;
			tekrar.aktif = bedava || (fiyat > 0 && kivilcim >= fiyat);
			tekrar.yer(x0 + bw + 4, by, bw, 18);
			String et = bedava ? "Bedava aç (" + hak + ")" : !tekrar.aktif ? "Kıvılcım yetmiyor" : onayda(kivilcimAc) ? "Emin misin?" : "Tekrar ✦ " + Cizim.sayi(fiyat);
			dugmeCiz(g, tekrar, et, onayda(kivilcimAc) ? Cizim.KIRMIZI : Cizim.MOR, mx, my);
			kapat.aktif = true;
			kapat.yer(x0 + (bw + 4) * 2, by, bw, 18);
			dugmeCiz(g, kapat, "Kapat", 0xFF4B5563, mx, my);
		} else {
			giy.aktif = bedavaAc.aktif = kivilcimAc.aktif = kapat.aktif = false;
			g.centeredText(this.font, "Kasa açılıyor…", px + pw / 2, ay + 4, Cizim.SOLUK);
		}
	}
}
