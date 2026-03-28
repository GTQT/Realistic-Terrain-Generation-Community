package rtg.api.world.deco;

import java.util.Random;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import rtg.api.util.BlockUtil;
import rtg.api.util.ChunkInfo;
import rtg.api.util.BlockUtil.MatchType;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.IRealisticBiome;
import rtg.api.util.Distribution;
import rtg.api.world.gen.feature.WorldGenLog;
import rtg.api.world.gen.feature.tree.rtg.TreeMaterials;

public class DecoVariableFallenTree extends DecoBase {
	/**
	 * @author WhichOnesPink
	 * @modifier Zeno410
	 */

    private int loops;
    private Distribution distribution; // Parameter object for noise calculations.
    private int maxY; // Height restriction.
    private int minSize; // Min log height (only used with certain log presets)
    private int maxSize; // Max log height (only used with certain log presets)
	private TreeMaterials.Picker materialsPicker = new TreeMaterials.Picker();
	private final Woodland woodland;

    public DecoVariableFallenTree(Woodland woodland) {

        super();

        /**
         * Default values.
         * These can be overridden when configuring the Deco object in the realistic biome.
         */
        this.setLoops(1);
        this.setDistribution(new Distribution(100f, 5f, 0.8f));
        this.setMaxY(80);
        this.setMinSize(2);
        this.setMaxSize(4);
        this.woodland = woodland;
    }

    @Override
    public void generate(final IRealisticBiome biome, final RTGWorld rtgWorld, final Random rand, final ChunkPos chunkPos, final float river, final boolean hasVillage, ChunkInfo chunkInot) {
        BlockPos offsetpos = getOffsetPos(chunkPos);
        float noise = distribution.getValue(offsetpos, rtgWorld.treeDistributionNoise());
        noise = (noise + 1f) / 2f;
        noise = Math.min(1f, noise * biome.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER.get());

        final int finalSize = (this.maxSize > this.minSize) ? getRangedRandom(rand, this.minSize, this.maxSize) : (this.maxSize == this.minSize) ? this.minSize : 4;

        if (rand.nextFloat() < noise) {
            BlockPos pos = offsetpos.add(rand.nextInt(16), 0, rand.nextInt(16));
            pos = pos.up(rtgWorld.world().getHeight(pos).getY());

            if (pos.getY() <= this.maxY) {
                if (hasVillage) {
                    if (!BlockUtil.checkAreaBlocks(MatchType.ALL_IGNORE_REPLACEABLE, rtgWorld.world(), pos, finalSize)) {
                        return;
                    }
                }
                TreeMaterials materials = materialFor(pos, rtgWorld, rand);
                new WorldGenLog(materials.log, materials.leaves, finalSize)
                        .generate(rtgWorld.world(), rand, pos);
            }
        }
    }


    public int getLoops() {

        return loops;
    }

    public DecoVariableFallenTree setLoops(int loops) {

        this.loops = loops;
        return this;
    }

    public Distribution getDistribution() {

        return distribution;
    }

    public DecoVariableFallenTree setDistribution(Distribution distribution) {

        this.distribution = distribution;
        return this;
    }

    public int getMaxY() {

        return maxY;
    }

    public DecoVariableFallenTree setMaxY(int maxY) {

        this.maxY = maxY;
        return this;
    }


    public int getMinSize() {

        return minSize;
    }

    public DecoVariableFallenTree setMinSize(int minSize) {

        this.minSize = minSize;
        return this;
    }

    public int getMaxSize() {

        return maxSize;
    }

    public DecoVariableFallenTree setMaxSize(int maxSize) {

        this.maxSize = maxSize;
        return this;
    }
    
    private TreeMaterials materialFor(BlockPos pos, RTGWorld world, Random rand) {
    	float divisor = RTGWorld.getTreeMaterialsNoiseDivisor();
    	float rawNoise = world.treeMaterialsNoise()
    			.noise2f(((float)pos.getX())/divisor, (float)pos.getZ()/divisor);
    	// adjust noise for woodland type
    	switch(woodland) {
    	case SPRUCE:
    		return this.materialsPicker.forNoise(rawNoise*-1f -2.0f + rand.nextFloat()*2.5f -1.25f);
    	case OAK:
    		return this.materialsPicker.forNoise(rawNoise + rand.nextFloat()*1.4f -0.7f);
    	case BIRCH:
    		return this.materialsPicker.forNoise(rawNoise*-1f +2.0f + rand.nextFloat()*2.5f -1.25f);
    		
    	}
    	throw new RuntimeException();
    	
    }
    
    public enum Woodland {
        SPRUCE,
        OAK,
        BIRCH
    }
    
}

