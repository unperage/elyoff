package io.github.unperage.elyoff.logic;

/**
 * 客户端兜底分支的完整判定逻辑（纯逻辑，不依赖任何 Minecraft 类，可离线测试）。
 * <p>
 * 它回答两个问题：
 * <ol>
 *     <li>本 tick 的按键/滑翔状态，按规则是否构成"取消"？—— 交给 {@link CancelStateMachine}。</li>
 *     <li>客户端<b>此刻到底该不该落手</b>？—— 只要服务端可能已接管（收到握手包，或能探测到服务端注册了本模组的通道），
 *         客户端就必须完全让位。</li>
 * </ol>
 * 把"是否落手"的判定单独拎出来，是因为这里出过一次事故：
 * 曾经为了"等握手结论落地"加了一个 40 tick 宽限期，写法是
 * {@code if (ticks < 40) ticks++;} 配上 {@code if (ticks <= 40) return false;} ——
 * 计数器饱和在 40，而判断用的是 {@code <=}，条件恒为真，
 * 结果客户端兜底分支被永久掐死，表现为"按跳跃键完全没反应"。
 * <p>
 * 现在的语义是：<b>没有宽限期，也不做任何计数</b>。
 * Fabric 的 {@code ClientPlayNetworking#canSend} 在登录阶段就已经知道服务端注册了哪些通道，
 * 因此进服第一个 tick 就能得到可靠结论，不需要靠"等一会儿"来猜。
 */
public final class ClientFallback {

    private final CancelStateMachine state = new CancelStateMachine();

    /** 是否已收到服务端的握手包（服务端确认自己也装了 ElyOff）。 */
    private boolean serverActive;

    /** 收到服务端握手包。 */
    public void markServerActive() {
        serverActive = true;
    }

    /** 换世界 / 断线重连，回到初始状态。 */
    public void reset() {
        serverActive = false;
        state.reset();
    }

    /**
     * 推进一个 tick，并给出客户端是否应当执行本地取消。
     * <p>
     * 无论服务端是否接管，状态机都会照常推进 —— 这样"上一次按键"的记忆始终与真实输入同步，
     * 不会在交接的瞬间凭空冒出一个假的上升沿。
     *
     * @param jump         当前 tick 是否按住跳跃键（{@code KeyMapping#isDown()}，
     *                     绝不能用 {@code consumeClick()}）
     * @param gliding      当前 tick 是否处于滑翔状态
     * @param serverHasMod 是否探测到服务端也装了 ElyOff
     * @return true 表示本 tick 客户端应当调用 {@code stopFallFlying()}
     */
    public boolean tick(boolean jump, boolean gliding, boolean serverHasMod) {
        boolean cancel = state.update(jump, gliding);
        if (serverActive || serverHasMod) {
            return false;
        }
        return cancel;
    }
}
