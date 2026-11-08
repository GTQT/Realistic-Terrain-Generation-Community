package rtg.api.util;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * 标记当前线程正在执行 {@code BiomeDecorator#generateOres}。
 *
 * <p>原版 {@code WorldGenMinable} 在矿脉生成时逐方块调用
 * {@code World#getBlockState} / {@code World#setBlockState}。生成期既没有玩家，也不需要
 * 邻居/客户端通知；这两个调用会做 chunk 查找和 {@code markAndNotifyBlock}，是纯开销。
 *
 * <p>只在原版 ore 生成期间打开：{@code WorldGenMinableMixin} 看到标记后直接读写当前
 * {@code Chunk}；未加载区块仍然回退到原版路径，避免改变跨区块语义。该标记不覆盖
 * 其他模组自己的 ore generator，也不覆盖 BOP 的装饰生成器。
 *
 * <p>一条矿脉的所有探针都落在很小的范围内，绝大多数在同一个区块里，所以这里按
 * "上一次查到的区块坐标" 做一层缓存，省掉 {@code ChunkProviderServer#getLoadedChunk}
 * 的 Map 查找（实测命中率 92.8%，每区块 3 万次探针）。
 */
public final class FastOreContext {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("rtg.fastOre", "true"));

    /** 最近一次命中的区块；服务器生成线程是单线程，直接放静态字段。 */
    private static int cachedChunkX = Integer.MIN_VALUE;
    private static int cachedChunkZ = Integer.MIN_VALUE;
    private static Chunk cachedChunk;

    private FastOreContext() {
    }

    public static void enter() {
        ACTIVE.set(Boolean.TRUE);
        invalidateChunkCache();
    }

    public static void exit() {
        ACTIVE.remove();
        invalidateChunkCache();
    }

    public static boolean isActive() {
        return ENABLED && ACTIVE.get();
    }

    private static void invalidateChunkCache() {
        cachedChunkX = Integer.MIN_VALUE;
        cachedChunkZ = Integer.MIN_VALUE;
        cachedChunk = null;
    }

    public static Chunk getLoadedChunk(World world, BlockPos pos) {
        return getLoadedChunkAt(world, pos.getX(), pos.getZ());
    }

    /** 不分配 {@link BlockPos} 的重载，供 {@code FastOreGen} 的逐方块循环使用。 */
    public static Chunk getLoadedChunkAt(World world, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (chunkX == cachedChunkX && chunkZ == cachedChunkZ) {
            return cachedChunk;
        }
        Chunk chunk = world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
        cachedChunkX = chunkX;
        cachedChunkZ = chunkZ;
        cachedChunk = chunk;
        return chunk;
    }

    private static int maskMismatchLogs;

    /** 诊断用：位图判定为非石头、但逐格查表是天然石。 */
    public static void noteMaskMismatch(String detail) {
        if (maskMismatchLogs++ < 5) {
            org.apache.logging.log4j.LogManager.getLogger("RTG").warn("[RTG] FastOreGen 位图不一致: " + detail);
        }
    }

    public static boolean setOreBlock(World world, BlockPos pos, IBlockState state, int flags) {
        Chunk chunk = getLoadedChunk(world, pos);
        if (chunk == null) {
            return world.setBlockState(pos, state, flags);
        }
        // Chunk#setBlockState still handles light/TE/break/onBlockAdded; it only skips
        // World#markAndNotifyBlock (neighbor + client notifications), which is pure overhead
        // while the world is being generated.
        return chunk.setBlockState(pos, state) != null;
    }
}
