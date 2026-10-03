package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.WaterLevel;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoFlowersRTG;
import rtg.api.world.deco.DecoShrub;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceGrassland;
import rtg.api.world.terrain.TerrainBase;

import static net.minecraft.block.BlockDoublePlant.EnumPlantType.*;
import static net.minecraft.block.BlockFlower.EnumFlowerType.*;


public class RealisticBiomeBOPFlowerField extends RealisticBiomeBase {

    public RealisticBiomeBOPFlowerField(final Biome biome) { super(biome); }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_VILLAGES.set(true);
        this.getConfig().addProperty(this.getConfig().ALLOW_PONDS_WATER).set(true);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPFlowerField();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {

        // First, let's get a few shrubs in to break things up a bit.
        DecoShrub decoShrub = new DecoShrub();
        decoShrub.setMaxY(110);
        decoShrub.setLoopMultiplier(3f);
        decoShrub.setChance(3);
        this.addDeco(decoShrub);

        // Flowers are the most aesthetically important feature of this biome, so let's add those next.
        DecoFlowersRTG decoFlowers1 = new DecoFlowersRTG()
            .addFlowers(POPPY, BLUE_ORCHID, ALLIUM, HOUSTONIA, RED_TULIP, ORANGE_TULIP, WHITE_TULIP, PINK_TULIP, OXEYE_DAISY, DANDELION)
            .setStrengthFactor(12f)
            .setHeightType(DecoFlowersRTG.HeightType.GET_HEIGHT_VALUE); // We're only bothered about surface flowers here.
        this.addDeco(decoFlowers1);

        DecoFlowersRTG decoFlowers2 = new DecoFlowersRTG();
        decoFlowers2.addPlants(SUNFLOWER, SYRINGA, ROSE, PAEONIA); //Only 2-block-tall flowers.
        decoFlowers2.setChance(8);
        decoFlowers2.setHeightType(DecoFlowersRTG.HeightType.GET_HEIGHT_VALUE); // We're only bothered about surface flowers here.
        this.addDeco(decoFlowers2);
    }

    @Override
    public void overrideDecorations() {
        baseBiome().decorator.flowersPerChunk = -999;
    }

    public static class TerrainBOPFlowerField extends TerrainBase {

        public TerrainBOPFlowerField() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java:307-311  flowerField -> TerrainMarsh()
            return terrainMarsh(x, y, rtgWorld, WaterLevel.current().waterSurfaceTop(), river);
        }
    }

}
