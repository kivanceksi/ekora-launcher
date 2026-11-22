package tr.ekora.istemci.ag;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sunucu → istemci: tek bir JSON metni (kanal "ekora:veri"; VarInt uzunluk + UTF-8). */
public record EkoraVeri(String json) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<EkoraVeri> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("ekora", "veri"));
	public static final StreamCodec<ByteBuf, EkoraVeri> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(262144), EkoraVeri::json, EkoraVeri::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
