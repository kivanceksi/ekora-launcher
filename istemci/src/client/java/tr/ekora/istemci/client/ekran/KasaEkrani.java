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
 * Kıvılcım Kasası açılışı: yatay kayan kart şeridi ortadaki çizgide yavaşlayıp durur.
 * Ödülü sunucu belirler ve verir; bu ekran yalnızca gösterir (kapatmak ödülü etkilemez).
 */
public final class KasaEkrani extends EkoraEkran {
	private record Kart(String ad, ItemStack ikon, String nadirlik) { }

	private static final int KW = 46, KH = 58, ARA = 4, KAZANAN = 42;
	private final List<Kart> serit = new ArrayList<>();
	private final Kart odul;
	private final boolean kopya;
	private final long kasa, kivilcim;
	private final long bas = System.currentTimeMillis();
	private final long sure = 6200;
	private final float kaydirmaSon;
	private int sonKart = -1;
	private boolean bitti, sesCaldi;
	private final Dugme tekrar, kapat;

	public KasaEkrani(JsonObject v) {
		super("Kıvılcım Kasası");
		JsonObject o = v.has("odul") && v.get("odul").isJsonObject() ? v.getAsJsonObject("odul") : new JsonObject();
		odul = new Kart(Json.yazi(o, "ad", "?"), Cizim.esya(Json.yazi(o, "ikon", "minecraft:nether_star")), Json.yazi(o, "nadirlik", "nadir"));
		kopya = Json.mantik(v, "kopya", false);
		kasa = Json.sayi(v, "kasa", 0);
		kivilcim = Json.sayi(v, "kivilcim", 0);
		List<Kart> havuz = new ArrayList<>();
		for (JsonObject h : Json.liste(v, "havuz")) havuz.add(new Kart(Json.yazi(h, "ad", ""), Cizim.esya(Json.yazi(h, "ikon", "minecraft:paper")), Json.yazi(h, "nadirlik", "yaygin")));
		if (havuz.isEmpty()) havuz.add(odul);
		Random r = new Random();
		for (int i = 0; i < KAZANAN + 6; i++) serit.add(i == KAZANAN ? odul : agirlikli(havuz, r));
		kaydirmaSon = KAZANAN * (KW + ARA) + (r.nextFloat() - 0.5f) * (KW - 10);
		tekrar = dugme(() -> Ekora.istek("kasa_ac"));
		kapat = dugme(this::onClose);
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
	protected int yukseklik() { return 190; }

	@Override
	protected void icerik(GuiGraphicsExtractor g, int mx, int my, float delta) {
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
		// kenar soluklaştırma
		Cizim.degradeX(g, sx + 1, sy - 5, 30, KH + 10, 0xF00D0F1A, 0x000D0F1A);
		Cizim.degradeX(g, sx + sw - 31, sy - 5, 30, KH + 10, 0x000D0F1A, 0xF00D0F1A);
		// orta çizgi
		g.fill(orta - 1, sy - 9, orta + 1, sy + KH + 9, Cizim.ALTIN);
		for (int r = 0; r < 5; r++) {
			g.fill(orta - 5 + r, sy - 9 - 5 + r, orta + 5 - r, sy - 8 - 5 + r, Cizim.ALTIN);
			g.fill(orta - 5 + r, sy + KH + 13 - r, orta + 5 - r, sy + KH + 14 - r, Cizim.ALTIN);
		}
		// tık sesi
		int ortadaki = Math.round(kay / (KW + ARA));
		if (ortadaki != sonKart && !bitti) {
			sonKart = ortadaki;
			try {
				Ekora.ses(SoundEvents.NOTE_BLOCK_HAT.value(), 1.0f + 0.5f * (1 - t), 0.4f);
			} catch (RuntimeException ignored) { }
		}
		if (t >= 1f && !bitti) bitti = true;
		int ay = sy + KH + 18;
		if (bitti) {
			if (!sesCaldi) {
				sesCaldi = true;
				try {
					Ekora.ses(SoundEvents.PLAYER_LEVELUP, 1.0f, 0.7f);
				} catch (RuntimeException ignored) { }
			}
			int nr = Cizim.nadirlikRengi(odul.nadirlik());
			g.centeredText(this.font, "§l" + odul.ad(), px + pw / 2, ay, nr);
			g.centeredText(this.font, Cizim.nadirlikAdi(odul.nadirlik()) + (kopya ? " §7• zaten sahipsin, Kıvılcım olarak iade edildi" : " §7• dolabına eklendi"), px + pw / 2, ay + 11, Cizim.SOLUK);
			int bw = 110;
			tekrar.aktif = kasa > 0;
			tekrar.yer(px + pw / 2 - bw - 4, py + ph - 26, bw, 18);
			dugmeCiz(g, tekrar, kasa > 0 ? "Tekrar aç (" + kasa + ")" : "Hakkın kalmadı", Cizim.MOR, mx, my);
			kapat.aktif = true;
			kapat.yer(px + pw / 2 + 4, py + ph - 26, bw, 18);
			dugmeCiz(g, kapat, "Kapat", 0xFF4B5563, mx, my);
		} else {
			tekrar.aktif = false;
			kapat.aktif = false;
			g.centeredText(this.font, "Kasa açılıyor…", px + pw / 2, ay + 4, Cizim.SOLUK);
		}
		g.text(this.font, "✦ " + Cizim.sayi(kivilcim), px + 10, py + 30, Cizim.ALTIN);
	}
}
