package io.github.unperage.elyoff.config;

import io.github.unperage.elyoff.logic.ForceMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * ElyOff 的图形设置界面（由 ModMenu 打开）。
 * <p>
 * 只放两个可调项，对应 {@code config/elyoff-client.json}：
 * <ul>
 *     <li><b>强制客户端生效</b>：开 / 关 / 智能，点一下循环一次。</li>
 *     <li><b>智能模式阈值</b>：0~500ms 的滑块，只在「智能」下起作用。</li>
 * </ul>
 * 模式改动会立即落盘（和游戏内按键切换一致）；滑块拖动期间只更新内存，
 * 松手/关闭界面时才写文件，免得拖动时反复写盘。
 */
@Environment(EnvType.CLIENT)
public class ElyOffConfigScreen extends Screen {

    /** 滑块上限（毫秒）。 */
    private static final int MAX_THRESHOLD_MS = 500;

    private final Screen parent;

    /** 滑块拖动期间的暂存值；关闭界面时才写回配置。 */
    private int pendingThresholdMs;

    public ElyOffConfigScreen(Screen parent) {
        super(Component.translatable("elyoff.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ElyOffClientConfig config = ElyOffClientConfig.get();
        this.pendingThresholdMs = config.smartPingThresholdMs();

        int width = 220;
        int x = this.width / 2 - width / 2;
        int y = this.height / 6;

        // 强制客户端生效：开 / 关 / 智能
        addRenderableWidget(Button.builder(forceModeLabel(config.mode()), button -> {
            config.setMode(config.mode().next());
            button.setMessage(forceModeLabel(config.mode()));
        }).bounds(x, y, width, 20).build());

        y += 24;

        // 智能模式阈值
        addRenderableWidget(new ThresholdSlider(x, y, width, 20));

        // 完成
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(x, this.height - 30, width, 20).build());
    }

    @Override
    public void onClose() {
        ElyOffClientConfig.get().setSmartPingThresholdMs(pendingThresholdMs);
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    private static Component forceModeLabel(ForceMode mode) {
        return Component.translatable("elyoff.config.force_mode",
                Component.translatable(mode.translationKey()));
    }

    /** 0~500ms 的滑块；value 是 0~1 的归一化值。 */
    private final class ThresholdSlider extends AbstractSliderButton {

        ThresholdSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(),
                    ElyOffConfigScreen.this.pendingThresholdMs / (double) MAX_THRESHOLD_MS);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("elyoff.config.smart_threshold",
                    ElyOffConfigScreen.this.pendingThresholdMs));
        }

        @Override
        protected void applyValue() {
            ElyOffConfigScreen.this.pendingThresholdMs =
                    (int) Math.round(this.value * MAX_THRESHOLD_MS);
        }
    }
}
