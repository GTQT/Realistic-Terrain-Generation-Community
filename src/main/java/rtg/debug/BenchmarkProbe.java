package rtg.debug;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import rtg.api.util.ChunkGenerationProfiler;
import rtg.api.util.ChunkGenerationProfiler.Category;
import rtg.api.util.Logger;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无头 500 区块加载基准（专用服务端）。
 *
 * <p>用法：在 {@code runServer} 的 JVM 参数里加
 * {@code -Drtg.benchmark=true -Drtg.enableProfiling=true}，然后启动专用服务端。
 * 世界必须是全新的（由 {@code server.properties} 的 {@code level-type=rtgc} 与
 * {@code level-seed} 决定），否则请求的区块可能已经存在，不会被 profiler 计入。
 *
 * <p>流程：等待 {@code rtg.benchWarmupTicks} 个 tick（让出生区加载结束）→
 * {@link ChunkGenerationProfiler#reset()} → 在 {@code (rtg.benchX, rtg.benchZ)}
 * 起 {@code rtg.benchWidth × rtg.benchDepth}（默认 25×20=500）区块，每个 tick 请求
 * {@code rtg.benchPerTick} 个 → 再等 {@code rtg.benchSettleTicks} 个 tick，
 * 让队列化的 population 与区块载入后的光照修补跑完 → 打印报告并停服。
 *
 * <p>输出里的 {@code wall} 是从第一次请求到最后一个区块请求返回的墙钟时间；
 * {@code msPerProfiled} = wall / 实际生成的区块数，是本次基准的吞吐指标。
 * 注意 {@code profiled} 可能小于 500：后请求的区块可能已经被邻区块的 population
 * 顺带生成，因此不会再进入 {@code beginChunk}。
 *
 * <p>除 profiler 的分阶段耗时外，本类还会输出三类"账外时间"证据，用来回答
 * "wall 减掉所有已知阶段后剩下的时间去哪了"：
 * <ul>
 *   <li>{@code cpu=}：服务器线程自身 CPU 时间。若明显小于 wall，说明时间花在
 *       等锁 / I/O / GC 停顿上，而不是在跑 Java 代码。</li>
 *   <li>{@code gc=}：基准窗口内 GC 的总停顿时间与次数（来自 GC MXBean）。</li>
 *   <li>{@code sample}：一个后台线程按 {@code rtg.benchSampleMs}（默认 10ms）
 *       抓服务器线程的栈，按"栈顶帧"和"包含帧"两个维度聚合，
 *       直接指出卡在哪个方法上。</li>
 * </ul>
 */
public final class BenchmarkProbe {

    private static final int START_X = Integer.getInteger("rtg.benchX", 30000);
    private static final int START_Z = Integer.getInteger("rtg.benchZ", 30000);
    private static final int WIDTH = Math.max(1, Integer.getInteger("rtg.benchWidth", 25));
    private static final int DEPTH = Math.max(1, Integer.getInteger("rtg.benchDepth", 20));
    private static final int PER_TICK = Math.max(1, Integer.getInteger("rtg.benchPerTick", 64));
    private static final int WARMUP_TICKS = Math.max(0, Integer.getInteger("rtg.benchWarmupTicks", 20));
    private static final int SETTLE_TICKS = Math.max(0, Integer.getInteger("rtg.benchSettleTicks", 20));
    private static final int SAMPLE_MS = Math.max(1, Integer.getInteger("rtg.benchSampleMs", 10));

    private static int warmupCounter;
    private static int settleCounter;
    private static int cursor;
    /** 0=未开始, 1=生成中, 2=收尾中, 3=已结束 */
    private static int phase;
    private static long startNs;
    private static long genEndNs;
    private static long settleEndNs;
    private static long maxTickNs;
    private static int loadedAtStart;
    private static int loadedAtEnd;
    private static long gcMsAtStart;
    private static long gcCountAtStart;
    private static long gcMsAtGenEnd;
    private static long gcCountAtGenEnd;
    private static long cpuNsAtStart;
    private static long cpuNsAtGenEnd;
    /** 所有 {@code world.getChunk} 调用自身的耗时之和（真实生成墙钟，不含每 tick 的世界更新）。 */
    private static long genCallNs;
    private static String randomTickSpeedBefore;
    private static Sampler sampler;

    private BenchmarkProbe() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!Boolean.getBoolean("rtg.benchmark") || phase == 3) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        long tickStart = System.nanoTime();
        try {
            step(server);
        } catch (Throwable t) {
            phase = 3;
            stopSampler();
            Logger.error("[RTG-Bench] FAILED at cursor=" + cursor + ": " + t);
            t.printStackTrace();
            server.initiateShutdown();
        } finally {
            if (phase == 1) {
                long dt = System.nanoTime() - tickStart;
                if (dt > maxTickNs) {
                    maxTickNs = dt;
                }
            }
        }
    }

    private static void step(MinecraftServer server) {
        WorldServer world = server.getWorld(0);
        if (world == null) {
            return;
        }
        int total = WIDTH * DEPTH;

        if (phase == 0) {
            if (warmupCounter++ < WARMUP_TICKS) {
                return;
            }
            start(world, total);
            return;
        }

        if (phase == 1) {
            for (int i = 0; i < PER_TICK && cursor < total; i++) {
                int cx = START_X + (cursor % WIDTH);
                int cz = START_Z + (cursor / WIDTH);
                long t0 = System.nanoTime();
                world.getChunk(cx, cz);
                genCallNs += System.nanoTime() - t0;
                cursor++;
            }
            if (cursor >= total) {
                genEndNs = System.nanoTime();
                gcMsAtGenEnd = gcMs();
                gcCountAtGenEnd = gcCount();
                cpuNsAtGenEnd = threadCpuNs();
                phase = 2;
                Logger.info(String.format(Locale.ROOT,
                        "[RTG-Bench] request phase done: requested=%d profiled=%d wall=%.2fs msPerProfiled=%.2f maxTick=%.1fms",
                        total, ChunkGenerationProfiler.getTotalChunks(),
                        (genEndNs - startNs) / 1e9,
                        ChunkGenerationProfiler.getTotalChunks() == 0 ? -1.0
                                : (genEndNs - startNs) / 1e6 / ChunkGenerationProfiler.getTotalChunks(),
                        maxTickNs / 1e6));
            }
            return;
        }

        if (phase == 2) {
            if (++settleCounter < SETTLE_TICKS) {
                return;
            }
            settleEndNs = System.nanoTime();
            finish(world, total);
        }
    }

    private static void start(WorldServer world, int total) {
        ChunkGenerationProfiler.reset();
        loadedAtStart = countLoaded(world);
        gcMsAtStart = gcMs();
        gcCountAtStart = gcCount();
        cpuNsAtStart = threadCpuNs();
        startNs = System.nanoTime();
        phase = 1;
        // 关掉随机刻：区块生成期间 500 个已载入区块的随机刻会随载入量线性增长，
        // 把"生成有多快"污染成"世界 tick 有多贵"。基准只关心生成。
        randomTickSpeedBefore = world.getGameRules().getString("randomTickSpeed");
        world.getGameRules().setOrCreateGameRule("randomTickSpeed", "0");
        Logger.info(String.format(Locale.ROOT,
                "[RTG-Bench] start: %d chunks at (%d,%d) seed=%d perTick=%d loadedAtStart=%d heap=%d/%dMB randomTickSpeed=%s->0",
                total, START_X, START_Z, world.getWorldInfo().getSeed(), PER_TICK,
                loadedAtStart, usedHeapMb(), maxHeapMb(), randomTickSpeedBefore));
        startSampler();
    }

    private static void finish(WorldServer world, int total) {
        phase = 3;
        stopSampler();
        if (randomTickSpeedBefore != null) {
            world.getGameRules().setOrCreateGameRule("randomTickSpeed", randomTickSpeedBefore);
        }
        loadedAtEnd = countLoaded(world);
        long profiled = ChunkGenerationProfiler.getTotalChunks();
        double wallSec = (genEndNs - startNs) / 1e9;
        double settleSec = (settleEndNs - genEndNs) / 1e9;
        double totalSec = (settleEndNs - startNs) / 1e9;
        Logger.info(String.format(Locale.ROOT,
                "[RTG-Bench] done: requested=%d profiled=%d wall=%.2fs settle=%.2fs total=%.2fs "
                        + "msPerProfiled=%.2f chunksPerSec=%.2f maxTick=%.1fms loaded=%d->%d heap=%d/%dMB",
                total, profiled, wallSec, settleSec, totalSec,
                profiled == 0 ? -1.0 : (genEndNs - startNs) / 1e6 / profiled,
                wallSec <= 0 ? -1.0 : profiled / wallSec,
                maxTickNs / 1e6, loadedAtStart, loadedAtEnd, usedHeapMb(), maxHeapMb()));

        // 汇总阶段与墙钟的差额 = profiler 没覆盖到的时间（区块装载、Chunk#onLoad、GC、I/O 等）。
        long chunkTotalNs = ChunkGenerationProfiler.getCategoryTotalNs(Category.CHUNK_TOTAL);
        long popTotalNs = ChunkGenerationProfiler.getCategoryTotalNs(Category.POP_TOTAL);
        double measuredSec = (chunkTotalNs + popTotalNs) / 1e9;
        Logger.info(String.format(Locale.ROOT,
                "[RTG-Bench] measured: chunkGen=%.2fs pop=%.2fs sum=%.2fs wall=%.2fs unaccounted=%.2fs (%.1f%%)",
                chunkTotalNs / 1e9, popTotalNs / 1e9, measuredSec, wallSec,
                wallSec - measuredSec, wallSec <= 0 ? 0.0 : 100.0 * (wallSec - measuredSec) / wallSec));

        // getChunk 调用自身的墙钟：这才是"生成 500 区块花了多久"，与 tick 循环/世界更新无关。
        double genCallSec = genCallNs / 1e9;
        Logger.info(String.format(Locale.ROOT,
                "[RTG-Bench] gen: getChunk=%.2fs (%.1f%% of wall) tickOverhead=%.2fs "
                        + "genMsPerProfiled=%.2f profiledPerSec=%.1f",
                genCallSec, wallSec <= 0 ? 0.0 : 100.0 * genCallSec / wallSec, wallSec - genCallSec,
                profiled == 0 ? -1.0 : genCallNs / 1e6 / profiled,
                genCallSec <= 0 ? -1.0 : profiled / genCallSec));

        // 账外时间归因：CPU / GC / 采样栈。
        long cpuGenNs = cpuNsAtGenEnd - cpuNsAtStart;
        long cpuAllNs = threadCpuNs() - cpuNsAtStart;
        long gcGenMs = gcMsAtGenEnd - gcMsAtStart;
        long gcAllMs = gcMs() - gcMsAtStart;
        Logger.info(String.format(Locale.ROOT,
                "[RTG-Bench] cpu/gc: genWindow cpu=%.2fs wall=%.2fs gc=%.2fs (%d pauses) idle=%.2fs; "
                        + "totalWindow cpu=%.2fs wall=%.2fs gc=%.2fs (%d pauses)",
                cpuGenNs / 1e9, wallSec, gcGenMs / 1e3, gcCountAtGenEnd - gcCountAtStart, (wallSec - cpuGenNs / 1e9),
                cpuAllNs / 1e9, totalSec, gcAllMs / 1e3, gcCount() - gcCountAtStart));

        if (sampler != null) {
            sampler.report(genEndNs - startNs);
        }

        // 内容指纹：用来证明优化没有改变生成结果（同 seed/坐标/全新世界）。
        // 拆成分量打印，方便定位"改动到底动了方块、高度图还是光照"。
        final long hashStartNs = System.nanoTime();
        final String[] parts = hashComponents(world);
        Logger.info(String.format(Locale.ROOT, "[RTG-Bench] hash: states=%s hmap=%s sky=%s block=%s (%dms)",
                parts[0], parts[1], parts[2], parts[3], (System.nanoTime() - hashStartNs) / 1_000_000L));

        ChunkGenerationProfiler.logReport(0);
        world.getMinecraftServer().initiateShutdown();
    }

    /**
     * 内容指纹：把网格内所有区块的方块状态、群系、天光/块光、高度图拼成一个 SHA-1。
     *
     * <p>同 seed + 同坐标 + 全新世界目录时，只要生成逻辑没变，指纹就应当逐位相同。
     * 这样"性能优化"可以带着证据提交：耗时下降而指纹不变 = 纯粹的等价优化。
     */
    /** 诊断：逐区块统计石头变种/矿物数量，用两次运行的这些数字 diff 定位内容差异。 */
    private static final boolean CENSUS = Boolean.getBoolean("rtg.benchCensus");
    private static final int CENSUS_SIZE = 15;
    private static final String[] CENSUS_NAMES = {
            "stone0", "granite1", "polished2", "diorite3", "polished4", "andesite5", "polished6",
            "dirt", "gravel", "coal", "iron", "gold", "redstone", "diamond", "lapis"
    };

    private static void countCensus(IBlockState state, int[] counts) {
        final Block block = state.getBlock();
        if (block == Blocks.STONE) {
            final int meta = Blocks.STONE.getMetaFromState(state);
            if (meta >= 0 && meta < 7) {
                counts[meta]++;
            }
        } else if (block == Blocks.DIRT) {
            counts[7]++;
        } else if (block == Blocks.GRAVEL) {
            counts[8]++;
        } else if (block == Blocks.COAL_ORE) {
            counts[9]++;
        } else if (block == Blocks.IRON_ORE) {
            counts[10]++;
        } else if (block == Blocks.GOLD_ORE) {
            counts[11]++;
        } else if (block == Blocks.REDSTONE_ORE) {
            counts[12]++;
        } else if (block == Blocks.DIAMOND_ORE) {
            counts[13]++;
        } else if (block == Blocks.LAPIS_ORE) {
            counts[14]++;
        }
    }

    private static String censusLine(int cx, int cz, int[] counts) {
        final StringBuilder sb = new StringBuilder(160);
        sb.append("[RTG-Bench] census ").append(cx).append(',').append(cz);
        for (int i = 0; i < CENSUS_SIZE; i++) {
            sb.append(' ').append(CENSUS_NAMES[i]).append('=').append(counts[i]);
        }
        return sb.toString();
    }

    private static String[] hashComponents(WorldServer world) {        try {
            final MessageDigest stateMd = MessageDigest.getInstance("SHA-1");
            final MessageDigest mapMd = MessageDigest.getInstance("SHA-1");
            final MessageDigest skyMd = MessageDigest.getInstance("SHA-1");
            final MessageDigest blockMd = MessageDigest.getInstance("SHA-1");
            for (int dz = 0; dz < DEPTH; dz++) {
                for (int dx = 0; dx < WIDTH; dx++) {
                    final Chunk chunk = world.getChunk(START_X + dx, START_Z + dz);
                    // 每区块单独算一份，日志里逐区块打印，跨运行 diff 就能定位到具体区块。
                    final MessageDigest cs = MessageDigest.getInstance("SHA-1");
                    final MessageDigest cm = MessageDigest.getInstance("SHA-1");
                    final MessageDigest ck = MessageDigest.getInstance("SHA-1");
                    final MessageDigest cb = MessageDigest.getInstance("SHA-1");
                    final int[] census = CENSUS ? new int[CENSUS_SIZE] : null;
                    cm.update(chunk.getBiomeArray());
                    final int[] heightMap = chunk.getHeightMap();
                    for (int h : heightMap) {
                        cm.update((byte) (h >>> 24));
                        cm.update((byte) (h >>> 16));
                        cm.update((byte) (h >>> 8));
                        cm.update((byte) h);
                    }
                    for (ExtendedBlockStorage section : chunk.getBlockStorageArray()) {
                        if (section == null) {
                            cs.update((byte) 0);
                            ck.update((byte) 0);
                            cb.update((byte) 0);
                            continue;
                        }
                        cs.update((byte) 1);
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                for (int x = 0; x < 16; x++) {
                                    final IBlockState state = section.get(x, y, z);
                                    final int id = Block.BLOCK_STATE_IDS.get(state);
                                    if (census != null) {
                                        countCensus(state, census);
                                    }
                                    cs.update((byte) (id >>> 24));
                                    cs.update((byte) (id >>> 16));
                                    cs.update((byte) (id >>> 8));
                                    cs.update((byte) id);
                                    ck.update((byte) section.getSkyLight(x, y, z));
                                    cb.update((byte) section.getBlockLight(x, y, z));
                                }
                            }
                        }
                    }
                    final byte[] ds = cs.digest();                    final byte[] dm = cm.digest();
                    final byte[] dk = ck.digest();
                    final byte[] db = cb.digest();
                    stateMd.update(ds);
                    mapMd.update(dm);
                    skyMd.update(dk);
                    blockMd.update(db);
                    Logger.info(String.format(Locale.ROOT, "[RTG-Bench] chunkhash %d,%d states=%s hmap=%s sky=%s block=%s",
                            START_X + dx, START_Z + dz, hex8(ds), hex8(dm), hex8(dk), hex8(db)));
                    if (census != null) {
                        Logger.info(censusLine(START_X + dx, START_Z + dz, census));
                    }
                }
            }
            return new String[] {hex(stateMd.digest()), hex(mapMd.digest()), hex(skyMd.digest()), hex(blockMd.digest())};
        } catch (Exception e) {
            return new String[] {"hash-failed:" + e, "-", "-", "-"};
        }
    }

    private static String hex8(byte[] digest) {
        return hex(digest).substring(0, 8);
    }

    private static String hex(byte[] digest) {
        final StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static int countLoaded(WorldServer world) {
        int loaded = 0;
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                if (world.getChunkProvider().chunkExists(START_X + dx, START_Z + dz)) {
                    loaded++;
                }
            }
        }
        return loaded;
    }

    private static long usedHeapMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
    }

    private static long maxHeapMb() {
        return Runtime.getRuntime().maxMemory() / (1024L * 1024L);
    }

    private static long gcMs() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long t = bean.getCollectionTime();
            if (t > 0L) {
                total += t;
            }
        }
        return total;
    }

    private static long gcCount() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = bean.getCollectionCount();
            if (c > 0L) {
                total += c;
            }
        }
        return total;
    }

    private static long threadCpuNs() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!bean.isCurrentThreadCpuTimeSupported()) {
            return 0L;
        }
        return bean.getCurrentThreadCpuTime();
    }

    // ==================== 采样器 ====================

    private static void startSampler() {
        try {
            sampler = new Sampler(Thread.currentThread(), SAMPLE_MS);
            Thread thread = new Thread(sampler, "RTG-Bench-Sampler");
            thread.setDaemon(true);
            thread.start();
        } catch (Throwable t) {
            Logger.warn("[RTG-Bench] sampler unavailable: " + t);
            sampler = null;
        }
    }

    private static void stopSampler() {
        if (sampler != null) {
            sampler.running = false;
        }
    }

    /**
     * 采样服务器线程栈。{@code getThreadInfo} 是安全点安全接口，对被测线程的干扰
     * 只有 safepoint 开销，足够用来做热点归因。
     */
    private static final class Sampler implements Runnable {

        private final ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        private final Thread target;
        private final long intervalMs;
        private final Map<String, int[]> self = new ConcurrentHashMap<>();
        private final Map<String, int[]> inclusive = new ConcurrentHashMap<>();
        private volatile boolean running = true;
        private volatile int samples;
        private volatile int emptySamples;

        Sampler(Thread target, long intervalMs) {
            this.target = target;
            this.intervalMs = intervalMs;
        }

        @Override
        public void run() {
            while (running) {
                try {
                    ThreadInfo info = bean.getThreadInfo(target.getId(), 48);
                    if (info != null) {
                        StackTraceElement[] st = info.getStackTrace();
                        if (st.length == 0) {
                            emptySamples++;
                        } else {
                            samples++;
                            bump(self, key(st[0]));
                            for (int i = 0; i < st.length && i < 16; i++) {
                                bump(inclusive, key(st[i]));
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // 采样是尽力而为的：任何异常都不应该影响基准本身。
                }
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        private static String key(StackTraceElement e) {
            return e.getClassName() + "." + e.getMethodName();
        }

        private static void bump(Map<String, int[]> map, String k) {
            int[] cell = map.get(k);
            if (cell == null) {
                map.putIfAbsent(k, new int[1]);
                cell = map.get(k);
            }
            if (cell != null) {
                cell[0]++;
            }
        }

        void report(long windowNs) {
            int n = samples;
            if (n == 0) {
                Logger.info("[RTG-Bench] sampler: no samples (window too short)");
                return;
            }
            Logger.info(String.format(Locale.ROOT,
                    "[RTG-Bench] sampler: %d stacks in %.2fs (interval=%dms, %d empty)",
                    n, windowNs / 1e9, intervalMs, emptySamples));
            log("[RTG-Bench] sample self (栈顶) 前 15:", topOf(self, 20), n, 15);
            log("[RTG-Bench] sample inclusive (栈内) 前 15:", topOf(inclusive, 20), n, 15);
        }

        private static List<Map.Entry<String, int[]>> topOf(Map<String, int[]> map, int cap) {
            List<Map.Entry<String, int[]>> all = new ArrayList<>(map.entrySet());
            Collections.sort(all, new Comparator<Map.Entry<String, int[]>>() {
                @Override
                public int compare(Map.Entry<String, int[]> a, Map.Entry<String, int[]> b) {
                    return Integer.compare(b.getValue()[0], a.getValue()[0]);
                }
            });
            return all.size() > cap ? all.subList(0, cap) : all;
        }

        private static void log(String title, List<Map.Entry<String, int[]>> rows, int n, int limit) {
            StringBuilder sb = new StringBuilder(512);
            sb.append(title).append('\n');
            int i = 0;
            for (Map.Entry<String, int[]> e : rows) {
                if (i++ >= limit) {
                    break;
                }
                sb.append(String.format(Locale.ROOT, "  %5.1f%% %5d  %s%n",
                        100.0 * e.getValue()[0] / n, e.getValue()[0], e.getKey()));
            }
            Logger.info(sb.toString());
        }
    }
}
