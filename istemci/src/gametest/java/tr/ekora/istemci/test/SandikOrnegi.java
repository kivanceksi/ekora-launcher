package tr.ekora.istemci.test;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import tr.ekora.istemci.client.ekran.Cizim;

/** Sunucu eklentisi menüsüne benzer örnek sandık (siyah/mor cam çerçeve + renkli adlı oyun eşyaları). */
final class SandikOrnegi {
	private SandikOrnegi() { }

	static Screen ac() {
		var mc = Minecraft.getInstance();
		var inv = mc.player.getInventory();
		ChestMenu m = ChestMenu.fourRows(0, inv);
		var c = m.getContainer();
		for (int i = 0; i < 36; i++) {
			ItemStack cam = Cizim.esya(i < 9 || i >= 27 || i % 9 == 0 || i % 9 == 8 ? "minecraft:black_stained_glass_pane" : "minecraft:purple_stained_glass_pane");
			cam.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
			c.setItem(i, cam);
		}
		Object[][] es = {{10, "grass_block", "SURVIVAL SMP", 0x4ADE80}, {11, "oak_sapling", "SKYBLOCK", 0xA3E635}, {12, "bell", "KASABA", 0xFACC15},
				{13, "red_bed", "BEDWARS", 0xFB923C}, {14, "firework_star", "MİNİ OYUNLAR", 0xF472B6}, {15, "diamond_sword", "PVP DÜELLO", 0xF87171},
				{16, "feather", "PARKUR", 0xC084FC}, {20, "blue_banner", "BAYRAK KAP", 0x38BDF8}, {21, "compass", "MANHUNT", 0xFDE047},
				{23, "diamond_pickaxe", "BOXPVP", 0xFB923C}, {24, "netherite_axe", "EKORA RPG", 0xF97316}, {30, "arrow", "« Geri", 0xAAAAAA}, {32, "barrier", "Kapat", 0xF87171}};
		for (Object[] e : es) {
			ItemStack s = Cizim.esya("minecraft:" + e[1]);
			s.set(DataComponents.CUSTOM_NAME, Component.literal((String) e[2]).withStyle(st -> st.withColor(TextColor.fromRgb((int) e[3])).withBold(true).withItalic(false)));
			c.setItem((int) e[0], s);
		}
		Component baslik = Component.literal("✦ ").withStyle(st -> st.withColor(TextColor.fromRgb(0x555555)))
				.append(Component.literal("OYUN MODLARI").withStyle(st -> st.withColor(TextColor.fromRgb(0xA855F7)).withBold(true)));
		return new ContainerScreen(m, inv, baslik);
	}

	static List<String> aciklama() { return List.of(); }
}
