package rtg.world.gen;

import java.util.Random;

import net.minecraft.block.BlockVine;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import rtg.api.world.RTGWorld;


/**
 * 山间河洞里的**洞穴藤蔓**。
 * <p>
 * 移植自 RWG 的 {@code rwg/support/EtFuturumCaveVines.java}（{@code rwg2} 分支）。
 * 结构与常量逐项对应，但有**两处已注明的替换**：
 *
 * <ol>
 *   <li><b>方块替换</b>：RWG 用 Et Futurum Requiem 的 {@code ModBlocks.CAVE_VINE}
 *       （发光浆果藤，`ganymedes01.etfuturum:Et-Futurum-Requiem`，**1.7.10 专属**）。
 *       1.12.2 没有该模组，按用户要求改用**原版 {@link Blocks#VINE}**，
 *       以 {@code UP = true} 垂直挂在洞顶下方。</li>
 *   <li><b>门控替换</b>：RWG 用 {@code hasMountainChainNearby(...)}（9 点采样要求
 *       {@code RealisticBiomeMountainChain}）。rtgc 尚无山地链系统，改为读取
 *       {@link ChunkLandscape#riverCaveCeiling} —— 即「本列确实被开凿过山间河洞」。
 *       这比 RWG 的邻域采样更精确（只在真有洞的地方挂），且不必等山地链先落地。
 *       山地链移植完成后，应再补上 RWG 原版的邻域判定。</li>
 * </ol>
 *
 * 其余部分与 RWG 一致：
 * <ul>
 *   <li>逐列遍历 16×16；</li>
 *   <li>概率式 {@code junction > 0f ? 0.42f * junction : 0.06f * tunnel}；</li>
 *   <li>自 {@code min(115, height - 1)} 向下扫到 {@code 63}，找「本身是空气、且上方方块
 *       的下表面为实心」的位置，即洞顶下方的第一格空气。</li>
 * </ul>
 *
 * @since 1.0.10
 */
public final class RiverCaveVines {

    /** RWG 的扫描下界。 */
    private static final int MIN_Y = 63;
    /** RWG 的扫描上界。 */
    private static final int MAX_Y = 115;
    /** 交汇洞厅处的挂载概率系数（RWG：{@code 0.42f * junction}）。 */
    private static final float JUNCTION_CHANCE = 0.42f;
    /** 隧道处的挂载概率系数（RWG：{@code 0.06f * tunnel}）。 */
    private static final float TUNNEL_CHANCE = 0.06f;
    /** 单株藤蔓的最大下垂长度。原版藤蔓本身不限长，取 1–4 格接近洞穴藤蔓的观感。 */
    private static final int MAX_VINE_LENGTH = 4;

    private RiverCaveVines() {}

    /**
     * 为一个区块挂洞穴藤蔓。对应 RWG {@code EtFuturumCaveVines.decorate}。
     *
     * @param world     世界
     * @param rand      装饰用随机源（沿用区块的 {@code rand}，与 RWG 一致）
     * @param rtgWorld  噪声源
     * @param landscape 该区块的地形数据（{@link ChunkLandscape#riverCaveCeiling} 由开凿阶段写入）
     * @param baseX     区块原点的**世界** X
     * @param baseZ     区块原点的**世界** Z
     */
    public static void decorate(final World world, final Random rand, final RTGWorld rtgWorld,
                                final ChunkLandscape landscape, final int baseX, final int baseZ) {

        final float[] strengths = new float[2];
        final IBlockState vine = Blocks.VINE.getDefaultState().withProperty(BlockVine.UP, true);

        final rtg.world.biome.RtgBiomeLayout layout = rtg.world.biome.RtgLayoutAccess.current();
        if (layout == null) {
            return;
        }

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {

                final int k = localX * 16 + localZ;
                // 门控（替换项，见类注释）：本列必须真的被开凿过山间河洞
                if (landscape.riverCaveCeiling[k] <= 0) {
                    continue;
                }

                final int x = baseX + localX;
                final int z = baseZ + localZ;
                // 隧道 / 交汇强度改由 RWG 布局提供（与开凿阶段同源，故两者必然一致）
                strengths[0] = layout.getRiverTunnelStrength(x, z);
                strengths[1] = layout.getRiverJunctionStrength(x, z);
                final float tunnel = strengths[0];
                final float junction = strengths[1];

                final float chance = junction > 0f ? JUNCTION_CHANCE * junction : TUNNEL_CHANCE * tunnel;
                if (chance <= 0f || rand.nextFloat() >= chance) {
                    continue;
                }

                final int ceilingAir = findCeilingAir(world, x, z);
                if (ceilingAir < 0) {
                    continue;
                }

                placeVine(world, rand, x, ceilingAir, z, vine);
            }
        }
    }

    /**
     * RWG {@code findCeilingAir} 的等价物：自 {@code min(115, height - 1)} 向下找
     * 「本身是空气、且上方方块下表面为实心」的第一格。
     */
    private static int findCeilingAir(final World world, final int x, final int z) {

        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        final int top = Math.min(MAX_Y, world.getHeight(x, z) - 1);

        for (int y = top; y >= MIN_Y; y--) {
            p.setPos(x, y, z);
            if (!world.isAirBlock(p)) {
                continue;
            }
            above.setPos(x, y + 1, z);
            if (world.getBlockState(above).isSideSolid(world, above, EnumFacing.DOWN)) {
                return y;
            }
        }
        return -1;
    }

    /** 从洞顶下方这一格起向下挂 {@code 1..MAX_VINE_LENGTH} 格原版藤蔓。 */
    private static void placeVine(final World world, final Random rand, final int x, final int startY, final int z,
                                  final IBlockState vine) {

        final int length = 1 + rand.nextInt(MAX_VINE_LENGTH);
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (int i = 0; i < length; i++) {
            final int y = startY - i;
            if (y < MIN_Y) {
                break;
            }
            p.setPos(x, y, z);
            if (!world.isAirBlock(p)) {
                break;
            }
            // flag 2 = 发送到客户端、不触发邻块更新（与原版世界生成的写法一致）
            world.setBlockState(p, vine, 2);
        }
    }
}
