package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.WaterLevel;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeVanillaSunflowerPlains extends RealisticBiomeBase {

    public static Biome biome = Biomes.MUTATED_PLAINS;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaSunflowerPlains() {

        super(biome);
    }

    @Override
    public void initConfig() {
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaSunflowerPlains();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {
    }

    public static class TerrainVanillaSunflowerPlains extends TerrainBase {

        public TerrainVanillaSunflowerPlains() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 照抄 RWG `Support.java:140-152` 的 **sunflowerPlains 条目**：
            //     BiomeGenBase sunflowerPlains = BiomeGenBase.getBiome(BiomeGenBase.plains.biomeID + 128);
            //     new RealisticBiomeSupport(sunflowerPlains, RWGBiomes.baseRiverTemperate,
            //                               new TerrainMarsh(), new SurfaceGrassland(…))
            // 即 1.12 的 `mutated_plains`（向日葵平原）；它的 COLD/SMALL placement 也已照抄。
            //
            // ⚠ 此前写的是"**推断**：RWG 无 MC 平原对应物 ⇒ 用 TerrainGrasslandFlats" ——
            // **前提不成立**：RWG 用 `plains.biomeID + 128` 明确收了 mutated plains（`TerrainMarsh`）。
            // RWG 给向日葵平原配沼泽地形确实怪，但那是它的 active 配方，照抄口径下按它来。
            return terrainMarsh(x, y, rtgWorld, WaterLevel.current().waterSurfaceTop(), river);
        }
    }

}
