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
     * 早期 mixin 配置。
     *
     * <p>{@code World}/{@code Chunk} 在 FML 构造 mod 之前就已加载，相关 mixin 必须走
     * early loader；放进 late 配置会抛 {@code MixinTargetAlreadyLoadedException}。
     * 因此本类由 {@code FMLCorePlugin} 注册为 coremod，并在 early 阶段登记
     * {@code mixins.rtg_core.json}。
     *
     * <p>{@code mixins.rtgc.json} 只是 RFG 生成的占位文件，不会被 MANIFEST 加载。
     */
    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.rtg_core.json");
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