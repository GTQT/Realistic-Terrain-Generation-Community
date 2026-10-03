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
 * 结构与常量逐项对应，但有**三处已注明的偏离**：
 *
 * <ol>
 *   <li><b>方块替换</b>：RWG 用 Et Futurum Requiem 的 {@code ModBlocks.CAVE_VINE}
 *       （发光浆果藤，`ganymedes01.etfuturum:Et-Futurum-Requiem`，**1.7.10 专属**）。
 *       1.12.2 没有该模组，按用户要求改用**原版 {@link Blocks#VINE}**，
 *       以 {@code UP = true} 垂直挂在洞顶下方。</li>
 *   <li><b>门控替换</b>：RWG 用 {@code hasMountainChainNearby(...)}（9 点采样要求
 *       {@code RealisticBiomeMountainChain}）。rtgc 改为读取
 *       {@link ChunkLandscape#riverCaveCeiling} —— 即「本列确实被开凿过山间河洞」。
 *       这比 RWG 的邻域采样更精确（只在真有洞的地方挂）。</li>
 *   <li><b>长度自己定</b>：RWG 只算到"洞顶那一格空气"，**实际摆放交给 EFR 的
 *       {@code WorldGenCaveVines}**（同样 1.7.10 专属，本仓库无从照抄它的长度规则）。
 *       1.12.2 侧由 {@link #placeVine} 自己决定：长度取 {@code 1..可用空间} 的随机值，
 *       上限就是**水面之上那一格** ⇒ 最长的一株正好垂到水面。用户要求"长一点、最好最长能到水面"。</li>
 * </ol>
 *
 * <p>⚠ 本类注释在 1.0.33 之前写着"rtgc 尚无山地链系统…待山地链移植完成后再补 RWG 的邻域判定"——
 * **那句已经过期**：山地链现已落地（隧道门控本身就在用 {@code mountainChainRiverHost}）。
 * 现在是**有意的选择**（用"本列真开凿过"代替"邻域有链"），不是待办。
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

    /**
     * 扫描下界（RWG 原值 63），同时也是**水面之上最低的那一格空气**：开凿阶段把
     * {@code y <= }{@link UndergroundRiver#CENTER_Y}（62）填成水、以上填空气，所以
     * y=63 就是紧贴水面的那一格。藤蔓最长正好垂到这一格 ⇒ **触到水面**。
     */
    private static final int MIN_Y = 63;
    /** RWG 的扫描上界。 */
    private static final int MAX_Y = 115;
    /** 交汇洞厅处的挂载概率系数（RWG：{@code 0.42f * junction}）。 */
    private static final float JUNCTION_CHANCE = 0.42f;
    /** 隧道处的挂载概率系数（RWG：{@code 0.06f * tunnel}）。 */
    private static final float TUNNEL_CHANCE = 0.06f;

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

    /**
     * 从洞顶下方这一格起向下挂藤蔓，长度**随机**，上限就是可用空间
     * （洞顶那一格 → {@link #MIN_Y}，即水面之上那一格）：
     *
     * <ul>
     *   <li><b>隧道段</b>：洞顶多在 63–70（{@code 62 + round(√tunnel × 8)}）⇒ 可用 1–8 格，
     *       长度均值 ≈4.5，最长的那一株**正好触到水面**（约 1/8 的藤会到水面）；</li>
     *   <li><b>洞厅段</b>：洞顶可到 80–100 ⇒ 可用十几到三十几格，偶尔出现垂到水面的长藤帘。</li>
     * </ul>
     *
     * 长度取 {@code 1..available} 的**均匀分布**（用户要求："长一点、最长能到水面、随机长度"）。
     * 中途撞到非空气方块（洞底、石柱、别的藤）就停下 ⇒ 实际"最长"是**到水面或洞底，先到者为准**；
     * 干洞（洞底高于水面）里的藤就停在自己那一层的洞底上方。
     *
     * <p>⚠ 这些藤蔓**不会自己变长**（已核对 1.12.2 原版 {@code BlockVine}）：{@code updateTick}
     * 向下长那一支要求"随机朝向为 DOWN **且** 该藤还保留至少一个**水平**面"（那是给墙面藤用的）；
     * 我们挂的是纯洞顶藤（只有 {@code UP=true}），而 {@code getNumGrownFaces} 把 {@code UP} 算作
     * "已长出的面" ⇒ 既不会被 {@code neighborChanged} 掉落，也不会再向下蔓延。**生成多长就是多长。**
     */
    private static void placeVine(final World world, final Random rand, final int x, final int startY, final int z,
                                  final IBlockState vine) {

        // findCeilingAir 只返回 y >= MIN_Y，故 available >= 1
        final int available = startY - MIN_Y + 1;
        final int length = 1 + rand.nextInt(available);
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
