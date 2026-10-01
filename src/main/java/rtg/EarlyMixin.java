package rtg;

import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@IFMLLoadingPlugin.Name(RTG.MODID)
@IFMLLoadingPlugin.MCVersion(ForgeVersion.mcVersion)
public class EarlyMixin implements IEarlyMixinLoader, IFMLLoadingPlugin {

    /**
     * 主 mixin 配置。
     *
     * <p><b>⚠ 必须在这里登记</b>：jar 的 MANIFEST 里**没有** {@code MixinConfigs} 属性
     * （RFG 的 {@code mixin_configs} 只生成 json 文件，不写 MANIFEST），所以
     * {@code mixins.rtgc.json} 不会自动加载。此前本方法返回 {@code null}，
     * 于是 {@code rtg.mixins.core.ChunkGetBiomeMixin} **从未生效** ——
     * 表现为 F3 在被回填后的那一瞬是对的、其余时间又变回 REID 的占位值。
     *
     * <p>走 MixinBooter 的 early loader 是可靠路径（日志里 {@code mixins.rtg_late.json}
     * 就是这样被 {@code Adding […] mixin configuration} 加载的），early 阶段早于原版类被使用，
     * 且 refmap（{@code mixins.rtgc.refmap.json}）已随 jar 打包。
     */
    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.rtgc.json");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}