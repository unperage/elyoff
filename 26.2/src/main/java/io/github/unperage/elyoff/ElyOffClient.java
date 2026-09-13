package io.github.unperage.elyoff;

import io.github.unperage.elyoff.logic.CancelStateMachine;
import io.github.unperage.elyoff.net.HelloC2S;
import io.github.unperage.elyoff.net.HelloS2C;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;

/**
 * 客户端分支（兜底）。
 * <p>
 * 规则：<b>仅当服务端没有生效时</b>才在本地取消滑翔；
 * 一旦确认服务端装了 ElyOff（收到握手包，或可以直接发送 C2S 包），客户端就不再落手，
 * 完全交给服务端处理，避免两端重复触发。
 * <p>
 * 判定规则与服务端共用 {@link CancelStateMachine}：只看跳跃键的<b>当前按住状态</b>，
 * 认"滑翔途中的上升沿"；取消后按住跳跃键期间保持取消，松开即解锁。
 * <p>
 * <b>注意</b>：这里刻意不使用 {@code KeyMapping#consumeClick()}。
 * 原版跳跃键从不消费点击，其内部 clickCount 只会随着每一次按下单调累加
 * （地面起跳 +1、空中起飞 +1 ……），而 vanilla 也从不清理它。
 * 一旦读取该队列，滑翔刚过门槛 tick 就会取到很久以前遗留的陈旧按键，
 * 表现为"刚按跳跃起飞就被取消，而且每次都是"。键位状态法是唯一正确的取法。
 */
@Environment(EnvType.CLIENT)
public class ElyOffClient implements ClientModInitializer {

    /** 加入服务器后先观望这么多 tick，等握手结论落地，避免两端同时动手。 */
    private static final int HANDSHAKE_GRACE_TICKS = 40;

    /** 服务端是否已生效（收到 S2C 握手包）。 */
    private static volatile boolean serverActive = false;

    /** 本地玩家独立的状态机（与服务端同源）。 */
    private static final CancelStateMachine STATE = new CancelStateMachine();

    /** 距离本次进入世界过了多少 tick，用于握手宽限期。 */
    private static int sinceJoin = 0;

    @Override
    public void onInitializeClient() {
        // 收到服务端握手 → 标记服务端已生效
        ClientPlayNetworking.registerGlobalReceiver(HelloS2C.TYPE, (payload, context) ->
                context.client().execute(() -> serverActive = true));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            reset();
            // 主动探测一次：服务端若注册了该 C2S 包就会回握手
            if (serverHasMod()) {
                ClientPlayNetworking.send(new HelloC2S());
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null) {
                reset();
                return;
            }

            if (sinceJoin < HANDSHAKE_GRACE_TICKS) {
                sinceJoin++;
            }

            // 始终推进状态机，让"上一次按键"的记忆与真实输入保持同步；
            // 这样即便随后服务端接管，也不会留下一个假的上升沿。
            boolean jump = client.options.keyJump.isDown();
            boolean cancel = STATE.update(jump, player.isFallFlying());

            // 服务端已生效（或尚在握手宽限期）→ 客户端兜底不落手
            if (serverActive || sinceJoin <= HANDSHAKE_GRACE_TICKS || serverHasMod()) {
                return;
            }

            if (cancel) {
                player.stopFallFlying();
            }
        });
    }

    private static void reset() {
        serverActive = false;
        sinceJoin = 0;
        STATE.reset();
    }

    /** 探测服务端是否安装了 ElyOff（服务端注册了对应的 C2S 包类型）。 */
    private static boolean serverHasMod() {
        try {
            return ClientPlayNetworking.canSend(HelloC2S.TYPE);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
