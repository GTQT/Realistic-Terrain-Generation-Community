package rtg.api.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 区块生成性能分析器，用于记录每个区块生成各阶段的耗时，
 * 帮助定位性能瓶颈，决定优化方向。
 * <p>
 * 用法: 在 ChunkGeneratorRTG 等生成代码中插入计时代码：
 * <pre>{@code
 *   long t = ChunkGenerationProfiler.start(Category.LANDSCAPE_GEN);
 *   // ... 目标代码 ...
 *   ChunkGenerationProfiler.end(Category.LANDSCAPE_GEN, t);
 * }</pre>
 * <p>
 * 通过 RTGConfig 或系统属性控制开关，禁用时零开销。
 *
 * @author RTG Community
 */
@UtilityClass
public final class ChunkGenerationProfiler {

    private ChunkGenerationProfiler() {
    }

    // ========== 分析阶段枚举 ==========

    /**
     * 区块生成各阶段分类。
     * 每个阶段代表 generateChunk() 或 populate() 中的一个可测量步骤。
     */
    public enum Category {
        /** 区块生成总耗时（generateChunk 全部） */
        CHUNK_TOTAL("Chunk generate total", true),
        /** 区块装饰总耗时（populate 全部） */
        POP_TOTAL("Populate total", true),

        // ---- generateChunk 各阶段 ----
        /** Landscape 缓存查询或生成（含 noise 计算） */
        LANDSCAPE("Landscape get/gen", false),
        /** 地形填充（石头/水的 ChunkPrimer 填充） */
        TERRAIN_FILL("Terrain fill", false),
        /** 地表抖动计算 */
        SURFACE_JITTER("Surface jitter calc", false),
        /** 生物群系地表方块替换 */
        SURFACE_REPLACE("Biome surface replace", false),
        /** 洞穴生成 */
        CAVES("Cave generation", false),
        /** 峡谷生成 */
        RAVINES("Ravine generation", false),
        /** 结构生成（要塞、村庄、矿井等） */
        STRUCTURES_GEN("Structure generation", false),
        /** Chunk 组装 + 生物群系数组 + 光照 */
        CHUNK_FINALIZE("Chunk finalize + skylight", false),

        // ---- populate 各阶段 ----
        /** 结构装饰 */
        POP_STRUCTURES("Pop: structures", false),
        /** 湖泊生成（水+熔岩） */
        POP_LAKES("Pop: lakes", false),
        /** 地牢生成 */
        POP_DUNGEONS("Pop: dungeons", false),
        /** 生物群系装饰（树木、花草等） */
        POP_DECORATION("Pop: decoration", false),
        /** 动物生成 */
        POP_ANIMALS("Pop: animals", false),
        /** 雪和冰放置 */
        POP_SNOW_ICE("Pop: snow & ice", false);

        /** UI 显示名称 */
        public final String displayName;
        /** 是否是汇总阶段（其耗时 = 所属各阶段之和，不单独计次） */
        public final boolean isSummary;

        Category(String displayName, boolean isSummary) {
            this.displayName = displayName;
            this.isSummary = isSummary;
        }
    }

    // ========== 配置 ==========

    /** 是否启用性能分析 */
    private static volatile boolean enabled = false;
    /** 每 N 个区块打印一次汇总报告，0 表示不自动打印 */
    private static volatile int logInterval = 200;
    /** 超过此阈值（纳秒）的区块被视为慢区块并单独输出 */
    private static volatile long slowThresholdNs = 100_000_000L; // 100ms

    // ========== 聚合统计 ==========

    /** 所有区块的总计数器 */
    private static final AtomicLong totalChunks = new AtomicLong(0);
    /** 慢区块计数器 */
    private static final AtomicLong slowChunks = new AtomicLong(0);
    /** 最慢区块记录 */
    private static final AtomicReference<SlowChunkRecord> slowestChunk = new AtomicReference<>();
    /** 每个 Category 的聚合统计 */
    private static final ConcurrentHashMap<Category, AggregateStats> aggregateStats = new ConcurrentHashMap<>();

    // ========== 线程本地（每区块）数据 ==========

    /** 当前区块各阶段累计纳秒 */
    private static final ThreadLocal<EnumMap<Category, Long>> chunkPhaseNanos =
            ThreadLocal.withInitial(() -> new EnumMap<>(Category.class));
    /** 当前区块坐标（用于日志） */
    private static final ThreadLocal<Integer> currentCX = new ThreadLocal<>();
    private static final ThreadLocal<Integer> currentCZ = new ThreadLocal<>();

    // ========== 公开控制 API ==========

    /**
     * 启用或禁用性能分析。
     * 禁用时所有计时方法均为零开销（仅一次 boolean 检查）。
     */
    public static void setEnabled(boolean flag) {
        if (enabled && !flag) {
            // 禁用时输出最终报告
            Logger.info("[RTG-Profiler] Profiling disabled. Final report:");
            logReport(0);
            reset();
        }
        enabled = flag;
        Logger.info("[RTG-Profiler] Profiling " + (flag ? "ENABLED" : "DISABLED"));
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** 设置每 N 个区块自动打印汇总，0 禁用自动打印 */
    public static void setLogInterval(int interval) {
        logInterval = Math.max(0, interval);
    }

    /** 设置慢区块阈值（毫秒） */
    public static void setSlowThresholdMs(long ms) {
        slowThresholdNs = Math.max(1, ms) * 1_000_000L;
    }

    /** 获取当前慢区块阈值（毫秒） */
    public static long getSlowThresholdMs() {
        return slowThresholdNs / 1_000_000L;
    }

    public static long getTotalChunks() {
        return totalChunks.get();
    }

    public static long getSlowChunks() {
        return slowChunks.get();
    }

    // ========== 计时 API ==========

    /**
     * 开始计时一个阶段。
     *
     * @param cat 阶段分类
     * @return 起始时间戳（纳秒），禁用时返回 0
     */
    public static long start(Category cat) {
        if (!enabled) return 0L;
        return System.nanoTime();
    }

    /**
     * 结束计时并记录耗时。
     *
     * @param cat      阶段分类
     * @param startNs  start() 返回的时间戳，0 表示跳过
     */
    public static void end(Category cat, long startNs) {
        if (startNs == 0L) return;
        long elapsed = System.nanoTime() - startNs;
        // 记录到当前区块
        EnumMap<Category, Long> map = chunkPhaseNanos.get();
        map.merge(cat, elapsed, Long::sum);
        // 更新聚合统计
        AggregateStats stats = aggregateStats.computeIfAbsent(cat, k -> new AggregateStats());
        stats.record(elapsed);
    }

    // ========== 区块生命周期 ==========

    /**
     * 在区块生成开始时调用。
     * 清理前一区块的残留数据。
     */
    public static void beginChunk(int cx, int cz) {
        if (!enabled) return;
        chunkPhaseNanos.get().clear();
        currentCX.set(cx);
        currentCZ.set(cz);
    }

    /**
     * 在区块生成全部完成时调用（generateChunk + populate 之后）。
     * 检查慢区块、打印定期汇总。
     */
    public static void endChunk(int cx, int cz) {
        if (!enabled) return;
        EnumMap<Category, Long> data = chunkPhaseNanos.get();

        // 计算本区块总耗时（排除 CHUNK_TOTAL / POP_TOTAL 这种汇总阶段，避免重复计算）
        long chunkTotal = 0L;
        for (Map.Entry<Category, Long> e : data.entrySet()) {
            if (!e.getKey().isSummary) {
                chunkTotal += e.getValue();
            }
        }

        // 更新计数器
        long count = totalChunks.incrementAndGet();

        // 检查慢区块
        if (chunkTotal > slowThresholdNs) {
            slowChunks.incrementAndGet();
            logSlowChunk(cx, cz, chunkTotal, data);

            // 更新最慢区块记录
            SlowChunkRecord rec = new SlowChunkRecord(cx, cz, chunkTotal, new EnumMap<>(data));
            slowestChunk.updateAndGet(current -> {
                if (current == null || rec.totalNs > current.totalNs) return rec;
                return current;
            });
        }

        // 定期汇总
        if (logInterval > 0 && count % logInterval == 0) {
            logSummary(count);
        }
    }

    // ========== 报告输出 ==========

    /**
     * 获取完整性能报告的格式化字符串。
     */
    public static String getReport() {
        long chunkCount = totalChunks.get();
        if (chunkCount == 0) {
            return "[RTG-Profiler] No chunks profiled yet.";
        }
        return buildReport(chunkCount, true);
    }

    /**
     * 立即输出完整报告到日志。
     */
    public static void logReport(long chunkCount) {
        if (chunkCount <= 0) {
            chunkCount = totalChunks.get();
        }
        if (chunkCount <= 0) {
            Logger.info("[RTG-Profiler] No chunks profiled yet.");
            return;
        }
        Logger.info(buildReport(chunkCount, false));
    }

    /**
     * 重置所有统计数据。
     */
    public static void reset() {
        aggregateStats.clear();
        totalChunks.set(0);
        slowChunks.set(0);
        slowestChunk.set(null);
    }

    // ========== 内部实现 ==========

    /** 打印慢区块详细耗时分解 */
    private static void logSlowChunk(int cx, int cz, long totalNs, EnumMap<Category, Long> data) {
        StringBuilder sb = new StringBuilder(512);
        double totalMs = totalNs / 1_000_000.0;
        sb.append(String.format("[RTG-Profiler] SLOW chunk [%d, %d] %.2fms:\n", cx, cz, totalMs));

        // 分离汇总阶段和子阶段
        List<Map.Entry<Category, Long>> details = new ArrayList<>();
        List<Map.Entry<Category, Long>> summaries = new ArrayList<>();
        for (Map.Entry<Category, Long> e : data.entrySet()) {
            if (e.getKey().isSummary) {
                summaries.add(e);
            } else {
                details.add(e);
            }
        }
        details.sort(Map.Entry.<Category, Long>comparingByValue().reversed());
        summaries.sort(Map.Entry.<Category, Long>comparingByValue().reversed());

        // 输出子阶段（按时长降序，百分比基于非汇总总计）
        for (Map.Entry<Category, Long> e : details) {
            double ms = e.getValue() / 1_000_000.0;
            double pct = totalNs > 0 ? (100.0 * e.getValue() / totalNs) : 0.0;
            sb.append(String.format("  %-25s %8.2fms (%5.1f%%)\n",
                    e.getKey().displayName, ms, pct));
        }

        // 输出汇总阶段（显示包含关系，不计算百分比以免误导）
        if (!summaries.isEmpty()) {
            sb.append("  --- (totals) ---\n");
            for (Map.Entry<Category, Long> e : summaries) {
                double ms = e.getValue() / 1_000_000.0;
                sb.append(String.format("  %-25s %8.2fms\n",
                        e.getKey().displayName, ms));
            }
        }
        Logger.warn(sb.toString().trim());
    }

    /** 定期汇总 */
    private static void logSummary(long chunkCount) {
        Logger.info(buildSummaryHead(chunkCount));

        // 输出 Top-5 最耗时阶段（按平均耗时降序）
        List<Map.Entry<Category, AggregateStats>> sorted = new ArrayList<>(aggregateStats.entrySet());
        sorted.sort((a, b) -> {
            double avgA = a.getValue().count > 0 ? (double) a.getValue().totalNs / a.getValue().count : 0;
            double avgB = b.getValue().count > 0 ? (double) b.getValue().totalNs / b.getValue().count : 0;
            return Double.compare(avgB, avgA);
        });

        StringBuilder sb = new StringBuilder(256);
        sb.append("[RTG-Profiler] Top phases (avg):");
        int printed = 0;
        for (Map.Entry<Category, AggregateStats> e : sorted) {
            if (e.getKey().isSummary) continue; // 跳过汇总阶段
            AggregateStats s = e.getValue();
            if (s.count == 0) continue;
            double avgMs = (s.totalNs / (double) s.count) / 1_000_000.0;
            sb.append(String.format(" %s=%.2fms", e.getKey().displayName, avgMs));
            if (++printed >= 5) break;
        }
        if (printed > 0) {
            Logger.info(sb.toString());
        }
    }

    /** 构建汇总头部行 */
    private static String buildSummaryHead(long chunkCount) {
        double slowPct = chunkCount > 0 ? (100.0 * slowChunks.get() / chunkCount) : 0.0;
        SlowChunkRecord slowest = slowestChunk.get();
        String slowestInfo = "";
        if (slowest != null) {
            slowestInfo = String.format(" Slowest: [%d,%d] %.2fms",
                    slowest.cx, slowest.cz, slowest.totalNs / 1_000_000.0);
        }
        return String.format(
                "[RTG-Profiler] Summary after %d chunks | Slow(>%dms): %d (%.1f%%) |%s",
                chunkCount, slowThresholdNs / 1_000_000, slowChunks.get(), slowPct, slowestInfo);
    }

    /** 构建完整/日志报告 */
    private static String buildReport(long chunkCount, boolean includeDetail) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("============================================================\n");
        sb.append("  RTG Chunk Generation Performance Report\n");
        sb.append("============================================================\n");
        sb.append(String.format("  Total chunks profiled : %d\n", chunkCount));
        sb.append(String.format("  Slow chunk threshold  : %dms\n", slowThresholdNs / 1_000_000));
        sb.append(String.format("  Slow chunks           : %d (%.1f%%)\n",
                slowChunks.get(),
                chunkCount > 0 ? (100.0 * slowChunks.get() / chunkCount) : 0.0));

        // 最慢区块
        SlowChunkRecord slowest = slowestChunk.get();
        if (slowest != null) {
            sb.append(String.format("  Slowest chunk         : [%d, %d] %.2fms\n",
                    slowest.cx, slowest.cz, slowest.totalNs / 1_000_000.0));
        }

        sb.append("------------------------------------------------------------\n");

        // 表头
        if (includeDetail) {
            sb.append(String.format("  %-28s %8s %8s %8s %8s %6s\n",
                    "Phase", "Avg(ms)", "Min(ms)", "Max(ms)", "Total(s)", "Calls"));
        } else {
            sb.append(String.format("  %-28s %8s %8s %8s %6s\n",
                    "Phase", "Avg(ms)", "Min(ms)", "Max(ms)", "Calls"));
        }
        sb.append("------------------------------------------------------------\n");

        // 分离详细阶段和汇总阶段
        List<Map.Entry<Category, AggregateStats>> details = new ArrayList<>();
        List<Map.Entry<Category, AggregateStats>> summaries = new ArrayList<>();
        for (Map.Entry<Category, AggregateStats> e : aggregateStats.entrySet()) {
            if (e.getKey().isSummary) {
                summaries.add(e);
            } else {
                details.add(e);
            }
        }
        // 按平均耗时降序排列
        details.sort((a, b) -> {
            double avgA = a.getValue().count > 0 ? (double) a.getValue().totalNs / a.getValue().count : 0;
            double avgB = b.getValue().count > 0 ? (double) b.getValue().totalNs / b.getValue().count : 0;
            return Double.compare(avgB, avgA);
        });
        summaries.sort((a, b) -> {
            double avgA = a.getValue().count > 0 ? (double) a.getValue().totalNs / a.getValue().count : 0;
            double avgB = b.getValue().count > 0 ? (double) b.getValue().totalNs / b.getValue().count : 0;
            return Double.compare(avgB, avgA);
        });

        // 输出详细阶段
        for (Map.Entry<Category, AggregateStats> e : details) {
            AggregateStats s = e.getValue();
            if (s.count == 0) continue;

            double avgMs = (s.totalNs / (double) s.count) / 1_000_000.0;
            double minMs = s.minNs / 1_000_000.0;
            double maxMs = s.maxNs / 1_000_000.0;

            if (includeDetail) {
                double totalSec = s.totalNs / 1_000_000_000.0;
                sb.append(String.format("  %-28s %8.2f %8.2f %8.2f %8.2f %6d\n",
                        e.getKey().displayName, avgMs, minMs, maxMs, totalSec, s.count));
            } else {
                sb.append(String.format("  %-28s %8.2f %8.2f %8.2f %6d\n",
                        e.getKey().displayName, avgMs, minMs, maxMs, s.count));
            }
        }

        // 输出汇总阶段（外层计时）
        if (!summaries.isEmpty()) {
            sb.append("  --- (totals) ---\n");
            for (Map.Entry<Category, AggregateStats> e : summaries) {
                AggregateStats s = e.getValue();
                if (s.count == 0) continue;
                double avgMs = (s.totalNs / (double) s.count) / 1_000_000.0;
                double totalSec = s.totalNs / 1_000_000_000.0;
                sb.append(String.format("  %-28s %8.2fms avg, %8.2fs total, %6d calls\n",
                        e.getKey().displayName, avgMs, totalSec, s.count));
            }
        }

        sb.append("============================================================\n");
        sb.append("  Optimization hints:\n");
        sb.append("    - Landscape gen: noise computation dominates → optimize noise algorithms\n");
        sb.append("    - Surface replace: block iteration → reduce replacement complexity\n");
        sb.append("    - Caves/Ravines: vanilla generation → tune density parameters\n");
        sb.append("    - Decoration: tree placement → reduce per-chunk density or simplify trees\n");
        sb.append("    - Snow & ice: height lookups → cache ChunkInfo height data\n");
        sb.append("============================================================");
        return sb.toString();
    }

    // ========== 内部数据类 ==========

    /**
     * 单阶段聚合统计。
     */
    private static final class AggregateStats {
        long totalNs;
        long minNs = Long.MAX_VALUE;
        long maxNs;
        long count;

        synchronized void record(long ns) {
            totalNs += ns;
            if (ns < minNs) minNs = ns;
            if (ns > maxNs) maxNs = ns;
            count++;
        }
    }

    /**
     * 最慢区块记录。
     */
    private static final class SlowChunkRecord {
        final int cx;
        final int cz;
        final long totalNs;
        final EnumMap<Category, Long> breakdown;

        SlowChunkRecord(int cx, int cz, long totalNs, EnumMap<Category, Long> breakdown) {
            this.cx = cx;
            this.cz = cz;
            this.totalNs = totalNs;
            this.breakdown = breakdown;
        }
    }
}
