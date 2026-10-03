package rtg;

import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.List;

public class LateMixin implements ILateMixinLoader {

    /**
     * 注册两份 mixin 配置。
     *
     * <p><b>为什么必须是这两个文件、而不是 {@code mixins.rtgc.json}</b>：
     * jar 的 MANIFEST 里**没有** {@code MixinConfigs} 属性（RFG 的 {@code mixin_configs}
     * 只生成 json，不写 MANIFEST），所以 {@code mixins.rtgc.json} **从不被加载** ——
     * 实测日志里它一次都没出现过。而 IDE 里直接 Run Client 时用的是 classes 目录、连 jar 的
     * MANIFEST 都不参与，靠 MANIFEST 注册就更不可能生效。
     * 唯一被证实会加载的路径就是这里：MixinBooter 的 late loader
     * （日志：{@code Loading late loader [rtg.LateMixin]} → {@code Adding [… mixin configuration]}）。
     *
     * <ul>
     *   <li>{@code mixins.rtg_core.json} —— 打原版类的通用 mixin（{@code Chunk.getBiome}），
     *       必须**无条件**加载；</li>
     *   <li>{@code mixins.rtg_late.json} —— BOP 专用，BOP 没装时不能加载（否则
     *       {@code @Mixin(biomesoplenty.…)} 解析不到类会崩）。</li>
     * </ul>
     */
    @Override
    public List<String> getMixinConfigs() {
        return java.util.Arrays.asList("mixins.rtg_core.json", "mixins.rtg_late.json");
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        if ("mixins.rtg_late.json".equals(mixinConfig)) {
            return Loader.isModLoaded("biomesoplenty");
        }
        return true;
    }
}