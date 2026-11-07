package rtg.api.util;

import biomesoplenty.api.block.BOPBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;

/**
 * 清理 BOP 放错位置的水下装饰。
 *
 * <p>BOP 按自己的海底高度和群系分布放置珊瑚/海草；换到 RWG 地形后会出现悬空、
 * 露出水面或嵌入陆地的方块。这里在装饰完成后扫过 34×34 区域，把站不住的水下
 * 装饰换回水。
 *
 * <p>与 RWG 的差异：
 * <ul>
 *   <li>1.12 的 BOP 珊瑚没有海草 metadata，无法重排整柱，只做“站不住就删”；</li>
 *   <li>站得住与否交给 {@link BlockBush#canBlockStay} 判断，不替 BOP 猜规则。</li>
 * </ul>
 */
public final class OceanDecorationSanitizer {

    private OceanDecorationSanitizer() {}

    /** RWG 的扫掠范围：区块原点外扩 -3..+30。 */
    private static final int MIN_OFFSET = -3;
    private static final int MAX_OFFSET = 30;

    /** 只检查海平面附近，与 RWG 的 y < 63 一致。 */
    private static final int MAX_Y = 62;

    /**
     * 清理以区块原点外扩 -3..+30、y=1..62 的区域。
     *
     * @param chunkX 区块原点的方块坐标（{@code chunkPos.x * 16}）
     * @param chunkZ 同上
     * @param baseBiome 只处理群系等于它的列
     */
    public static void sanitize(final World world, final int chunkX, final int chunkZ, final Biome baseBiome) {
        if (world == null || baseBiome == null) {
            return;
        }
        final IBlockState water = Blocks.WATER.getDefaultState();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final BiomeProvider biomeProvider = world.getBiomeProvider();

        final int minX = chunkX + MIN_OFFSET;
        final int maxX = chunkX + MAX_OFFSET;
        final int minZ = chunkZ + MIN_OFFSET;
        final int maxZ = chunkZ + MAX_OFFSET;

        // 扫掠范围会覆盖未加载的邻区块；先按区块判断加载状态，
        // 避免 World#getBlockState 触发级联 generate/populate。
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                pos.setPos(cx << 4, 0, cz << 4);
                if (!world.isBlockLoaded(pos, false)) {
                    continue;
                }

                final Chunk chunk = world.getChunk(cx, cz);
                final int startX = Math.max(minX, cx << 4);
                final int endX = Math.min(maxX, (cx << 4) + 15);
                final int startZ = Math.max(minZ, cz << 4);
                final int endZ = Math.min(maxZ, (cz << 4) + 15);

                for (int x = startX; x <= endX; x++) {
                    for (int z = startZ; z <= endZ; z++) {
                        pos.setPos(x, 0, z);
                        if (chunk.getBiome(pos, biomeProvider) != baseBiome) {
                            continue;
                        }

                        for (int y = 1; y <= MAX_Y; y++) {
                            pos.setPos(x, y, z);
                            // 区块已加载，直接读 Chunk storage，跳过 World 的坐标检查和区块查找。
                            final IBlockState state = chunk.getBlockState(x & 15, y, z & 15);
                            final Block block = state.getBlock();
                            if (!isUnderwaterDecoration(block)) {
                                continue;
                            }
                            // canBlockStay 定义在 BlockBush 上；非 BlockBush 不猜判据，原样保留。
                            if (!(block instanceof BlockBush)) {
                                continue;
                            }
                            if (!((BlockBush) block).canBlockStay(world, pos, state)) {
                                // 悬空、离水或基质不对：换回水，与 RWG 一致。
                                world.setBlockState(pos, water, 2);
                            }
                        }
                    }
                }
            }
        }
    }

    /** 只清理 BOP 珊瑚和两个植物块；其他模组方块不动。 */
    private static boolean isUnderwaterDecoration(final Block block) {
        return block == BOPBlocks.coral
                || block == BOPBlocks.plant_0
                || block == BOPBlocks.plant_1;
    }
}
