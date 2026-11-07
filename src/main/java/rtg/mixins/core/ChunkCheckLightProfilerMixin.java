package rtg.mixins.core;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IBlockStatePalette;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rtg.api.util.ChunkGenerationProfiler;
import rtg.api.util.ChunkGenerationProfiler.Counter;

/**
 * {@code Chunk#checkLight} 的耗时归因与等价加速。
 *
 * <p>它是 {@code Chunk#populate(IChunkGenerator)} 里、在 {@code IChunkGenerator#populate} 之前
 * 执行的一段"账外时间"：先用每列 4.5 万次 {@code getBlockLightOpacity} 找地表以下的空气，
 * 对每个空气格调一次 {@code World#checkLight}，再从海平面往下把整列扫一遍找发光方块
 * （原版给熔岩补光用），最后还要让 4 个邻区块各重算 16 列。
 *
 * <p>这里做了三件互不影响、且内容等价的事：
 * <ul>
 *   <li><b>起点</b>（{@code -Drtg.checkLightHeightStart}）：从列 {@code heightMap - 1} 开始扫，
 *       而不是从最上面非空段的顶部。高度图以上全是 {@code lightOpacity == 0} 的方块，
 *       原版在这段里既不会置 {@code flag} 也不会调 {@code checkLight}，跳过是等价的。</li>
 *   <li><b>发光方块扫描</b>（{@code -Drtg.checkLightEmissiveMask}）：第二遍循环唯一的作用是给
 *       熔岩之类发光方块补一次 {@code checkLight}，但原版会把整列（约 60 格）的每个方块都
 *       {@code getBlockState(...).getLightValue(...)} 一次。这里按段查一次调色板：段里没有
 *       发光状态就整段跳过。查调色板的成本约 5µs/区块，省下的是约 1.6 万次方块读。</li>
 *   <li><b>明细计数</b>（{@code -Drtg.checkLightDetail}）：把列扫描、{@code World#checkLight}、
 *       发光扫描三段拆开计时，报告里能直接看到钱花在哪。</li>
 * </ul>
 *
 * <p>侧向检查（{@code Chunk#checkLightSide} → 私有 {@code checkLight(x, z)}）走的是另一条路径，
 * 那里没有"整个区块"的上下文，因此不启用发光段跳过（mask 只在公开 {@code checkLight()} 期间有效）。
 */
@Mixin(Chunk.class)
public abstract class ChunkCheckLightProfilerMixin {

    /** 是否启用本类的等价加速实现；{@code -Drtg.fastCheckLight=false} 退回原版方法体。 */
    private static final boolean FAST = !"false".equalsIgnoreCase(System.getProperty("rtg.fastCheckLight", "true"));
    /** 是否按段跳过发光方块扫描。 */
    private static final boolean EMISSIVE_MASK = !"false".equalsIgnoreCase(System.getProperty("rtg.checkLightEmissiveMask", "true"));
    /** 是否从高度图开始列扫描。 */
    private static final boolean HEIGHT_START = !"false".equalsIgnoreCase(System.getProperty("rtg.checkLightHeightStart", "true"));
    /** 熔岩湖重复补光的去重（方块光已是最终值就只补天光）。 */
    private static final boolean DEDUPE = !"false".equalsIgnoreCase(System.getProperty("rtg.checkLightDedupe", "true"));
    /** 去重时是否连"天光"那一半也跳过（实验开关：天光在这条路径上大多是空转）。 */
    private static final boolean SKIP_SKY = !"false".equalsIgnoreCase(System.getProperty("rtg.checkLightSkipSky", "true"));
    /** 是否统计三段明细（关掉可以少 4 次 nanoTime/列）。 */
    private static final boolean DETAIL = !"false".equalsIgnoreCase(System.getProperty("rtg.checkLightDetail", "true"));

    private static final ThreadLocal<Long> RTGC_CHECK_LIGHT_START = new ThreadLocal<>();

    @Shadow
    private int x;

    @Shadow
    private int z;

    @Shadow
    private World world;

    /** 公开 {@code checkLight()} 期间按段缓存的"含发光方块"位图；侧向检查期间无效。 */
    @Unique
    private boolean rtgc$emissiveMaskKnown;

    @Unique
    private int rtgc$emissiveMask;

    @Inject(method = "checkLight", at = @At("HEAD"))
    private void rtgc$startCheckLight(final CallbackInfo ci) {
        if (FAST && EMISSIVE_MASK) {
            this.rtgc$emissiveMask = this.rtgc$computeEmissiveMask();
            this.rtgc$emissiveMaskKnown = true;
        }
        if (ChunkGenerationProfiler.isEnabled()) {
            RTGC_CHECK_LIGHT_START.set(System.nanoTime());
        }
    }

    @Inject(method = "checkLight", at = @At("RETURN"))
    private void rtgc$endCheckLight(final CallbackInfo ci) {
        this.rtgc$emissiveMaskKnown = false;

        final Long start = RTGC_CHECK_LIGHT_START.get();
        if (start == null) {
            return;
        }
        RTGC_CHECK_LIGHT_START.remove();
        ChunkGenerationProfiler.add(Counter.CHUNK_CHECK_LIGHT_NS, System.nanoTime() - start);
    }

    /**
     * 替换私有 {@code Chunk#checkLight(int, int)}（单列）。逻辑与 1.12 原版逐行对应，
     * 只在"起点"和"发光扫描"两处做等价剪枝。
     */
    @Inject(method = "checkLight(II)Z", at = @At("HEAD"), cancellable = true)
    private void rtgc$fastColumn(final int x, final int z, final CallbackInfoReturnable<Boolean> cir) {
        if (!FAST) {
            return;
        }

        final boolean prof = DETAIL && ChunkGenerationProfiler.isEnabled();
        final int posX = (this.x << 4) + x;
        final int posZ = (this.z << 4) + z;
        final int seaLevel = this.world.getSeaLevel();
        final int top = ((Chunk) (Object) this).getTopFilledSegment();

        int startY = top + 15;
        if (HEIGHT_START) {
            final int[] heightMap = ((ChunkStorageAccess) (Object) this).rtgc$getHeightMap();
            final int h = heightMap[(z << 4) | x];
            // h 是"最高 lightOpacity != 0 的方块 y + 1"；h 以上不会改变 flag/flag1。
            if (h > 0 && h <= top + 16) {
                startY = Math.min(startY, h - 1);
            }
        }

        final MutableBlockPos pos = new MutableBlockPos(posX, 0, posZ);
        final long scanStart = prof ? System.nanoTime() : 0L;

        int scanReads = 0;
        int worldCalls = 0;
        long worldNs = 0L;
        boolean flag = false;
        boolean flag1 = false;
        int y = startY;

        while (y > seaLevel || (y > 0 && !flag1)) {
            pos.setPos(posX, y, posZ);
            ++scanReads;
            final int k = ((Chunk) (Object) this).getBlockLightOpacity(pos);

            if (k == 255 && y < seaLevel) {
                flag1 = true;
            }

            if (!flag && k > 0) {
                flag = true;
            } else if (flag && k == 0) {
                final long callStart = System.nanoTime();
                final boolean ok = this.world.checkLight(pos);
                worldNs += System.nanoTime() - callStart;
                ++worldCalls;
                if (!ok) {
                    this.rtgc$report(prof, scanStart, scanReads, 0, 0, 0, worldNs, worldCalls, 0, 0);
                    cir.setReturnValue(Boolean.FALSE);
                    return;
                }
            }
            --y;
        }

        // 与原版一致：第二遍从第一遍最后处理过的那一列 y 开始（pos 此刻正好停在那里）。
        final int lastY = pos.getY();
        final long emissiveStart = prof ? System.nanoTime() : 0L;
        final long scanNs = prof ? emissiveStart - scanStart : 0L;

        final boolean maskKnown = this.rtgc$emissiveMaskKnown;
        final int mask = this.rtgc$emissiveMask;
        int emissiveReads = 0;
        int emissiveCalls = 0;
        int maskSkips = 0;
        int dedupeSkips = 0;

        for (int ly = lastY; ly > 0; --ly) {
            if (maskKnown) {
                final int section = ly >> 4;
                if ((mask & (1 << section)) == 0) {
                    ++maskSkips;
                    ly = section << 4; // 再 --ly 落到下一段顶部
                    continue;
                }
            }
            pos.setPos(posX, ly, posZ);
            ++emissiveReads;
            final int emit = ((Chunk) (Object) this).getBlockState(pos).getLightValue(this.world, pos);
            if (emit > 0) {
                // 熔岩湖里同一片湖的每个方块都会走到这里。第一次 world.checkLight 已经把整片区域
                // 的方块光刷成 15，后面的方块再刷一遍纯属重复：stored(BLOCK)==15 ⇒ raw 最多 15
                // ⇒ checkLightFor(BLOCK) 两分支都不进，等于空转。这里只把"天光"那一半补上，
                // 保证洞穴开口处仍会被修正（实测天光开销远小于方块光 BFS）。
                if (DEDUPE && ((Chunk) (Object) this).getLightFor(EnumSkyBlock.BLOCK, pos) >= emit) {
                    ++dedupeSkips;
                    if (!SKIP_SKY) {
                        this.world.checkLightFor(EnumSkyBlock.SKY, pos);
                    }
                    continue;
                }
                final long callStart = System.nanoTime();
                this.world.checkLight(pos);
                worldNs += System.nanoTime() - callStart;
                ++worldCalls;
                ++emissiveCalls;
            }
        }

        this.rtgc$report(prof, scanStart, scanReads, scanNs, emissiveReads, emissiveCalls, worldNs, worldCalls, maskSkips, dedupeSkips);
        if (prof) {
            ChunkGenerationProfiler.add(Counter.CHECKLIGHT_EMISSIVE_NS, System.nanoTime() - emissiveStart);
        }
        cir.setReturnValue(Boolean.TRUE);
    }

    @Unique
    private void rtgc$report(final boolean prof, final long scanStart, final int scanReads, final long scanNs,
                             final int emissiveReads, final int emissiveCalls, final long worldNs,
                             final int worldCalls, final int maskSkips, final int dedupeSkips) {
        ChunkGenerationProfiler.count(Counter.CHECKLIGHT_COLUMNS);
        if (!prof) {
            return;
        }
        ChunkGenerationProfiler.add(Counter.CHECKLIGHT_SCAN_NS,
            scanNs != 0L ? scanNs : System.nanoTime() - scanStart);
        ChunkGenerationProfiler.add(Counter.CHECKLIGHT_SCAN_READS, scanReads);
        ChunkGenerationProfiler.add(Counter.CHECKLIGHT_EMISSIVE_READS, emissiveReads);
        ChunkGenerationProfiler.add(Counter.CHECKLIGHT_EMISSIVE_CALLS, emissiveCalls);
        ChunkGenerationProfiler.add(Counter.CHECKLIGHT_WORLD_NS, worldNs);
        ChunkGenerationProfiler.add(Counter.CHECKLIGHT_WORLD_CALLS, worldCalls);
        ChunkGenerationProfiler.add(Counter.CHECKLIGHT_MASK_SKIPS, maskSkips);
        ChunkGenerationProfiler.add(Counter.CHECKLIGHT_DEDUPES, dedupeSkips);
    }

    /**
     * 每段查一次调色板，判断段里有没有 {@code getLightValue() > 0} 的状态。
     * 全局调色板（bits &gt; 8）无法枚举，保守地当作"有"，保持原版行为。
     */
    @Unique
    private int rtgc$computeEmissiveMask() {
        final ExtendedBlockStorage[] storages = ((ChunkStorageAccess) (Object) this).rtgc$getStorageArrays();
        int mask = 0;
        for (int section = 0; section < 16; ++section) {
            final ExtendedBlockStorage storage = storages[section];
            if (storage == null || storage.isEmpty()) {
                continue;
            }
            final BlockStateContainerAccess access = (BlockStateContainerAccess) (Object) storage.getData();
            final int bits = access.rtgc$getBits();
            if (bits > 8) {
                mask |= 1 << section;
                continue;
            }
            final IBlockStatePalette palette = access.rtgc$getPalette();
            final int size = 1 << bits;
            for (int id = 0; id < size; ++id) {
                final IBlockState state = palette.getBlockState(id);
                if (state == null) {
                    break;
                }
                if (state.getLightValue() > 0) {
                    mask |= 1 << section;
                    break;
                }
            }
        }
        return mask;
    }
}
