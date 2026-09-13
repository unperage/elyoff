package io.github.unperage.elyoff;

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
 * 一旦确认服务端装了 ElyOff（收到握手包，或可以发送 C2S 包），客户端逻辑立即停跑，
 * 完全交给服务端处理，避免两端重复触发。
 * <p>
 * 与状态机同源的规则：取消只认"滑翔途中的上升沿"；取消后按住跳跃键期间保持取消，松开即解锁。
 */
@Environment(EnvType.CLIENT)
public class ElyOffClient implements ClientModInitializer {

    /** 滑翔开始后至少经过这么多 tick 才允许按键取消（避开"跳跃起飞"那次按下）。 */
    private static final int MIN_GLIDE_TICKS = 5;

    /** 服务端是否已生效（收到 S2C 握手包）。 */
    private static volatile boolean serverActive = false;

    /** 本次滑翔已持续的 tick 数。 */
    private static int glideTicks = 0;

    /** 取消之后、跳跃键尚未松开期间，禁止重新进入滑翔。 */
    private static boolean suppressWhileHeld = false;

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

            boolean jumpDown = client.options.keyJump.isDown();

            // 取消后"按住期间保持取消"，松开即解锁
            if (suppressWhileHeld) {
                if (!jumpDown) {
                    suppressWhileHeld = false;
                } else if (player.isFallFlying()) {
                    player.stopFallFlying();
                    glideTicks = 0;
                    return;
                }
            }

            if (!player.isFallFlying()) {
                glideTicks = 0;
                return;
            }
            glideTicks++;

            // 服务端已生效 → 客户端兜底不跑
            if (serverActive || serverHasMod()) {
                return;
            }

            if (glideTicks > MIN_GLIDE_TICKS && client.options.keyJump.consumeClick()) {
                player.stopFallFlying();
                suppressWhileHeld = true;
                glideTicks = 0;
            }
        });
    }

    private static void reset() {
        serverActive = false;
        glideTicks = 0;
        suppressWhileHeld = false;
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
