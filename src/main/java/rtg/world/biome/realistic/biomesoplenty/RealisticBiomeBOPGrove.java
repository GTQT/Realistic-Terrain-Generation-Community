package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.block.BlockPlanks.EnumType;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.util.BlockUtil;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.deco.DecoShrub;
import rtg.api.world.deco.helper.DecoHelper5050;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPGrove extends RealisticBiomeBase {

    public RealisticBiomeBOPGrove(final Biome biome) { super(biome); }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPGrove();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {

        DecoShrub decoShrubCustom = new DecoShrub();
        decoShrubCustom.setLogBlock(BlockUtil.getStateLog(EnumType.BIRCH));
        decoShrubCustom.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.BIRCH));
        decoShrubCustom.setMaxY(110);
        decoShrubCustom.setLoopMultiplier(1f);
        DecoShrub decoShrubCustom2 = new DecoShrub();
        decoShrubCustom2.setLogBlock(BlockUtil.getStateLog(EnumType.DARK_OAK));
        decoShrubCustom2.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.DARK_OAK));
        decoShrubCustom2.setMaxY(110);
        decoShrubCustom2.setLoopMultiplier(1f);
        DecoHelper5050 decoHelperHelper50502 = new DecoHelper5050(decoShrubCustom, decoShrubCustom2);
        this.addDeco(decoHelperHelper50502);

        DecoFallenTree decoFallenTree1 = new DecoFallenTree();
        decoFallenTree1.getDistribution().setNoiseDivisor(80f);
        decoFallenTree1.getDistribution().setNoiseFactor(60f);
        decoFallenTree1.getDistribution().setNoiseAddend(-15f);
        decoFallenTree1.setLogConditionChance(3);
        decoFallenTree1.setMaxY(100);
        decoFallenTree1.setLogBlock(BlockUtil.getStateLog(EnumType.BIRCH));
        decoFallenTree1.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.BIRCH));
        decoFallenTree1.setMinSize(3);
        decoFallenTree1.setMaxSize(6);

        DecoFallenTree decoFallenTree2 = new DecoFallenTree();
        decoFallenTree2.getDistribution().setNoiseDivisor(80f);
        decoFallenTree2.getDistribution().setNoiseFactor(60f);
        decoFallenTree2.getDistribution().setNoiseAddend(-15f);
        decoFallenTree2.setLogConditionChance(3);
        decoFallenTree2.setMaxY(100);
        decoFallenTree2.setLogBlock(BlockUtil.getStateLog(EnumType.DARK_OAK));
        decoFallenTree2.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.SPRUCE));
        decoFallenTree2.setMinSize(3);
        decoFallenTree2.setMaxSize(6);

        DecoHelper5050 decoHelperHelper5050 = new DecoHelper5050(decoFallenTree1, decoFallenTree2);
        this.addDeco(decoHelperHelper5050, this.getConfig().ALLOW_LOGS.get());
    }

    public static class TerrainBOPGrove extends TerrainBase {

        private float baseHeight = 64f;
        private float peakyHillWavelength = 40f;
        private float peakyHillStrength = 5f;
        private float smoothHillWavelength = 20f;
        private float smoothHillStrength = 10f;

        public TerrainBOPGrove() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java:385-390  grove -> TerrainHighland(0f, 140f, 68f, 200f, .3f)
            return terrainHighland(x, y, rtgWorld, river, 0f, 140f, 68f, 200f, 0.3f);
        }
    }

}
