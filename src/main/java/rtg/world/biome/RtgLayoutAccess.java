package rtg.world.biome;

import net.minecraft.world.biome.Biome;

import rtg.api.RTGAPI;
import rtg.api.util.Logger;
import rtg.api.world.biome.IRealisticBiome;
import rtg.world.biome.realistic.land.RealisticBiomeMountainChain;


/**
 * 布局的**世界级持有者** —— 让 provider 与生成器共用同一个 {@link RtgBiomeLayout} 实例。
 *
 * <p>模式与既有的 {@code rtg.api.world.WaterLevel} 一致：静态持有「当前世界的布局」，
 * 由 provider 构造时建立（那时才知道世界种子，且 {@code BiomeInit} 早已跑完、群系已注册）。
 *
 * <p><b>为什么是静态</b>：{@code ChunkGeneratorRTG} 与 {@code BiomeProvider} 是两个独立对象，
 * 都需要同一份布局；rtgc 已是单线程串行生成（见 {@code WaterLevel} 的同类说明）。
 * 世界种子变化时（换世界/换维度）会重建。
 *
 * <p><b>为什么 fail-soft</b>：分类器把约 130 个群系分到 4 个气候，
 * 无法在游戏外验证每个池都非空。海洋占 52%、内陆占 33% —— 任一核心池为空，
 * 该片区域就会拿到 null。故 {@link #biomeAt} 在拿不到群系时返回 null，
 * 由调用方回退到父类结果，而不是崩掉世界生成。
 *
 * @since 1.0.10
 */
public final class RtgLayoutAccess {

    private static volatile RtgBiomeLayout layout;
    private static volatile long layoutSeed = Long.MIN_VALUE;

    private RtgLayoutAccess() {}

    /** 取得（必要时按种子重建）当前布局。 */
    public static synchronized RtgBiomeLayout forSeed(final long seed) {

        // ⚠ 结构性防护：**客户端不得用不同种子重建共享布局。**
        //
        // 1.12.2 的客户端世界是 `new WorldClient(..., new WorldSettings(0L, ...), ...)` 建起来的
        // —— 服务端从不把真实种子发给客户端，所以 `world.getSeed()` 在客户端恒为 0。
        // 一旦客户端用 seed 0 调进来，就会把服务端正在用的布局**整体覆盖**，
        // 于是"玩家加入之后生成的所有区块"换了布局，与加入之前生成好的出生点区块
        // （约 25×25 区块）之间出现**垂直断层**。1.0.33 实测就踩到了：
        //     [Server thread] 建立布局：seed=7175654573957494923
        //     [Client thread] 建立布局：seed=0  旧seed=7175654573957494923
        //
        // 客户端本来也不需要"建"布局 —— 单人游戏里它与服务端同进程，直接共用即可；
        // 多人游戏里客户端没有布局，调用方会退回到原版 provider。
        if (layout != null && layoutSeed != seed && layoutSeed != Long.MIN_VALUE
                && net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide().isClient()) {
            Logger.warn("[RTG] ⚠ 客户端试图用种子 {} 重建布局（现有 seed={}）—— **已忽略**。"
                            + "客户端的 world.getSeed() 恒为 0（WorldClient 用 WorldSettings(0L) 构造），"
                            + "重建会覆盖服务端正在使用的布局，导致出生点区块与之后生成的区块地形断层。"
                            + " thread={}",
                    seed, layoutSeed, Thread.currentThread().getName());
            return layout;
        }

        if (layout == null || layoutSeed != seed) {
            if (layout != null && layoutSeed != seed) {
                Logger.warn("[RTG] ⚠ 布局被**换掉**了（种子从 {} 变成 {}）—— 换掉之前生成的区块与之后生成的"
                        + "会属于不同世界，交界处会出现垂直断层。若这是同一局游戏，即说明有代码在换种子。",
                        layoutSeed, seed);
            }
            if (rtg.RTG.layoutDebug()) {
                Logger.info("[RTG] 建立布局：side={} thread={} seed={} rtgBiomes={} 旧seed={}",
                        net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide(),
                        Thread.currentThread().getName(), seed, RTGAPI.rtgBiomeCount(), layoutSeed);
            }
            final RtgBiomeLayout fresh = new RtgBiomeLayout(seed);
            final RtgBiomeCategorizer.Report report = RtgBiomeCategorizer.apply(fresh);
            // 边界三池的内容来自 `RtgBiomeCategorizer.RWG_PLACEMENTS`（照抄 RWG 的显式标注），
            // 没标到的气候/方向由 `getLandBiomeAt` 落到核心池 —— 与 RWG 的 fall-through 一致。
            // 本轮（同版本 1.0.33）删除了 `mirrorCoreIntoBorders()`：它对群系选择是恒等变换，
            // 却会把核心池灌进极端边界池，把 RWG 的 6 个山地链放大成 104 个。
            // B4：极端气候边界（隔带相接）池 —— 由 cold/hot 边界池**镜像**成山地链。
            fresh.rebuildExtremeBorderMountains(RealisticBiomeMountainChain::forBiome);
            // 池统计明细只在 -Drtg.debugLayout 时打印；池为空的 ERROR/WARN 安全网始终生效。
            RtgBiomeCategorizer.logReport(fresh, report);
            if (rtg.RTG.layoutDebug()) {
                RtgBiomeCategorizer.logExtremeBorderReport(fresh);
            }
            layout = fresh;
            layoutSeed = seed;
        }
        return layout;
    }

    /** 当前布局；未初始化时为 {@code null}（此时调用方应回退到旧行为）。 */
    public static RtgBiomeLayout current() {
        return layout;
    }

    /**
     * 取该列的现实主义群系。
     *
     * <p><b>本方法保证在有布局时返回非 null</b>（布局未就绪才返回 null）。
     *
     * <p><b>为什么这个保证是必须的</b>：调用方把 null 理解成"我自己回退"，而两条回退路径
     * 的后果完全不同 —— 地形侧只是少一列参与混合（看不出来），群系 provider 侧却会落到
     * BOP 自己的 GenLayer（一张与 RWG 布局毫无关系的群系表，带
     * {@code GenLayerAddMushroomIsland}）。后者会让 F3 显示与本列地形**完全无关**的群系，
     * 实测就是"地上是正常树林、F3 显示蘑菇群系"。
     * 故拿不到时改用 {@link RtgBiomeLayout#lastResortAt}（气候正确的布局成员）。
     *
     * @return 该列群系；仅在布局未就绪时返回 {@code null}
     */
    public static IRealisticBiome biomeAt(final int x, final int z) {
        final RtgBiomeLayout l = layout;
        if (l == null) {
            noteNoLayout(x, z);
            return null;
        }
        IRealisticBiome output;
        try {
            output = l.getBiomeDataAt(x, z);
        } catch (final Throwable t) {
            Logger.error("[RTG] 布局在 ({}, {}) 选择群系时抛异常，改用气候兜底群系: {}", x, z, t);
            output = null;
        }
        if (output != null) {
            return output;
        }
        noteLastResort(x, z);
        return l.lastResortAt(x, z);
    }

    /**
     * 「布局尚未建立」的计数与节流日志。
     *
     * <p><b>为什么必须有</b>：{@code biomeAt} 返回 null 会让群系 provider 回落到
     * 「另一个生成器」（BOP 自己的 GenLayer），那条链里带 {@code GenLayerAddMushroomIsland}，
     * 产出的群系表是**海洋 + 蘑菇岛**这一套。若这行日志出现，就说明 F3 走的是回落路径，
     * 而不是 rtgc 的布局 —— 这正是"F3 与实际生成不一致"的判定点。
     */
    private static final java.util.concurrent.atomic.AtomicLong NO_LAYOUT_COUNT =
            new java.util.concurrent.atomic.AtomicLong();

    private static void noteNoLayout(final int x, final int z) {
        final long n = NO_LAYOUT_COUNT.incrementAndGet();
        if (n <= 5L || n % 100000L == 0L) {
            Logger.warn("[RTG] ⚠ 布局尚未建立就收到群系查询 ({}, {})（第 {} 次）—— "
                    + "群系 provider 将回落到原版/BOP 的 GenLayer，F3 会显示那里面的群系"
                    + "（海洋 + 蘑菇岛那一套），与实际生成的 RTG 地形不符。side={} thread={}",
                    x, z, n,
                    net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide(),
                    Thread.currentThread().getName());
        }
    }

    /** 「布局尚未建立」的累计次数（诊断用）。 */
    public static long noLayoutCount() {
        return NO_LAYOUT_COUNT.get();
    }

    /**
     * 兜底触发计数与**节流日志**。兜底本身是罕见的，但如果它开始频繁出现，
     * 说明某个池是空的（或池里有 null 成员），必须能看见 —— 否则"群系看起来随机"
     * 这类问题会再一次无迹可循。
     */
    private static final java.util.concurrent.atomic.AtomicLong LAST_RESORT_COUNT =
            new java.util.concurrent.atomic.AtomicLong();

    private static void noteLastResort(final int x, final int z) {
        final long n = LAST_RESORT_COUNT.incrementAndGet();
        if (n <= 5L || n % 100000L == 0L) {
            Logger.warn("[RTG] 布局在 ({}, {}) 没有可用群系，已用气候兜底群系（累计第 {} 次）。"
                    + "这通常意味着某个池为空或池内有 null 成员，请检查启动日志里的池计数。", x, z, n);
        }
    }

    /** 兜底累计触发次数（诊断用）。 */
    public static long lastResortCount() {
        return LAST_RESORT_COUNT.get();
    }

    /** 取该列的 MC 群系。仅在布局未就绪时返回 {@code null}。 */
    public static Biome mcBiomeAt(final int x, final int z) {
        final IRealisticBiome rb = biomeAt(x, z);
        return rb == null ? null : rb.baseBiome();
    }
}
