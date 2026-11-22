package tr.ekora.istemci.client;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import tr.ekora.istemci.client.ekran.Cizim;

/** Sağ üstte kayarak gelen Ekora bildirimleri (ödül, kozmetik, etkinlik…). */
public final class Bildirimler {
	private Bildirimler() { }

	private record Bildirim(String baslik, String metin, ItemStack ikon, int renk, long bas) { }

	private static final Deque<Bildirim> KUYRUK = new ArrayDeque<>();
	private static final long SURE = 4800;

	public static void ekle(String baslik, String metin, String ikon, int renk) {
		synchronized (KUYRUK) {
			if (KUYRUK.size() >= 4) KUYRUK.pollFirst();
			KUYRUK.addLast(new Bildirim(baslik == null ? "" : baslik, metin == null ? "" : metin, Cizim.esya(ikon), renk, System.currentTimeMillis()));
		}
		try {
			Ekora.ses(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4f, 0.5f);
		} catch (RuntimeException ignored) { }
	}

	static void tik() {
		long simdi = System.currentTimeMillis();
		synchronized (KUYRUK) {
			KUYRUK.removeIf(b -> simdi - b.bas > SURE);
		}
	}

	static void ciz(GuiGraphicsExtractor g, DeltaTracker d) {
		Bildirim[] liste;
		synchronized (KUYRUK) {
			if (KUYRUK.isEmpty()) return;
			liste = KUYRUK.toArray(new Bildirim[0]);
		}
		Font f = Minecraft.getInstance().font;
		long simdi = System.currentTimeMillis();
		int y = 8;
		for (Bildirim b : liste) {
			long gecen = simdi - b.bas;
			float giris = Math.min(1f, gecen / 260f), cikis = Math.min(1f, Math.max(0f, (SURE - gecen) / 300f));
			float k = (1 - (1 - giris) * (1 - giris) * (1 - giris)) * cikis;
			int w = Math.max(150, Math.max(f.width(b.baslik), f.width(b.metin)) + 40);
			int x = g.guiWidth() - (int) ((w + 8) * k);
			Cizim.panel(g, x, y, w, 30, Cizim.alfa(0xF0141828, k), Cizim.alfa(Cizim.KENAR, k));
			g.fill(x + 2, y + 1, x + 4, y + 29, Cizim.alfa(b.renk, k));
			g.fakeItem(b.ikon, x + 8, y + 7);
			g.text(f, b.baslik, x + 28, y + 6, Cizim.alfa(b.renk | 0xFF000000, Math.max(0.05f, k)));
			g.text(f, b.metin, x + 28, y + 17, Cizim.alfa(Cizim.YAZI, Math.max(0.05f, k)));
			float ilerleme = 1f - Math.min(1f, gecen / (float) SURE);
			g.fill(x + 4, y + 28, x + 4 + (int) ((w - 6) * ilerleme), y + 29, Cizim.alfa(b.renk, 0.6f * k));
			y += 34;
		}
	}
}
