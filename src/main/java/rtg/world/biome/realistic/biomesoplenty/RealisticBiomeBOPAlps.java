package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceTundra;


public class RealisticBiomeBOPAlps extends RealisticBiomeBase {

    public RealisticBiomeBOPAlps(final Biome biome) { super(biome, RiverType.FROZEN); }

    @Override
    public void initDecos() {}

    @Override
    public Biome preferredBeach() {
        return baseBiome();
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPAlps();
    }

    @Override
    public SurfaceBase initSurface() {
        // 推断（**不是照抄**）：RWG `SupportBOP.java:55-59` 的 alps 条目整块被 `/* */` 注释
        //（注释行原配方是 `TerrainMountainRiver()` + `SurfaceMountainSnow(Blocks.grass, Blocks.dirt, false, null, 0.45f)`）。
        // 注释 ≠ 生效：这里保留 rtgc 自己的寒冬山地配方（`SurfaceTundra(top, filler)`，与
        // `terrainMountain` 同一家族），**不**按注释行改写。见 `docs/rwg-port-gaps.md` §0.5.4。
        return new SurfaceTundra(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock);
    }

    /**
     * RWG-style mountain terrain for dramatic Alpine peaks.
     *
     * <p>⚠ 与上面 `initSurface` 同一条：RWG 的 alps 条目是**被注释掉的**，
     * 所以这里的 `terrainMountain` 属于**推断**，不是照抄。
     */
    public static class TerrainBOPAlps extends TerrainBase {

        public TerrainBOPAlps() {
            base = 120f;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            return terrainMountain(x, y, rtgWorld, river);
        }
    }

}
