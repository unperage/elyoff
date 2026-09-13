package io.github.unperage.elyoff.net;

import io.github.unperage.elyoff.ElyOff;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端 的握手包（空包）。
 * <p>
 * 客户端用它探测"服务端是否安装了 ElyOff"：只有服务端注册了该 C2S 包类型，
 * 客户端的 {@code ClientPlayNetworking.canSend(TYPE)} 才会返回 true。
 */
public record HelloC2S() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HelloC2S> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(ElyOff.MOD_ID, "hello_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HelloC2S> CODEC =
            StreamCodec.unit(new HelloC2S());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
