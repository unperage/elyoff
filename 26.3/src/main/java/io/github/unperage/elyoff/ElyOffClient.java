package io.github.unperage.elyoff;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.unperage.elyoff.config.ElyOffClientConfig;
import io.github.unperage.elyoff.logic.ClientFallback;
import io.github.unperage.elyoff.logic.ForceMode;
import io.github.unperage.elyoff.net.CancelC2S;
import io.github.unperage.elyoff.net.ForceStateC2S;
import io.github.unperage.elyoff.net.HelloC2S;
import io.github.unperage.elyoff.net.HelloS2C;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * 客户端分支。
 * <p>
 * 默认规则：<b>服务端没有生效时</b>在本地取消滑翔；一旦确认服务端装了 ElyOff
 * （收到握手包，或可以直接发送 C2S 包），客户端就让位，交给服务端处理。
 * <p>
 * 但服务端分支要等一个完整 RTT 才生效，而客户端本地取消是<b>本地预测</b>、按键当 tick 就见效。
 * 所以本类提供一个玩家可调的「强制客户端生效」策略（{@link ForceMode}，配置在
 * {@code config/elyoff-client.json}，也可以按键循环切换）：
 * <ul>
 *     <li><b>开</b>：总是强制客户端本地取消，即使服务端也装了 —— 手感 0 延迟。</li>
 *     <li><b>关</b>：不强制，服务端装了就让位（1.0.x 的原有行为）。</li>
 *     <li><b>智能</b>：延迟高于阈值（默认 50ms）时按「开」，否则按「关」。</li>
 * </ul>
 * 三种模式都不影响"服务端没装本模组"时的客户端兜底 —— 那时候本地取消是唯一手段。
 * <p>
 * 判定规则与服务端共用 {@link io.github.unperage.elyoff.logic.CancelStateMachine}：只看跳跃键的
 * <b>当前按住状态</b>，认"滑翔途中的上升沿"；取消后按住跳跃键期间保持取消，松开即解锁。
 * <p>
 * <b>注意</b>：判定跳跃键时刻意不使用 {@code KeyMapping#consumeClick()}。
 * 原版跳跃键从不消费点击，其内部 clickCount 只会随着每一次按下单调累加
 * （地面起跳 +1、空中起飞 +1 ……），而 vanilla 也从不清理它。
 * 一旦读取该队列，滑翔刚过门槛 tick 就会取到很久以前遗留的陈旧按键，
 * 表现为"刚按跳跃起飞就被取消，而且每次都是"。键位状态法是唯一正确的取法。
 * <p>
 * 本类只负责接线；所有判定都在 {@link ClientFallback} / {@link ForceMode} 里，可离线测试。
 */
@Environment(EnvType.CLIENT)
public class ElyOffClient implements ClientModInitializer {

    private static final ClientFallback FALLBACK = new ClientFallback();

    /** 循环切换"强制客户端生效"的按键（默认不绑定，由玩家自行设置）。 */
    private static KeyMapping cycleForceModeKey;

    /** 上一次读到的延迟；读不到时沿用旧值，避免闪烁。 */
    private static int lastPingMs = 0;

    /** 上一次同步给服务端的接管状态；null 表示本届连接还没同步过。 */
    private static Boolean lastSentForce = null;

    @Override
    public void onInitializeClient() {
        // 收到服务端握手 → 标记服务端已生效
        ClientPlayNetworking.registerGlobalReceiver(HelloS2C.TYPE, (payload, context) ->
                context.client().execute(FALLBACK::markServerActive));

        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("elyoff", "main"));
        cycleForceModeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.elyoff.cycle_force_mode",
                InputConstants.Type.KEYBOARD,
                InputConstants.UNKNOWN.getValue(),
                category));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            FALLBACK.reset();
            lastPingMs = 0;
            lastSentForce = null;
            // 主动探测一次：服务端若注册了该 C2S 包就会回握手
            if (serverHasMod()) {
                ClientPlayNetworking.send(new HelloC2S());
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            FALLBACK.reset();
            lastSentForce = null;
        });

        ClientTickEvents.END_CLIENT_TICK.register(ElyOffClient::onEndTick);
    }

    private static void onEndTick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            // 不在世界里：保持现状，等下一次 JOIN 重置
            return;
        }

        ElyOffClientConfig config = ElyOffClientConfig.get();
        handleModeKey(client, player, config);

        int ping = readPingMs(client, player);
        boolean force = config.mode().forcesClient(ping, config.smartPingThresholdMs());

        // 先申报接管状态：服务端据此决定要不要继续监控本玩家的按键
        syncForceState(force);

        boolean jump = client.options.keyJump.isDown();
        if (FALLBACK.tick(jump, player.isFallFlying(), serverHasMod(), force)) {
            player.stopFallFlying();
            // 本地已抢跑；告诉服务端权威落定一次，别让它还认为你在滑翔
            notifyServerCancelled();
        }
    }

    /**
     * 只在接管状态<b>变化</b>时通知服务端（边沿触发，不是每 tick 发）。
     * <p>
     * 服务端会因此停止或恢复对本玩家按键的逐 tick 监控。
     */
    private static void syncForceState(boolean force) {
        if (lastSentForce != null && lastSentForce == force) {
            return;
        }
        try {
            if (!ClientPlayNetworking.canSend(ForceStateC2S.TYPE)) {
                return;
            }
            ClientPlayNetworking.send(new ForceStateC2S(force));
            lastSentForce = force;
        } catch (Throwable ignored) {
            // 发不出去就算了：服务端继续按自己的节奏轮询，行为退回到 1.0.x
        }
    }

    /** 客户端本地取消后，请服务端也停下来并同步给所有人。 */
    private static void notifyServerCancelled() {
        try {
            if (ClientPlayNetworking.canSend(CancelC2S.TYPE)) {
                ClientPlayNetworking.send(new CancelC2S());
            }
        } catch (Throwable ignored) {
            // 原版那条 START_FALL_FLYING 副作用链路仍然会兜住这种情况
        }
    }

    /** 按键循环切换策略，并提示当前模式与延迟。 */
    private static void handleModeKey(Minecraft client, LocalPlayer player, ElyOffClientConfig config) {
        if (cycleForceModeKey == null) {
            return;
        }
        boolean changed = false;
        // 这是我们自己的键位，点击队列只由我们消费，用 while 清空是正确做法
        while (cycleForceModeKey.consumeClick()) {
            ForceMode next = config.mode().next();
            config.setMode(next);
            changed = true;
        }
        if (changed) {
            player.sendOverlayMessage(Component.translatable(
                    "elyoff.force_mode.changed",
                    Component.translatable(config.mode().translationKey()),
                    readPingMs(client, player)));
        }
    }

    /**
     * 读取自己在服务端玩家列表里的延迟（毫秒）。
     * <p>
     * 玩家还可能没被同步到列表里，这里所有异常都吞掉并沿用上一次的值 —— 拿不到延迟时
     * 报 0，智能模式就会按「关」处理，退回到最保守、两端天然一致的行为。
     */
    private static int readPingMs(Minecraft client, LocalPlayer player) {
        try {
            ClientPacketListener connection = client.getConnection();
            if (connection == null) {
                return lastPingMs;
            }
            PlayerInfo info = connection.getPlayerInfo(player.getUUID());
            if (info == null) {
                return lastPingMs;
            }
            lastPingMs = Math.max(0, info.getLatency());
        } catch (Throwable ignored) {
            // 保持上一次的值
        }
        return lastPingMs;
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
