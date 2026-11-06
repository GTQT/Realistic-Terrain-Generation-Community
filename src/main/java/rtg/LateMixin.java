package rtg;

import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.List;

public class LateMixin implements ILateMixinLoader {

    /**
     * 注册 BOP 专用 mixin 配置。
     *
     * <p>通用原版 mixin（{@code ChunkGetBiomeMixin}、profiler 的两个 mixin）必须走
     * {@link EarlyMixin} 的 early loader，因为 {@code World}/{@code Chunk} 在 late
     * 阶段已经加载，再 prepare 会抛 {@code MixinTargetAlreadyLoadedException}。
     *
     * <p>{@code mixins.rtg_late.json} 里的类直接引用 BOP 类型，BOP 没装时不能加载
     * （否则 {@code @Mixin(biomesoplenty.…)} 解析不到类会崩）。
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