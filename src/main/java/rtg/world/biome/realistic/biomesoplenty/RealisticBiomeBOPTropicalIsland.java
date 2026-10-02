package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.biome.BOPBiomes;
import biomesoplenty.api.block.BOPBlocks;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import rtg.api.util.noise.RwgCellNoise;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceIslandMountainStone;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgBiomeLayout;
import rtg.world.biome.RtgLayoutAccess;
import rtg.world.gen.MapVolcano;

import java.util.Random;

import static rtg.api.world.deco.DecoFallenTree.LogCondition.RANDOM_CHANCE;


/**
 * RWG {@code rwg/biomes/realistic/ocean/RealisticBiomeIslandTropical.java} 的对应物。
 *
 * <p><b>包归属说明</b>：RWG 这份文件在 {@code biomes/realistic/ocean/} 下（RWG 的
 * 「热带岛屿」是**海洋气候**的岛屿群系，其 {@code baseBiome} 是 {@code RWGBiomes.baseOceanWet}，
 * 见 RWG L24），而 rtgc 的对应类在 {@code biomesoplenty} 包下 ——
 * rtgc 没有「海洋气候基群系」这一层，热带岛屿在这里就是 BOP 的 {@code tropical_island}，
 * 按群系所属 mod 归档。**所在地图生成逻辑与 RWG 的海洋版完全同源**（见下面的 {@code rMapGen}）。
 */
public class RealisticBiomeBOPTropicalIsland extends RealisticBiomeBase {

    public RealisticBiomeBOPTropicalIsland(final Biome biome) { super(biome); }

    @Override
    public Biome preferredBeach() {

        return BOPBiomes.white_beach.orNull();
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPTropicalIsland();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceIslandMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, 67, Blocks.SAND.getDefaultState(), 0f);
    }

    @Override
    public void initDecos() {

        DecoFallenTree decoFallenTree = new DecoFallenTree();
        decoFallenTree.getDistribution().setNoiseDivisor(80f);
        decoFallenTree.getDistribution().setNoiseFactor(60f);
        decoFallenTree.getDistribution().setNoiseAddend(-15f);
        decoFallenTree.setLogCondition(RANDOM_CHANCE);
        decoFallenTree.setLogConditionChance(12);
        decoFallenTree.setLogBlock(BOPBlocks.log_1.getStateFromMeta(7));
        decoFallenTree.setLeavesBlock(Blocks.LEAVES.getDefaultState());
        decoFallenTree.setMinSize(3);
        decoFallenTree.setMaxSize(4);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());
    }

    /**
     * RWG {@code rwg/biomes/realistic/ocean/RealisticBiomeIslandTropical.rMapGen}（L56-74）的**逐行照抄**：
     * 热带岛屿是火山地标（{@code MapVolcano}）在 RWG 里**唯一**的宿主 —— 一座热带岛屿的中心
     * 会顶起一座火山锥。
     *
     * <pre>
     * RWG：                                                       rtgc：
     *   if (baseX % 4 == 0 &amp;&amp; baseY % 4 == 0                     逐字（baseY → baseZ，仅命名）
     *       &amp;&amp; mapRand.nextInt(6) == 0) {
     *     float river = cmr.getRiverStrength(baseX*16, baseY*16)   逐字
     *                     + 1f;
     *     if (river &gt; 0.98f &amp;&amp; cmr.isBorderlessAt(…, …)            逐字
     *         &amp;&amp; cmr.getNoiseWithRiverOceanAt(
     *                …, river,
     *                cmr.getTerrainOceanValue(…)) &gt; 110f) {        ocean 实参照传（rtgc 侧不使用，
     *                                                              见 RtgBiomeLayout#getNoiseWithRiverOceanAt）
     *       long i1 = mapRand.nextLong() / 2L * 2L + 1L;          逐字
     *       long j1 = mapRand.nextLong() / 2L * 2L + 1L;          逐字
     *       mapRand.setSeed((long) chunkX * i1                    world.getSeed() → rtgWorld.world().getSeed()
     *                        + (long) chunkY * j1 ^ world.getSeed());
     *       MapVolcano.build(blocks, metadata, world, mapRand,
     *                        baseX, baseY, chunkX, chunkY,
     *                        perlin, cell, noise);                Block[]+metadata → primer；
     *                                                             perlin → rtgWorld.simplexInstance(0)；
     *                                                             cell   → new RwgCellNoise(worldSeed, (short)0)
     *   }
     * </pre>
     *
     * <p><b>门控在说什么</b>（四道，缺一不可）：
     * <ol>
     *   <li>{@code baseX % 4 == 0 && baseZ % 4 == 0}：候选中心只在每 4 个区块的格点上
     *       —— 于是同一个格点会被它周围 11×11 内的**每个**区块各自看到一次，
     *       这正是 {@code MapVolcano} 能跨区块拼出一整座锥体的原因；</li>
     *   <li>{@code mapRand.nextInt(6) == 0}：只有 1/6 的格点真有火山（稀疏化）。
     *       {@code mapRand} 此刻已被基类的 {@code generateMapGen} 按本候选点单独播种；</li>
     *   <li>{@code river > 0.98f}：中心必须在**内陆**（{@code getRiverStrength + 1f}
     *       的 0 = 河心、1 = 内陆）—— 不在河网上；</li>
     *   <li>{@code isBorderlessAt && getNoiseWithRiverOceanAt(…) > 110f}：中心一带是同一种群系
     *       且**地形已经足够高**（&gt; 110 格）—— 即"只在高地上长火山"，
     *       避免在半山腰/海面上戳出一座锥体。</li>
     * </ol>
     *
     * <p><b>rtgc 侧仅有的两处适配（都在门控之外）</b>：布局由
     * {@link RtgLayoutAccess#current()} 取（RWG 是构造器持有的 {@code cmr} 字段）；
     * 布局未就绪（为 null）时直接返回 —— 没有布局就无从判定门控，也就不会有火山。
     * <p>另外 {@code worldSeed} 由 {@code rtgWorld} 取：RWG 在这个方法里读的是
     * {@code world.getSeed()}，rtgc 的 {@link RTGWorld#seed()} 就是它的别名。
     */
    @Override
    public void rMapGen(RTGWorld rtgWorld, ChunkPrimer primer, Random mapRand, int baseX, int baseZ,
            int chunkX, int chunkZ, float[] noise) {

        final RtgBiomeLayout cmr = RtgLayoutAccess.current();
        if (cmr == null) {
            return;
        }

        if (baseX % 4 == 0 && baseZ % 4 == 0 && mapRand.nextInt(6) == 0) {
            float river = cmr.getRiverStrength(baseX * 16, baseZ * 16) + 1f;
            if (river > 0.98f && cmr.isBorderlessAt(baseX * 16, baseZ * 16)
                    && cmr.getNoiseWithRiverOceanAt(
                            baseX * 16,
                            baseZ * 16,
                            river,
                            RtgBiomeLayout.getTerrainOceanValue(cmr.getContinentValue(baseX * 16, baseZ * 16))) > 110f) {
                long i1 = mapRand.nextLong() / 2L * 2L + 1L;
                long j1 = mapRand.nextLong() / 2L * 2L + 1L;
                mapRand.setSeed((long) chunkX * i1 + (long) chunkZ * j1 ^ rtgWorld.world().getSeed());

                MapVolcano.build(primer, rtgWorld.world(), mapRand, baseX, baseZ, chunkX, chunkZ,
                        rtgWorld.simplexInstance(0), new RwgCellNoise(rtgWorld.seed(), (short) 0), noise);
            }
        }
    }

    /**
     * 依据：RWG {@code ocean\RealisticBiomeIslandTropical.java} 的 {@code rNoise}
     *（它没有 terrain 字段，而是直接覆写 rNoise）—— rtgc 的 {@code TerrainBase.terrainIslandTropical}
     * 就是它的逐行移植。
     * <p>
     * 本类原先是一个**从未被实例化的死类** {@code TerrainVanillaExtremeHillsPlus}
     *（同名真身在 {@code RealisticBiomeVanillaExtremeHillsPlus.java}，由那个群系自己使用）；
     * 而 {@code initTerrain()} 实际返回的是 {@code RealisticBiomeVanillaExtremeHills.GrandMountain}。
     * 两者都已按 RWG 拨正。
     */
    public static class TerrainBOPTropicalIsland extends TerrainBase {

        public TerrainBOPTropicalIsland() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            return terrainIslandTropical(x, y, rtgWorld, border);
        }
    }

}
