package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.block.BOPBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoBoulder;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.deco.DecoJungleCacti;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceGrassland;
import rtg.api.world.terrain.TerrainBase;


public class RealisticBiomeBOPLushDesert extends RealisticBiomeBase {

    public RealisticBiomeBOPLushDesert(final Biome biome) { super(biome); }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_VILLAGES.set(true);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().ALLOW_CACTUS).set(true);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPLushDesert();
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
        decoFallenTree.setLogConditionChance(12);
        decoFallenTree.setRandomLogBlocks(new IBlockState[]{Blocks.LOG2.getStateFromMeta(1), BOPBlocks.log_3.getStateFromMeta(2), Blocks.LOG.getDefaultState()});
        decoFallenTree.setMinSize(3);
        decoFallenTree.setMaxSize(5);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());

        DecoJungleCacti decoJungleCacti = new DecoJungleCacti();
        decoJungleCacti.setStrengthFactor(8f);
        decoJungleCacti.setMaxY(110);
        this.addDeco(decoJungleCacti, this.getConfig().ALLOW_CACTUS.get());
    }

    public static class TerrainBOPLushDesert extends TerrainBase {

        public TerrainBOPLushDesert() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java  lushDesert -> TerrainGrasslandHills(90f,180f,13f,100f,38f,260f,71f)
            return terrainGrasslandHills(x, y, rtgWorld, river, 90f, 180f, 13f, 100f, 38f, 260f, 71f);
        }
    }

}
