package tr.ekora.istemci.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;

import tr.ekora.istemci.ag.EkoraIstek;
import tr.ekora.istemci.ag.EkoraVeri;

public final class EkoraIstemciClient implements ClientModInitializer {
	public static final KeyMapping MENU_TUSU = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.ekora.menu", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_K, KeyMapping.Category.MISC));

	@Override
	public void onInitializeClient() {
		Ayarlar.yukle();
		Ekora.surum = FabricLoader.getInstance().getModContainer("ekora").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("1.0.0");

		ClientPlayNetworking.registerGlobalReceiver(EkoraVeri.TYPE, (payload, context) -> {
			String json = payload.json();
			context.client().execute(() -> Ekora.isle(json));
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			Ekora.destek = false;
			Ekora.merhabaGitti = false;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			Ekora.destek = false;
			Ekora.merhabaGitti = false;
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.getConnection() != null && !Ekora.merhabaGitti && ClientPlayNetworking.canSend(EkoraIstek.TYPE)) {
				Ekora.merhabaGitti = true;
				JsonObject o = new JsonObject();
				o.addProperty("t", "merhaba");
				o.addProperty("surum", Ekora.surum);
				Ekora.gonder(o);
			}
			while (MENU_TUSU.consumeClick()) {
				if (client.player == null) break;
				if (Ekora.destekli()) Ekora.istek("menu");
				else Bildirimler.ekle("Ekora Menüsü", "Yalnızca play.ekoramc.fun sunucusunda açılır.", "minecraft:barrier", 0xFFEC4899);
			}
			Bildirimler.tik();
		});

		HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, Identifier.fromNamespaceAndPath("ekora", "bildirimler"), Bildirimler::ciz);
	}
}
