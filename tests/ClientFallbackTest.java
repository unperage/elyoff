import io.github.unperage.elyoff.logic.ClientFallback;

/**
 * 客户端兜底分支的回归测试（离线，不依赖 Minecraft）。
 *
 * 覆盖两次真实事故：
 *   1.0.0：用 KeyMapping#consumeClick() 读到了永不清理的陈旧按键，
 *          滑翔刚过 5 tick 就被误取消，每次都是。
 *   1.0.1：为了"等握手结论"加了一个 40 tick 宽限期，写法是
 *          if (ticks < 40) ticks++;  配上  if (ticks <= 40) return false;
 *          计数器饱和在 40 而判断用 <=，条件恒为真 →
 *          客户端兜底分支被永久掐死，表现为"按跳跃键完全没反应"。
 *
 * 因此这里刻意用「长时间滑翔之后才按键」的脚本来跑，
 * 任何形态的宽限期 / 计数器 off-by-one 都会被它抓住。
 */
public class ClientFallbackTest {

    private static int checks = 0;
    private static int failures = 0;

    private static void check(String label, boolean ok, String detail) {
        checks++;
        if (!ok) failures++;
        System.out.printf("  [%s] %-52s %s%n", ok ? "PASS" : "FAIL", label, detail);
    }

    public static void main(String[] args) {
        scenario1_vanillaServerDoesCancel();
        scenario2_takeoffPressIsNotCancel();
        scenario3_serverWithModIsLeftAlone();
        scenario4_handshakePacketDisablesFallback();
        scenario5_fullRoundTrip();
        scenario6_forceOverridesServerMod();

        System.out.println();
        System.out.printf("共 %d 项断言, 失败 %d 项%n", checks, failures);
        if (failures == 0) {
            System.out.println("ALL PASS: 客户端兜底分支行为正确");
        } else {
            System.out.println("HAS FAILURES");
            System.exit(1);
        }
    }

    /**
     * 主场景（1.0.1 的事故）：服务端没装模组时，客户端必须真的落手。
     * 特意先滑翔 500 tick 再按键 —— 宽限期 / 计数器类的 bug 会在这里暴露。
     */
    private static void scenario1_vanillaServerDoesCancel() {
        System.out.println("场景1：服务端未安装 → 滑翔途中按跳跃必须取消（1.0.1 事故）");
        ClientFallback fb = new ClientFallback();

        boolean anyEarlyCancel = false;
        for (int i = 0; i < 500; i++) {
            if (fb.tick(false, true, false)) anyEarlyCancel = true;
        }
        check("滑翔 500 tick 内不应自行取消", !anyEarlyCancel, "误取消=" + anyEarlyCancel);

        boolean cancelled = fb.tick(true, true, false);
        check("第 501 tick 按下跳跃 → 客户端落手取消 ★", cancelled, "cancel=" + cancelled);
        System.out.println();
    }

    /** 用跳跃键起飞那一次按下不应被当成取消。 */
    private static void scenario2_takeoffPressIsNotCancel() {
        System.out.println("场景2：用跳跃键起飞不应被误取消");
        ClientFallback fb = new ClientFallback();

        int spurious = 0;
        if (fb.tick(true, false, false)) spurious++;        // 空中按下 → 请求起飞
        for (int i = 0; i < 400; i++) {
            if (fb.tick(true, true, false)) spurious++;     // 起飞后一直按住滑翔
        }
        check("起飞后按住滑翔 400 tick 不误取消", spurious == 0, "误取消=" + spurious + " 次");
        System.out.println();
    }

    /** 服务端装了模组（canSend 探测为真）→ 客户端必须完全让位。 */
    private static void scenario3_serverWithModIsLeftAlone() {
        System.out.println("场景3：服务端已安装 → 客户端绝不落手");
        ClientFallback fb = new ClientFallback();

        int acted = 0;
        for (int i = 0; i < 300; i++) {
            if (fb.tick(false, true, true)) acted++;
        }
        // 飞行途中反复按放跳跃，客户端也必须全程不落手
        for (int i = 0; i < 100; i++) {
            if (fb.tick(i % 2 == 0, true, true)) acted++;
        }
        check("服务端已接管时客户端一次都不落手", acted == 0, "落手=" + acted + " 次");
        System.out.println();
    }

    /** 收到 HelloS2C 之后同理。 */
    private static void scenario4_handshakePacketDisablesFallback() {
        System.out.println("场景4：收到握手包 → 客户端绝不落手");
        ClientFallback fb = new ClientFallback();
        fb.markServerActive();

        int acted = 0;
        for (int i = 0; i < 300; i++) {
            if (fb.tick(i % 3 == 0, true, false)) acted++;
        }
        check("握手后客户端一次都不落手", acted == 0, "落手=" + acted + " 次");

        fb.reset();
        int afterReset = 0;
        for (int i = 0; i < 30; i++) {
            if (fb.tick(false, true, false)) afterReset++;
        }
        boolean works = fb.tick(true, true, false);
        check("reset() 后兜底恢复工作", afterReset == 0 && works, "cancel=" + works);
        System.out.println();
    }

    /** 完整来回：取消 → 按住 → 松开 → 重新起飞 → 正常飞行。 */
    private static void scenario5_fullRoundTrip() {
        System.out.println("场景5：取消 → 按住 → 松开 → 重新起飞（1.0.0 事故）");
        ClientFallback fb = new ClientFallback();

        for (int i = 0; i < 20; i++) {
            fb.tick(false, true, false);
        }
        boolean cancelled = fb.tick(true, true, false);
        check("飞行中按下跳跃 → 取消", cancelled, "cancel=" + cancelled);

        int held = 0;
        for (int i = 0; i < 10; i++) {
            if (fb.tick(true, true, false)) held++;
        }
        check("按住不放期间持续抑制", held == 10, "抑制 " + held + "/10 tick");

        boolean unlock = fb.tick(false, false, false);
        check("松开 → 解锁（本 tick 不取消）", !unlock, "cancel=" + unlock);

        fb.tick(true, false, false);                        // 重新按下，请求起飞
        int afterRetakeoff = 0;
        for (int i = 0; i < 300; i++) {
            if (fb.tick(true, true, false)) afterRetakeoff++;
        }
        check("重新起飞后正常飞行，不再误取消 ★", afterRetakeoff == 0, "误取消=" + afterRetakeoff + " 次");
        System.out.println();
    }

    /**
     * 新增的「强制客户端生效」：force=true 时即使服务端已接管，客户端也要本地抢跑。
     */
    private static void scenario6_forceOverridesServerMod() {
        System.out.println("场景6：强制客户端生效（force）");

        // 服务端装了 + 不强制 → 客户端完全不落手
        ClientFallback quiet = new ClientFallback();
        int acted = 0;
        for (int i = 0; i < 60; i++) {
            if (quiet.tick(false, true, true, false)) acted++;
        }
        if (quiet.tick(true, true, true, false)) acted++;
        check("serverHasMod + force=false → 不落手", acted == 0, "落手=" + acted + " 次");

        // 服务端装了 + 强制 → 滑翔途中按键要本地取消
        ClientFallback forced = new ClientFallback();
        int early = 0;
        for (int i = 0; i < 500; i++) {
            if (forced.tick(false, true, true, true)) early++;
        }
        check("force=true 滑翔 500 tick 不自行取消", early == 0, "误取消=" + early);
        boolean cancelled = forced.tick(true, true, true, true);
        check("serverHasMod + force=true → 本地抢跑取消 ★", cancelled, "cancel=" + cancelled);

        // 收到握手包后同理
        ClientFallback handshaked = new ClientFallback();
        handshaked.markServerActive();
        int acted2 = 0;
        for (int i = 0; i < 60; i++) {
            if (handshaked.tick(false, true, false, false)) acted2++;
        }
        check("握手后 + force=false → 不落手", acted2 == 0, "落手=" + acted2 + " 次");
        handshaked.tick(false, true, false, true);
        check("握手后 + force=true → 本地抢跑取消 ★", handshaked.tick(true, true, false, true), "cancel=true");

        // 服务端没装时，force 与否都必须照常兜底（功能不能因此消失）
        ClientFallback noMod = new ClientFallback();
        for (int i = 0; i < 20; i++) {
            noMod.tick(false, true, false, false);
        }
        check("服务端没装 + force=false → 仍然兜底", noMod.tick(true, true, false, false), "cancel=true");
        System.out.println();
    }
}
