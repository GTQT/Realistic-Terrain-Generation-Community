package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.block.BOPBlocks;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoBoulder;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceMountainStone;


public class RealisticBiomeBOPCherryBlossomGrove extends RealisticBiomeBase {

    public RealisticBiomeBOPCherryBlossomGrove(final Biome biome) { super(biome); }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPCherryBlossomGrove(58f, 76f, 20f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, true, Blocks.SAND.getDefaultState(), 0.45f, 1.5f, 60f, 65f, 1.5f);
    }

    @Override
    public void initDecos() {

        DecoBoulder decoBoulder = new DecoBoulder();
        decoBoulder.setBoulderBlock(Blocks.COBBLESTONE.getDefaultState());
        decoBoulder.setChance(16);
        decoBoulder.setMaxY(95);
        this.addDeco(decoBoulder);

        DecoFallenTree decoFallenTree1 = new DecoFallenTree();
        decoFallenTree1.getDistribution().setNoiseDivisor(100f);
        decoFallenTree1.getDistribution().setNoiseFactor(6f);
        decoFallenTree1.getDistribution().setNoiseAddend(0.8f);
        decoFallenTree1.setLogConditionChance(16);
        decoFallenTree1.setMaxY(100);
        decoFallenTree1.setLogBlock(BOPBlocks.log_0.getStateFromMeta(5));
        decoFallenTree1.setLeavesBlock(Blocks.LEAVES.getDefaultState());
        decoFallenTree1.setMinSize(2);
        decoFallenTree1.setMaxSize(5);
        this.addDeco(decoFallenTree1, this.getConfig().ALLOW_LOGS.get());
    }

    public static class TerrainBOPCherryBlossomGrove extends TerrainBase {

        private float minHeight;
        private float maxHeight;
        private float hillStrength;

        public TerrainBOPCherryBlossomGrove(float minHeight, float maxHeight, float hillStrength) {

            this.minHeight = minHeight;
            this.maxHeight = (maxHeight > rollingHillsMaxHeight) ? rollingHillsMaxHeight : ((maxHeight < this.minHeight) ? rollingHillsMaxHeight : maxHeight);
            this.hillStrength = hillStrength;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java  cherryBlossomGrove -> TerrainHighland(6f, 120f, 65f, 200f)
            return terrainHighland(x, y, rtgWorld, river, 6f, 120f, 65f, 200f);
        }
    }

}
