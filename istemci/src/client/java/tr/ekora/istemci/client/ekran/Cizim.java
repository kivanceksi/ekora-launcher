package tr.ekora.istemci.client.ekran;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Ekora arayüz çizim yardımcıları: köşeleri yumuşatılmış paneller, degrade, renk karışımı, eşya simgesi. */
public final class Cizim {
	private Cizim() { }

	public static final int ARKA = 0xF0101322, ARKA2 = 0xF0171B2E, KENAR = 0xFF2E3352, YAZI = 0xFFE9ECF7, SOLUK = 0xFF9AA3BF,
			MOR = 0xFFA855F7, CAMGOBEGI = 0xFF22D3EE, ALTIN = 0xFFF5B83D, YESIL = 0xFF4ADE80, KIRMIZI = 0xFFF87171;

	public static int renk(String hex, int varsayilan) {
		if (hex == null) return varsayilan;
		String s = hex.startsWith("#") ? hex.substring(1) : hex;
		try {
			if (s.length() == 6) return 0xFF000000 | Integer.parseInt(s, 16);
			if (s.length() == 8) return (int) Long.parseLong(s, 16);
		} catch (NumberFormatException ignored) { }
		return varsayilan;
	}

	public static int karis(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
		int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
		return (int) (aa + (ba - aa) * t) << 24 | (int) (ar + (br - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
	}

	public static int alfa(int c, float a) {
		return ((int) ((c >>> 24) * Math.max(0, Math.min(1, a))) << 24) | (c & 0xFFFFFF);
	}

	public static int koyu(int c, float k) {
		return karis(c, 0xFF000000 | 0, k) & 0xFFFFFF | (c & 0xFF000000);
	}

	/** Köşeleri 2 piksel yumuşatılmış dolu panel + 1 piksel kenar. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int ic, int kenar) {
		g.fill(x + 2, y, x + w - 2, y + h, ic);
		g.fill(x, y + 2, x + 2, y + h - 2, ic);
		g.fill(x + w - 2, y + 2, x + w, y + h - 2, ic);
		g.fill(x + 1, y + 1, x + 2, y + 2, ic);
		g.fill(x + w - 2, y + 1, x + w - 1, y + 2, ic);
		g.fill(x + 1, y + h - 2, x + 2, y + h - 1, ic);
		g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, ic);
		if (kenar != 0) {
			g.fill(x + 2, y, x + w - 2, y + 1, kenar);
			g.fill(x + 2, y + h - 1, x + w - 2, y + h, kenar);
			g.fill(x, y + 2, x + 1, y + h - 2, kenar);
			g.fill(x + w - 1, y + 2, x + w, y + h - 2, kenar);
			g.fill(x + 1, y + 1, x + 2, y + 2, kenar);
			g.fill(x + w - 2, y + 1, x + w - 1, y + 2, kenar);
			g.fill(x + 1, y + h - 2, x + 2, y + h - 1, kenar);
			g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, kenar);
		}
	}

	/** Yatay degrade şerit (soldan sağa). */
	public static void degradeX(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
		int adim = Math.max(1, w / 48);
		for (int i = 0; i < w; i += adim) g.fill(x + i, y, Math.min(x + w, x + i + adim), y + h, karis(c1, c2, (float) i / Math.max(1, w - 1)));
	}

	/** Dikey degrade (yukarıdan aşağı). */
	public static void degradeY(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
		int adim = Math.max(1, h / 48);
		for (int i = 0; i < h; i += adim) g.fill(x, y + i, x + w, Math.min(y + h, y + i + adim), karis(c1, c2, (float) i / Math.max(1, h - 1)));
	}

	/** Başlık: renkli harfler arası degrade metin. */
	public static void degradeYazi(GuiGraphicsExtractor g, Font f, String s, int x, int y, int c1, int c2) {
		int cx = x;
		for (int i = 0; i < s.length(); i++) {
			String ch = String.valueOf(s.charAt(i));
			g.text(f, ch, cx, y, karis(c1, c2, s.length() <= 1 ? 0 : (float) i / (s.length() - 1)));
			cx += f.width(ch);
		}
	}

	public static void ortaYazi(GuiGraphicsExtractor g, Font f, String s, int cx, int y, int c) {
		g.centeredText(f, s, cx, y, c);
	}

	/** Metni genişliğe sığdırır (sonuna … ekler). */
	public static String sigdir(Font f, String s, int w) {
		if (s == null) return "";
		if (f.width(s) <= w) return s;
		String e = "…";
		while (!s.isEmpty() && f.width(s + e) > w) s = s.substring(0, s.length() - 1);
		return s + e;
	}

	public static ItemStack esya(String id) {
		if (id == null || id.isBlank()) return new ItemStack(Items.PAPER);
		try {
			String s = id.toLowerCase(java.util.Locale.ROOT);
			if (s.startsWith("model:")) { // kaynak paketi modeli (Ekora kozmetikleri): kağıt + item_model bileşeni
				String m = s.substring(6);
				ItemStack st = new ItemStack(Items.PAPER);
				st.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, m.contains(":") ? Identifier.parse(m) : Identifier.fromNamespaceAndPath("ekora", m));
				return st;
			}
			if (!s.contains(":")) s = "minecraft:" + s;
			Item it = BuiltInRegistries.ITEM.getValue(Identifier.parse(s));
			if (it == null || it == Items.AIR) return new ItemStack(Items.PAPER);
			return new ItemStack(it);
		} catch (RuntimeException e) {
			return new ItemStack(Items.PAPER);
		}
	}

	public static boolean icinde(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	public static int nadirlikRengi(String n) {
		if (n == null) return SOLUK;
		return switch (n) {
			case "nadir" -> 0xFF3B82F6;
			case "destansi", "destansı" -> 0xFFA855F7;
			case "efsanevi" -> 0xFFF59E0B;
			case "ozel", "özel" -> 0xFFEC4899;
			default -> 0xFF9CA3AF;
		};
	}

	public static String nadirlikAdi(String n) {
		if (n == null) return "Yaygın";
		return switch (n) {
			case "nadir" -> "Nadir";
			case "destansi", "destansı" -> "Destansı";
			case "efsanevi" -> "Efsanevi";
			case "ozel", "özel" -> "Özel";
			default -> "Yaygın";
		};
	}

	public static String sayi(long n) {
		String s = Long.toString(Math.abs(n));
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			if (i > 0 && (s.length() - i) % 3 == 0) b.append('.');
			b.append(s.charAt(i));
		}
		return (n < 0 ? "-" : "") + b;
	}
}
