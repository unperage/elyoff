import io.github.unperage.elyoff.logic.CancelStateMachine;

/**
 * CancelStateMachine 离线验证：
 * 模拟 服务端每 tick 观测到的 (jump, gliding)，断言"是否应取消滑翔"。
 *
 * 重点覆盖用户报告的 bug：
 *   第一次空中取消后，松开再按应能重新起飞（早期计时器版本会失败）。
 */
public class StateMachineTest {

    private static int failures = 0;
    private static int checks = 0;

    private static void expect(CancelStateMachine sm, boolean jump, boolean gliding,
                               boolean expectedCancel, String label) {
        boolean actual = sm.update(jump, gliding);
        checks++;
        String mark = actual == expectedCancel ? "PASS" : "FAIL";
        if (actual != expectedCancel) failures++;
        System.out.printf("  [%s] %-46s jump=%-5s gliding=%-5s -> cancel=%-5s (期望 %s)%n",
                mark, label, jump, gliding, actual, expectedCancel);
    }

    public static void main(String[] args) {
        scenario1_reportedBug();
        scenario2_startByJump();
        scenario3_holdThenRelease();
        scenario4_groundedJumping();

        System.out.println();
        System.out.printf("共 %d 项断言, 失败 %d 项%n", checks, failures);
        if (failures == 0) {
            System.out.println("ALL PASS: 状态机行为符合预期");
        } else {
            System.out.println("HAS FAILURES");
            System.exit(1);
        }
    }

    /** 用户报告的 bug：取消一次后，松开再按必须能重新起飞。 */
    private static void scenario1_reportedBug() {
        System.out.println("场景1：首次取消 → 松开 → 重新起飞（复现用户 bug）");
        CancelStateMachine sm = new CancelStateMachine();

        for (int i = 0; i < 10; i++) {
            expect(sm, false, true, false, "滑翔中未按键 tick" + (i + 1));
        }
        expect(sm, true, true, true, "飞行中按下跳跃 → 取消");
        expect(sm, true, true, true, "仍按住 + 客户端重试起飞 → 继续取消");
        expect(sm, false, false, false, "松开（此时未滑翔）→ 解锁");
        expect(sm, true, false, false, "重新按下（服务端尚未进入滑翔）");
        expect(sm, true, true, false, "滑翔已重新开始 → 不应取消 ★");
        expect(sm, true, true, false, "继续按住正常飞行 ★");
        expect(sm, true, true, false, "继续按住正常飞行 ★");
        System.out.println();
    }

    /** 用跳跃键起飞那一次按下不应被取消。 */
    private static void scenario2_startByJump() {
        System.out.println("场景2：用跳跃键起飞不应被误取消");
        CancelStateMachine sm = new CancelStateMachine();

        expect(sm, false, false, false, "空中下落，未按键");
        expect(sm, true, false, false, "按下跳跃（起飞请求）");
        expect(sm, true, true, false, "滑翔刚开始 → 不取消 ★");
        expect(sm, true, true, false, "继续按住飞行");
        System.out.println();
    }

    /** 按住不放无法重新起飞；松开重按即可。 */
    private static void scenario3_holdThenRelease() {
        System.out.println("场景3：按住不放保持取消，松开重按恢复");
        CancelStateMachine sm = new CancelStateMachine();

        for (int i = 0; i < 8; i++) {
            expect(sm, false, true, false, "滑翔 tick" + (i + 1));
        }
        expect(sm, true, true, true, "按下跳跃 → 取消");
        for (int i = 0; i < 5; i++) {
            expect(sm, true, true, true, "按住不放 + 反复重试起飞 → 每次取消");
        }
        expect(sm, false, false, false, "松开跳跃 → 解锁");
        expect(sm, true, false, false, "再次按下");
        expect(sm, true, true, false, "重新滑翔成功 ★");
        System.out.println();
    }

    /** 地面跳跃（不滑翔）不应有任何取消动作。 */
    private static void scenario4_groundedJumping() {
        System.out.println("场景4：地面跳跃不应被干预");
        CancelStateMachine sm = new CancelStateMachine();

        for (int i = 0; i < 5; i++) {
            expect(sm, i % 2 == 0, false, false, "地面跳跃 tick" + (i + 1));
        }
        System.out.println();
    }
}
