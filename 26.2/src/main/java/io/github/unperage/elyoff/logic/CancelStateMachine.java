package io.github.unperage.elyoff.logic;

/**
 * "跳跃取消滑翔"的纯状态机（不依赖任何 Minecraft 类，便于离线单元验证）。
 * <p>
 * 客户端兜底分支与服务端权威分支共用这一份实现，保证两端判定完全一致。
 * <p>
 * 设计要点：
 * <ul>
 *     <li><b>只看键位状态，不看点击队列</b>：输入来自 {@code KeyMapping#isDown()} /
 *         {@code Input#jump()} 这类"当前是否按住"的瞬时状态。
 *         绝不能用 {@code KeyMapping#consumeClick()}：原版跳跃键从不消费点击，
 *         那个计数器只会一直累加（地面起跳一次、起飞一次……），
 *         滑翔到门槛 tick 时会把很久以前的陈旧按键当成"新按下"，导致刚起飞就被取消。</li>
 *     <li><b>上升沿取消</b>：跳跃键从"未按下"→"按下"，且本次滑翔已超过 {@link #MIN_GLIDE_TICKS}，
 *         才判定为"飞行中主动取消"（门槛用于避开"用跳跃键起飞"那一次按下）。</li>
 *     <li><b>按住期间保持取消</b>：取消后若跳跃键仍按着，玩家再次进入滑翔会被立刻取消，
 *         避免"按住空格一直飞"。</li>
 *     <li><b>松开即解锁</b>：检测到跳跃键松开就清除抑制。因为任何"新的按下"都必然先有一次松开，
 *         所以玩家松开再按一定可以重新起飞 —— 这正是修复"取消一次后再也飞不起来"的关键。</li>
 * </ul>
 * 每个玩家（客户端为本地玩家）持有一个实例。
 */
public final class CancelStateMachine {

    /** 滑翔开始后至少经过这么多 tick，才允许按键取消（避免误伤"跳跃起飞"）。 */
    public static final int MIN_GLIDE_TICKS = 5;

    private boolean lastJump;
    private int glideTicks;
    private boolean suppressWhileHeld;

    /**
     * 推进一个 tick。
     *
     * @param jump    当前 tick 玩家是否按住跳跃键
     * @param gliding 当前 tick 玩家是否处于滑翔状态
     * @return true 表示本 tick 应当取消滑翔
     */
    public boolean update(boolean jump, boolean gliding) {
        boolean previousJump = lastJump;
        lastJump = jump;

        // 1) 取消后的"按住期间抑制"：松开解锁；仍按着又飞起来 → 继续取消
        if (suppressWhileHeld) {
            if (!jump) {
                suppressWhileHeld = false;
            } else if (gliding) {
                glideTicks = 0;
                return true;
            }
        }

        if (!gliding) {
            glideTicks = 0;
            return false;
        }

        glideTicks++;

        // 2) 飞行途中"新按下"跳跃 → 取消
        boolean risingEdge = jump && !previousJump;
        if (risingEdge && glideTicks > MIN_GLIDE_TICKS) {
            suppressWhileHeld = true;
            glideTicks = 0;
            return true;
        }

        return false;
    }

    /** 回到初始状态（换世界、断线重连时调用）。不保留任何上一局的按键记忆。 */
    public void reset() {
        lastJump = false;
        glideTicks = 0;
        suppressWhileHeld = false;
    }
}
