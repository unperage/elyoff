package io.github.unperage.elyoff.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.unperage.elyoff.logic.ForceMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端配置：{@code config/elyoff-client.json}
 * <p>
 * 只有一个玩家可调项 —— 「强制客户端生效」策略（{@link ForceMode}），
 * 外加智能模式的延迟阈值。
 * <p>
 * 读写全部做了兜底：任何异常都只记日志，绝不因为配置文件的问题把游戏搞崩
 * （这条教训来自 quickshulker 3.2.9：它的配置序列化需要客户端专属类，
 * 在专用服务端上直接把启动干掉了）。这里只用 Gson + 基本类型，两边都能跑。
 */
@Environment(EnvType.CLIENT)
public final class ElyOffClientConfig {

    /** 智能模式的默认阈值：延迟高于它就强制客户端本地生效。 */
    public static final int DEFAULT_SMART_PING_MS = 50;

    private static final String FILE_NAME = "elyoff-client.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ElyOffClientConfig instance;

    /** 序列化用的原始字符串；对外统一走 {@link #mode()}。 */
    private String forceMode = ForceMode.DEFAULT.name();

    private int smartPingThresholdMs = DEFAULT_SMART_PING_MS;

    private ElyOffClientConfig() {
    }

    /** 取全局实例；首次调用时从磁盘读取（失败则用默认值）。 */
    public static ElyOffClientConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public ForceMode mode() {
        return ForceMode.parse(forceMode);
    }

    public int smartPingThresholdMs() {
        if (smartPingThresholdMs < 0) {
            return DEFAULT_SMART_PING_MS;
        }
        return smartPingThresholdMs;
    }

    /** 设置策略并立刻落盘。 */
    public void setMode(ForceMode mode) {
        this.forceMode = (mode == null ? ForceMode.DEFAULT : mode).name();
        save();
    }

    public void setSmartPingThresholdMs(int ms) {
        this.smartPingThresholdMs = Math.max(0, ms);
        save();
    }

    // ---------------------------------------------------------------- 磁盘

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static ElyOffClientConfig load() {
        ElyOffClientConfig cfg = new ElyOffClientConfig();
        Path p = path();
        try {
            if (!Files.exists(p)) {
                cfg.save();
                return cfg;
            }
            String raw = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            if (obj.has("forceMode")) {
                cfg.forceMode = obj.get("forceMode").getAsString();
            }
            if (obj.has("smartPingThresholdMs")) {
                cfg.smartPingThresholdMs = obj.get("smartPingThresholdMs").getAsInt();
            }
        } catch (Throwable t) {
            System.err.println("[ElyOff] 读取 " + p + " 失败，使用默认配置: " + t);
        }
        return cfg;
    }

    private void save() {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("forceMode", forceMode);
            obj.addProperty("smartPingThresholdMs", smartPingThresholdMs);
            Files.writeString(p, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            System.err.println("[ElyOff] 写入 " + p + " 失败: " + e);
        }
    }
}
