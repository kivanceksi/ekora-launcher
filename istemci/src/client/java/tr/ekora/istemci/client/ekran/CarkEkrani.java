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
 * Şans Çarkı: gerçek dönen çark. Sonucu SUNUCU belirler; istemci yalnızca o dilime yumuşakça yavaşlayarak durur.
 * Akış: ÇEVİR → çark hızlanır (sunucu yanıtı beklenir) → "cark_sonuc" gelince hız korunarak hedef dilime yavaşlar.
 */
public final class CarkEkrani extends EkoraEkran {
	private static final float TAM = (float) (Math.PI * 2), ISARET = (float) (-Math.PI / 2);

	private record Dilim(String ad, String kisa, int renk, ItemStack ikon, double yuzde) { }

	private enum Durum { HAZIR, BEKLIYOR, YAVASLIYOR, BITTI }

	private final List<Dilim> dilimler = new ArrayList<>();
	private final Random rnd = new Random();
	private long hak, ucret, kivilcim;
	private boolean bedava;
	private Durum durum = Durum.HAZIR;
	private float aci = 0f, hiz = 0.35f;
	private float yavasBas, yavasHedef;
	private long yavasZaman, yavasSure, sonKare = System.nanoTime(), bekleBas, bitisZaman;
	private int kazanan = -1, sonIsaretDilim = -1;
	private String mesaj = "", hataMesaji = "";
	private final Dugme cevir;
	private final List<float[]> konfeti = new ArrayList<>();

	public CarkEkrani(JsonObject veri) {
		super("Şans Çarkı");
		cevir = dugme(this::cevir);
		guncelle(veri);
	}

	public void guncelle(JsonObject v) {
		List<JsonObject> l = Json.liste(v, "dilimler");
		if (!l.isEmpty() && durum != Durum.BEKLIYOR && durum != Durum.YAVASLIYOR) {
			dilimler.clear();
			for (JsonObject d : l) {
				String ad = Json.yazi(d, "ad", "?");
				dilimler.add(new Dilim(ad, Json.yazi(d, "kisa", ad), Cizim.renk(Json.yazi(d, "renk", null), 0xFF6D28D9),
						Cizim.esya(Json.yazi(d, "ikon", "minecraft:paper")), Json.ondalik(d, "yuzde", 0)));
			}
		}
		hak = Json.sayi(v, "hak", hak);
		ucret = Json.sayi(v, "ucret", ucret);
		kivilcim = Json.sayi(v, "kivilcim", kivilcim);
		bedava = Json.mantik(v, "bedava", bedava);
	}

	private boolean cevrilebilir() {
		return (durum == Durum.HAZIR || durum == Durum.BITTI) && !dilimler.isEmpty() && (hak > 0 || kivilcim >= ucret && ucret > 0);
	}

	private void cevir() {
		if (!cevrilebilir()) return;
		if (hak <= 0 && !onay(cevir)) return; // Kıvılcım ile çevirme iki tık ister
		durum = Durum.BEKLIYOR;
		bekleBas = System.currentTimeMillis();
		kazanan = -1;
		mesaj = "";
		hataMesaji = "";
		konfeti.clear();
		Ekora.istek("cark_cevir");
	}

	/** Sunucunun seçtiği dilim geldi: mevcut hızı koruyarak o dilimin ortasına (biraz kaydırarak) yavaşla. */
	public void sonuc(JsonObject v) {
		int w = (int) Json.sayi(v, "dilim", -1);
		guncelle(v);
		if (w < 0 || w >= dilimler.size()) { hata("Çark sonucu okunamadı."); return; }
		kazanan = w;
		mesaj = Json.yazi(v, "mesaj", dilimler.get(w).ad());
		float d = TAM / dilimler.size();
		float hedef = ISARET - (w + 0.5f + (rnd.nextFloat() - 0.5f) * 0.6f) * d;
		float v0 = Math.max(hiz, 6f);
		float enAz = aci + v0 * 4.8f / 4f;
		hedef += TAM * (float) Math.ceil((enAz - hedef) / TAM);
		yavasBas = aci;
		yavasHedef = hedef;
		yavasZaman = System.currentTimeMillis();
		yavasSure = (long) Math.max(3800, Math.min(7500, 4000f * (hedef - aci) / v0));
		durum = Durum.YAVASLIYOR;
	}

	public void hata(String m) {
		hataMesaji = m == null || m.isEmpty() ? "Çevrilemedi." : m;
		if (durum == Durum.BEKLIYOR) durum = Durum.HAZIR;
	}

	@Override
	protected int genislik() { return 400; }

	@Override
	protected int yukseklik() { return 240; }

	@Override
	public void tick() {
		super.tick();
		if (durum == Durum.BEKLIYOR && System.currentTimeMillis() - bekleBas > 8000) hata("Sunucu yanıt vermedi. Tekrar dene.");
	}

	private void ilerle() {
		long simdi = System.nanoTime();
		float dt = Math.min(0.1f, (simdi - sonKare) / 1e9f);
		sonKare = simdi;
		switch (durum) {
			case HAZIR -> { hiz += (0.35f - hiz) * Math.min(1, dt * 2); aci += hiz * dt; }
			case BEKLIYOR -> { hiz = Math.min(11f, hiz + dt * 14f); aci += hiz * dt; }
			case YAVASLIYOR -> {
				float t = Math.min(1f, (System.currentTimeMillis() - yavasZaman) / (float) yavasSure);
				float e = 1 - (float) Math.pow(1 - t, 4);
				float once = aci;
				aci = yavasBas + (yavasHedef - yavasBas) * e;
				hiz = dt > 0 ? (aci - once) / dt : hiz;
				if (t >= 1f) {
					durum = Durum.BITTI;
					hiz = 0f;
					bitisZaman = System.currentTimeMillis();
					kutla();
				}
			}
			case BITTI -> { }
		}
		// işaretin altından geçen dilim sınırı: tık sesi
		if (!dilimler.isEmpty()) {
			float d = TAM / dilimler.size();
			int i = (int) Math.floor(norm(ISARET - aci) / d);
			if (i != sonIsaretDilim) {
				if (sonIsaretDilim != -1 && (durum == Durum.BEKLIYOR || durum == Durum.YAVASLIYOR)) {
					try {
						Ekora.ses(SoundEvents.NOTE_BLOCK_HAT.value(), 0.9f + Math.min(0.6f, hiz / 20f), 0.45f);
					} catch (RuntimeException ignored) { }
				}
				sonIsaretDilim = i;
			}
		}
	}

	private void kutla() {
		try {
			Ekora.ses(SoundEvents.PLAYER_LEVELUP, 1.2f, 0.7f);
		} catch (RuntimeException ignored) { }
		for (int i = 0; i < 70; i++) {
			konfeti.add(new float[]{rnd.nextFloat(), -rnd.nextFloat() * 0.3f, 0.15f + rnd.nextFloat() * 0.35f, (rnd.nextFloat() - 0.5f) * 0.2f, rnd.nextInt(6)});
		}
	}

	private static float norm(float a) {
		a %= TAM;
		return a < 0 ? a + TAM : a;
	}

	@Override
	protected void icerik(GuiGraphicsExtractor g, int mx, int my, float delta) {
		ilerle();
		int alan = Math.min(ph - 36, 206);
		int R = Math.max(40, alan / 2 - 12);
		int cx = px + 14 + R + 8, cy = py + 28 + alan / 2;
		tekerlek(g, cx, cy, R);
		// sağ panel
		int rx = cx + R + 22, ry = py + 30, rw = px + pw - 10 - rx;
		Cizim.panel(g, rx, ry, rw, ph - 40, Cizim.ARKA2, Cizim.KENAR);
		int y = ry + 7;
		int x = rx + 7;
		x += rozet(g, x, y, (bedava ? "Bedava + " : "") + "Hak: " + hak, Cizim.ALTIN) + 4;
		rozet(g, x, y, "✦ " + Cizim.sayi(kivilcim), Cizim.CAMGOBEGI);
		y += 19;
		g.text(this.font, "Olasılıklar", rx + 7, y, Cizim.SOLUK);
		y += 11;
		int satirSay = Math.max(1, Math.min(dilimler.size(), (ph - 40 - 92) / 10));
		for (int i = 0; i < satirSay; i++) {
			Dilim d = dilimler.get(i);
			boolean kaz = i == kazanan && durum == Durum.BITTI;
			g.fill(rx + 7, y + 1, rx + 13, y + 7, d.renk());
			g.text(this.font, Cizim.sigdir(this.font, d.ad(), rw - 50), rx + 17, y, kaz ? 0xFFFFFFFF : Cizim.YAZI);
			String yz = String.format(java.util.Locale.ROOT, "%%%.1f", d.yuzde());
			g.text(this.font, yz, rx + rw - 7 - this.font.width(yz), y, kaz ? Cizim.ALTIN : Cizim.SOLUK);
			y += 10;
		}
		// sonuç / hata
		int altY = ry + ph - 40 - 50;
		if (durum == Durum.BITTI && kazanan >= 0) {
			float p = Math.min(1f, (System.currentTimeMillis() - bitisZaman) / 300f);
			int c = dilimler.get(kazanan).renk();
			Cizim.panel(g, rx + 5, altY - 4, rw - 10, 20, Cizim.alfa(Cizim.koyu(c, 0.6f), p), Cizim.alfa(c, p));
			g.centeredText(this.font, Cizim.sigdir(this.font, "KAZANDIN: " + mesaj, rw - 16), rx + rw / 2, altY + 2, Cizim.alfa(0xFFFFFFFF, Math.max(0.1f, p)));
		} else if (!hataMesaji.isEmpty()) {
			g.centeredText(this.font, Cizim.sigdir(this.font, hataMesaji, rw - 12), rx + rw / 2, altY + 2, Cizim.KIRMIZI);
		} else if (durum == Durum.BEKLIYOR || durum == Durum.YAVASLIYOR) {
			g.centeredText(this.font, "Çark dönüyor…", rx + rw / 2, altY + 2, Cizim.SOLUK);
		}
		String etiket = durum == Durum.BEKLIYOR || durum == Durum.YAVASLIYOR ? "Dönüyor…"
				: hak > 0 ? (durum == Durum.BITTI ? "TEKRAR ÇEVİR!" : "ÇEVİR!")
				: ucret > 0 ? (onayda(cevir) ? "Emin misin? ✦ " + Cizim.sayi(ucret) : "✦ " + Cizim.sayi(ucret) + " ile çevir") : "Hakkın yok";
		cevir.aktif = cevrilebilir();
		cevir.yer(rx + 7, ry + ph - 40 - 27, rw - 14, 20);
		dugmeCiz(g, cevir, etiket, Cizim.ALTIN, mx, my);
		if (!cevir.aktif && durum != Durum.BEKLIYOR && durum != Durum.YAVASLIYOR && hak <= 0) {
			g.centeredText(this.font, "§7Her gün 1 bedava + oynadıkça hak", rx + rw / 2, ry + ph - 40 - 38, Cizim.SOLUK);
		}
	}

	private void tekerlek(GuiGraphicsExtractor g, int cx, int cy, int R) {
		int n = Math.max(1, dilimler.size());
		float d = TAM / n;
		long zaman = System.currentTimeMillis();
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		// gölge
		for (int k = 0; k < 180; k++) {
			g.pose().pushMatrix();
			g.pose().rotate(k * TAM / 180);
			g.fill(R - 2, -2, R + 10, 2, 0x22000000);
			g.pose().popMatrix();
		}
		// dilimler (ince ışınlarla dolu pasta)
		int adim = 720;
		int ic = (int) (R * 0.5f);
		boolean parla = durum == Durum.BITTI && kazanan >= 0;
		float nabiz = parla ? 0.5f + 0.5f * (float) Math.sin((zaman - bitisZaman) / 120.0) : 0f;
		for (int k = 0; k < adim; k++) {
			float a = k * TAM / adim;
			int i = dilimler.isEmpty() ? 0 : (int) (norm(a - aci) / d) % n;
			int c = dilimler.isEmpty() ? 0xFF333344 : dilimler.get(i).renk();
			if (parla && i == kazanan) c = Cizim.karis(c, 0xFFFFFFFF, 0.25f * nabiz);
			else if (parla) c = Cizim.karis(c, 0xFF000000, 0.35f);
			g.pose().pushMatrix();
			g.pose().rotate(a);
			g.fill(0, -1, ic, 1, Cizim.koyu(c, 0.3f));
			g.fill(ic, -1, R - 3, 1, c);
			g.fill(R - 3, -1, R, 1, Cizim.karis(c, 0xFFFFFFFF, 0.25f));
			g.pose().popMatrix();
		}
		// dilim ayırıcıları
		for (int i = 0; i < n; i++) {
			g.pose().pushMatrix();
			g.pose().rotate(aci + i * d);
			g.fill(14, 0, R, 1, 0xCC0B0D16);
			g.pose().popMatrix();
		}
		// altın çember
		for (int k = 0; k < 360; k++) {
			g.pose().pushMatrix();
			g.pose().rotate(k * TAM / 360);
			g.fill(R, -1, R + 6, 1, Cizim.karis(0xFFB7791F, 0xFFFCD34D, 0.5f + 0.5f * (float) Math.sin(k * 0.105f)));
			g.pose().popMatrix();
		}
		// ampuller (dönen çarktan bağımsız, sırayla yanıp söner)
		int amp = Math.max(12, n * 2);
		int faz = (int) (zaman / (durum == Durum.HAZIR ? 400 : 110));
		for (int j = 0; j < amp; j++) {
			float a = j * TAM / amp;
			int bx = Math.round((float) Math.cos(a) * (R + 3)), by = Math.round((float) Math.sin(a) * (R + 3));
			boolean yan = ((j + faz) & 1) == 0;
			g.fill(bx - 2, by - 2, bx + 2, by + 2, yan ? 0xFFFFF7D6 : 0xFF8A6A2A);
		}
		// kısa yazılar (yarıçap boyunca)
		for (int i = 0; i < dilimler.size(); i++) {
			float th = aci + (i + 0.5f) * d;
			boolean ters = Math.cos(norm(th)) < 0;
			g.pose().pushMatrix();
			g.pose().rotate(ters ? th + (float) Math.PI : th);
			g.pose().scale(0.75f, 0.75f);
			String s = Cizim.sigdir(this.font, dilimler.get(i).kisa(), (int) ((R * 0.62f - 22) / 0.75f));
			int yr = (int) ((R * 0.25f) / 0.75f);
			if (ters) g.text(this.font, s, -yr - this.font.width(s), -4, 0xFFFFFFFF);
			else g.text(this.font, s, yr, -4, 0xFFFFFFFF);
			g.pose().popMatrix();
		}
		// göbek
		for (int k = 0; k < 120; k++) {
			g.pose().pushMatrix();
			g.pose().rotate(k * TAM / 120);
			g.fill(0, -1, 13, 1, 0xFF151827);
			g.fill(13, -1, 16, 1, 0xFFF5B83D);
			g.pose().popMatrix();
		}
		g.pose().popMatrix();
		// simgeler (döndürülmeden, dilim ortasında)
		for (int i = 0; i < dilimler.size(); i++) {
			float th = aci + (i + 0.5f) * d;
			int ix = cx + Math.round((float) Math.cos(th) * R * 0.74f), iy = cy + Math.round((float) Math.sin(th) * R * 0.74f);
			g.fakeItem(dilimler.get(i).ikon(), ix - 8, iy - 8);
		}
		g.fakeItem(Cizim.esya("minecraft:sunflower"), cx - 8, cy - 8);
		// işaretçi (üstte, aşağı bakan üçgen)
		int ux = cx, uy = cy - R - 9;
		for (int r = 0; r < 11; r++) {
			int yari = 6 - r / 2;
			g.fill(ux - yari - 1, uy + r, ux + yari + 1, uy + r + 1, 0xFF0B0D16);
			if (yari > 0) g.fill(ux - yari, uy + r, ux + yari, uy + r + 1, r < 3 ? 0xFFFFF1C1 : 0xFFF5B83D);
		}
		// konfeti
		if (!konfeti.isEmpty()) {
			float sn = (System.currentTimeMillis() - bitisZaman) / 1000f;
			int[] renkler = {0xFFF87171, 0xFFFBBF24, 0xFF34D399, 0xFF60A5FA, 0xFFA78BFA, 0xFFF472B6};
			for (float[] k : konfeti) {
				float kx = px + pw * (k[0] + k[3] * sn), ky = py + ph * (k[1] + k[2] * sn * 1.4f);
				if (ky > py + ph - 4 || sn > 3.2f) continue;
				int boy = ((int) (sn * 10 + k[0] * 10) & 1) == 0 ? 2 : 3;
				g.fill((int) kx, (int) ky, (int) kx + boy, (int) ky + 2, Cizim.alfa(renkler[(int) k[4] % renkler.length], Math.max(0, 1 - sn / 3.2f)));
			}
		}
	}

	@Override
	public void onClose() {
		if (durum == Durum.YAVASLIYOR) { // ödül sunucuda zaten verildi; kapatınca sadece animasyon atlanır
			durum = Durum.BITTI;
		}
		super.onClose();
	}
}
