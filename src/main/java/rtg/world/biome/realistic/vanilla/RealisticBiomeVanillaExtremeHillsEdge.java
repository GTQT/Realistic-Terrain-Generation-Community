package rtg.world.biome.realistic.vanilla;

import net.minecraft.block.BlockPlanks.EnumType;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.util.BlockUtil;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.*;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGPinusNigra;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceMountainSnow;


public class RealisticBiomeVanillaExtremeHillsEdge extends RealisticBiomeBase {

    public static Biome biome = Biomes.EXTREME_HILLS_EDGE;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaExtremeHillsEdge() {

        super(biome, BeachType.STONE);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_FILLER_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaExtremeHillsEdge();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainSnow(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, true, Blocks.SAND.getDefaultState(), 0.2f);
    }

    @Override
    public void initDecos() {

        TreeRTG nigraTree = new TreeRTGPinusNigra();
        nigraTree.setLogBlock(Blocks.LOG.getDefaultState());
        nigraTree.setLeavesBlock(Blocks.LEAVES.getDefaultState());
        nigraTree.setMinTrunkSize(18);
        nigraTree.setMaxTrunkSize(27);
        nigraTree.setMinCrownSize(7);
        nigraTree.setMaxCrownSize(10);
        this.addTree(nigraTree);

        DecoTree decoTrees = new DecoTree(nigraTree);
        decoTrees.setStrengthFactorForLoops(4f);
        decoTrees.setStrengthNoiseFactorXForLoops(true);
        decoTrees.getDistribution().setNoiseDivisor(100f);
        decoTrees.getDistribution().setNoiseFactor(6f);
        decoTrees.getDistribution().setNoiseAddend(0.8f);
        decoTrees.setTreeType(DecoTree.TreeType.RTG_TREE);
        decoTrees.setTreeCondition(DecoTree.TreeCondition.RANDOM_CHANCE);
        decoTrees.setTreeConditionChance(24);
        decoTrees.setMaxY(100);
        this.addDeco(decoTrees);

        DecoFallenTree decoFallenTree = new DecoFallenTree();
        decoFallenTree.getDistribution().setNoiseDivisor(100f);
        decoFallenTree.getDistribution().setNoiseFactor(6f);
        decoFallenTree.getDistribution().setNoiseAddend(0.8f);
        decoFallenTree.setLogConditionChance(6);
        decoFallenTree.setLogBlock(BlockUtil.getStateLog(EnumType.SPRUCE));
        decoFallenTree.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.SPRUCE));
        decoFallenTree.setMinSize(3);
        decoFallenTree.setMaxSize(6);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());

        DecoShrub decoShrub = new DecoShrub();
        decoShrub.setMaxY(100);
        decoShrub.setLoopMultiplier(1f);
        this.addDeco(decoShrub);

        DecoBoulder decoBoulder = new DecoBoulder();
        decoBoulder.setBoulderBlock(Blocks.MOSSY_COBBLESTONE.getDefaultState());
        decoBoulder.setChance(12);
        decoBoulder.setMaxY(95);
        decoBoulder.setStrengthFactor(2f);
        this.addDeco(decoBoulder);

        DecoMushrooms decoMushrooms = new DecoMushrooms();
        decoMushrooms.setMaxY(90);
        decoMushrooms.setRandomFloat(3f);
        this.addDeco(decoMushrooms);

        DecoPumpkin decoPumpkin = new DecoPumpkin();
        decoPumpkin.setMaxY(90);
        decoPumpkin.setRandomFloat(20f);
        this.addDeco(decoPumpkin);
    }

    /**
     * RWG-style mountain river — gentler lower slopes for the transition zone
     * between full mountains and plains. Perfect for Extreme Hills Edge.
     */
    public static class TerrainVanillaExtremeHillsEdge extends TerrainBase {

        public TerrainVanillaExtremeHillsEdge() {
            base = 67f;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            return terrainMountainRiver(x, y, rtgWorld, river);
        }
    }

}
