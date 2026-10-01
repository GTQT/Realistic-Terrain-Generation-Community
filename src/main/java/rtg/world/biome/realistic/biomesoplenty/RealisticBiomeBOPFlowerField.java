package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.WaterLevel;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoFlowersRTG;
import rtg.api.world.deco.DecoShrub;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import static net.minecraft.block.BlockDoublePlant.EnumPlantType.PAEONIA;
import static net.minecraft.block.BlockDoublePlant.EnumPlantType.ROSE;
import static net.minecraft.block.BlockDoublePlant.EnumPlantType.SUNFLOWER;
import static net.minecraft.block.BlockDoublePlant.EnumPlantType.SYRINGA;
import static net.minecraft.block.BlockFlower.EnumFlowerType.ALLIUM;
import static net.minecraft.block.BlockFlower.EnumFlowerType.BLUE_ORCHID;
import static net.minecraft.block.BlockFlower.EnumFlowerType.DANDELION;
import static net.minecraft.block.BlockFlower.EnumFlowerType.HOUSTONIA;
import static net.minecraft.block.BlockFlower.EnumFlowerType.ORANGE_TULIP;
import static net.minecraft.block.BlockFlower.EnumFlowerType.OXEYE_DAISY;
import static net.minecraft.block.BlockFlower.EnumFlowerType.PINK_TULIP;
import static net.minecraft.block.BlockFlower.EnumFlowerType.POPPY;
import static net.minecraft.block.BlockFlower.EnumFlowerType.RED_TULIP;
import static net.minecraft.block.BlockFlower.EnumFlowerType.WHITE_TULIP;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPFlowerField extends RealisticBiomeBase {

    public RealisticBiomeBOPFlowerField(final Biome biome) { super(biome); }

    @Override
    public void initConfig() {
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
