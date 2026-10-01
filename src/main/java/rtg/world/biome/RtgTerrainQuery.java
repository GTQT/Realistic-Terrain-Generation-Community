package rtg.world.biome;

import java.util.List;
import java.util.Random;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;


/**
 * 结构选址 / 出生点选址用的**群系查询** —— 把 {@code BiomeProvider} 的两个批量入口接到 RWG 布局上。
 *
 * <h2>要解决的问题</h2>
 *
 * 1.12.2 里这两个方法被这些地方调用（在 {@code build/rfg/mcp_patched_minecraft-sources.jar}
 * 里逐个查过，不是推测）：
 *
 * <pre>
 * BiomeProvider.areBiomesViable(x, z, radius, allowed)
 *   MapGenVillage.java:76          半径 0   VILLAGE_SPAWN_BIOMES
 *   StructureOceanMonument.java:83 半径 16  SPAWN_BIOMES
 *   StructureOceanMonument.java:88 半径 29  WATER_BIOMES
 *   WoodlandMansion.java:59        半径 32  ALLOWED_BIOMES
 *
 * BiomeProvider.findBiomePosition(x, z, range, biomes, random)
 *   WorldServer.java:971           出生点搜索（0,0 周围 256 格）
 *   MapGenStronghold.java:155      要塞选址（半径 112）
 * </pre>
 *
 * rtgc 此前**一个都没覆写** ⇒ 它们全都落在父类的 GenLayer 实现上，而 rtgc 的 provider
 * 把 GenLayer 设成了 **BOP 自己那套**（{@code BiomeProviderBOP.setupRTGGenLayers}）——
 * 那是一张与 RTG 布局毫无关系的群系表。于是：**村庄/神庙/海底神殿/林地府邸/要塞/出生点
 * 全部按"另一个世界"的群系图在选址。**
 *
 * <h2>为什么不照抄 RWG 的那两个方法体</h2>
 *
 * RWG 1.7.10 的 {@code ChunkManagerRealistic:1044} / {@code :1073} 是这样写的：
 *
 * <ul>
 *   <li>{@code areBiomesViable} —— <b>忽略 {@code radius} 与候选列表</b>，只判地形：
 *       "中心列高度 ≥ 62 且周围 5×5（间隔 16 格）的高度极差 &lt; 22"；</li>
 *   <li>{@code findBiomePosition} —— <b>直接返回 {@code null}</b>（"找不到"）。</li>
 * </ul>
 *
 * 这两条在 1.12.2 上**都不能照抄**，因为调用方变了（1.7.10 只有村庄用这个方法）：
 *
 * <ol>
 *   <li>上面那四个调用方里，**海底神殿**传的是 {@code WATER_BIOMES} 且半径 29/16。
 *       海洋列的高度是 34–52，永远过不了"≥ 62"那道闸 ⇒ **海底神殿会一个都不生成**。
 *       林地府邸同理（黑森林的高度也不保证 ≥ 62）。</li>
 *   <li>{@code findBiomePosition} 返回 null 时：{@code WorldServer} 会打
 *       "Unable to find spawn biome" 并把出生点放回默认的 (8, y, 8)；
 *       {@code MapGenStronghold} 则放弃按群系修正要塞位置。两者都不会崩，
 *       但**出生点会几乎总是落在 0,0 附近**。</li>
 * </ol>
 *
 * ⇒ 本类保留 **rtgc/RWG 真正要害的那一件事**：**数据来源换成 RTG 布局**（而不是 BOP 的 GenLayer），
 * 而语义按**原版**（群系列表 + 半径 + 4 格采样）—— 这样上面 6 个调用方全部拿到
 * "与世界一致的群系图"，且没有新的失效路径。
 *
 * <p>逐行对应原版 {@code BiomeProvider.areBiomesViable} / {@code .findBiomePosition}
 * （本仓库 {@code build/rfg/mcp_patched_minecraft-sources.jar} 里的
 * {@code net/minecraft/world/biome/BiomeProvider.java}）：只把
 * {@code this.genBiomes.getInts(i, j, i1, j1)} 换成"直接向布局取每一格的群系"。
 */
public final class RtgTerrainQuery {

    private RtgTerrainQuery() {}

    /** 采样步长：原版是 4 格（{@code >> 2}）。 */
    private static final int SAMPLE_SHIFT = 2;

    /**
     * 原版 {@code BiomeProvider.areBiomesViable} 的等价物，数据源换成 RTG 布局。
     *
     * @return 布局不可用时返回 {@code null}（调用方回落到父类）
     */
    public static Boolean biomesViable(final int x, final int z, final int radius, final List<Biome> allowed) {
        if (RtgLayoutAccess.current() == null) {
            return null;
        }
        final int i = x - radius >> SAMPLE_SHIFT;
        final int j = z - radius >> SAMPLE_SHIFT;
        final int k = x + radius >> SAMPLE_SHIFT;
        final int l = z + radius >> SAMPLE_SHIFT;
        final int i1 = k - i + 1;
        final int j1 = l - j + 1;

        for (int l1 = 0; l1 < i1 * j1; ++l1) {
            final int x2 = (i + l1 % i1) << SAMPLE_SHIFT;
            final int z2 = (j + l1 / i1) << SAMPLE_SHIFT;
            final Biome biome = RtgLayoutAccess.mcBiomeAt(x2, z2);
            if (biome == null || !allowed.contains(biome)) {
                return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
    }

    /**
     * 原版 {@code BiomeProvider.findBiomePosition} 的等价物，数据源换成 RTG 布局。
     *
     * <p>保留原版的**蓄水池抽样**（{@code random.nextInt(k1 + 1) == 0}），
     * 所以在候选点里是均匀随机取一个 —— 这一点对"每次重开世界出生点都不同"是必要的。
     *
     * @return 找到的位置；布局不可用或一个都没找到时返回 {@code null}
     */
    public static BlockPos findBiomePosition(final int x, final int z, final int range,
                                             final List<Biome> biomes, final Random random) {
        if (RtgLayoutAccess.current() == null) {
            return null;
        }
        final int i = x - range >> SAMPLE_SHIFT;
        final int j = z - range >> SAMPLE_SHIFT;
        final int k = x + range >> SAMPLE_SHIFT;
        final int l = z + range >> SAMPLE_SHIFT;
        final int i1 = k - i + 1;
        final int j1 = l - j + 1;

        BlockPos found = null;
        int k1 = 0;
        for (int l1 = 0; l1 < i1 * j1; ++l1) {
            final int i2 = (i + l1 % i1) << SAMPLE_SHIFT;
            final int j2 = (j + l1 / i1) << SAMPLE_SHIFT;
            final Biome biome = RtgLayoutAccess.mcBiomeAt(i2, j2);
            if (biome != null && biomes.contains(biome) && (found == null || random.nextInt(k1 + 1) == 0)) {
                found = new BlockPos(i2, 0, j2);
                ++k1;
            }
        }
        return found;
    }
}
