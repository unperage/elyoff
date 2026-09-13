package io.github.unperage.elyoff.server;

import io.github.unperage.elyoff.logic.CancelStateMachine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;

import java.util.HashMap;
import java.util.Map;
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
 */
public final class ServerElytraCancel {

    /** 每个玩家一个独立状态机。 */
    private static final Map<UUID, CancelStateMachine> STATES = new HashMap<>();

    private ServerElytraCancel() {
    }

    public static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        CancelStateMachine state = STATES.computeIfAbsent(id, k -> new CancelStateMachine());

        boolean jump = readJump(player);
        boolean gliding = player.isFallFlying();

        if (state.update(jump, gliding)) {
            player.stopFallFlying();
        }
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
    }
}
