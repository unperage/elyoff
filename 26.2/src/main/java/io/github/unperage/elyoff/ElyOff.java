package io.github.unperage.elyoff;

import io.github.unperage.elyoff.net.HelloC2S;
import io.github.unperage.elyoff.net.HelloS2C;
import io.github.unperage.elyoff.server.ServerElytraCancel;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ElyOff —— 滑翔途中按跳跃键即可取消飞行。
 * <p>
 * 双端设计：
 * <ul>
 *     <li><b>服务端分支（权威）</b>：装到服务端后，所有玩家都能使用（含原版客户端），
 *         且取消动作由服务端同步，不存在两端状态不一致。</li>
 *     <li><b>客户端分支（兜底）</b>：服务端没装时，由玩家自己的客户端本地取消。</li>
 * </ul>
 * 两端通过握手包协商：服务端装了 → 客户端兜底逻辑自动停跑。
 */
public class ElyOff implements ModInitializer {

    public static final String MOD_ID = "elyoff";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElyOff");

    @Override
    public void onInitialize() {
        // 注册双向握手包类型（两端都注册，才能编解码）
        PayloadTypeRegistry.serverboundPlay().register(HelloC2S.TYPE, HelloC2S.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HelloS2C.TYPE, HelloS2C.CODEC);

        // 客户端问好 → 服务端回问好（客户端据此确认服务端已生效）
        ServerPlayNetworking.registerGlobalReceiver(HelloC2S.TYPE, (payload, context) ->
                ServerPlayNetworking.send(context.player(), new HelloS2C()));

        // 玩家加入时主动告知客户端：服务端已安装
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sender.sendPacket(new HelloS2C()));

        // 玩家离开时清理状态
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ServerElytraCancel.forget(handler.getPlayer().getUUID()));

        // 服务端权威逻辑：滑翔中按下跳跃 → 取消飞行
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerElytraCancel.tick(player);
            }
        });

        LOGGER.info("[ElyOff] 服务端分支已启用：滑翔中按跳跃键可取消飞行");
    }
}
