package tr.ekora.istemci.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import tr.ekora.istemci.client.Ekora;
import tr.ekora.istemci.client.ekran.Cizim;

/** Gerçek eklenti menülerinin (EkoraPazar mezat/market/onay, EkoraOyunlar Manhunt rolleri) aynı biçimdeki örnekleri. */
final class OrnekMenuler {
	private OrnekMenuler() { }

	static Component r(String s, int renk) { return Component.literal(s).withStyle(st -> st.withColor(TextColor.fromRgb(renk)).withItalic(false)); }

	static Component k(String s, int renk) { return Component.literal(s).withStyle(st -> st.withColor(TextColor.fromRgb(renk)).withBold(true).withItalic(false)); }

	static ItemStack es(String id, int adet, Component ad, Component... lore) {
		ItemStack s = Cizim.esya("minecraft:" + id).copyWithCount(adet);
		s.set(DataComponents.CUSTOM_NAME, ad);
		if (lore.length > 0) s.set(DataComponents.LORE, new ItemLore(List.of(lore)));
		return s;
	}

	static ItemStack cam(String renk) { return es(renk + "_stained_glass_pane", 1, Component.literal(" ")); }

	private static final int GRI = 0xAAAAAA, KOYU = 0x555555, BEYAZ = 0xFFFFFF, SARI = 0xFFFF55, YESIL = 0x55FF55, KIRMIZI = 0xFF5555, ALTIN = 0xFFAA00, AQUA = 0x55FFFF;

	private static Screen ac(int satir, Component baslik, String eklenti, java.util.function.Consumer<Container> doldur) {
		return ac(satir, baslik, eklenti, "", doldur);
	}

	private static Screen ac(int satir, Component baslik, String eklenti, String arama, java.util.function.Consumer<Container> doldur) {
		var mc = Minecraft.getInstance();
		var inv = mc.player.getInventory();
		ChestMenu m = switch (satir) { case 3 -> ChestMenu.threeRows(0, inv); case 4 -> ChestMenu.fourRows(0, inv); case 5 -> ChestMenu.fiveRows(0, inv); default -> ChestMenu.sixRows(0, inv); };
		doldur.accept(m.getContainer());
		Ekora.ipucuAyarla(baslik.getString().trim(), eklenti, true, arama);
		return new ContainerScreen(m, inv, baslik);
	}

	static Component baslik(String s) { return Component.literal(s).withStyle(st -> st.withColor(TextColor.fromRgb(0x3B2A18))); }

	static Screen mezat() {
		return ac(6, baslik("Mezat • SMP (1/3)"), "EkoraPazar", "Mezatta ara", OrnekMenuler::mezatDoldur);
	}

	static void mezatDoldur(Container c) {
		{
			Object[][] ilan = {{"diamond_sword", 1, "Keskinlik V Kılıç", "4.500 SMP Coin", "Deniz_42", "1g 4s"}, {"elytra", 1, "Elytra", "38.000 SMP Coin", "MertPvP", "11s 20d"},
					{"netherite_ingot", 4, "Netherit Külçesi", "9.600 SMP Coin (2.400 / adet)", "KasabaReisi", "2g 1s"}, {"golden_apple", 16, "Altın Elma", "1.120 SMP Coin (70 / adet)", "Zeynep", "5s 2d"},
					{"enchanted_book", 1, "Onarım Kitabı", "6.000 SMP Coin", "ElifYapar", "3g"}, {"shulker_box", 1, "Shulker Kutusu", "3.200 SMP Coin", "Kaan", "22s"},
					{"diamond", 32, "Elmas", "3.840 SMP Coin (120 / adet)", "Yusuf07", "1g"}, {"totem_of_undying", 1, "Ölümsüzlük Totemi", "7.500 SMP Coin", "Ahmet_TR", "9s 41d"},
					{"beacon", 1, "Fener", "25.000 SMP Coin", "SuperSteve", "2g 6s"}, {"trident", 1, "Üç Dişli Mızrak", "12.000 SMP Coin", "Deniz_42", "14s"},
					{"ender_pearl", 16, "Ender İncisi", "640 SMP Coin (40 / adet)", "MertPvP", "3s"}, {"experience_bottle", 64, "Tecrübe Şişesi", "1.920 SMP Coin (30 / adet)", "Zeynep", "1g 2s"}};
			for (int i = 0; i < ilan.length; i++) {
				Object[] l = ilan[i];
				c.setItem(i, es((String) l[0], (int) l[1], r((String) l[2], i % 3 == 0 ? AQUA : BEYAZ), r("────────────────", KOYU), r("Fiyat: " + l[3], GRI), r("Satıcı: " + l[4], GRI),
						r("Kalan süre: " + l[5], GRI), r("#" + (1040 + i), KOYU), r("▶ Tıkla: satın al", YESIL)));
			}
			for (int i = 45; i < 54; i++) c.setItem(i, cam("black"));
			c.setItem(46, es("hopper", 1, r("Sıralama: En yeni", AQUA), r("Tıkla: değiştir", GRI)));
			c.setItem(47, es("sunflower", 1, r("Para birimi: Tümü", AQUA), r("Tümü → SMP Coin → EP", GRI), r("Tıkla: değiştir", GRI)));
			c.setItem(48, es("chest", 1, r("Kategori: Tümü", AQUA), r("Sol tık: sonraki • Sağ tık: önceki", GRI)));
			c.setItem(49, es("nether_star", 1, k("Mezat", ALTIN), r("Bakiyen: 12.500 SMP Coin", GRI)));
			c.setItem(50, es("writable_book", 1, r("İlanlarım", YESIL), r("Aktif ilanlarını gör ve geri çek.", GRI)));
			c.setItem(51, es("ender_chest", 1, r("Teslim Kutusu", ALTIN), r("Satın aldıkların ve iade edilenler.", GRI)));
			c.setItem(53, es("arrow", 1, r("Sonraki sayfa »", SARI)));
		}
	}

	static Screen market() {
		return ac(6, baslik("Bloklar (1/2)"), "EkoraPazar", "Markette ara", c -> {
			Object[][] u = {{"oak_log", "Meşe Kütüğü", 12, 4, 32}, {"stone", "Taş", 2, 1, 0}, {"glass", "Cam", 6, 2, 12}, {"bricks", "Tuğla", 9, 3, 0},
					{"quartz_block", "Kuvars Bloğu", 30, 10, 0}, {"sea_lantern", "Deniz Feneri", 48, 0, 0}, {"obsidian", "Obsidyen", 60, 20, 5}, {"terracotta", "Pişmiş Kil", 5, 2, 0}};
			for (int i = 0; i < u.length; i++) {
				Object[] x = u[i];
				int al = (int) x[2], sat = (int) x[3], var = (int) x[4];
				List<Component> l = new ArrayList<>();
				l.add(r("Satın al: " + al + " SMP Coin / adet • 64 adet: " + al * 64, GRI));
				l.add(sat > 0 ? r("Sat: " + sat + " SMP Coin / adet • 64 adet: " + sat * 64, GRI) : r("Sunucuya satılamaz", KOYU));
				if (var > 0) l.add(r("Envanterinde " + var + " adet → " + var * sat, YESIL));
				l.add(Component.empty());
				l.add(r("Sol tık: miktar seç ve al", SARI));
				if (sat > 0) l.add(r("Sağ tık: 1 tane sat", SARI));
				c.setItem(10 + i + (i >= 7 ? 2 : 0), es((String) x[0], 1, r((String) x[1], BEYAZ), l.toArray(new Component[0])));
			}
			for (int i = 0; i < 54; i++) if (c.getItem(i).isEmpty()) c.setItem(i, cam(i >= 45 || i < 9 || i % 9 == 0 || i % 9 == 8 ? "black" : "gray"));
			c.setItem(45, es("oak_door", 1, r("« Kategoriler", SARI)));
			c.setItem(49, es("sunflower", 1, r("Bakiye", ALTIN), r("12.500 SMP Coin", GRI)));
			c.setItem(50, es("arrow", 1, r("Sonraki »", SARI)));
			c.setItem(53, es("chest", 1, r("Satış Sandığı", YESIL)));
		});
	}

	static Screen onay() {
		return ac(3, baslik("Satın almayı onayla"), "EkoraPazar", c -> {
			for (int i = 0; i < 27; i++) c.setItem(i, cam("gray"));
			for (int s : new int[]{10, 11, 12}) c.setItem(s, es("lime_stained_glass_pane", 1, k("SATIN AL", YESIL), r("Tutar: 38.000 SMP Coin", GRI), r("Bakiyen: 52.300 SMP Coin", GRI)));
			c.setItem(13, es("elytra", 1, r("Elytra", BEYAZ), r("Fiyat: 38.000 SMP Coin", GRI), r("Satıcı: MertPvP", GRI), r("Kalan süre: 11s 20d", GRI)));
			for (int s : new int[]{14, 15, 16}) c.setItem(s, es("red_stained_glass_pane", 1, k("VAZGEÇ", KIRMIZI)));
		});
	}

	/** Eklentinin açtığı depo (BedWars takım sandığı gibi): adsız eşyalar → klasik görünüme kendiliğinden geçmeli. */
	static Screen depo() {
		return ac(3, baslik("Mavi Takım Ganimeti"), "EkoraOyunlar", c -> {
			String[] e = {"iron_ingot", "gold_ingot", "diamond", "wool", "oak_planks", "ender_pearl", "golden_apple", "arrow"};
			for (int i = 0; i < e.length; i++) c.setItem(i * 2, Cizim.esya("minecraft:" + (e[i].equals("wool") ? "blue_wool" : e[i])).copyWithCount(8 + i * 3));
		});
	}

	static Screen roller() {
		return ac(3, baslik("Manhunt • Roller"), "EkoraOyunlar", c -> {
			Object[][] o = {{"EkoraOyuncu", true, true}, {"Deniz_42", false, false}, {"MertPvP", false, false}, {"Zeynep", true, false}};
			for (int i = 0; i < o.length; i++) {
				boolean kacan = (boolean) o[i][1], sahip = (boolean) o[i][2];
				List<Component> l = new ArrayList<>();
				l.add(Component.literal("Rol: ").withStyle(st -> st.withColor(TextColor.fromRgb(GRI)).withItalic(false)).append(r(kacan ? "Kaçan" : "Kovalayan", kacan ? YESIL : KIRMIZI)));
				if (sahip) l.add(r("★ Oda sahibi", ALTIN));
				l.add(Component.empty());
				l.add(r("▶ Tıkla: " + (kacan ? "Kovalayan" : "Kaçan") + " yap", SARI));
				c.setItem(9 + i, es("player_head", 1, k((String) o[i][0], kacan ? YESIL : KIRMIZI), l.toArray(new Component[0])));
			}
			c.setItem(19, es("feather", 1, r("Kaçanlar: 2", YESIL), r("Hedef: Ender Ejderhası'nı öldür.", GRI)));
			c.setItem(20, es("iron_sword", 1, r("Kovalayanlar: 2", KIRMIZI), r("Hedef: bütün kaçanları öldür.", GRI)));
			c.setItem(22, es("ender_eye", 1, k("Rastgele dağıt", 0xFF55FF), r("1 kaçan (5+ kişide 2), kalanlar kovalayan", GRI)));
			c.setItem(24, es("lime_concrete", 1, k("OYUNU BAŞLAT", YESIL), r("Yeni bir dünya oluşturulur.", GRI)));
			c.setItem(26, es("barrier", 1, r("Kapat", KIRMIZI)));
		});
	}
}
