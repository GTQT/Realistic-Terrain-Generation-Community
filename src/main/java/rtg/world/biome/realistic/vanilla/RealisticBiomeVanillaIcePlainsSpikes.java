package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.deco.collection.DecoCollectionIceTrees;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeVanillaIcePlainsSpikes extends RealisticBiomeBase {

    public static Biome biome = Biomes.MUTATED_ICE_FLATS;
    public static Biome river = Biomes.FROZEN_RIVER;

    public RealisticBiomeVanillaIcePlainsSpikes() {

        super(biome, RiverType.FROZEN, BeachType.COLD);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_ICE_TREES).set(true);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaIcePlainsSpikes();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {

        if (this.getConfig().ALLOW_ICE_TREES.get()) {
            this.addDecoCollection(new DecoCollectionIceTrees(this.getConfig()));
        }
    }

    public static class TerrainVanillaIcePlainsSpikes extends TerrainBase {

        public TerrainVanillaIcePlainsSpikes() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG `Support.java:127-139` 的 **icePlainsSpikes 条目**：
            //     BiomeGenBase icePlainsSpikes = BiomeGenBase.getBiome(BiomeGenBase.icePlains.biomeID + 128);
            //     new RealisticBiomeSupport(icePlainsSpikes, RWGBiomes.baseRiverIce,
            //                               new TerrainHighland(0f, 140f, 68f, 200f),
            //                               new SurfaceGrassland(icePlainsSpikes.topBlock, icePlainsSpikes.fillerBlock,
            //                                                    Blocks.stone, Blocks.cobblestone))
            // 即 1.12 的 `mutated_ice_flats`（冰刺平原）；它的 SNOW/SMALL placement 也已照抄（见 RtgBiomeCategorizer）。
            //
            // ⚠ 此前接的是 `TerrainMountainSpikes()`（注释写"照抄 RWG SnowHills"）—— 那是 RWG
            // **另一个**群系类（`land/RealisticBiomeSnowHills`），不是 mutated ice flats 的条目。
            // 地表**不动**：现在的 `SurfaceGrassland(top, filler, stone, cobble)` 恰好与 RWG 给
            // icePlainsSpikes 的那份一致（SnowHills 那份是 `SurfaceMountainSnow`）。
            return terrainHighland(x, y, rtgWorld, river, 0f, 140f, 68f, 200f);
        }
    }

}
