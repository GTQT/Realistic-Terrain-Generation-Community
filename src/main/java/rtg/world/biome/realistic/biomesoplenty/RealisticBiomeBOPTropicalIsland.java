package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.biome.BOPBiomes;
import biomesoplenty.api.block.BOPBlocks;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.realistic.vanilla.RealisticBiomeVanillaExtremeHills;

import static rtg.api.world.deco.DecoFallenTree.LogCondition.RANDOM_CHANCE;
import rtg.api.world.surface.SurfaceIslandMountainStone;


public class RealisticBiomeBOPTropicalIsland extends RealisticBiomeBase {

    public RealisticBiomeBOPTropicalIsland(final Biome biome) { super(biome); }

    @Override
    public Biome preferredBeach() {

        return BOPBiomes.white_beach.orNull();
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPTropicalIsland();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceIslandMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, 67, Blocks.SAND.getDefaultState(), 0f);
    }

    @Override
    public void initDecos() {

        DecoFallenTree decoFallenTree = new DecoFallenTree();
        decoFallenTree.getDistribution().setNoiseDivisor(80f);
        decoFallenTree.getDistribution().setNoiseFactor(60f);
        decoFallenTree.getDistribution().setNoiseAddend(-15f);
        decoFallenTree.setLogCondition(RANDOM_CHANCE);
        decoFallenTree.setLogConditionChance(12);
        decoFallenTree.setLogBlock(BOPBlocks.log_1.getStateFromMeta(7));
        decoFallenTree.setLeavesBlock(Blocks.LEAVES.getDefaultState());
        decoFallenTree.setMinSize(3);
        decoFallenTree.setMaxSize(4);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());
    }

    /**
     * 依据：RWG {@code ocean\RealisticBiomeIslandTropical.java} 的 {@code rNoise}
     *（它没有 terrain 字段，而是直接覆写 rNoise）—— rtgc 的 {@code TerrainBase.terrainIslandTropical}
     * 就是它的逐行移植。
     * <p>
     * 本类原先是一个**从未被实例化的死类** {@code TerrainVanillaExtremeHillsPlus}
     *（同名真身在 {@code RealisticBiomeVanillaExtremeHillsPlus.java}，由那个群系自己使用）；
     * 而 {@code initTerrain()} 实际返回的是 {@code RealisticBiomeVanillaExtremeHills.GrandMountain}。
     * 两者都已按 RWG 拨正。
     */
    public static class TerrainBOPTropicalIsland extends TerrainBase {

        public TerrainBOPTropicalIsland() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            return terrainIslandTropical(x, y, rtgWorld, border);
        }
    }

}
