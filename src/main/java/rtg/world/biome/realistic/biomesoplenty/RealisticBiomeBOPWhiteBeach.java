package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceCoastDunes;
import rtg.api.world.terrain.TerrainBase;


/**
 * BOP 的白沙滩（`biomesoplenty:white_beach`）。
 *
 * <h2>它用 RWG 的**暖海岸**（coastDunes），不是冷海岸（coastIce）</h2>
 *
 * RWG 自己的分派规则是
 * {@code ChunkManagerRealistic:590-593}：{@code continent < 24} 时
 * {@code baseBiome.temperature < 0.15f ? coastIce : coastDunes}。
 * BOP 的白沙滩是**热带白沙**（实测温度 1.00 / 雨量 0.95，见启动日志的
 * {@code [类] HOT white_beach temp=1.00 rain=0.95}）⇒ 按 RWG 的规则它落在 **coastDunes** 一侧。
 *
 * <p>⚠ 本类此前被接成 {@code terrainCoastIce + SurfaceGrassland(packed_ice×3, ice)} ——
 * 那是 `RealisticBiomeCoastIce` 的配方，于是**热带群系的地表是浮冰**（叠在"海滩进 CORE 池"
 * 这个适配之上，就会在内陆热带随机出现一片冰面）。已按 RWG 的规则改回 coastDunes。
 * `coastIce` 那一侧现在只剩 {@link rtg.world.biome.realistic.vanilla.RealisticBiomeVanillaColdBeach}
 * （`minecraft:cold_beach`，SNOWY 标签，温度 0.05）——那才是 RWG 的 `temp < 0.15f` 分支。
 */
public class RealisticBiomeBOPWhiteBeach extends RealisticBiomeBase {

    public RealisticBiomeBOPWhiteBeach(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public void initConfig() {

    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPWhiteBeach();
    }

    @Override
    public SurfaceBase initSurface() {
        // RWG `RealisticBiomeCoastDunes.rReplace:66-106`（暖海岸：沙/砂岩，陡坡圆石+石头）
        return new SurfaceCoastDunes(this.getConfig());
    }

    public static class TerrainBOPWhiteBeach extends TerrainBase {

        public TerrainBOPWhiteBeach() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 照抄 RWG `coast\RealisticBiomeCoastDunes.java:43-63`（暖海岸）
            return terrainCoastDunes(x, y, rtgWorld, river);
        }
    }
}
