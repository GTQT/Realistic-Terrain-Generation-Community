package rtg.api.world.deco;

import rtg.RTGConfig;
import rtg.api.util.Distribution;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.IRealisticBiome;

public abstract class AbstractDecoTree extends DecoBase {
	
    protected Distribution distribution = new Distribution(RTGWorld.getTreeFrequencyNoiseDivisor(), 2.5f, 4.5f); 
    // Parameter object for noise calculations.

	public AbstractDecoTree() {
	}

    protected int applyConfigMultipliers(final int loopCount, final IRealisticBiome biome) {
        // 乘上 rtgc 的全局降密系数（见 DecoTree.TREE_DENSITY_REDUCTION 的说明）。
        // DecoTree 覆盖了本方法，这里的是给其它 AbstractDecoTree 子类用的兜底路径。
        return (int)(loopCount * DecoTree.TREE_DENSITY_REDUCTION
                * RTGConfig.treeDensityMultiplier() * biome.getConfig().TREE_DENSITY_MULTIPLIER.get());
    }

    public Distribution getDistribution() {

        return distribution;
    }

    public AbstractDecoTree setDistribution(Distribution distribution) {

        this.distribution = distribution;
        return this;
    }
}