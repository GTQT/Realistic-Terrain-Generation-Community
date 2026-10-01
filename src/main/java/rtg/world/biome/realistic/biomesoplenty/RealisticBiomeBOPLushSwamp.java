package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoBoulder;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import static rtg.api.world.deco.DecoFallenTree.LogCondition.RANDOM_CHANCE;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPLushSwamp extends RealisticBiomeBase {

    public RealisticBiomeBOPLushSwamp(final Biome biome) { super(biome); }

    @Override
    public Biome preferredBeach() {
        return baseBiome();
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPLushSwamp();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {

        DecoBoulder decoBoulder = new DecoBoulder();
        decoBoulder.setBoulderBlock(Blocks.MOSSY_COBBLESTONE.getDefaultState());
        decoBoulder.setMaxY(80);
        decoBoulder.setChance(16);
        decoBoulder.setStrengthFactor(1f);
        this.addDeco(decoBoulder);

        DecoFallenTree decoFallenTree = new DecoFallenTree();
        decoFallenTree.getDistribution().setNoiseDivisor(80f);
        decoFallenTree.getDistribution().setNoiseFactor(60f);
        decoFallenTree.getDistribution().setNoiseAddend(-15f);
        decoFallenTree.setLogCondition(RANDOM_CHANCE);
        decoFallenTree.setLogConditionChance(4);
        decoFallenTree.setLogBlock(Blocks.LOG.getDefaultState());
        decoFallenTree.setLeavesBlock(Blocks.LEAVES.getDefaultState());
        decoFallenTree.setMinSize(3);
        decoFallenTree.setMaxSize(4);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());
    }

    public static class TerrainBOPLushSwamp extends TerrainBase {

        public TerrainBOPLushSwamp() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java  lushSwamp -> TerrainSwampRiver()
            return terrainSwampRiver(x, y, rtgWorld, river);
        }
    }

}
