package io.github.unperage.elyoff.net;

import io.github.unperage.elyoff.ElyOff;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端 的握手包（空包）。
 * <p>
 * 服务端在玩家加入时主动发送，客户端收到后即认为"服务端已生效"，
 * 从而关掉自己的兜底逻辑，避免两端重复处理。
 */
public record HelloS2C() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HelloS2C> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(ElyOff.MOD_ID, "hello_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HelloS2C> CODEC =
            StreamCodec.unit(new HelloS2C());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
