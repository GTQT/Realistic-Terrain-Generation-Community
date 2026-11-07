package rtg;

import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.List;

public class LateMixin implements ILateMixinLoader {

    /**
     * 注册 BOP 专用 mixin 配置。
     *
     * <p>原版 {@code World}/{@code Chunk} 的 mixin 必须走 {@link EarlyMixin}；late 阶段
     * 它们已经加载，再 prepare 会抛 {@code MixinTargetAlreadyLoadedException}。
     *
     * <p>{@code mixins.rtg_late.json} 直接引用 BOP 类型，未安装 BOP 时不能加载。
     */
    @Override
    public List<String> getMixinConfigs() {
        return java.util.Collections.singletonList("mixins.rtg_late.json");
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        if ("mixins.rtg_late.json".equals(mixinConfig)) {
            return Loader.isModLoaded("biomesoplenty");
        }
        return true;
    }
}