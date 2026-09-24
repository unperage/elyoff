package io.github.unperage.elyoff.net;

import io.github.unperage.elyoff.ElyOff;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端："我刚刚在本地取消了自己的滑翔，请你权威落定一次"。
 * <p>
 * 为什么需要它：客户端本地调用 {@code stopFallFlying()} 只是<b>本地预测</b>，
 * 服务端并不会因此知道。在没有本模组的原版服务端上，客户端只能靠原版那条
 * "跳跃键仍按着 → 再发一次 START_FALL_FLYING → 服务端发现你已在滑翔于是拒绝并把你停下"
 * 的副作用链路去通知服务端 —— 它依赖"下一 tick 还按着键"，点得太快就会漏掉。
 * <p>
 * 服务端装了本模组时，用这个包把通知变成确定性的：客户端本地取消的同时直接告诉服务端，
 * 服务端立刻 {@code stopFallFlying()} 并同步，不再依赖原版副作用，也不再需要逐 tick 轮询。
 * <p>
 * 空包。服务端没装本模组时客户端不会发（{@code canSend} 为 false）。
 */
public record CancelC2S() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CancelC2S> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(ElyOff.MOD_ID, "cancel_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CancelC2S> CODEC =
            StreamCodec.unit(new CancelC2S());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
