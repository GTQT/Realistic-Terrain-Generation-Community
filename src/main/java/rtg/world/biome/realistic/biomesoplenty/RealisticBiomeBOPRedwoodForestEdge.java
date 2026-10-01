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

import static rtg.api.world.deco.DecoFallenTree.LogCondition.RANDOM_CHANCE;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPRedwoodForestEdge extends RealisticBiomeBase {

    public RealisticBiomeBOPRedwoodForestEdge(final Biome biome) { super(biome); }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPRedwoodForest();
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
        decoFallenTree.setLogConditionChance(12);
        decoFallenTree.setLogBlock(BOPBlocks.log_2.getStateFromMeta(4));
        decoFallenTree.setLeavesBlock(Blocks.LEAVES.getDefaultState());
        decoFallenTree.setMinSize(3);
        decoFallenTree.setMaxSize(9);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());
    }

    /**
     * **推断（同族）**：RWG 没有 {@code redwoodForestEdge}。
     * 同族的 {@code RealisticBiomeBOPRedwoodForest} 用的是
     * {@code TerrainGrasslandHills(80f, 180f, 13f, 100f, 38f, 260f, 71f)}
     *（出自 RWG {@code SupportBOP} 的 redwood 条目），本群系取同值。
     */
    public static class TerrainBOPRedwoodForest extends TerrainBase {

        public TerrainBOPRedwoodForest() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            return terrainGrasslandHills(x, y, rtgWorld, river, 80f, 180f, 13f, 100f, 38f, 260f, 71f);
        }
    }

}

