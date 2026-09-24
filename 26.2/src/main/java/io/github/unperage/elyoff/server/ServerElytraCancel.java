package io.github.unperage.elyoff.server;

import io.github.unperage.elyoff.logic.CancelStateMachine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 服务端权威逻辑：滑翔途中"按下跳跃键"即取消滑翔。
 * <p>
 * 之所以放在服务端：滑翔状态是同步实体数据里的共享标志位（flag 7）。
 * 服务端调用 {@link net.minecraft.world.entity.LivingEntity#stopFallFlying()} 后会自动同步给所有客户端，
 * 因此连原版客户端也能享受该功能，且不会出现两端状态不一致。
 * <p>
 * 判定规则全部在 {@link CancelStateMachine} 中实现（纯逻辑，可离线测试）：
 * 上升沿取消 + 按住期间保持取消 + 松开即解锁。
 * <p>
 * <b>客户端接管豁免</b>：当某玩家在客户端开启了「强制客户端生效」时，客户端会发
 * {@code ForceStateC2S(true)}；此后本类对该玩家<b>完全不再做每 tick 轮询</b>。
 * 取消动作由客户端的本地预测完成，并通过 {@code CancelC2S} 通知服务端做一次权威落定
 * （见 {@link #cancel(ServerPlayer)}）。客户端把开关关掉时会再发一次 {@code false}，
 * 监控随即恢复（状态机重新从零开始，不会带入旧的按键记忆）。
 */
public final class ServerElytraCancel {

    /** 每个玩家一个独立状态机。 */
    private static final Map<UUID, CancelStateMachine> STATES = new HashMap<>();

    /** 已声明"我自己在客户端处理"的玩家；这些人不再走服务端轮询。 */
    private static final Set<UUID> CLIENT_FORCED = new HashSet<>();

    private ServerElytraCancel() {
    }

    /**
     * 客户端申报自己是否接管了本地取消。
     *
     * @param id     玩家 UUID
     * @param forced true = 客户端接管，服务端停止监控该玩家
     */
    public static void setClientForced(UUID id, boolean forced) {
        if (forced) {
            CLIENT_FORCED.add(id);
            // 顺手丢掉按键记忆：等开关关掉时从零开始，免得带出一个假的上升沿
            STATES.remove(id);
        } else {
            CLIENT_FORCED.remove(id);
        }
    }

    /** 该玩家是否已声明由客户端接管。 */
    public static boolean isClientForced(UUID id) {
        return CLIENT_FORCED.contains(id);
    }

    /** 每 tick 轮询入口；客户端已接管时立刻返回，不做任何判定。 */
    public static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        if (CLIENT_FORCED.contains(id)) {
            return;
        }

        CancelStateMachine state = STATES.computeIfAbsent(id, k -> new CancelStateMachine());

        boolean jump = readJump(player);
        boolean gliding = player.isFallFlying();

        if (state.update(jump, gliding)) {
            player.stopFallFlying();
        }
    }

    /**
     * 权威落定一次取消：客户端本地已经停止滑翔，这里让服务端也停下来并同步给所有人。
     * <p>
     * 不需要额外校验 —— 玩家本来就有权在任何时候停止自己的滑翔，这不是特权动作。
     */
    public static void cancel(ServerPlayer player) {
        player.stopFallFlying();
    }

    /** 读取玩家最近的客户端输入（含跳跃键状态）。 */
    private static boolean readJump(ServerPlayer player) {
        try {
            Input input = player.getLastClientInput();
            return input != null && input.jump();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 玩家离开时清理状态，避免内存泄漏。 */
    public static void forget(UUID id) {
        STATES.remove(id);
        CLIENT_FORCED.remove(id);
    }
}
