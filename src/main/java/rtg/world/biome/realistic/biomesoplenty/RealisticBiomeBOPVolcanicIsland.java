package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceIslandMountainStone;


public class RealisticBiomeBOPVolcanicIsland extends RealisticBiomeBase {

    public RealisticBiomeBOPVolcanicIsland(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPVolcanicIsland();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceIslandMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, 67, Blocks.SAND.getDefaultState(), 0f);
    }

    /**
     * 火山地貌已按用户要求**移除**（原实现调用 {@code TerrainBase.terrainVolcano(..., 70f)}）。
     * <p>
     * 保留本群系类本身是必须的：BOP 装了 {@code biomesoplenty:volcanic_island} 时，
     * 若没有对应的 {@code RealisticBiome} 包装，该群系会走 {@code RTGAPI} 的兜底解析，
     * 拿不到本仓库的配置项与地表规则。因此这里改为**普通低缓丘陵**，只是不再生成锥体与火山口。
     *
     * <p>⚠ <b>地表与 RWG 不一致（未移植）</b>：RWG 的
     * {@code RealisticBiomeIslandVolcano} 用的是 {@code SurfaceVolcanoAsh(ash, ashStone)}
     * —— rtgc **没有**这个类（全仓 grep 0 命中），本类当前返回的是岛屿地表
     * {@code SurfaceIslandMountainStone(top, filler, 67, sand, 0f)}。
     * 本文件里那个 {@code SurfaceBOPVolcanicIsland} 是 RTG 时代的旧内层实现，**当前零调用**。
     * 之所以不接它：火山内容整体是用户要求删除的，重新给它配火山渣地表等于半途把火山捡回来。
     * 这一条记在 {@code docs/rwg-port-gaps.md} §0.5.1（未移植 + 死类）。
     */
    public static class TerrainBOPVolcanicIsland extends TerrainBase {

        public TerrainBOPVolcanicIsland() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 低缓起伏的岛屿：基准 68，丘陵强度与地表噪声幅度都取小值，避免变成山地
            // 推断：RWG 的 IslandVolcano 已按用户要求删除，热带岛地形是最近亲
            return terrainIslandTropical(x, y, rtgWorld, border);
        }
    }

}
