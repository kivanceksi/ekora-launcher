package tr.ekora.istemci.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import tr.ekora.istemci.client.ekran.Cizim;

/**
 * Sunucudaki bütün sandık menülerini (oyunlar, mağaza, kasaba, RPG… her eklenti) Ekora görünümüyle çizer.
 * Yalnızca görünüm değişir: yuvalar, tıklama, sürükleme vanilla ile aynıdır, böylece her eklenti olduğu gibi çalışır.
 * Adı boş renkli camlar (süs dolgusu) çizilmez; yerine o rengin hafif bir karosu konur.
 */
public final class SandikGorunumu {
	private SandikGorunumu() { }

	/** Ekora sunucusunda ve ayar açıkken. */
	public static boolean aktif() {
		return Ayarlar.sandikGorunumu && Ekora.destekli();
	}

	/** Renkli/düz cam bölme ve adı boş (" ") ise süs dolgusudur. */
	public static boolean susCami(ItemStack s) {
		if (!Ayarlar.camGizle || s == null || s.isEmpty()) return false;
		String yol = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		if (!yol.endsWith("glass_pane")) return false;
		return s.getHoverName().getString().isBlank();
	}

	private static int camRengi(ItemStack s) {
		String yol = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		return switch (yol.replace("_stained_glass_pane", "")) {
			case "white" -> 0xFFE5E7EB;
			case "orange" -> 0xFFF97316;
			case "magenta" -> 0xFFD946EF;
			case "light_blue" -> 0xFF38BDF8;
			case "yellow" -> 0xFFFACC15;
			case "lime" -> 0xFF84CC16;
			case "pink" -> 0xFFF472B6;
			case "gray" -> 0xFF4B5563;
			case "light_gray" -> 0xFF9CA3AF;
			case "cyan" -> 0xFF06B6D4;
			case "purple" -> 0xFFA855F7;
			case "blue" -> 0xFF3B82F6;
			case "brown" -> 0xFFB45309;
			case "green" -> 0xFF16A34A;
			case "red" -> 0xFFEF4444;
			case "black" -> 0xFF0B0D16;
			default -> 0xFF94A3B8;
		};
	}

	/** Eşya adının rengi (yuva kenarına hafif ton verir); yoksa -1. */
	private static int adRengi(ItemStack s) {
		Component ad = s.getHoverName();
		TextColor c = ad.getStyle().getColor();
		if (c == null) for (Component k : ad.getSiblings()) { c = k.getStyle().getColor(); if (c != null) break; }
		return c == null ? -1 : 0xFF000000 | c.getValue();
	}

	public static void arkaPlan(GuiGraphicsExtractor g, int x0, int y0, int w, int h, AbstractContainerMenu menu, int satir) {
		int ust = satir * 18 + 17;
		// arkadaki dünyayı hafifçe karart (okunurluk)
		Cizim.degradeY(g, 0, 0, g.guiWidth(), g.guiHeight(), 0x60060812, 0x900A0C18);
		// dış panel
		Cizim.panel(g, x0 - 7, y0 - 5, w + 14, h + 10, 0x50000000, 0);
		Cizim.panel(g, x0 - 5, y0 - 3, w + 10, h + 6, Cizim.ARKA, Cizim.KENAR);
		Cizim.degradeX(g, x0 - 3, y0 - 2, w + 6, 2, Cizim.MOR, Cizim.CAMGOBEGI);
		Cizim.degradeY(g, x0 - 4, y0, w + 8, 16, 0x30A855F7, 0x00000000);
		// sandık ve envanter arası ayraç
		g.fill(x0 + 4, y0 + ust + 1, x0 + w - 4, y0 + ust + 2, Cizim.KENAR);
		int kap = satir * 9;
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot s = menu.slots.get(i);
			int x = x0 + s.x - 1, y = y0 + s.y - 1;
			ItemStack st = s.getItem();
			boolean sandikta = i < kap;
			if (sandikta && susCami(st)) {
				int c = camRengi(st);
				g.fill(x, y, x + 18, y + 18, Cizim.alfa(c, c == 0xFF0B0D16 ? 0.55f : 0.16f));
				continue;
			}
			int ton = sandikta && !st.isEmpty() ? adRengi(st) : -1;
			int ic = sandikta ? (st.isEmpty() ? 0xFF10131F : 0xFF1A1E32) : 0xFF141726;
			int kenar = ton != -1 ? Cizim.karis(Cizim.KENAR, ton, 0.55f) : sandikta && !st.isEmpty() ? 0xFF3A4066 : 0xFF262A40;
			Cizim.panel(g, x, y, 18, 18, ic, kenar);
			if (ton != -1) g.fill(x + 2, y + 16, x + 16, y + 17, Cizim.alfa(ton, 0.7f));
		}
	}

	/** Başlık (koyu renkli parçalar okunur olsun diye açılır) ve "Envanter" yazısı. Koordinatlar menüye göre. */
	public static void basliklar(GuiGraphicsExtractor g, Font f, Component baslik, Component envanter, int w, int ex, int ey) {
		Cizim.degradeYazi(g, f, "EKORA", 4, 5, Cizim.MOR, Cizim.CAMGOBEGI);
		int x = 4 + f.width("EKORA") + 4;
		g.text(f, "•", x, 5, 0xFF5B6188, false);
		x += f.width("• ");
		Component acik = acikRenk(baslik);
		int kalan = w - x - 2;
		if (f.width(acik) > kalan) {
			String duz = Cizim.sigdir(f, baslik.getString(), kalan);
			g.text(f, duz, x, 5, Cizim.YAZI, false);
		} else g.text(f, acik, x, 5, Cizim.YAZI, false);
		g.text(f, "Envanter", ex, ey, Cizim.SOLUK, false);
	}

	/** Koyu renkleri (koyu gri, siyah, lacivert…) Ekora'nın soluk yazı rengine çevirir; diğer renkler ve biçimler kalır. */
	static Component acikRenk(Component c) {
		List<Component> parcalar = new ArrayList<>();
		c.visit((stil, metin) -> {
			Style st = stil;
			TextColor renk = st.getColor();
			if (renk == null || parlaklik(renk.getValue()) < 0.36) st = st.withColor(TextColor.fromRgb(renk == null ? Cizim.YAZI & 0xFFFFFF : Cizim.SOLUK & 0xFFFFFF));
			parcalar.add(Component.literal(metin).withStyle(st));
			return Optional.empty();
		}, Style.EMPTY);
		MutableComponent m = Component.empty();
		for (Component p : parcalar) m.append(p);
		return m;
	}

	private static double parlaklik(int rgb) {
		int r = rgb >> 16 & 255, gg = rgb >> 8 & 255, b = rgb & 255;
		return (0.299 * r + 0.587 * gg + 0.114 * b) / 255.0;
	}
}
