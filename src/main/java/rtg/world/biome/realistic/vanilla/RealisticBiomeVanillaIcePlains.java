package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.util.Distribution;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoVariableSpruce;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGPiceaSitchensis;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainSnow;
import rtg.api.world.terrain.TerrainBase;


public class RealisticBiomeVanillaIcePlains extends RealisticBiomeBase {

    public static Biome biome = Biomes.ICE_PLAINS;
    public static Biome river = Biomes.FROZEN_RIVER;

    public RealisticBiomeVanillaIcePlains() {

        super(biome, RiverType.FROZEN, BeachType.COLD);
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().USE_ARCTIC_SURFACE).set(true);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        super.initConfig();
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaIcePlains();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainSnow(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, true, Blocks.SAND.getDefaultState(), 0.2f);
    }

    @Override
    public void initDecos() {

        // 移植上游新树系统（c6dd655 + d8d685b 收尾）：冰原的散生云杉。
        // 上游原文照抄，仅把上游的 useTreeManager() 换成本仓库的等价登记方式。
        this.useTreeManager();

        DecoVariableSpruce scatteredSpruce = new DecoVariableSpruce();
        scatteredSpruce.changeAverageHeightSqrt(-2.0f);
        scatteredSpruce.changeHeightNoiseVariability(-.75f);
        scatteredSpruce.changeLocalNoiseVariability(+0.5f);
        scatteredSpruce.setSmallTreeMinimumHeight(0);
        TreeRTG icePlainsTree = new TreeRTGPiceaSitchensis();
        icePlainsTree.setAbsoluteMinimumTrunk(0);
        scatteredSpruce.setSmallTree(icePlainsTree);
        scatteredSpruce.setDistribution(new Distribution(RTGWorld.getTreeFrequencyNoiseDivisor(),
                0.02f * this.getConfig().TREE_DENSITY_MULTIPLIER.get(),
                0.01f * this.getConfig().TREE_DENSITY_MULTIPLIER.get()));
        scatteredSpruce.setStrengthNoiseFactorForLoops(true);
        scatteredSpruce.setStrengthNoiseFactorXForLoops(false);
        this.treeGenerator = scatteredSpruce;
    }

    public static class TerrainVanillaIcePlains extends TerrainBase {

        public TerrainVanillaIcePlains() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 照抄 RWG land\RealisticBiomeTundraPlains.java -> TerrainFlatLakes()
            return terrainFlatLakes(x, y, rtgWorld, river);
        }
    }

}
