package tr.ekora.istemci.test;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Sunucu eklentisi menüsüne benzer örnek sandık (siyah/mor cam çerçeve + renkli adlı oyun eşyaları). */
final class SandikOrnegi {
	private SandikOrnegi() { }

	static Screen ac() {
		var mc = Minecraft.getInstance();
		var inv = mc.player.getInventory();
		ChestMenu m = ChestMenu.fourRows(0, inv);
		var c = m.getContainer();
		for (int i = 0; i < 36; i++) c.setItem(i, cam(i < 9 || i >= 27 || i % 9 == 0 || i % 9 == 8 ? Items.BLACK_STAINED_GLASS_PANE : Items.PURPLE_STAINED_GLASS_PANE));
		Object[][] es = {{10, Items.GRASS_BLOCK, "SURVIVAL SMP", 0x4ADE80}, {11, Items.OAK_SAPLING, "SKYBLOCK", 0xA3E635}, {12, Items.BELL, "KASABA", 0xFACC15},
				{13, Items.RED_BED, "BEDWARS", 0xFB923C}, {14, Items.FIREWORK_STAR, "MİNİ OYUNLAR", 0xF472B6}, {15, Items.DIAMOND_SWORD, "PVP DÜELLO", 0xF87171},
				{16, Items.FEATHER, "PARKUR", 0xC084FC}, {20, Items.BLUE_BANNER, "BAYRAK KAP", 0x38BDF8}, {21, Items.COMPASS, "MANHUNT", 0xFDE047},
				{23, Items.DIAMOND_PICKAXE, "BOXPVP", 0xFB923C}, {24, Items.NETHERITE_AXE, "EKORA RPG", 0xF97316}, {30, Items.ARROW, "« Geri", 0xAAAAAA}, {32, Items.BARRIER, "Kapat", 0xF87171}};
		for (Object[] e : es) {
			ItemStack s = new ItemStack((Item) e[1]);
			s.set(DataComponents.CUSTOM_NAME, Component.literal((String) e[2]).withStyle(st -> st.withColor(TextColor.fromRgb((int) e[3])).withBold(true).withItalic(false)));
			c.setItem((int) e[0], s);
		}
		Component baslik = Component.literal("✦ ").withStyle(st -> st.withColor(TextColor.fromRgb(0x555555)))
				.append(Component.literal("OYUN MODLARI").withStyle(st -> st.withColor(TextColor.fromRgb(0xA855F7)).withBold(true)));
		return new ContainerScreen(m, inv, baslik);
	}

	static List<String> aciklama() { return List.of(); }
}
