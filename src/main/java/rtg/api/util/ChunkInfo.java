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

}
