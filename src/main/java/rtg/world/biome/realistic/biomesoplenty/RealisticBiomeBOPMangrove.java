package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceGrassland;
import rtg.api.world.terrain.TerrainBase;


/**
 * BOP 红树林。
 *
 * <p><b>装饰保持 BOP 自己的</b>：RWG 的 `SupportBOP.java` 给 BOP mangrove 用的是
 * `RealisticBiomeSupport(BOPCBiomes.mangrove, …, TerrainSwampRiver, …)`，而
 * `RealisticBiomeSupport.rDecorate` 在 {@code strength > 0.3} 时调用的正是
 * `customBiome.decorate(...)` —— 也就是 **BOP 自己的装饰器**。因此这里不覆盖装饰，
 * 也不抑制 BOP 的树生成。
 *
 * <p>RWG 自己的 {@code RealisticBiomeCoastMangrove}（2×圆石 + 5×红树 + 9×水草带）
 * 在 RWG 里没有对应的 BOP/原版外壳被使用；它的逐行移植
 * （{@code rtg.api.world.gen.feature.WorldGenMangrove}）已保留，待将来有
 * 对应群系外壳时再接。
 */
public class RealisticBiomeBOPMangrove extends RealisticBiomeBase {

    public RealisticBiomeBOPMangrove(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {

        // 有意留空：BOP 自己的 decorate 就是 RWG 的 Support 语义（见类注释）。
    }

    @Override
    public Biome preferredBeach() {
        return baseBiome();
    }

    @Override
    public void initConfig() {
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPMangrove();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock,
                Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    public static class TerrainBOPMangrove extends TerrainBase {

        public TerrainBOPMangrove() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java:532-537  mangrove -> TerrainSwampRiver()
            return terrainSwampRiver(x, y, rtgWorld, river);
        }
    }
}
