package rtg.api.util;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import biomesoplenty.api.block.BOPBlocks;


/**
 * BOP 海洋装饰的**事后清理** —— RWG {@code RealisticBiomeBOPOcean.sanitizeColumn} 的等价物。
 *
 * <h2>为什么需要它</h2>
 *
 * BOP 自己的装饰器是**按 BOP 的地形假设**放珊瑚/海草的（它自己的海底深度与群系分布）。
 * 放到 RWG 的地形上，这些判据就会失效 ⇒ 出现**悬空珊瑚、海草戳出水面、珊瑚嵌在陆地里**。
 * RWG 于是在"装饰完成"之后补一遍验算：逐列扫过去，**站不住的水下装饰直接换成水**。
 *
 * <h2>逐行对应</h2>
 *
 * <pre>
 * RWG RealisticBiomeBOPOcean:16-17   MIN_DECORATION_OFFSET = -3, MAX_DECORATION_OFFSET = 30
 * RWG :28-32   对 (chunkX-3 .. chunkX+30) x (chunkZ-3 .. chunkZ+30) 的 34x34 逐列调用
 * RWG :35-45   sanitizeColumn：y 从 1 到 62 逐格看
 * RWG :79-81   isMatchingBiome：该列群系必须是这个海洋群系
 * </pre>
 *
 * <h2>与 RWG 的两处差异（都是 1.12.2 上无法照抄的部分）</h2>
 *
 * <ol>
 *   <li><b>不重写海草柱的 metadata。</b> RWG 的 1.7.10 里海草是
 *       {@code coral1} 的 meta 8/9/10/11（底/中/顶/单节），所以它能"重排整柱"。
 *       BOP <b>1.12.2</b> 的珊瑚是 {@code BOPBlocks.coral}，其变体枚举
 *       （{@code BlockBOPCoral.CoralType}，实测 {@code javap}）只有
 *       {@code PINK / ORANGE / BLUE / GLOWING / ALGAE} —— **没有海草的变体**；
 *       1.12 的海草由 BOP 自己的生成器按它的状态机放置。
 *       照抄那套 meta 重写在这里没有对应物，硬做就是**发明**，故本类只做
 *       "站不住就删"这一半（那正是可见症状的来源）。</li>
 *   <li><b>用 {@code Block.canBlockStay(world, pos, state)} 判"站得住"</b>，
 *       而不是 RWG 手写的 {@code canBlockStay(world,x,y,z,meta) && 上面是水}。
 *       1.12 把这类判据统一成了这个钩子，BOP 的珊瑚/植物块**自己覆写了它**
 *       （实测 {@code javap biomesoplenty.common.block.BlockBOPCoral} /
 *       {@code BlockBOPPlant} 都有 {@code canBlockStay(World, BlockPos, IBlockState)}），
 *       所以这是"问 BOP 自己"而不是替它猜。</li>
 * </ol>
 */
public final class OceanDecorationSanitizer {

    private OceanDecorationSanitizer() {}

    /** RWG 的扫掠范围：区块原点外扩 −3 … +30（覆盖 16×16 的装饰区加上边缘）。 */
    private static final int MIN_OFFSET = -3;
    private static final int MAX_OFFSET = 30;

    /** RWG 只看到 {@code y < 63}（海平面附近），照抄。 */
    private static final int MAX_Y = 62;

    /**
     * 扫掠并清理。
     *
     * @param chunkX 区块原点的**方块坐标**（{@code chunkPos.x * 16}），与 RWG 的调用口径一致
     * @param chunkZ 同上
     * @param baseBiome 该海洋群系的 MC 群系；只有群系与它相同的列才处理
     *              （RWG {@code isMatchingBiome}：不要动隔壁群系的东西）
     */
    public static void sanitize(final World world, final int chunkX, final int chunkZ, final Biome baseBiome) {
        if (world == null || baseBiome == null) {
            return;
        }
        final IBlockState water = Blocks.WATER.getDefaultState();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = chunkX + MIN_OFFSET; x <= chunkX + MAX_OFFSET; x++) {
            for (int z = chunkZ + MIN_OFFSET; z <= chunkZ + MAX_OFFSET; z++) {

                pos.setPos(x, 0, z);
                if (world.getBiome(pos) != baseBiome) {
                    continue;                   // RWG isMatchingBiome
                }

                for (int y = 1; y <= MAX_Y; y++) {
                    pos.setPos(x, y, z);
                    final IBlockState state = world.getBlockState(pos);
                    final Block block = state.getBlock();
                    if (!isUnderwaterDecoration(block)) {
                        continue;
                    }
                    // ⚠ 1.12 的 `canBlockStay(World, BlockPos, IBlockState)` **不在** `Block` 上，
                    // 它定义在 `BlockBush`（以及覆写它的 BOP 装饰块）上 —— RWG 的 1.7.10 里
                    // 它反而是 BOP 自己的 5 参重载。故这里先确认它确实是 BlockBush，
                    // 不是就**不动它**（宁可不清理，也不要替别的方块猜判据）。
                    if (!(block instanceof net.minecraft.block.BlockBush)) {
                        continue;
                    }
                    if (!((net.minecraft.block.BlockBush) block).canBlockStay(world, pos, state)) {
                        // 站不住（悬空 / 不在水里 / 基质不对）⇒ 换回水，与 RWG 一致
                        world.setBlockState(pos, water, 2);
                    }
                }
            }
        }
    }

    /**
     * 该方块是否属于"会被 BOP 放错的水下装饰"。
     *
     * <p>只认 BOP 的三个块：珊瑚（{@code BOPBlocks.coral}）与两个植物块
     * （{@code plant_0} / {@code plant_1} —— 1.12 的海草若存在，属于其中之一）。
     * 不认识的方块一律不碰：宁可不清理，也不要动到别的模组的东西。
     */
    private static boolean isUnderwaterDecoration(final Block block) {
        return block == BOPBlocks.coral
                || block == BOPBlocks.plant_0
                || block == BOPBlocks.plant_1;
    }
}
