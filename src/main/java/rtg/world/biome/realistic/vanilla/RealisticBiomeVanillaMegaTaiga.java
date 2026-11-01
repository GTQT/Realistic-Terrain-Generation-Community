package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainSnow;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgRedwoodDecos;


/**
 * 原版巨型针叶林。装饰接 RWG {@code land/RealisticBiomeRedwood.rDecorate}
 * （巨型松树 + 小松/橡树/白桦 + 倒木/灌木/野麦/南瓜/花/草）。
 *
 * <p>推断说明：RWG 的 Redwood 实际是巨型松树，地形 `TerrainHilly(230,120,90)`；
 * rtgc 的 MegaTaiga 地形走的是 TaigaHills 的 `TerrainMountainRiver`，但“巨型针叶林”
 * 这一观感与 RWG Redwood 最接近，故装饰取它。
 */
public class RealisticBiomeVanillaMegaTaiga extends RealisticBiomeBase {

    public static Biome biome = Biomes.REDWOOD_TAIGA;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaMegaTaiga() {

        super(biome, BeachType.STONE);
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaMegaTaiga();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainSnow(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, true, Blocks.SAND.getDefaultState(), 0.2f);
    }

    @Override
    public void initDecos() {

        RtgRedwoodDecos.addRedwood(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainVanillaMegaTaiga extends TerrainBase {

        public TerrainVanillaMegaTaiga() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG TaigaHills -> TerrainMountainRiver()
            return terrainMountainRiver(x, y, rtgWorld, river);
        }
    }
}
