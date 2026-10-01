package rtg.api.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import rtg.api.util.noise.SimplexNoise;
import rtg.api.world.RTGWorld;
import rtg.api.world.deco.DecoBase;

// This class stores expensive to calculate info like noises on a chunk basis to be used for regionally varying decorators.
public class ChunkInfo {

    public ChunkPos pos;
    public RTGWorld rtgWorld;

    private final static int TREESIMPLEX = 8;

    private Distribution treeDistribution;

    // ====== 区块16x16高度图缓存，惰性初始化 ======
    private int[] heightCache = null;
    private boolean heightCacheReady = false;

    public ChunkInfo(ChunkPos _pos, RTGWorld _rtgWorld) {
        pos = _pos;
        rtgWorld = _rtgWorld;
    }

    public ChunkInfo(ChunkPos _pos, RTGWorld _rtgWorld, float[] noise) {
        pos = _pos;
        rtgWorld = _rtgWorld;
        if (noise != null) {
            setHeightsFromNoise(noise);
        }
    }

    // ====== 直接从terrain noise构建高度缓存，避免256次world.getHeight()调用 ======
    /**
     * 从地形噪声数组建立高度缓存。
     *
     * <p><b>⚠ 两个数组的索引顺序是相反的，必须转置：</b>
     * <ul>
     *   <li>{@code noise[]} 由 {@code ChunkGeneratorRTG.getNewerNoise} 按
     *       {@code k = x * 16 + z} 写入；</li>
     *   <li>{@code heightCache} 由 {@link #getHeight(int, int)} 按
     *       {@code (z & 15) * 16 + (x & 15)} 读取（与 {@link #buildHeightCache} 一致）。</li>
     * </ul>
     *
     * <p>原实现写的是 {@code heightCache[i] = (int) noise[i]} —— 整张高度图被<b>转置</b>了。
     * 后果：装饰取 {@code getHeight(x, z)} 时拿到的其实是 (z, x) 那一列的高度；
     * 配合 {@code DecoTree.doGenerate} 里精确的 {@code pos.up(y)}（不向下找地面），
     * 山地上约一半的树被埋进石头、另一半悬空，还有大量被
     * {@code y > maxY || y < minY} 剔除 —— 这就是"没有地表装饰（树）"的直接原因。
     */
    public void setHeightsFromNoise(float[] noise) {
        heightCache = new int[256];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                heightCache[z * 16 + x] = (int) noise[x * 16 + z];
            }
        }
        heightCacheReady = true;
    }

    // ====== 获取区块内(x,z)的地表高度（带缓存，仅首次调用时构建） ======
    public int getHeight(int x, int z) {
        if (!heightCacheReady) {
            buildHeightCache();
        }
        return heightCache[(z & 15) * 16 + (x & 15)];
    }

    private void buildHeightCache() {
        heightCache = new int[256];
        net.minecraft.world.World world = rtgWorld.world();
        int baseX = pos.x * 16;
        int baseZ = pos.z * 16;
        net.minecraft.util.math.BlockPos.MutableBlockPos mpos = new net.minecraft.util.math.BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                mpos.setPos(baseX + x, 0, baseZ + z);
                heightCache[z * 16 + x] = world.getHeight(mpos).getY();
            }
        }
        heightCacheReady = true;
    }

    public float treedensity() {
        BlockPos offsetPos = DecoBase.getOffsetPos(pos);
        float noise = rtgWorld.simplexInstance(TREESIMPLEX)
                .noise2f(offsetPos.getX() / treeDistribution.getNoiseDivisor(), offsetPos.getZ() / treeDistribution.getNoiseDivisor())
                * treeDistribution.getNoiseFactor() + treeDistribution.getNoiseAddend();
        return noise;
    }

    private final int TREE_HEIGHT_INDEX = 8;
    private SimplexNoise treeHeightNoise() {return rtgWorld.simplexInstance(TREE_HEIGHT_INDEX);}
    private final float treeHeightNoiseDivisor = 1237;

    private Float storedTreeHeight = null;

    public float treeHeightNoiseValue() {
        if (storedTreeHeight == null) {
            BlockPos offsetPos = DecoBase.getOffsetPos(pos);
            storedTreeHeight = treeHeightNoise()
                    .noise2f(offsetPos.getX() / treeHeightNoiseDivisor, offsetPos.getZ() / treeHeightNoiseDivisor);
        }
        return storedTreeHeight;
    }

    public World world() {return rtgWorld.world();}

    // ==================================================================
    // 装饰调用计数（D4 的诊断仪表）
    // ==================================================================

    /**
     * 本区块内 {@code DecoBase.generate} 被调用的次数。
     *
     * <p>为什么放在这里：{@link ChunkInfo} 是**唯一**会传进每一个 deco 的对象
     * （见 {@code IRealisticBiome#rDecorate} 与山地链的同名覆写），
     * 所以调用方在调 {@code deco.generate(...)} 前顺手 {@link #noteInvocation()} 即可，
     * 不必去改约 32 个装饰类。
     *
     * <p>用途（目标 D4 明确要求的仪表）："没有地表装饰"这类问题里，
     * <b>"该群系有多少 deco"与"实际被调用了几个"是两件事</b>
     * —— {@code preGenerate(river)} 会过滤、概率缩放会过滤、快捷键分支会整个跳过。
     * 只有把两个数并排打出来才能一眼看出卡在哪一步。
     *
     * <p><b>静态</b>：与世界生成的单线程前提一致（同 {@code WaterLevel} / {@code RtgLayoutAccess}）。
     */
    private static int chunkInvocations;

    /** 每个区块开始装饰前调用。 */
    public static void resetInvocations() {
        chunkInvocations = 0;
    }

    /** 由 {@code rDecorate} 的调用方在每次 {@code deco.generate(...)} 前调用。 */
    public static void noteInvocation() {
        chunkInvocations++;
    }

    /** 本区块的装饰调用次数。 */
    public static int chunkInvocations() {
        return chunkInvocations;
    }

    // ==================================================================
    // 装饰耗时归因（只在 profiler 打开时累计）
    // ==================================================================

    /**
     * 为什么需要它：性能首测显示装饰占绝大头（平均 17ms，**最慢的区块 3.5 秒里 99.8% 是装饰**），
     * 而"Pop: decoration"这一格是个黑盒 —— 它同时包含
     * ①rtgc 自己的 {@code DecoBase.generate} 循环、②{@code Biome.decorate} 的原版植被。
     * 更麻烦的是 ②**会按被装饰的群系各跑一遍**（{@link IRealisticBiome#rDecorate} 是
     * 按 `decoWeights` 逐群系调用的），所以"一个区块装饰了 5 个群系"就意味着原版植被跑了 5 次。
     *
     * <p>这里把两半分开记，并记下每一半里**最慢的那几个装饰器**，于是"谁的几秒"可以直接读出来。
     *
     * <p><b>只在 {@link ChunkGenerationProfiler#isEnabled()} 为真时累计</b>：正常游玩零开销
     * （每次调用只是一次 volatile 读），这也免得再给用户加一个启动参数。
     */
    private static final java.util.Map<String, long[]> DECO_NS = new java.util.HashMap<>();
    private static long decoOwnNs;
    private static long decoVanillaNs;
    private static final StringBuilder DECO_BIOMES = new StringBuilder();

    /** 每个区块开始装饰前调用（与 {@link #resetInvocations()} 同处）。 */
    public static void resetDecoProfile() {
        if (!ChunkGenerationProfiler.isEnabled()) {
            return;
        }
        DECO_NS.clear();
        decoOwnNs = 0L;
        decoVanillaNs = 0L;
        DECO_BIOMES.setLength(0);
    }

    /** 记一次 rtgc 自己的 deco（名字 → 累计纳秒）。 */
    public static void noteDeco(final String name, final long ns) {
        if (!ChunkGenerationProfiler.isEnabled()) {
            return;
        }
        long[] slot = DECO_NS.get(name);
        if (slot == null) {
            slot = new long[2];
            DECO_NS.put(name, slot);
        }
        slot[0] += ns;
        slot[1]++;
    }

    /** 记 rtgc 自己的 deco 总耗时（含 {@code preGenerate} 过滤）。 */
    public static void noteOwnDecoNs(final long ns) {
        if (ChunkGenerationProfiler.isEnabled()) {
            decoOwnNs += ns;
        }
    }

    /** 记原版 {@code Biome.decorate} 的耗时。 */
    public static void noteVanillaDecoNs(final long ns) {
        if (ChunkGenerationProfiler.isEnabled()) {
            decoVanillaNs += ns;
        }
    }

    /** 记一个被装饰到的群系（同一个区块可能被多个群系装饰）。 */
    public static void noteDecoratedBiome(final String name) {
        if (!ChunkGenerationProfiler.isEnabled()) {
            return;
        }
        if (DECO_BIOMES.length() > 0) {
            DECO_BIOMES.append(',');
        }
        DECO_BIOMES.append(name);
    }

    /** 一行归因报告：被装饰的群系、rtgc 自己的 deco、原版植被、以及最慢的前 5 个装饰器。 */
    public static String decoReport() {
        final StringBuilder sb = new StringBuilder(192);
        sb.append("decoBiomes=[").append(DECO_BIOMES).append("] rtgDecos=")
          .append(String.format(java.util.Locale.ROOT, "%.2fms", decoOwnNs / 1_000_000.0))
          .append(" vanillaDecorate=")
          .append(String.format(java.util.Locale.ROOT, "%.2fms", decoVanillaNs / 1_000_000.0));

        final java.util.List<java.util.Map.Entry<String, long[]>> top =
                new java.util.ArrayList<>(DECO_NS.entrySet());
        top.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        sb.append(" slowest=[");
        for (int i = 0; i < top.size() && i < 5; i++) {
            final java.util.Map.Entry<String, long[]> e = top.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append('=')
              .append(String.format(java.util.Locale.ROOT, "%.2fms", e.getValue()[0] / 1_000_000.0))
              .append('x').append(e.getValue()[1]);
        }
        sb.append(']');
        return sb.toString();
    }

}
