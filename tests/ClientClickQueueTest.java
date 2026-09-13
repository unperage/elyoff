import io.github.unperage.elyoff.logic.CancelStateMachine;

import java.util.ArrayList;
import java.util.List;

/**
 * 复现并验证「客户端一按跳跃起飞就被取消」的 bug。
 *
 * 根因：旧客户端分支用了 KeyMapping#consumeClick()。
 * 原版跳跃键只被当作"是否按住"使用（isDown），任何人都不消费它的点击，
 * 于是 clickCount 随着每一次按下单调累加且永不清理：
 *   地面起跳 +1、空中起飞 +1 …… 滑翔到第 6 tick 时 consumeClick() 立刻取到那个陈旧的按键，
 * 于是刚起飞就被取消，而且每飞一次计数又涨，所以"后续都是如此"。
 *
 * 本测试用同一段输入脚本分别驱动「旧算法」与「新算法」，断言：
 *   - 旧算法确实会误取消（证明根因成立）
 *   - 新算法一次都不误取消
 */
public class ClientClickQueueTest {

    private static int checks = 0;
    private static int failures = 0;

    private static void check(String label, boolean ok, String detail) {
        checks++;
        if (!ok) failures++;
        System.out.printf("  [%s] %-50s %s%n", ok ? "PASS" : "FAIL", label, detail);
    }

    // ---------------------------------------------------------------- 桩

    /** 极简 KeyMapping：只保留 isDown / clickCount / consumeClick 的原版语义。 */
    static final class FakeKey {
        boolean isDown;
        int clickCount;

        /** KeyMapping.click()：按下时 isDown=true 且 clickCount++。 */
        void press() {
            isDown = true;
            clickCount++;
        }

        /** KeyMapping.setDown(false)。注意：原版不会因此清理 clickCount。 */
        void release() {
            isDown = false;
        }

        boolean isDown() {
            return isDown;
        }

        boolean consumeClick() {
            if (clickCount > 0) {
                clickCount--;
                return true;
            }
            return false;
        }
    }

    interface Impl {
        FakeKey key();

        /** 推进一 tick，返回本 tick 是否执行了取消。 */
        boolean tick(boolean gliding);
    }

    /** 旧客户端算法（ElyOff 1.0.0）：用 consumeClick()，有 bug。 */
    static final class OldClient implements Impl {
        final FakeKey key = new FakeKey();
        int glideTicks;
        boolean suppress;

        @Override
        public FakeKey key() {
            return key;
        }

        @Override
        public boolean tick(boolean gliding) {
            boolean jumpDown = key.isDown();
            if (suppress) {
                if (!jumpDown) {
                    suppress = false;
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
            if (glideTicks > 5 && key.consumeClick()) {
                suppress = true;
                glideTicks = 0;
                return true;
            }
            return false;
        }
    }

    /** 新客户端算法：只看按住状态 + 共用状态机。 */
    static final class NewClient implements Impl {
        final FakeKey key = new FakeKey();
        final CancelStateMachine sm = new CancelStateMachine();

        @Override
        public FakeKey key() {
            return key;
        }

        @Override
        public boolean tick(boolean gliding) {
            return sm.update(key.isDown(), gliding);
        }
    }

    // ------------------------------------------------------------ 场景

    static final class Step {
        final boolean press;
        final boolean release;
        final boolean gliding;

        Step(boolean press, boolean release, boolean gliding) {
            this.press = press;
            this.release = release;
            this.gliding = gliding;
        }

        static Step hold(boolean gliding) {
            return new Step(false, false, gliding);
        }
    }

    /** 跑完整脚本；同时把每 tick 的取消情况交给回调观察。 */
    static int run(Impl impl, List<Step> steps, boolean[] cancelPerTick) {
        int cancels = 0;
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            if (s.press) impl.key().press();
            if (s.release) impl.key().release();
            boolean c = impl.tick(s.gliding);
            if (cancelPerTick != null) cancelPerTick[i] = c;
            if (c) cancels++;
        }
        return cancels;
    }

    public static void main(String[] args) {
        scenarioA_takeoffByJump();
        scenarioB_retakeoffAfterCancel();
        scenarioC_staleCounterEvidence();

        System.out.println();
        System.out.printf("共 %d 项断言, 失败 %d 项%n", checks, failures);
        if (failures == 0) {
            System.out.println("ALL PASS: 客户端误取消已被修复");
        } else {
            System.out.println("HAS FAILURES");
            System.exit(1);
        }
    }

    /**
     * 场景A：地面起跳 → 空中按下跳跃起飞 → 按住滑翔 20 tick。
     * 全程只有一次"起飞"用的按下，绝不该出现取消。
     */
    private static void scenarioA_takeoffByJump() {
        System.out.println("场景A：按跳跃键起飞后按住滑翔（用户报告的主场景）");
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(true, false, false));   // 1  地面：按下跳跃（起跳）
        steps.add(new Step(false, true, false));   // 2  地面：松开
        steps.add(Step.hold(false));               // 3  空中下落
        steps.add(new Step(true, false, false));   // 4  空中：按下跳跃（请求起飞）
        for (int i = 0; i < 20; i++) {
            steps.add(Step.hold(true));            // 5-24 滑翔中，跳跃键保持按住
        }

        int oldCancels = run(new OldClient(), steps, null);
        int newCancels = run(new NewClient(), steps, null);

        check("旧算法会误取消（复现 bug）", oldCancels > 0,
                "取消 " + oldCancels + " 次");
        check("新算法全程不取消", newCancels == 0,
                "取消 " + newCancels + " 次");
        System.out.println();
    }

    /**
     * 场景B：滑翔中松开→再按（正常取消一次）→ 按住 → 松开 → 重新起飞。
     * 重点：重新起飞之后绝不能被再次取消。
     */
    private static void scenarioB_retakeoffAfterCancel() {
        System.out.println("场景B：取消一次后重新起飞，不应被再次取消");
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            steps.add(Step.hold(true));            // 1-10  正常滑翔，未按键
        }
        int pressTick = steps.size();
        steps.add(new Step(true, false, true));    // 11 飞行中按下 → 正确取消
        for (int i = 0; i < 9; i++) {
            steps.add(Step.hold(true));            // 12-20 按住不放 + 客户端重试起飞
        }
        steps.add(new Step(false, true, false));   // 21 松开 → 解锁
        steps.add(new Step(true, false, false));   // 22 再次按下（请求起飞）
        int retakeoffFrom = steps.size();
        for (int i = 0; i < 8; i++) {
            steps.add(Step.hold(true));            // 23-30 重新滑翔并按住
        }

        boolean[] oldTicks = new boolean[steps.size()];
        boolean[] newTicks = new boolean[steps.size()];
        run(new OldClient(), steps, oldTicks);
        run(new NewClient(), steps, newTicks);

        check("旧算法：第11 tick 正确取消", oldTicks[pressTick], "tick=" + (pressTick + 1));
        check("新算法：第11 tick 正确取消", newTicks[pressTick], "tick=" + (pressTick + 1));

        int oldAfter = 0;
        int newAfter = 0;
        for (int i = retakeoffFrom; i < steps.size(); i++) {
            if (oldTicks[i]) oldAfter++;
            if (newTicks[i]) newAfter++;
        }
        check("旧算法：重新起飞后被误取消（复现 bug）", oldAfter > 0, "取消 " + oldAfter + " 次");
        check("新算法：重新起飞后正常飞行", newAfter == 0, "取消 " + newAfter + " 次");
        System.out.println();
    }

    /** 直接展示 clickCount 永不清理这一事实 —— 根因证据。 */
    private static void scenarioC_staleCounterEvidence() {
        System.out.println("场景C：clickCount 从不被原版消费（根因证据）");
        OldClient old = new OldClient();
        old.key.press();     // 地面起跳
        old.key.release();
        old.key.press();     // 空中起飞
        boolean consumedByVanilla = old.key.consumeClick(); // 模拟"原版会不会消费"
        check("原版从不消费跳跃键点击", consumedByVanilla && old.key.clickCount == 1,
                "consumeClick 前残留=" + (old.key.clickCount + 1) + ", 消费后仍残留=" + old.key.clickCount);
        System.out.println();
    }
}
