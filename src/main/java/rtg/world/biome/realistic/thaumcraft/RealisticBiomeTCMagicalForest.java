package rtg.world.biome.realistic.thaumcraft;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceGrassland;

public class RealisticBiomeTCMagicalForest extends RealisticBiomeBase
{
    public RealisticBiomeTCMagicalForest(Biome biome) {
        super(biome);
    }

    @Override
    public void initConfig() {

    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainTCMagicalForest();
    }

    public static final class TerrainTCMagicalForest extends TerrainBase {

        private TerrainTCMagicalForest() { }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG `SupportTC.java:38-50`（"Magical Forest" 分支的第一条，SNOW/SMALL_ISLAND）：
            //     new RealisticBiomeSupport(b[i], RWGBiomes.baseRiverCold,
            //                               new TerrainSwampMountain(135f, 300f),
            //                               new SurfaceGrassland(b[i].topBlock, b[i].fillerBlock,
            //                                                    Blocks.stone, Blocks.cobblestone)),
            //             BiomeCategory.SNOW, BiomePlacement.SMALL_ISLAND);
            //（RWG 还有第二条 COLD/SMALL_ISLAND，配方完全相同；RtgBiomeCategorizer 的表一名一行，
            //  取第一条 SNOW —— 与其它多条目群系同一约定。）
            //
            // ⚠ 此前接的是 `terrainSmallSupport(...)`：那是 RWG 给 **Tainted Land** 的配方
            //（`SupportTC.java:24-36`），不是 Magical Forest 的。
            return terrainSwampMountain(x, y, rtgWorld, river, 135f, 300f);
        }
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }


    @Override
    public void initDecos() {
    }
}
