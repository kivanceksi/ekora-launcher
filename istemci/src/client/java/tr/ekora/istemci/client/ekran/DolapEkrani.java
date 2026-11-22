package tr.ekora.istemci.client.ekran;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.ItemStack;

import tr.ekora.istemci.client.Ekora;
import tr.ekora.istemci.client.Json;

/**
 * Kozmetik Dolabı: solda yuvalar (şapka, kostüm, dost, iz…), ortada kozmetik ızgarası (nadirlik renkli),
 * sağda oyuncunun 3B görünümü ve seçili kozmetiğin ayrıntısı + giy/çıkar/al düğmesi.
 */
public final class DolapEkrani extends EkoraEkran {
	private record Yuva(String kod, String ad, ItemStack ikon) { }

	private record Urun(String id, String ad, String yuva, ItemStack ikon, String nadirlik, boolean sahip, boolean takili, long fiyat,
						List<String> aciklama, boolean satista) { }

	private final List<Yuva> yuvalar = new ArrayList<>();
	private final List<Urun> urunler = new ArrayList<>();
	private final List<Dugme> yuvaDugmeleri = new ArrayList<>();
	private final List<Dugme> kartDugmeleri = new ArrayList<>();
	private String seciliYuva = null, seciliUrun = null;
	private long kivilcim;
	private boolean sadeceSahip = false;
	private int kaydir = 0;
	private final Dugme eylem, sahipFiltre, tumunuCikar;
	private long sonTik;
	private String sonTikId;

	public DolapEkrani(JsonObject v) {
		super("Kozmetik Dolabı");
		eylem = dugme(this::eylemYap);
		sahipFiltre = dugme(() -> { sadeceSahip = !sadeceSahip; kaydir = 0; });
		tumunuCikar = dugme(() -> Ekora.istek("cikar", "yuva", "*"));
		guncelle(v);
	}

	public void guncelle(JsonObject v) {
		kivilcim = Json.sayi(v, "kivilcim", kivilcim);
		List<JsonObject> ys = Json.liste(v, "yuvalar");
		if (!ys.isEmpty()) {
			yuvalar.clear();
			for (JsonObject y : ys) yuvalar.add(new Yuva(Json.yazi(y, "kod", ""), Json.yazi(y, "ad", ""), Cizim.esya(Json.yazi(y, "ikon", "minecraft:chest"))));
		}
		urunler.clear();
		for (JsonObject u : Json.liste(v, "urunler")) {
			urunler.add(new Urun(Json.yazi(u, "id", ""), Json.yazi(u, "ad", ""), Json.yazi(u, "yuva", ""), Cizim.esya(Json.yazi(u, "ikon", "minecraft:paper")),
					Json.yazi(u, "nadirlik", "yaygin"), Json.mantik(u, "sahip", false), Json.mantik(u, "takili", false), Json.sayi(u, "fiyat", 0),
					Json.yazilar(u, "aciklama"), Json.mantik(u, "satista", true)));
		}
		if (seciliYuva == null || yuvalar.stream().noneMatch(y -> y.kod().equals(seciliYuva))) seciliYuva = yuvalar.isEmpty() ? null : yuvalar.get(0).kod();
		while (yuvaDugmeleri.size() < yuvalar.size()) {
			int idx = yuvaDugmeleri.size();
			yuvaDugmeleri.add(dugme(() -> { if (idx < yuvalar.size()) { seciliYuva = yuvalar.get(idx).kod(); kaydir = 0; seciliUrun = null; } }));
		}
	}

	private List<Urun> gorunen() {
		List<Urun> l = new ArrayList<>();
		for (Urun u : urunler) if (u.yuva().equals(seciliYuva) && (!sadeceSahip || u.sahip())) l.add(u);
		return l;
	}

	private Urun secili() {
		for (Urun u : urunler) if (u.id().equals(seciliUrun)) return u;
		return null;
	}

	private void eylemYap() {
		Urun u = secili();
		if (u == null) return;
		if (u.sahip()) {
			if (u.takili()) Ekora.istek("cikar", "yuva", u.yuva());
			else Ekora.istek("giy", "id", u.id());
		} else if (u.satista() && u.fiyat() > 0) {
			if (kivilcim >= u.fiyat() && onay(eylem)) Ekora.istek("satin", "id", u.id());
		}
		else Ekora.istek("onizle", "id", u.id());
	}

	@Override
	protected int genislik() { return 430; }

	@Override
	protected int yukseklik() { return 248; }

	private static final int KART = 30, ARA = 4;

	@Override
	protected void icerik(GuiGraphicsExtractor g, int mx, int my, float delta) {
		int ust = py + 28;
		// bakiye
		rozet(g, px + pw - 26 - this.font.width("✦ " + Cizim.sayi(kivilcim)) - 10, py + 6, "✦ " + Cizim.sayi(kivilcim), Cizim.ALTIN);
		// sol: yuvalar
		int lx = px + 8, lw = 92;
		for (int i = 0; i < yuvalar.size() && i < yuvaDugmeleri.size(); i++) {
			Yuva y = yuvalar.get(i);
			Dugme d = yuvaDugmeleri.get(i).yer(lx, ust + i * 19, lw, 17);
			boolean sec = y.kod().equals(seciliYuva);
			boolean ustunde = d.ustunde(mx, my);
			Cizim.panel(g, d.x, d.y, d.w, d.h, sec ? 0xFF2A1F4A : ustunde ? 0xFF1E2238 : 0xFF151829, sec ? Cizim.MOR : 0);
			if (sec) g.fill(d.x + 1, d.y + 2, d.x + 3, d.y + d.h - 2, Cizim.MOR);
			g.pose().pushMatrix();
			g.pose().translate(d.x + 5, d.y + 1);
			g.pose().scale(0.9f, 0.9f);
			g.fakeItem(y.ikon(), 0, 0);
			g.pose().popMatrix();
			long sahip = urunler.stream().filter(u -> u.yuva().equals(y.kod()) && u.sahip()).count();
			sigdirarakYaz(g, y.ad(), d.x + 23, d.y + 5, lw - 36, sec ? 0xFFFFFFFF : Cizim.YAZI, 0.7f, false);
			String s = String.valueOf(sahip);
			g.text(this.font, s, d.x + d.w - 5 - this.font.width(s), d.y + 5, Cizim.SOLUK);
		}
		// orta: ızgara
		int gx = lx + lw + 8, gy = ust + 18, sagW = 118;
		int gw = px + pw - 8 - sagW - 8 - gx;
		int sutun = Math.max(1, (gw + ARA) / (KART + ARA));
		int satir = Math.max(1, (py + ph - 8 - gy + ARA) / (KART + ARA));
		List<Urun> l = gorunen();
		int toplamSatir = (l.size() + sutun - 1) / sutun;
		kaydir = Math.max(0, Math.min(kaydir, Math.max(0, toplamSatir - satir)));
		sahipFiltre.yer(gx, ust, Math.min(104, gw - 74), 14);
		Cizim.panel(g, sahipFiltre.x, sahipFiltre.y, sahipFiltre.w, sahipFiltre.h, sadeceSahip ? 0xFF1F3A2C : 0xFF181B2C, sadeceSahip ? Cizim.YESIL : Cizim.KENAR);
		sigdirarakYaz(g, (sadeceSahip ? "☑" : "☐") + " Sahip olduklarım", sahipFiltre.x + 4, sahipFiltre.y + 3, sahipFiltre.w - 8, sadeceSahip ? Cizim.YESIL : Cizim.SOLUK, 0.75f, false);
		tumunuCikar.yer(gx + gw - 70, ust, 70, 14);
		Cizim.panel(g, tumunuCikar.x, tumunuCikar.y, tumunuCikar.w, tumunuCikar.h, tumunuCikar.ustunde(mx, my) ? 0xFF3A2030 : 0xFF181B2C, Cizim.KENAR);
		sigdirarakYaz(g, "Tümünü çıkar", tumunuCikar.x + 3, tumunuCikar.y + 3, tumunuCikar.w - 6, tumunuCikar.ustunde(mx, my) ? Cizim.KIRMIZI : Cizim.SOLUK, 0.75f, true);
		while (kartDugmeleri.size() < sutun * satir) {
			int idx = kartDugmeleri.size();
			kartDugmeleri.add(dugme(() -> kartTik(idx)));
		}
		for (int i = 0; i < kartDugmeleri.size(); i++) kartDugmeleri.get(i).aktif = false;
		Urun ipucu = null;
		if (l.isEmpty()) g.centeredText(this.font, sadeceSahip ? "Bu yuvada kozmetiğin yok." : "Bu yuvada kozmetik yok.", gx + gw / 2, gy + 30, Cizim.SOLUK);
		for (int r = 0; r < satir; r++) {
			for (int c = 0; c < sutun; c++) {
				int idx = (r + kaydir) * sutun + c;
				if (idx >= l.size()) break;
				Urun u = l.get(idx);
				Dugme d = kartDugmeleri.get(r * sutun + c).yer(gx + c * (KART + ARA), gy + r * (KART + ARA), KART, KART);
				d.aktif = true;
				boolean ustunde = d.ustunde(mx, my);
				if (ustunde) ipucu = u;
				int nr = Cizim.nadirlikRengi(u.nadirlik());
				boolean sec = u.id().equals(seciliUrun);
				Cizim.panel(g, d.x, d.y, d.w, d.h, ustunde ? 0xFF22263D : 0xFF171A2B, sec ? 0xFFFFFFFF : u.takili() ? Cizim.YESIL : Cizim.koyu(nr, 0.35f));
				Cizim.degradeY(g, d.x + 1, d.y + d.h - 9, d.w - 2, 8, 0x00000000, Cizim.alfa(nr, 0.45f));
				g.fill(d.x + 2, d.y + d.h - 2, d.x + d.w - 2, d.y + d.h - 1, nr);
				g.fakeItem(u.ikon(), d.x + 7, d.y + 6);
				if (!u.sahip()) {
					g.fill(d.x + 1, d.y + 1, d.x + d.w - 1, d.y + d.h - 1, 0x88101320);
					g.text(this.font, "✦", d.x + d.w - 8, d.y + 2, Cizim.ALTIN);
				}
				if (u.takili()) g.text(this.font, "✔", d.x + 2, d.y + 2, Cizim.YESIL);
			}
		}
		if (toplamSatir > satir) {
			int sh = py + ph - 8 - gy;
			int bh = Math.max(12, sh * satir / toplamSatir);
			int by = gy + (sh - bh) * kaydir / Math.max(1, toplamSatir - satir);
			g.fill(gx + gw + 2, gy, gx + gw + 4, gy + sh, 0xFF1C2033);
			g.fill(gx + gw + 2, by, gx + gw + 4, by + bh, Cizim.MOR);
		}
		// sağ: önizleme + ayrıntı
		int rx = px + pw - 8 - sagW, ry = ust, rh = py + ph - 8 - ry;
		Cizim.panel(g, rx, ry, sagW, rh, Cizim.ARKA2, Cizim.KENAR);
		Cizim.degradeY(g, rx + 1, ry + 1, sagW - 2, 40, Cizim.alfa(Cizim.MOR, 0.22f), 0x00000000);
		if (this.minecraft.player != null) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, rx + 10, ry + 4, rx + sagW - 10, ry + 96, 36, 0.0625F, mx, my, this.minecraft.player);
		}
		Urun u = secili();
		int ty = ry + 100;
		if (u == null) {
			g.centeredText(this.font, "Bir kozmetik seç", rx + sagW / 2, ty + 4, Cizim.SOLUK);
			eylem.aktif = false;
			eylem.yer(-100, -100, 0, 0);
		} else {
			int nr = Cizim.nadirlikRengi(u.nadirlik());
			g.text(this.font, Cizim.sigdir(this.font, u.ad(), sagW - 12), rx + 6, ty, 0xFFFFFFFF);
			g.text(this.font, Cizim.nadirlikAdi(u.nadirlik()), rx + 6, ty + 10, nr);
			int ay = ty + 22;
			for (String s : u.aciklama()) {
				for (var parca : this.font.split(net.minecraft.network.chat.Component.literal(s), sagW - 12)) {
					if (ay > ry + rh - 34) break;
					g.text(this.font, parca, rx + 6, ay, Cizim.SOLUK);
					ay += 9;
				}
			}
			String et;
			int renk;
			if (u.sahip()) { et = u.takili() ? "Çıkar" : "Giy"; renk = u.takili() ? 0xFF6B7280 : Cizim.YESIL; }
			else if (u.satista() && u.fiyat() > 0) {
				boolean yeter = kivilcim >= u.fiyat();
				et = !yeter ? "Eksik: ✦ " + Cizim.sayi(u.fiyat() - kivilcim) : onayda(eylem) ? "Emin misin? ✦ " + Cizim.sayi(u.fiyat()) : "✦ " + Cizim.sayi(u.fiyat()) + " ile al";
				renk = !yeter ? 0xFF6B7280 : onayda(eylem) ? Cizim.KIRMIZI : Cizim.ALTIN;
			}
			else { et = "Önizle"; renk = Cizim.CAMGOBEGI; }
			eylem.aktif = true;
			eylem.yer(rx + 6, ry + rh - 26, sagW - 12, 20);
			dugmeCiz(g, eylem, et, renk, mx, my);
		}
		if (ipucu != null) {
			final Urun ip = ipucu;
			g.setTooltipForNextFrame(this.font, net.minecraft.network.chat.Component.literal(ip.ad() + (ip.sahip() ? "" : "  §6✦ " + Cizim.sayi(ip.fiyat()))), mx, my);
		}
	}

	private void kartTik(int ekranIdx) {
		int sutun = Math.max(1, kartDugmeleri.isEmpty() ? 1 : sutunSay());
		List<Urun> l = gorunen();
		int idx = kaydir * sutun + ekranIdx;
		if (idx < 0 || idx >= l.size()) return;
		Urun u = l.get(idx);
		long simdi = System.currentTimeMillis();
		if (u.id().equals(sonTikId) && simdi - sonTik < 350 && u.sahip()) {
			seciliUrun = u.id();
			eylemYap();
		}
		seciliUrun = u.id();
		sonTik = simdi;
		sonTikId = u.id();
	}

	private int sutunSay() {
		int lx = px + 8, lw = 92, sagW = 118;
		int gx = lx + lw + 8;
		int gw = px + pw - 8 - sagW - 8 - gx;
		return Math.max(1, (gw + ARA) / (KART + ARA));
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double yatay, double dikey) {
		kaydir = Math.max(0, kaydir - (int) Math.signum(dikey));
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean ciftTik) {
		return super.mouseClicked(e, ciftTik);
	}
}
