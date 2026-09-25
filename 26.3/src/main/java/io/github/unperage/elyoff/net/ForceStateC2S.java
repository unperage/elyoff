package io.github.unperage.elyoff.net;

import io.github.unperage.elyoff.ElyOff;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：告知"本玩家现在是否强制客户端本地生效"。
 * <p>
 * 当它为 {@code true} 时，服务端会<b>停止对该玩家按键行为的每 tick 监控</b> ——
 * 取消完全由客户端抢跑完成，服务端只在收到 {@link CancelC2S} 时做一次权威落定。
 * 这样既省掉服务端的逐 tick 轮询，也避免两端重复判定。
 * <p>
 * 只在状态<b>发生变化</b>时发送（边沿触发），不是每 tick 发。
 * 服务端没装本模组时客户端根本不发（{@code canSend} 为 false）。
 */
public record ForceStateC2S(boolean force) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ForceStateC2S> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(ElyOff.MOD_ID, "force_state_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForceStateC2S> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.force()),
                    buf -> new ForceStateC2S(buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
