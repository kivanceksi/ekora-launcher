package tr.ekora.istemci.client.ekran;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import tr.ekora.istemci.client.Ekora;

/**
 * Ekora ekranlarının ortak tabanı: ortalanmış koyu panel, degrade başlık şeridi, kapat düğmesi,
 * fareyle büyüyen düğmeler ve açılış animasyonu. Oyunu durdurmaz (çok oyunculu).
 */
public abstract class EkoraEkran extends Screen {
	protected int px, py, pw, ph;
	protected final long acilis = System.currentTimeMillis();
	protected final List<Dugme> dugmeler = new ArrayList<>();
	protected int fareX, fareY;

	protected EkoraEkran(String baslik) {
		super(Component.literal(baslik));
	}

	/** Tıklanabilir alan: konum her karede çizimle birlikte güncellenir. */
	public static final class Dugme {
		public int x, y, w, h;
		public boolean aktif = true;
		public float vurgu;
		public final Runnable eylem;

		public Dugme(Runnable eylem) { this.eylem = eylem; }

		public Dugme yer(int x, int y, int w, int h) {
			this.x = x; this.y = y; this.w = w; this.h = h;
			return this;
		}

		public boolean ustunde(double mx, double my) { return Cizim.icinde(mx, my, x, y, w, h); }
	}

	private Dugme onayDugme;
	private long onayZamani;

	/** Para harcayan düğmeler için iki tık: ilk tık onay ister, 3,5 sn içinde ikinci tık true döner. */
	protected boolean onay(Dugme d) {
		long s = System.currentTimeMillis();
		if (onayDugme == d && s - onayZamani < 3500) {
			onayDugme = null;
			return true;
		}
		onayDugme = d;
		onayZamani = s;
		return false;
	}

	protected boolean onayda(Dugme d) {
		return onayDugme == d && System.currentTimeMillis() - onayZamani < 3500;
	}

	protected Dugme dugme(Runnable eylem) {
		Dugme d = new Dugme(eylem);
		dugmeler.add(d);
		return d;
	}

	protected abstract int genislik();

	protected abstract int yukseklik();

	@Override
	protected void init() {
		pw = Math.min(genislik(), this.width - 16);
		ph = Math.min(yukseklik(), this.height - 16);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	protected float acilisOrani() {
		float t = Math.min(1f, (System.currentTimeMillis() - acilis) / 220f);
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		fareX = mouseX;
		fareY = mouseY;
		// 26.3: Screen.extractRenderState arka planı (bulanıklık) kendisi çizer; kare başına bir kez olmalı
		super.extractRenderState(g, mouseX, mouseY, delta);
		float a = acilisOrani();
		g.pose().pushMatrix();
		float s = 0.94f + 0.06f * a;
		g.pose().translate(px + pw / 2f, py + ph / 2f);
		g.pose().scale(s, s);
		g.pose().translate(-(px + pw / 2f), -(py + ph / 2f));
		// gölge + panel
		Cizim.panel(g, px - 3, py - 3, pw + 6, ph + 6, 0x40000000, 0);
		Cizim.panel(g, px, py, pw, ph, Cizim.ARKA, Cizim.KENAR);
		Cizim.degradeX(g, px + 2, py + 1, pw - 4, 2, Cizim.MOR, Cizim.CAMGOBEGI);
		// başlık
		Cizim.degradeYazi(g, this.font, "EKORA", px + 10, py + 9, Cizim.MOR, Cizim.CAMGOBEGI);
		g.text(this.font, "§8•§r " + this.title.getString(), px + 10 + this.font.width("EKORA") + 4, py + 9, Cizim.YAZI);
		// kapat
		int kx = px + pw - 18, ky = py + 6;
		boolean kv = Cizim.icinde(mouseX, mouseY, kx, ky, 12, 12);
		Cizim.panel(g, kx, ky, 12, 12, kv ? 0xFF3A2030 : 0x00000000, kv ? Cizim.KIRMIZI : 0);
		g.centeredText(this.font, "✕", kx + 6, ky + 2, kv ? Cizim.KIRMIZI : Cizim.SOLUK);
		g.fill(px + 8, py + 22, px + pw - 8, py + 23, Cizim.KENAR);
		icerik(g, mouseX, mouseY, delta);
		g.pose().popMatrix();
		ustKatman(g, mouseX, mouseY, delta);
	}

	/** Panelin içi (başlığın altı: py+28'den itibaren). */
	protected abstract void icerik(GuiGraphicsExtractor g, int mx, int my, float delta);

	/** Her şeyin üstüne çizilen katman (ipuçları vb.). */
	protected void ustKatman(GuiGraphicsExtractor g, int mx, int my, float delta) { }

	/** Standart Ekora düğmesi çizer ve tıklama alanını kaydeder. */
	protected void dugmeCiz(GuiGraphicsExtractor g, Dugme d, String yazi, int renk, int mx, int my) {
		boolean ust = d.aktif && d.ustunde(mx, my);
		d.vurgu += ((ust ? 1f : 0f) - d.vurgu) * 0.35f;
		int ic = d.aktif ? Cizim.karis(Cizim.koyu(renk, 0.55f), renk, d.vurgu * 0.6f) : 0xFF2A2D3A;
		Cizim.panel(g, d.x, d.y, d.w, d.h, ic, d.aktif ? Cizim.karis(renk, 0xFFFFFFFF, d.vurgu * 0.5f) : Cizim.KENAR);
		g.centeredText(this.font, yazi, d.x + d.w / 2, d.y + (d.h - 8) / 2, d.aktif ? 0xFFFFFFFF : Cizim.SOLUK);
	}

	/** Simgeli kart (menü kutuları). */
	protected void kartCiz(GuiGraphicsExtractor g, Dugme d, ItemStack ikon, String baslik, String alt, int renk, int mx, int my) {
		boolean ust = d.aktif && d.ustunde(mx, my);
		d.vurgu += ((ust ? 1f : 0f) - d.vurgu) * 0.3f;
		int yuk = (int) (d.vurgu * 2);
		Cizim.panel(g, d.x, d.y - yuk, d.w, d.h, Cizim.karis(Cizim.ARKA2, Cizim.koyu(renk, 0.75f), d.vurgu), Cizim.karis(Cizim.KENAR, renk, d.vurgu));
		g.fill(d.x + 1, d.y - yuk + 2, d.x + 3, d.y - yuk + d.h - 2, renk);
		Cizim.panel(g, d.x + 8, d.y - yuk + (d.h - 22) / 2, 22, 22, Cizim.koyu(renk, 0.7f), 0);
		g.fakeItem(ikon, d.x + 11, d.y - yuk + (d.h - 22) / 2 + 3);
		sigdirarakYaz(g, baslik, d.x + 36, d.y - yuk + d.h / 2 - 9, d.w - 42, Cizim.YAZI, 0.8f, false);
		sigdirarakYaz(g, alt, d.x + 36, d.y - yuk + d.h / 2 + 2, d.w - 42, Cizim.SOLUK, 0.72f, false);
	}

	/**
	 * Yazıyı genişliğe sığdırır: önce küçültür (en az enAz ölçeğine), yine sığmazsa sonunu "…" ile keser.
	 * Dikeyde 8 piksellik satırın ortasında kalır.
	 */
	protected void sigdirarakYaz(GuiGraphicsExtractor g, String yazi, int x, int y, int genislik, int renk, float enAz, boolean ortala) {
		int w = this.font.width(yazi);
		float s = w <= genislik ? 1f : Math.max(enAz, genislik / (float) w);
		String t = s < 1f ? Cizim.sigdir(this.font, yazi, (int) (genislik / s)) : yazi;
		int tw = (int) (this.font.width(t) * s);
		g.pose().pushMatrix();
		g.pose().translate(ortala ? x + (genislik - tw) / 2f : x, y + (8 - 8 * s) / 2f);
		g.pose().scale(s, s);
		g.text(this.font, t, 0, 0, renk);
		g.pose().popMatrix();
	}

	/** Küçük değer rozeti ("✦ 12.500"). */
	protected int rozet(GuiGraphicsExtractor g, int x, int y, String yazi, int renk) {
		int w = this.font.width(yazi) + 10;
		Cizim.panel(g, x, y, w, 13, Cizim.koyu(renk, 0.78f), Cizim.koyu(renk, 0.35f));
		g.text(this.font, yazi, x + 5, y + 3, renk);
		return w;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean ciftTik) {
		double mx = e.x(), my = e.y();
		int kx = px + pw - 18, ky = py + 6;
		if (Cizim.icinde(mx, my, kx, ky, 12, 12)) {
			tik();
			this.onClose();
			return true;
		}
		for (Dugme d : List.copyOf(dugmeler)) {
			if (d.aktif && d.ustunde(mx, my)) {
				tik();
				d.eylem.run();
				return true;
			}
		}
		return super.mouseClicked(e, ciftTik);
	}

	protected static void tik() {
		try {
			Ekora.ses(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.6f);
		} catch (RuntimeException ignored) { }
	}
}
