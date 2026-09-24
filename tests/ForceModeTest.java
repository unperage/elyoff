import io.github.unperage.elyoff.logic.ForceMode;

/**
 * ForceMode 离线验证：开 / 关 / 智能 三档策略的判定与容错解析。
 */
public class ForceModeTest {

    private static int checks = 0;
    private static int failures = 0;

    private static void check(String label, boolean ok, String detail) {
        checks++;
        if (!ok) failures++;
        System.out.printf("  [%s] %-52s %s%n", ok ? "PASS" : "FAIL", label, detail);
    }

    public static void main(String[] args) {
        scenario1_on();
        scenario2_off();
        scenario3_smart();
        scenario4_parse();
        scenario5_cycle();

        System.out.println();
        System.out.printf("共 %d 项断言, 失败 %d 项%n", checks, failures);
        if (failures == 0) {
            System.out.println("ALL PASS: ForceMode 行为正确");
        } else {
            System.out.println("HAS FAILURES");
            System.exit(1);
        }
    }

    /** 开：任何延迟下都强制客户端本地生效。 */
    private static void scenario1_on() {
        System.out.println("场景1：开 —— 任何延迟都强制客户端");
        for (int ping : new int[] {0, 1, 49, 50, 51, 200, 999}) {
            check("ON @ ping=" + ping + "ms", ForceMode.ON.forcesClient(ping, 50), "应强制");
        }
        System.out.println();
    }

    /** 关：一律不强制，交给服务端。 */
    private static void scenario2_off() {
        System.out.println("场景2：关 —— 一律不强制");
        for (int ping : new int[] {0, 49, 50, 51, 200, 999}) {
            check("OFF @ ping=" + ping + "ms", !ForceMode.OFF.forcesClient(ping, 50), "不应强制");
        }
        System.out.println();
    }

    /** 智能：严格大于阈值才强制（等于阈值不算）。 */
    private static void scenario3_smart() {
        System.out.println("场景3：智能 —— ping > 阈值 才强制（严格大于）");
        int threshold = 50;
        check("SMART @ 0ms", !ForceMode.SMART.forcesClient(0, threshold), "0 <= 50 → 不强制");
        check("SMART @ 49ms", !ForceMode.SMART.forcesClient(49, threshold), "49 <= 50 → 不强制");
        check("SMART @ 50ms（边界）", !ForceMode.SMART.forcesClient(50, threshold), "等于阈值 → 不强制 ★");
        check("SMART @ 51ms", ForceMode.SMART.forcesClient(51, threshold), "51 > 50 → 强制");
        check("SMART @ 200ms", ForceMode.SMART.forcesClient(200, threshold), "200 > 50 → 强制");

        // 阈值可调
        check("SMART @ 120ms / 阈值 100", ForceMode.SMART.forcesClient(120, 100), "120 > 100 → 强制");
        check("SMART @ 80ms / 阈值 100", !ForceMode.SMART.forcesClient(80, 100), "80 <= 100 → 不强制");
        check("SMART @ 30ms / 阈值 0", ForceMode.SMART.forcesClient(30, 0), "30 > 0 → 强制");
        check("SMART @ 0ms / 阈值 0", !ForceMode.SMART.forcesClient(0, 0), "0 不大于 0 → 不强制（拿不到延迟时退化为关）");
        System.out.println();
    }

    /** 解析容错：大小写、空格、null、垃圾值都不能抛异常。 */
    private static void scenario4_parse() {
        System.out.println("场景4：解析容错");
        check("parse(\"ON\")", ForceMode.parse("ON") == ForceMode.ON, "精确");
        check("parse(\"on\")", ForceMode.parse("on") == ForceMode.ON, "小写");
        check("parse(\" Off \")", ForceMode.parse(" Off ") == ForceMode.OFF, "带空格 + 混合大小写");
        check("parse(\"smart\")", ForceMode.parse("smart") == ForceMode.SMART, "小写");
        check("parse(null) → 默认", ForceMode.parse(null) == ForceMode.DEFAULT, "null 回落默认");
        check("parse(\"\") → 默认", ForceMode.parse("") == ForceMode.DEFAULT, "空串回落默认");
        check("parse(\"garbage\") → 默认", ForceMode.parse("garbage") == ForceMode.DEFAULT, "垃圾值回落默认");
        check("默认值是 SMART", ForceMode.DEFAULT == ForceMode.SMART, "DEFAULT=SMART");
        System.out.println();
    }

    /** 按键循环顺序。 */
    private static void scenario5_cycle() {
        System.out.println("场景5：按键循环顺序");
        check("ON.next()=OFF", ForceMode.ON.next() == ForceMode.OFF, "开 → 关");
        check("OFF.next()=SMART", ForceMode.OFF.next() == ForceMode.SMART, "关 → 智能");
        check("SMART.next()=ON", ForceMode.SMART.next() == ForceMode.ON, "智能 → 开（回环）");
        check("翻译键", "elyoff.mode.smart".equals(ForceMode.SMART.translationKey()), ForceMode.SMART.translationKey());
        System.out.println();
    }
}
