package rtg;

import net.minecraftforge.fml.common.Loader;
import rtg.compat.ModCompat;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Collections;
import java.util.List;

public class LateMixin implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.rtg_late.json");
    }
    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        if ("mixins.rtg_late.json".equals(mixinConfig)) {
            return Loader.isModLoaded("biomesoplenty");
        }
        return true;
    }
}