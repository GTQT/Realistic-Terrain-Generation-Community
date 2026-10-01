package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.biome.BOPBiomes;

import net.minecraft.block.BlockPlanks.EnumType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.util.BlockUtil;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoBoulder;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import static rtg.api.world.deco.DecoFallenTree.LogCondition.RANDOM_CHANCE;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPSeasonalForest extends RealisticBiomeBase {

    public RealisticBiomeBOPSeasonalForest(final Biome biome) { super(biome); }

    @Override
    public Biome preferredBeach() {
        return BOPBiomes.gravel_beach.orNull();
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPSeasonalForest(15f, 80f, 68f, 170f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {

        DecoBoulder decoBoulder = new DecoBoulder();
        decoBoulder.setBoulderBlock(Blocks.COBBLESTONE.getDefaultState());
        decoBoulder.setMaxY(80);
        decoBoulder.setChance(16);
        decoBoulder.setStrengthFactor(1f);
        this.addDeco(decoBoulder);

        DecoFallenTree decoFallenTree = new DecoFallenTree();
        decoFallenTree.getDistribution().setNoiseDivisor(80f);
        decoFallenTree.getDistribution().setNoiseFactor(60f);
        decoFallenTree.getDistribution().setNoiseAddend(-15f);
        decoFallenTree.setLogCondition(RANDOM_CHANCE);
        decoFallenTree.setLogConditionChance(6);
        decoFallenTree.setRandomLogBlocks(new IBlockState[]{BlockUtil.getStateLog(EnumType.DARK_OAK), Blocks.LOG.getDefaultState(), BlockUtil.getStateLog(EnumType.BIRCH)});
        decoFallenTree.setMinSize(3);
        decoFallenTree.setMaxSize(4);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());
    }

    public static class TerrainBOPSeasonalForest extends TerrainBase {

        private float start;
        private float height;
        private float base;
        private float width;

        public TerrainBOPSeasonalForest(float hillStart, float landHeight, float baseHeight, float hillWidth) {

            start = hillStart;
            height = landHeight;
            base = baseHeight;
            width = hillWidth;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java  seasonalForest -> TerrainHighland(0f, 140f, 68f, 200f)  [由偏离版 terrainHighlandLegacy 拨回忠实版]
            return terrainHighland(x, y, rtgWorld, river, 0f, 140f, 68f, 200f);
        }
    }

}
