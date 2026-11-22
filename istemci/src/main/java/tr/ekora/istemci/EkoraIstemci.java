package tr.ekora.istemci;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import tr.ekora.istemci.ag.EkoraIstek;
import tr.ekora.istemci.ag.EkoraVeri;

/** Ekora İstemci: EkoraMC sunucusuyla konuşan özel oyun içi menüler (çark, kozmetik dolabı, kasa, Ekora menüsü). */
public final class EkoraIstemci implements ModInitializer {
	public static final String MOD_ID = "ekora";

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(EkoraVeri.TYPE, EkoraVeri.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(EkoraIstek.TYPE, EkoraIstek.CODEC);
	}
}
