package io.github.unperage.elyoff.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.unperage.elyoff.config.ElyOffConfigScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * ModMenu 集成：在模组列表里给 ElyOff 挂一个「设置」按钮。
 * <p>
 * ModMenu 是<b>可选</b>依赖 —— 它只在客户端装了 ModMenu 时才会加载这个入口点，
 * 所以没装 ModMenu 的玩家（以及专用服务端）完全不受影响。
 */
@Environment(EnvType.CLIENT)
public class ElyOffModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ElyOffConfigScreen(parent);
    }
}
