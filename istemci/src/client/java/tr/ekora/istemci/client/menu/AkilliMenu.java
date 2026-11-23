package tr.ekora.istemci.client.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import tr.ekora.istemci.client.Ayarlar;
import tr.ekora.istemci.client.Ekora;
import tr.ekora.istemci.client.SandikGorunumu;
import tr.ekora.istemci.client.ekran.Cizim;

/**
 * Akıllı Menü: sunucudaki eklenti menülerini (mezat, market, siparişler, mağaza, oyun ve rol seçimi, kasaba, RPG…)
 * sandık ızgarası yerine Ekora kartlarıyla gösterir.
 * <ul>
 * <li>İçerik eşyaları kart olur: simge, renkli ad, açıklama, fiyat/satıcı/süre/adet rozetleri, tıklama ipuçları.</li>
 * <li>Sayfa okları, geri, kapat ve son satırdaki denetimler alttaki araç çubuğuna; adlı renkli camlar büyük eylem düğmelerine dönüşür.</li>
 * <li>Arama kutusu kartları süzer; envanter istenirse altta açılır.</li>
 * <li>Tıklamalar aynı yuvaya aynı tıklama türüyle gider (sol, sağ, shift, orta): eklentiler hiçbir fark görmez.</li>
 * </ul>
 * "Klasik" düğmesi menüyü sandık görünümüyle yeniden açar ve bu başlık için hatırlanır.
 */
public final class AkilliMenu extends AbstractContainerScreen<ChestMenu> {
	private static final Locale TR = Locale.forLanguageTag("tr");
	private static final Map<String, Integer> TEMA_RENK = Map.of("ana", 0xFF22D3EE, "pazar", 0xFFF5B83D, "magaza", 0xFFA855F7, "oyun", 0xFFF87171,
			"rpg", 0xFFF97316, "kasaba", 0xFFFACC15, "sosyal", 0xFF38BDF8, "parkur", 0xFFC084FC);
	private static final Map<String, String> TEMA_AD = Map.of("ana", "Ekora", "pazar", "Pazar", "magaza", "Mağaza", "oyun", "Oyunlar",
			"rpg", "RPG", "kasaba", "Kasaba", "sosyal", "Sosyal", "parkur", "Parkur");
	private static final Map<String, String> EKLENTI_TEMA = Map.of("EkoraPazar", "pazar", "EkoraPuan", "magaza", "EkoraOyunlar", "oyun", "EkoraRPG", "rpg",
			"EkoraKasaba", "kasaba", "EkoraSosyal", "sosyal", "EkoraParkur", "parkur", "EkoraHub", "ana");

	private final Inventory envanter;
	private final int satir;
	private final String tema;
	private final int vurgu;
	private final String baslikDuz;
	private final Component baslikAcik;
	private int px, py, pw, ph;
	private int icX, icY, icW, icH;
	private int envX, envY;
	private float kaydir;
	private int icerikH;
	private EditBox arama;
	private String sorgu = "";
	/** Sunucu araması (tüm sayfalarda): boş değilse arama çubuğu büyük ve Enter sunucuda arar. */
	private final String sunucuArama;
	/** Az önce sunucuda aranan sorgu: sonuç menüsü açılınca kutuda kalsın. */
	private static String sonSunucuSorgu = "";
	private static long sonSunucuZaman;
	private boolean gecis;
	private final Map<Integer, MenuOgesi> onbellek = new HashMap<>();
	private final Map<Integer, ItemStack> onbellekYigin = new HashMap<>();

	/** Bu kare çizilen tıklanabilir alanlar (kart, düğme) ve bağlı yuva. */
	private record Alan(int x, int y, int w, int h, MenuOgesi o) {
		boolean icinde(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
	}

	private final List<Alan> alanlar = new ArrayList<>();
	private String sorguIlk = "";

	public AkilliMenu(ChestMenu menu, Inventory inv, Component title, String eklenti) {
		this(menu, inv, title, eklenti, "");
	}

	public AkilliMenu(ChestMenu menu, Inventory inv, Component title, String eklenti, String sunucuArama) {
		super(menu, inv, title, 176, 166);
		this.sunucuArama = sunucuArama == null ? "" : sunucuArama;
		if (!this.sunucuArama.isEmpty() && System.currentTimeMillis() - sonSunucuZaman < 4000) sorguIlk = sonSunucuSorgu;
		this.envanter = inv;
		this.satir = menu.getRowCount();
		String t = SandikGorunumu.tema(title);
		if (t == null) t = EKLENTI_TEMA.getOrDefault(eklenti, "ana");
		this.tema = t;
		this.vurgu = TEMA_RENK.getOrDefault(t, Cizim.CAMGOBEGI);
		this.baslikDuz = SandikGorunumu.duzBaslik(title);
		this.baslikAcik = SandikGorunumu.acikRenk(title);
	}

	/** Vanilla sandık ekranını, sunucu ipucu varsa Akıllı Menü'ye çevirir; yoksa aynısını döndürür. */
	public static Screen donustur(Screen s) {
		if (!(s instanceof ContainerScreen cs) || !Ayarlar.akilliMenu || !SandikGorunumu.aktif()) return s;
		String b = SandikGorunumu.duzBaslik(cs.getTitle());
		Ekora.GuiIpucu i = Ekora.ipucuAl(b);
		if (i == null || !i.akilli() || Ayarlar.klasikBasliklar.contains(b)) return s;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return s;
		return new AkilliMenu(cs.getMenu(), mc.player.getInventory(), cs.getTitle(), i.eklenti(), i.arama());
	}

	// ================================================================== yerleşim
	@Override
	protected void init() {
		pw = Math.min(this.width - 12, 460);
		ph = Math.min(this.height - 10, 300);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		int altH = Ayarlar.menuEnvanter ? 96 : 26;
		icX = px + 8;
		boolean genisArama = !sunucuArama.isEmpty();
		icY = py + 26 + (genisArama ? 22 : 0);
		icW = pw - 16;
		icH = ph - 26 - altH - 6 - (genisArama ? 22 : 0);
		envX = px + 10;
		envY = py + ph - 80;
		// oyuncu envanteri yuvaları sabit konumludur (menüye göre); menü kökünü envanter istenen yere gelecek şekilde kaydır
		this.leftPos = envX - 8;
		this.topPos = envY - (satir * 18 + 31);
		this.titleLabelX = -9999;
		int ogeSayisi = 0;
		for (int i = 0; i < satir * 9; i++) if (!menu.slots.get(i).getItem().isEmpty()) ogeSayisi++;
		if (genisArama) {
			// büyük arama çubuğu: yazdıkça bu sayfayı süzer, Enter bütün sayfalarda (sunucuda) arar
			arama = new EditBox(this.font, px + 30, py + 31, pw - 30 - 120, 12, Component.literal("Ara"));
			arama.setHint(Component.literal("§7" + sunucuArama + "… §8(ör. elmas, elytra)"));
		} else if (ogeSayisi >= 8) {
			int aw = Math.min(110, pw / 4);
			arama = new EditBox(this.font, px + pw - 18 - 60 - 6 - aw, py + 6, aw, 12, Component.literal("Ara"));
			arama.setHint(Component.literal("§7Ara…"));
		} else arama = null;
		if (arama != null) {
			arama.setBordered(false);
			arama.setMaxLength(40);
			arama.setTextColor(0xFFFFFFFF);
			// sunucu araması sonucu: sorgu kutuda görünür ama yerel süzme yapılmaz (sonuçlar zaten süzülmüş; oyuncu İngilizce dilde olabilir)
			arama.setValue(genisArama && sorgu.isEmpty() && !sorguIlk.isEmpty() ? sorguIlk : sorgu);
			arama.setResponder(v -> { sorgu = v.toLowerCase(TR).trim(); kaydir = 0; });
			this.addRenderableWidget(arama);
		}
	}

	private MenuOgesi oge(int slot) {
		ItemStack st = menu.slots.get(slot).getItem();
		ItemStack eski = onbellekYigin.get(slot);
		MenuOgesi o = onbellek.get(slot);
		if (o == null || eski != st || (eski != null && eski.getCount() != st.getCount())) {
			o = MenuOgesi.analiz(slot, st, satir);
			onbellek.put(slot, o);
			onbellekYigin.put(slot, st);
		}
		return o;
	}

	// ================================================================== çizim
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractBackground(g, mx, my, delta);
		alanlar.clear();
		Cizim.degradeY(g, 0, 0, this.width, this.height, 0x70060812, 0xA00A0C18);
		Cizim.panel(g, px - 3, py - 3, pw + 6, ph + 6, 0x50000000, 0);
		Cizim.panel(g, px, py, pw, ph, Cizim.ARKA, Cizim.KENAR);
		Cizim.degradeX(g, px + 2, py + 1, pw - 4, 2, vurgu, Cizim.CAMGOBEGI);
		Cizim.degradeY(g, px + 1, py + 3, pw - 2, 20, Cizim.alfa(vurgu, 0.16f), 0x00000000);

		// başlık
		Cizim.degradeYazi(g, this.font, "EKORA", px + 8, py + 8, Cizim.MOR, Cizim.CAMGOBEGI);
		int bx = px + 8 + this.font.width("EKORA") + 4;
		g.text(this.font, "•", bx, py + 8, 0xFF5B6188, false);
		bx += this.font.width("• ");
		int sag = px + pw - 18 - 60 - 6 - (arama != null && sunucuArama.isEmpty() ? Math.min(110, pw / 4) + 6 : 0);
		yaz(g, baslikAcik, bx, py + 8, sag - bx - 4);
		// arama kutusu çerçevesi
		if (arama != null && sunucuArama.isEmpty()) {
			Cizim.panel(g, arama.getX() - 4, py + 4, Math.min(110, pw / 4) + 6, 14, 0xFF0D101C, arama.isFocused() ? vurgu : Cizim.KENAR);
		} else if (arama != null) {
			int ay = py + 27;
			Cizim.panel(g, px + 8, ay, pw - 16, 18, 0xFF0D101C, arama.isFocused() ? vurgu : Cizim.karis(Cizim.KENAR, vurgu, 0.35f));
			Cizim.degradeX(g, px + 9, ay + 1, (pw - 18) / 3, 16, Cizim.alfa(vurgu, 0.12f), 0x00000000);
			// büyüteç: halka + sap
			int bx0 = px + 14, by0 = ay + 4;
			g.fill(bx0 + 1, by0, bx0 + 6, by0 + 1, vurgu); g.fill(bx0 + 1, by0 + 6, bx0 + 6, by0 + 7, vurgu);
			g.fill(bx0, by0 + 1, bx0 + 1, by0 + 6, vurgu); g.fill(bx0 + 6, by0 + 1, bx0 + 7, by0 + 6, vurgu);
			g.fill(bx0 + 6, by0 + 6, bx0 + 8, by0 + 8, vurgu); g.fill(bx0 + 8, by0 + 8, bx0 + 10, by0 + 10, vurgu);
			int dx = px + pw - 112;
			boolean ust = Cizim.icinde(mx, my, dx, ay + 2, 100, 14);
			Cizim.panel(g, dx, ay + 2, 100, 14, ust ? Cizim.koyu(vurgu, 0.35f) : Cizim.koyu(vurgu, 0.65f), vurgu);
			yazi(g, "§lTümünde ara ↵", dx + 2, ay + 5, 96, 0xFFFFFFFF, 0.6f, true);
			if (ust) g.setTooltipForNextFrame(this.font, Component.literal("Bütün sayfalarda ara (Enter). Yazarken bu sayfa anında süzülür."), mx, my);
		}
		// sağ üst düğmeler: envanter, klasik, kapat
		dugme(g, mx, my, px + pw - 18 - 60, py + 5, 28, 12, Ayarlar.menuEnvanter ? "§fÇanta" : "§7Çanta", Ayarlar.menuEnvanter ? vurgu : 0xFF3A3F5C);
		dugme(g, mx, my, px + pw - 18 - 30, py + 5, 28, 12, "§7Eski", 0xFF3A3F5C);
		boolean kv = Cizim.icinde(mx, my, px + pw - 16, py + 5, 12, 12);
		g.centeredText(this.font, "✕", px + pw - 10, py + 7, kv ? Cizim.KIRMIZI : Cizim.SOLUK);
		if (Cizim.icinde(mx, my, px + pw - 18 - 60, py + 5, 28, 12)) g.setTooltipForNextFrame(this.font, Component.literal("Envanterini göster/gizle"), mx, my);
		if (Cizim.icinde(mx, my, px + pw - 18 - 30, py + 5, 28, 12)) g.setTooltipForNextFrame(this.font, Component.literal("Bu menüyü eski (sandık) görünümüyle aç"), mx, my);

		// öğeleri ayır
		List<MenuOgesi> icerik = new ArrayList<>(), arac = new ArrayList<>(), eylem = new ArrayList<>();
		MenuOgesi onceki = null;
		for (int i = 0; i < satir * 9; i++) {
			MenuOgesi o = oge(i);
			if (o.bos()) { onceki = null; continue; }
			if (o.ayni(onceki) && (o.tur == MenuOgesi.Tur.EYLEM || onceki.slot == i - 1)) continue;
			onceki = o;
			switch (o.tur) {
				case ICERIK -> { if (sorgu.isEmpty() || o.aramaMetni.contains(sorgu)) icerik.add(o); }
				case ARAC -> arac.add(o);
				case EYLEM -> eylem.add(o);
				default -> { }
			}
		}

		// eylem düğmeleri (adlı renkli camlar) içerik alanının altına
		int eylemH = eylem.isEmpty() ? 0 : 26;
		int kartAlanH = icH - eylemH;
		g.enableScissor(icX, icY, icX + icW, icY + kartAlanH);
		icerikH = kartlar(g, mx, my, icerik, icX, icY - Math.round(kaydir), icW, kartAlanH, eylem.isEmpty());
		g.disableScissor();
		if (icerikH > kartAlanH) {
			float oran = kartAlanH / (float) icerikH;
			int h = Math.max(14, (int) (kartAlanH * oran));
			int y = icY + (int) ((kartAlanH - h) * (kaydir / Math.max(1f, icerikH - kartAlanH)));
			g.fill(icX + icW + 2, icY, icX + icW + 4, icY + kartAlanH, 0x40FFFFFF);
			g.fill(icX + icW + 2, y, icX + icW + 4, y + h, vurgu);
		}
		if (icerik.isEmpty()) g.centeredText(this.font, sorgu.isEmpty() ? "§7Burada bir şey yok" : "§7\"" + sorgu + "\" bulunamadı", icX + icW / 2, icY + kartAlanH / 2 - 4, Cizim.SOLUK);
		if (!eylem.isEmpty()) eylemler(g, mx, my, eylem, icX, icY + kartAlanH + 4, icW);

		// alt bölüm: envanter (isteğe bağlı) + araç çubuğu
		int aracY = py + ph - (Ayarlar.menuEnvanter ? 94 : 24);
		g.fill(px + 6, aracY - 3, px + pw - 6, aracY - 2, Cizim.KENAR);
		if (Ayarlar.menuEnvanter) {
			g.text(this.font, "Envanterin", envX, aracY + 1, Cizim.SOLUK, false); // yuvalar envY'den başlar (etiketin altında)
			for (int i = satir * 9; i < menu.slots.size(); i++) {
				Slot s = menu.slots.get(i);
				int x = leftPos + s.x - 1, y = topPos + s.y - 1;
				Cizim.panel(g, x, y, 18, 18, 0xFF141726, 0xFF262A40);
			}
			araclar(g, mx, my, arac, envX + 170, aracY + 12, px + pw - 8 - (envX + 170), true);
		} else {
			araclar(g, mx, my, arac, px + 8, aracY, pw - 16, false);
		}
	}

	private void yaz(GuiGraphicsExtractor g, Component c, int x, int y, int genislik) {
		int w = this.font.width(c);
		if (w <= genislik) { g.text(this.font, c, x, y, Cizim.YAZI, false); return; }
		float s = Math.max(0.7f, genislik / (float) w);
		g.pose().pushMatrix();
		g.pose().translate(x, y + (8 - 8 * s) / 2f);
		g.pose().scale(s, s);
		if (w * s <= genislik) g.text(this.font, c, 0, 0, Cizim.YAZI, false);
		else g.text(this.font, Cizim.sigdir(this.font, c.getString(), (int) (genislik / s)), 0, 0, Cizim.YAZI, false);
		g.pose().popMatrix();
	}

	private void yazi(GuiGraphicsExtractor g, String metin, int x, int y, int genislik, int renk, float enAz, boolean ortala) {
		int w = this.font.width(metin);
		float s = w <= genislik ? 1f : Math.max(enAz, genislik / (float) Math.max(1, w));
		String t = s < 1f ? Cizim.sigdir(this.font, metin, (int) (genislik / s)) : metin;
		int tw = (int) (this.font.width(t) * s);
		g.pose().pushMatrix();
		g.pose().translate(ortala ? x + (genislik - tw) / 2f : x, y + (8 - 8 * s) / 2f);
		g.pose().scale(s, s);
		g.text(this.font, t, 0, 0, renk, false);
		g.pose().popMatrix();
	}

	private void dugme(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String yazi, int renk) {
		boolean ust = Cizim.icinde(mx, my, x, y, w, h);
		Cizim.panel(g, x, y, w, h, ust ? Cizim.koyu(renk, 0.4f) : Cizim.koyu(renk, 0.7f), ust ? Cizim.karis(renk, 0xFFFFFFFF, 0.3f) : renk);
		yazi(g, yazi, x + 2, y + (h - 8) / 2, w - 4, 0xFFFFFFFF, 0.6f, true);
	}

	/** İçerik kartları. Döndürdüğü: toplam içerik yüksekliği. */
	private int kartlar(GuiGraphicsExtractor g, int mx, int my, List<MenuOgesi> l, int x, int y0, int w, int h, boolean eylemYok) {
		int n = l.size();
		if (n == 0) return 0;
		boolean buyuk = n <= 4 && w >= 200;
		int sutun, kh;
		if (buyuk) {
			sutun = n == 1 ? 1 : n == 3 ? 3 : 2;
			if (sutun == 3 && w < 330) sutun = 2;
			int satirlar = (n + sutun - 1) / sutun;
			int enCokSatir = 0;
			for (MenuOgesi o : l) enCokSatir = Math.max(enCokSatir, o.bilgi.size());
			int icerikKh = 34 + Math.min(6, enCokSatir) * 9 + 6;
			kh = Math.max(54, Math.min(Math.min(110, icerikKh), (h - (satirlar - 1) * 6) / satirlar));
		} else {
			sutun = Math.max(1, w / 128);
			kh = 40;
		}
		int kw = (w - (sutun - 1) * 6) / sutun;
		if (buyuk && n == 1) { kw = Math.min(w, 240); }
		int ox = buyuk && n == 1 ? x + (w - kw) / 2 : x;
		if (buyuk && n == 1) y0 = y0 + Math.max(0, (h - kh) / 2);
		for (int i = 0; i < n; i++) {
			MenuOgesi o = l.get(i);
			int kx = ox + (i % sutun) * (kw + 6), ky = y0 + (i / sutun) * (kh + 6);
			if (ky + kh < icY || ky > icY + h) continue;
			kart(g, mx, my, o, kx, ky, kw, kh, buyuk, ky >= icY - kh && my >= icY && my < icY + h);
		}
		return ((n + sutun - 1) / sutun) * (kh + 6);
	}

	private void kart(GuiGraphicsExtractor g, int mx, int my, MenuOgesi o, int x, int y, int w, int h, boolean buyuk, boolean tiklanir) {
		boolean ust = tiklanir && Cizim.icinde(mx, my, x, y, w, h);
		int r = acikMi(o.renk) ? vurgu : o.renk;
		Cizim.panel(g, x, y, w, h, ust ? Cizim.karis(0xE0141828, Cizim.koyu(r, 0.75f), 0.8f) : 0xE0141828, ust ? r : Cizim.karis(Cizim.KENAR, r, 0.35f));
		Cizim.degradeX(g, x + 1, y + 1, Math.max(1, w / 2), h - 2, Cizim.alfa(r, 0.12f), 0x00000000);
		g.fill(x + 1, y + 3, x + 3, y + h - 3, r);
		if (tiklanir) alanlar.add(new Alan(x, y, w, h, o));
		int sat = 0;
		if (buyuk) {
			// büyük kart: üstte büyük simge, altında ad ve açıklama
			int s = h >= 90 ? 2 : 1;
			int iw = 16 * (s + 1) / (s == 2 ? 1 : 1);
			g.pose().pushMatrix();
			g.pose().translate(x + 8, y + 6);
			g.pose().scale(s == 2 ? 2f : 1.5f, s == 2 ? 2f : 1.5f);
			g.item(o.stack, 0, 0);
			g.pose().popMatrix();
			int tx = x + (s == 2 ? 44 : 36);
			yaz(g, SandikGorunumu.acikRenk(o.ad), tx, y + 7, x + w - tx - 6);
			int ly = y + 19;
			int max = (h - 30) / 9;
			for (Component c : o.bilgi) {
				if (sat >= max) break;
				yaz(g, SandikGorunumu.acikRenk(c), tx, ly, x + w - tx - 6);
				ly += 9;
				sat++;
			}
			iw = tx;
		} else {
			Cizim.panel(g, x + 6, y + (h - 22) / 2, 22, 22, Cizim.koyu(r, 0.78f), 0);
			g.item(o.stack, x + 9, y + (h - 22) / 2 + 3);
			g.itemDecorations(this.font, o.stack, x + 9, y + (h - 22) / 2 + 3);
			int tx = x + 33;
			yaz(g, SandikGorunumu.acikRenk(o.ad), tx, y + 5, x + w - tx - 5);
			Component ilk = !o.bilgi.isEmpty() ? o.bilgi.get(0) : null;
			if (ilk != null) yaz(g, SandikGorunumu.acikRenk(ilk), tx, y + 16, x + w - tx - 5);
		}
		// rozetler: fiyat(lar), kişi, süre, adet
		int rx = buyuk ? x + 8 : x + 33, ry = y + h - 12;
		int bitis = x + w - 5;
		for (MenuOgesi.Alan f : o.fiyatlar) {
			if (rx >= bitis - 20) break;
			String et = kisaAnahtar(f.anahtar()) + " " + kisaDeger(f.deger());
			rx += rozet(g, rx, ry, et, Cizim.ALTIN, bitis - rx) + 3;
		}
		if (o.adet != null && rx < bitis - 20) rx += rozet(g, rx, ry, kisaAnahtar(o.adet.anahtar()) + " " + kisaDeger(o.adet.deger()), Cizim.CAMGOBEGI, bitis - rx) + 3;
		if (o.kisi != null && rx < bitis - 20) rx += rozet(g, rx, ry, o.kisi.deger(), 0xFF94A3B8, bitis - rx) + 3;
		if (o.sure != null && rx < bitis - 20) rozet(g, rx, ry, kisaDeger(o.sure.deger()), 0xFFFB923C, bitis - rx);
		// üzerine gelince: tam ipucu + tıklama ipuçları
		if (ust) {
			g.setTooltipForNextFrame(this.font, o.stack, mx, my);
			if (!o.ipuclari.isEmpty() && !buyuk) {
				MenuOgesi.Ipucu ip = o.ipuclari.get(0);
				Cizim.panel(g, x + 30, y + 15, w - 34, 10, 0xF0101322, 0);
				yazi(g, "§e" + ip.tus() + ": §f" + ip.eylem(), x + 32, y + 16, w - 38, 0xFFFFFFFF, 0.55f, false);
			}
		}
		if (buyuk && !o.ipuclari.isEmpty()) {
			MenuOgesi.Ipucu ip = o.ipuclari.get(0);
			yazi(g, "§e" + ip.tus() + ": §f" + ip.eylem(), x + w - 6 - Math.min(w / 2, this.font.width(ip.tus() + ": " + ip.eylem())), y + h - 12, Math.min(w / 2, this.font.width(ip.tus() + ": " + ip.eylem())), 0xFFFFFFFF, 0.55f, false);
		}
	}

	/** Beyaza yakın renk (kart tonu olarak gri görünür; yerine tema rengi). */
	private static boolean acikMi(int c) {
		int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
		return r > 200 && g > 200 && b > 200;
	}

	private static String kisaAnahtar(String a) {
		String k = a.toLowerCase(TR);
		if (k.contains("satın al") || k.startsWith("alış")) return "Al";
		if (k.equals("sat") || k.startsWith("satış")) return "Sat";
		if (k.startsWith("fiyat") || k.startsWith("tutar")) return "";
		return a.length() > 10 ? a.substring(0, 10) : a;
	}

	/** "10 SMP Coin / adet • 64 adet: 640" → "10 SMP Coin" */
	private static String kisaDeger(String d) {
		String s = d.replaceAll("[-]", "");
		int i = s.indexOf(" • ");
		if (i > 0) s = s.substring(0, i);
		i = s.indexOf(" / adet");
		if (i > 0) s = s.substring(0, i) + "/ad";
		i = s.indexOf(" (");
		if (i > 0) s = s.substring(0, i);
		return s.trim();
	}

	private int rozet(GuiGraphicsExtractor g, int x, int y, String yazi, int renk, int enFazla) {
		yazi = yazi.trim();
		int tw = this.font.width(yazi);
		float s = 0.7f;
		int w = Math.min(enFazla, (int) (tw * s) + 8);
		if (w < 14) return 0;
		Cizim.panel(g, x, y, w, 10, Cizim.koyu(renk, 0.78f), Cizim.koyu(renk, 0.35f));
		yazi(g, yazi, x + 4, y + 1, w - 7, renk, 0.55f, false);
		return w;
	}

	/** Adlı renkli camlar: büyük renkli eylem düğmeleri (Satın al, Vazgeç, Teslim et…). */
	private void eylemler(GuiGraphicsExtractor g, int mx, int my, List<MenuOgesi> l, int x, int y, int w) {
		int n = l.size();
		int bw = Math.min(140, (w - (n - 1) * 6) / n);
		int ox = x + (w - (bw * n + (n - 1) * 6)) / 2;
		for (int i = 0; i < n; i++) {
			MenuOgesi o = l.get(i);
			int bx = ox + i * (bw + 6);
			boolean ust = Cizim.icinde(mx, my, bx, y, bw, 20);
			int r = o.renk;
			Cizim.panel(g, bx, y, bw, 20, ust ? Cizim.koyu(r, 0.35f) : Cizim.koyu(r, 0.6f), ust ? Cizim.karis(r, 0xFFFFFFFF, 0.4f) : r);
			yazi(g, "§l" + o.adDuz, bx + 4, y + 6, bw - 8, 0xFFFFFFFF, 0.6f, true);
			alanlar.add(new Alan(bx, y, bw, 20, o));
			if (ust) g.setTooltipForNextFrame(this.font, o.stack, mx, my);
		}
	}

	/** Araç çubuğu: önceki/geri solda, sonraki sağda, diğerleri ortada. */
	private void araclar(GuiGraphicsExtractor g, int mx, int my, List<MenuOgesi> l, int x, int y, int w, boolean izgara) {
		if (l.isEmpty()) return;
		List<MenuOgesi> sol = new ArrayList<>(), orta = new ArrayList<>(), sag = new ArrayList<>();
		for (MenuOgesi o : l) {
			switch (o.yon) {
				case ONCEKI, GERI, KAPAT -> sol.add(o);
				case SONRAKI -> sag.add(o);
				default -> orta.add(o);
			}
		}
		if (izgara) {
			List<MenuOgesi> hepsi = new ArrayList<>(sol); hepsi.addAll(orta); hepsi.addAll(sag);
			int sutun = Math.max(1, w / 92);
			int bw = (w - (sutun - 1) * 4) / sutun;
			for (int i = 0; i < hepsi.size(); i++) arac(g, mx, my, hepsi.get(i), x + (i % sutun) * (bw + 4), y + (i / sutun) * 20, bw, 17);
			return;
		}
		int bh = 18;
		int cx = x;
		for (MenuOgesi o : sol) { int bw = aracGenislik(o, 96); arac(g, mx, my, o, cx, y, bw, bh); cx += bw + 4; }
		int sx = x + w;
		for (MenuOgesi o : sag) { int bw = aracGenislik(o, 96); sx -= bw; arac(g, mx, my, o, sx, y, bw, bh); sx -= 4; }
		int kalan = sx - cx;
		if (orta.isEmpty() || kalan < 30) return;
		int bw = Math.min(110, (kalan - (orta.size() - 1) * 4) / orta.size());
		if (bw < 62) bw = 20; // sığmıyorsa yalnızca simge (adı ipucunda)
		int ox = cx + (kalan - (bw * orta.size() + (orta.size() - 1) * 4)) / 2;
		for (int i = 0; i < orta.size(); i++) arac(g, mx, my, orta.get(i), ox + i * (bw + 4), y, bw, bh);
	}

	private int aracGenislik(MenuOgesi o, int enFazla) {
		return Math.min(enFazla, 26 + (int) (this.font.width(o.adDuz) * 0.75f));
	}

	private void arac(GuiGraphicsExtractor g, int mx, int my, MenuOgesi o, int x, int y, int w, int h) {
		if (w < 18) return;
		if (w <= 24) { x += (w - 18) / 2 + 1; }
		boolean ust = Cizim.icinde(mx, my, x, y, w, h);
		int r = o.yon == MenuOgesi.Yon.KAPAT ? Cizim.KIRMIZI : o.yon != MenuOgesi.Yon.YOK ? vurgu : o.renk;
		Cizim.panel(g, x, y, w, h, ust ? Cizim.koyu(r, 0.45f) : 0xE0171A2B, ust ? r : Cizim.karis(Cizim.KENAR, r, 0.3f));
		g.pose().pushMatrix();
		g.pose().translate(x + 3, y + (h - 12) / 2f);
		g.pose().scale(0.75f, 0.75f);
		g.item(o.stack, 0, 0);
		g.pose().popMatrix();
		if (w > 24) yaz(g, SandikGorunumu.acikRenk(o.ad), x + 17, y + (h - 8) / 2, w - 20);
		alanlar.add(new Alan(x, y, w, h, o));
		if (ust) g.setTooltipForNextFrame(this.font, o.stack, mx, my);
	}

	// ================================================================== vanilla parçaları
	@Override
	protected void extractSlot(GuiGraphicsExtractor g, Slot slot, int mx, int my) {
		if (Ayarlar.menuEnvanter && slot.container == envanter) super.extractSlot(g, slot, mx, my);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) { }

	/** Menü yuvaları gizli: üzerlerine gelinmez (vurgu, ipucu, sayı tuşu takası olmaz). Envanter yuvaları açıksa normal. */
	@Override
	protected boolean isHovering(int x, int y, int w, int h, double mx, double my) {
		if (!Ayarlar.menuEnvanter || y < satir * 18 + 20) return false;
		return super.isHovering(x, y, w, h, mx, my);
	}

	@Override
	protected boolean hasClickedOutside(double mx, double my, int l, int t) {
		return !Cizim.icinde(mx, my, px, py, pw, ph);
	}

	private boolean envanterde(double mx, double my) {
		return Ayarlar.menuEnvanter && Cizim.icinde(mx, my, envX - 2, envY - 2, 166, 80);
	}

	// ================================================================== girdi
	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean cift) {
		double mx = e.x(), my = e.y();
		if (arama != null && arama.isMouseOver(mx, my)) return super.mouseClicked(e, cift);
		if (arama != null && !sunucuArama.isEmpty() && Cizim.icinde(mx, my, px + pw - 112, py + 29, 100, 14)) { tik(); sunucudaAra(); return true; }
		if (arama != null) arama.setFocused(false);
		if (Cizim.icinde(mx, my, px + pw - 16, py + 5, 12, 12)) { tik(); this.onClose(); return true; }
		if (Cizim.icinde(mx, my, px + pw - 18 - 60, py + 5, 28, 12)) {
			tik();
			Ayarlar.menuEnvanter = !Ayarlar.menuEnvanter;
			Ayarlar.kaydet();
			this.rebuildWidgets();
			return true;
		}
		if (Cizim.icinde(mx, my, px + pw - 18 - 30, py + 5, 28, 12)) { tik(); klasigeGec(); return true; }
		for (int i = alanlar.size() - 1; i >= 0; i--) {
			Alan a = alanlar.get(i);
			if (a.icinde(mx, my)) { tikla(a.o(), e); return true; }
		}
		if (envanterde(mx, my) || isQuickCrafting) return super.mouseClicked(e, cift);
		if (Cizim.icinde(mx, my, px, py, pw, ph)) return true;
		return super.mouseClicked(e, cift);
	}

	private void tikla(MenuOgesi o, MouseButtonEvent e) {
		Slot s = menu.slots.get(o.slot);
		int b = e.button();
		boolean shift = (e.modifiers() & 3) != 0; // GLFW: 1, SDL: sol 1 / sağ 2
		ContainerInput tur = b == 2 ? ContainerInput.CLONE : shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
		this.slotClicked(s, o.slot, b == 2 ? 2 : b == 1 ? 1 : 0, tur);
		tik();
	}

	private static void tik() {
		Ekora.ses(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.5f);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (envanterde(e.x(), e.y()) || isQuickCrafting) return super.mouseReleased(e);
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (envanterde(e.x(), e.y()) || isQuickCrafting) return super.mouseDragged(e, dx, dy);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double yatay, double dikey) {
		if (Cizim.icinde(mx, my, icX, icY, icW + 6, icH)) {
			float maks = Math.max(0, icerikH - icH + 26);
			kaydir = Math.max(0, Math.min(maks, kaydir - (float) dikey * 20f));
			return true;
		}
		return super.mouseScrolled(mx, my, yatay, dikey);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (arama != null && arama.isFocused()) {
			if (e.key() == 256 || e.key() == 27) { arama.setFocused(false); return true; } // ESC: önce aramadan çık
			if ((e.key() == 257 || e.key() == 335 || e.key() == 13) && !sunucuArama.isEmpty()) { sunucudaAra(); return true; } // Enter
			arama.keyPressed(e);
			return true; // yazarken envanter tuşu (E) menüyü kapatmasın
		}
		return super.keyPressed(e);
	}

	private int denetimTik;

	/**
	 * Güvenlik ağı: içerik geldikten sonra bu bir depo gibi görünüyorsa (süs camı yok, eşyaların çoğu adsız: takım sandığı, ganimet sandığı…)
	 * eşya alıp koymak için klasik görünüme kendiliğinden geçilir (hatırlanmaz).
	 */
	@Override
	protected void containerTick() {
		super.containerTick();
		if (gecis || denetimTik > 40) return;
		int dolu = 0, adli = 0, cam = 0;
		for (int i = 0; i < satir * 9; i++) {
			ItemStack st = menu.slots.get(i).getItem();
			if (st.isEmpty()) continue;
			dolu++;
			if (SandikGorunumu.susCami(st) || net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().endsWith("glass_pane")) cam++;
			else {
				// ITEM_NAME ve boş LORE her eşyada varsayılan olarak bulunur; yalnızca özel ad ya da dolu açıklama sayılır
				net.minecraft.world.item.component.ItemLore l = st.get(net.minecraft.core.component.DataComponents.LORE);
				if (st.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) || (l != null && !l.lines().isEmpty())) adli++;
			}
		}
		if (dolu == 0) { denetimTik++; return; }
		denetimTik = 99;
		if (cam == 0 && adli * 2 < dolu) klasigeGec(false);
	}

	/** Testler için: arama kutusuna yaz (bu sayfayı süzer). */
	public void testAramaYaz(String s) {
		if (arama != null) { arama.setValue(s); arama.setFocused(true); }
	}

	private void sunucudaAra() {
		String q = arama == null ? "" : arama.getValue().trim();
		if (q.isEmpty()) return;
		sonSunucuSorgu = q;
		sonSunucuZaman = System.currentTimeMillis();
		Ekora.istekCok("ara", "sorgu", q);
	}

	/** Bu başlığı klasik (sandık) görünümle yeniden açar ve hatırlar. */
	private void klasigeGec() { klasigeGec(true); }

	private void klasigeGec(boolean hatirla) {
		if (hatirla) {
			Ayarlar.klasikBasliklar.add(baslikDuz);
			Ayarlar.kaydet();
		}
		gecis = true;
		Minecraft.getInstance().gui.setScreen(new ContainerScreen(menu, envanter, this.title));
	}

	@Override
	public void removed() {
		if (!gecis) super.removed();
	}
}
