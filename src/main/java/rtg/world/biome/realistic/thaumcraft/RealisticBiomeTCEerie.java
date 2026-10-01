package rtg.world.biome.realistic.thaumcraft;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceGrassland;

public class RealisticBiomeTCEerie extends RealisticBiomeBase
{
    public RealisticBiomeTCEerie(Biome biome) {
        super(biome);
    }

    @Override public void initConfig() {

    }

    @Override public TerrainBase initTerrain() {
        return new TerrainTCEerie();
    }

    public static final class TerrainTCEerie extends TerrainBase {

        private TerrainTCEerie() { }
        @Override public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // ⚠ **推断，不是照抄**：RWG `SupportTC.java` 里**没有** Eerie 的条目 —— 它只包了
            // `"Tainted Land"`（:24-36，`TerrainSmallSupport` + `SurfaceGrassland`，SMALL）与
            // `"Magical Forest"`。rtgc 没有 Tainted Land 的包装类，Eerie 借的是**前者的配方**。
            // 也就是说 RWG 的 Tainted Land 配对在 rtgc 里整体缺失，这一条只是"同模组的最近亲"。
            return terrainSmallSupport(x, y, rtgWorld, river);
        }
    }

    @Override public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override public void initDecos() {
    }
}
