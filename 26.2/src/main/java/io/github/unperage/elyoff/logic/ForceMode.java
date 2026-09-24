package io.github.unperage.elyoff.logic;

import java.util.Locale;

/**
 * 客户端"强制生效"策略（纯逻辑，可离线测试）。
 * <p>
 * 背景：ElyOff 有两条分支 —— 服务端权威分支与客户端本地分支。
 * 服务端分支要等一个完整 RTT 才生效（客户端按键 → 输入包上行 → 服务端 tick → 同步包下行），
 * 而客户端分支是<b>本地预测</b>，按键当 tick 就在你屏幕上生效，延迟为 0。
 * 所以延迟越高，越应该让客户端「抢跑」。
 * <p>
 * 但客户端抢跑也有代价：服务端仍按自己的节奏结算（掉落伤害、其它玩家看到的姿态等
 * 要等 RTT 后才对齐）。延迟很低时这点差异无感，不如干脆全交给服务端，让两端状态天然一致。
 * 这个枚举就是这把开关。
 * <ul>
 *     <li>{@link #ON} —— 总是强制客户端本地取消，即使服务端也装了 ElyOff。</li>
 *     <li>{@link #OFF} —— 不强制：服务端装了就让位（1.0.x 的原有行为）。</li>
 *     <li>{@link #SMART} —— 延迟高于阈值时按 {@link #ON}，否则按 {@link #OFF}。</li>
 * </ul>
 * 三种模式都只在"本地有滑翔状态"时才会动手；"服务端没装本模组"时客户端兜底始终工作，
 * 与这里的选择无关（否则功能就直接没了）。
 */
public enum ForceMode {

    ON,
    OFF,
    SMART;

    /** 默认策略：智能。 */
    public static final ForceMode DEFAULT = SMART;

    /** 容错解析；无法识别时回落到 {@link #DEFAULT}，绝不抛异常。 */
    public static ForceMode parse(String raw) {
        if (raw == null) {
            return DEFAULT;
        }
        String s = raw.trim();
        for (ForceMode mode : values()) {
            if (mode.name().equalsIgnoreCase(s)) {
                return mode;
            }
        }
        return DEFAULT;
    }

    /**
     * 在本模式下，给定延迟是否要求客户端「强行本地取消」。
     *
     * @param pingMs      玩家当前延迟（毫秒）
     * @param thresholdMs 智能模式的阈值（毫秒）
     */
    public boolean forcesClient(int pingMs, int thresholdMs) {
        return switch (this) {
            case ON -> true;
            case OFF -> false;
            case SMART -> pingMs > thresholdMs;
        };
    }

    /** 按键循环切换时的下一个模式。 */
    public ForceMode next() {
        ForceMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** 语言文件键，如 {@code elyoff.mode.smart}。 */
    public String translationKey() {
        return "elyoff.mode." + name().toLowerCase(Locale.ROOT);
    }
}
