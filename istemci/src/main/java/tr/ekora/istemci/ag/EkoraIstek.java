package tr.ekora.istemci.ag;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** İstemci → sunucu: tek bir JSON metni (kanal "ekora:istek"). Sunucu her isteği doğrular; istemciye güvenmez. */
public record EkoraIstek(String json) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<EkoraIstek> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("ekora", "istek"));
	public static final StreamCodec<ByteBuf, EkoraIstek> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(8192), EkoraIstek::json, EkoraIstek::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
