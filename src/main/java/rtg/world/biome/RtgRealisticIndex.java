package rtg.world.biome;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import rtg.api.RTGAPI;
import rtg.api.util.Logger;
import rtg.api.world.biome.IRealisticBiome;


/**
 * 「现实主义群系编号空间」—— RWG 的 {@code RealisticBiomeBase.getBiome(k)} 所对应的东西。
 *
 * <h2>为什么需要它</h2>
 *
 * RWG 的群系选择、混合权重、地表替换全都跑在一个**独立编号空间**里：
 * {@code ChunkManagerRealistic.getBiomeDataAt(x,z)} 返回的是 {@code RealisticBiomeBase} 的编号，
 * MC 群系只是它的 {@code baseBiome} 附属物。RWG 的
 * {@code RealisticBiomeMountainChain} 正是靠这一点存在：它
 * {@code super(0, backingBiome.baseBiome, …)} —— 与备份群系**共用同一个 MC 群系**，
 * 但在现实主义编号空间里占一个**自己的槽位**。
 *
 * <p>rtgc 原先没有这层编号空间：{@code ChunkGeneratorRTG.getNewerNoise} 用
 * {@code Biome.getIdForBiome(biomeProvider.getBiome(...))} 建采样数组，
 * 于是"山地链"与"它的备份群系"被折叠成同一个编号 —— 山地链根本无法表示。
 *
 * <h2>编号分配</h2>
 *
 * <ul>
 *   <li>普通群系沿用 MC 编号（{@code [0, mcBound)}），语义与改动前完全一致；</li>
 *   <li>合成群系从 {@code mcBound} 起顺序分配，上限 {@link #MAX_SYNTHETIC} 个。</li>
 * </ul>
 *
 * <b>顺序无关</b>：{@code mcBound()} 第一次被调用时冻结并缓存。谁先调用都得到同一个值 ——
 * 生成器先跑就在那时冻结，布局先跑就在那时冻结。因此
 * {@code ChunkGeneratorRTG} 的数组尺寸与这里的编号基准永远一致，不依赖
 * {@code BiomeProvider} 与 {@code ChunkGenerator} 的构造顺序。
 *
 * <h2>性能约定（改动前务必先读）</h2>
 *
 * {@link #biomeOf(int)} 与 {@link #isSynthetic(int)} 位于**每区块数万次**的热路径上
 * （{@code nearbyMountainChainInfluence} 每列扫 441 个采样点）。因此这里的读路径
 * **一律无锁**：{@code mcBound} 用 volatile 惰性冻结（竞态下两个线程算出同一个值，幂等），
 * 合成表用 volatile 数组，绝不在这两个方法里加 {@code synchronized}。
 * 写路径（{@link #register}）不常调用，才用锁。
 *
 * @since 1.0.12
 */
public final class RtgRealisticIndex {

    /**
     * 合成槽位上限。
     *
     * <p>山地链是「每个备份群系一个变体」，而极端边界池镜像自 cold/hot 边界池
     * （每个气候数十个），故 256 足够；它同时决定了
     * {@code hugeRender}/{@code smallRender} 的**预留**宽度（81×256 与 625×256 个 float，
     * 合计约 0.7 MB，可忽略）。
     *
     * <p><b>绝对不要把它调成 65536。</b> 历史教训（见 {@code ChunkGeneratorRTG} 的旧注释）：
     * 曾按 {@code RTGAPI.getMaxBiomeIDs()} 分配 —— 装了 REID 就直接返回 65536，
     * 于是两个数组放大 256 倍（约 176 MB/生成器），每区块多出约 4600 万次浮点写入，
     * 实测造成约 <b>10 倍</b>的世界生成退化。
     * 本类只在**内存**上按预留宽度分配，热循环一律走 {@link #usedBound()}。
     */
    public static final int MAX_SYNTHETIC = 256;

    private static final Map<IRealisticBiome, Integer> SYNTHETIC_IDS = new IdentityHashMap<>();

    /** 按槽位存合成群系；**volatile** 以便读路径无需加锁。 */
    private static volatile IRealisticBiome[] syntheticById = new IRealisticBiome[0];

    private static volatile int syntheticCount = 0;

    /** MC 编号上界（不含）。首次调用时扫描注册表并冻结；-1 = 未冻结。 */
    private static volatile int mcBound = -1;

    private RtgRealisticIndex() {
    }

    /**
     * MC 群系编号上界（不含）。首次调用时扫描注册表并冻结。
     *
     * <p><b>故意不加锁</b>：本方法是热路径（见类注释）。竞态下两个线程各自扫描一次注册表，
     * 结果必然相同，是幂等的；代价只是极少数情况下多扫一遍。
     */
    public static int mcBound() {
        int bound = mcBound;
        if (bound < 0) {
            int max = 255;
            for (final Biome biome : ForgeRegistries.BIOMES) {
                if (biome == null) {
                    continue;
                }
                final int id = Biome.getIdForBiome(biome);
                if (id > max) {
                    max = id;
                }
            }
            bound = max + 1;
            mcBound = bound;
        }
        return bound;
    }

    /** 采样数组需要的**预留**总宽度：MC 编号 + 全部合成槽位。 */
    public static int biomeIdBound() {
        return mcBound() + MAX_SYNTHETIC;
    }

    /**
     * 采样数组**当前实际需要遍历**的宽度：MC 编号 + 已分配的合成槽位。
     *
     * <p>这是本类存在的性能关键：{@link #biomeIdBound()} 是"预留宽度"（构造时就要定下来，
     * 因为 1.12.2 里 {@code ChunkGenerator} 先于 {@code BiomeProvider} 构造，那一刻山地链
     * 还不存在），而每区块最热的几个循环应该按**这个**宽度走。
     * 没有山地链时它就等于 {@code mcBound()}，即与引入本类之前**完全一致**。
     */
    public static int usedBound() {
        return mcBound() + syntheticCount;
    }

    /**
     * 为一个合成群系分配编号（幂等）。
     *
     * @return 分配到的编号；槽位耗尽时返回 {@code -1}
     */
    public static synchronized int register(final IRealisticBiome realisticBiome) {
        final Integer existing = SYNTHETIC_IDS.get(realisticBiome);
        if (existing != null) {
            return existing;
        }
        if (syntheticCount >= MAX_SYNTHETIC) {
            Logger.error("[RTG] 合成群系槽位已满（{}），{} 将退回其备份群系的地形。",
                    MAX_SYNTHETIC, realisticBiome.baseBiomeResLoc());
            return -1;
        }

        final IRealisticBiome[] grown = new IRealisticBiome[syntheticCount + 1];
        final IRealisticBiome[] current = syntheticById;
        System.arraycopy(current, 0, grown, 0, current.length);
        grown[syntheticCount] = realisticBiome;
        syntheticById = grown;

        final int id = mcBound() + syntheticCount;
        syntheticCount++;
        SYNTHETIC_IDS.put(realisticBiome, id);
        return id;
    }

    /**
     * 取该现实主义群系在本编号空间里的编号。普通群系返回其 MC 编号。
     *
     * <p>热路径：只做一次 IdentityHashMap 查找。
     */
    public static int idFor(final IRealisticBiome realisticBiome) {
        if (realisticBiome == null) {
            return -1;
        }
        final Integer synthetic = SYNTHETIC_IDS.get(realisticBiome);
        return synthetic != null ? synthetic : realisticBiome.baseBiomeId();
    }

    /** 该编号是否是合成槽位。**无锁**，供热路径使用。 */
    public static boolean isSynthetic(final int id) {
        return id >= mcBound();
    }

    /**
     * 按编号取回现实主义群系。编号非法或槽位为空时返回 {@code null}。
     *
     * <p>热路径：MC 编号走 {@link RTGAPI#getRTGBiome(int)}（数组缓存），
     * 合成编号走 volatile 数组，全程无锁。
     */
    public static IRealisticBiome biomeOf(final int id) {
        if (id < 0) {
            return null;
        }
        if (id >= mcBound()) {
            final int index = id - mcBound();
            final IRealisticBiome[] current = syntheticById;
            return index < current.length ? current[index] : null;
        }
        return RTGAPI.getRTGBiome(id);
    }

    /** 已分配的合成槽位数（诊断用）。 */
    public static int syntheticCount() {
        return syntheticCount;
    }
}
