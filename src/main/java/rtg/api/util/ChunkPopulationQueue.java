package rtg.api.util;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.registry.GameRegistry;
import rtg.mixins.core.ChunkPopulateAccess;

import java.util.ArrayDeque;

/**
 * 防止区块 population 重入。
 *
 * <p>1.12 在 {@code provideChunk} 生成区块后立即调用 {@code Chunk#populate}。
 * 装饰阶段读取未加载的邻区块时，{@code World#getBlockState} 会再次触发
 * {@code provideChunk} 和 {@code populate}；同一个 {@code BiomeDecorator} 被重入，
 * 原版因此抛出 {@code Already decorating}。
 *
 * <p>这里不伪造方块读取，而是把装饰期间的嵌套请求先入队，等外层 populate 结束后
 * 再按正常 {@code Chunk#populate} 路径 drain。排队区块仍用自己的 chunk seed
 * 重新播种，地形、结构和装饰逻辑不变；只有跨区块读取的时序被推迟。
 */
public final class ChunkPopulationQueue {

    private static final int MAX_DRAIN = 8192;

    private static final ThreadLocal<ArrayDeque<Chunk>> QUEUE =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Boolean> POPULATING =
            ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Integer> DECORATION_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private ChunkPopulationQueue() {
    }

    /** 进入装饰阶段；只有装饰期间的区块请求会被延迟。 */
    public static void enterDecoration() {
        DECORATION_DEPTH.set(DECORATION_DEPTH.get() + 1);
    }

    public static void exitDecoration() {
        int depth = DECORATION_DEPTH.get() - 1;
        if (depth <= 0) {
            DECORATION_DEPTH.remove();
        } else {
            DECORATION_DEPTH.set(depth);
        }
    }

    private static boolean isDecorating() {
        return DECORATION_DEPTH.get() > 0;
    }

    public static void submit(Chunk chunk, IChunkProvider provider, IChunkGenerator generator, World world) {
        if (isDecorating()) {
            enqueue(chunk);
            return;
        }

        // 非装饰的嵌套请求保持原版同步语义；队列只在最外层 submit 结束时 drain。
        if (POPULATING.get()) {
            chunk.populate(provider, generator);
            return;
        }

        POPULATING.set(Boolean.TRUE);
        try {
            chunk.populate(provider, generator);
            drain(provider, generator, world);
        } finally {
            QUEUE.get().clear();
            POPULATING.remove();
        }
    }

    private static void enqueue(Chunk chunk) {
        if (chunk == null || chunk.isTerrainPopulated()) {
            return;
        }

        ArrayDeque<Chunk> queue = QUEUE.get();
        if (!queue.contains(chunk)) {
            queue.add(chunk);
            ChunkGenerationProfiler.count(ChunkGenerationProfiler.Counter.POPULATION_QUEUE_ENQUEUED);
        }
    }

    private static void drain(IChunkProvider provider, IChunkGenerator generator, World world) {
        ArrayDeque<Chunk> queue = QUEUE.get();
        int processed = 0;

        while (!queue.isEmpty() && processed++ < MAX_DRAIN) {
            Chunk chunk = queue.poll();
            if (chunk == null || chunk.isTerrainPopulated()) {
                continue;
            }

            // 先走原版邻区块检查；邻区块没准备好时再强制补齐。
            chunk.populate(provider, generator);

            if (!chunk.isTerrainPopulated()) {
                // 该区块是在装饰期间生成的，+x/+z 可能还没加载；
                // 用 protected populate(generator) 强制完成，避免永久未装饰。
                // 它读取到的新区块由 provideChunk 继续入队。
                ChunkGenerationProfiler.count(ChunkGenerationProfiler.Counter.POPULATION_QUEUE_FORCED);
                ((ChunkPopulateAccess) chunk).rtgc$populate(generator);
                GameRegistry.generateWorld(chunk.x, chunk.z, world, generator, provider);
            }
            ChunkGenerationProfiler.count(ChunkGenerationProfiler.Counter.POPULATION_QUEUE_DRAINED);
        }

        if (!queue.isEmpty()) {
            ChunkGenerationProfiler.add(ChunkGenerationProfiler.Counter.POPULATION_QUEUE_DROPPED, queue.size());
            Logger.warn("RTG population queue exceeded {} chunks; dropping {} pending chunks",
                    MAX_DRAIN, queue.size());
            queue.clear();
        }
    }
}
