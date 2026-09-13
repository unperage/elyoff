package io.github.unperage.elyoff;

import io.github.unperage.elyoff.logic.ClientFallback;
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
 * 判定规则与服务端共用 {@link io.github.unperage.elyoff.logic.CancelStateMachine}：只看跳跃键的
 * <b>当前按住状态</b>，认"滑翔途中的上升沿"；取消后按住跳跃键期间保持取消，松开即解锁。
 * <p>
 * <b>注意</b>：这里刻意不使用 {@code KeyMapping#consumeClick()}。
 * 原版跳跃键从不消费点击，其内部 clickCount 只会随着每一次按下单调累加
 * （地面起跳 +1、空中起飞 +1 ……），而 vanilla 也从不清理它。
 * 一旦读取该队列，滑翔刚过门槛 tick 就会取到很久以前遗留的陈旧按键，
 * 表现为"刚按跳跃起飞就被取消，而且每次都是"。键位状态法是唯一正确的取法。
 * <p>
 * 本类只负责接线；所有判定都在 {@link ClientFallback} 里，可离线测试。
 */
@Environment(EnvType.CLIENT)
public class ElyOffClient implements ClientModInitializer {

    private static final ClientFallback FALLBACK = new ClientFallback();

    @Override
    public void onInitializeClient() {
        // 收到服务端握手 → 标记服务端已生效
        ClientPlayNetworking.registerGlobalReceiver(HelloS2C.TYPE, (payload, context) ->
                context.client().execute(FALLBACK::markServerActive));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            FALLBACK.reset();
            // 主动探测一次：服务端若注册了该 C2S 包就会回握手
            if (serverHasMod()) {
                ClientPlayNetworking.send(new HelloC2S());
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FALLBACK.reset());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null) {
                // 不在世界里：保持现状，等下一次 JOIN 重置
                return;
            }

            boolean jump = client.options.keyJump.isDown();
            if (FALLBACK.tick(jump, player.isFallFlying(), serverHasMod())) {
                player.stopFallFlying();
            }
        });
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
