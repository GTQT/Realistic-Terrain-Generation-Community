package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.deco.DecoBoulder;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceCoastIce;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;


public class RealisticBiomeVanillaColdBeach extends RealisticBiomeBase {

    public static Biome biome = Biomes.COLD_BEACH;
    public static Biome river = Biomes.FROZEN_RIVER;

    public RealisticBiomeVanillaColdBeach() {

        super(biome, RiverType.FROZEN, BeachType.COLD);
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_FILLER_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaColdBeach();
    }

    @Override
    public SurfaceBase initSurface() {
        // RWG `RealisticBiomeCoastIce` 覆写了 `rReplace`（L58-92）⇒ 它那个
        // `surface = new SurfaceGrassland(packed_ice×3, ice)` 字段**是死代码**，实际地表是
        // "雪地 + 砾石底、只有崖壁刷浮冰/冰"。此前 rtgc 抄的是那个死字段（整片浮冰）。
        return new SurfaceCoastIce(this.getConfig());
    }

    @Override
    public void initDecos() {

        DecoBoulder decoBoulder = new DecoBoulder();
        decoBoulder.setBoulderBlock(Blocks.COBBLESTONE.getDefaultState());
        decoBoulder.setChance(16);
        decoBoulder.setMaxY(95);
        decoBoulder.setStrengthFactor(3f);
        this.addDeco(decoBoulder);
    }

    public static class TerrainVanillaColdBeach extends TerrainBase {

        public TerrainVanillaColdBeach() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 照抄 RWG coast\RealisticBiomeCoastIce.java:34-56（RWG 在 baseBiome 温度 < 0.15f 时用 coastIce）
            return terrainCoastIce(x, y, rtgWorld, river);
        }
    }

}
