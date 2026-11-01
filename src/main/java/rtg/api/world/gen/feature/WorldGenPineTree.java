package rtg.api.world.gen.feature;

import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockOldLeaf;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.Random;


/**
 * RWG {@code rwg2.deco.trees.DecoPineTree} 的逐行移植（1.12 API 适配）。
 *
 * <p>算法是 1.7.10 的「大云杉/松」：先检查空间，再从顶往下按层铺叶团，最后立树干 +
 * {@code createTrunk} 的 4 根侧根。RWG 里 {@code height} 只决定树冠层数，树干 metadata
 * 写死 0（橡木）；这里照抄。
 */
public class WorldGenPineTree extends WorldGenerator {

    private final int height;
    private final IBlockState leaves;

    public WorldGenPineTree(int height, int metadata) {
        this.height = height;
        this.leaves = Blocks.LEAVES.getDefaultState()
                .withProperty(BlockOldLeaf.VARIANT, BlockPlanks.EnumType.byMetadata(metadata & 3));
    }

    @Override
    public boolean generate(World world, Random rand, BlockPos pos) {

        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();

        final int l = rand.nextInt(this.height * 2) + this.height * 2;
        final int i1 = this.height + rand.nextInt(this.height);
        final int j1 = l - i1;
        final int k1 = 2 + rand.nextInt(2);
        boolean flag = true;

        if (y < 1 || y + l + 1 > 256) {
            return false;
        }

        for (int l1 = y; l1 <= y + 1 + l && flag; ++l1) {
            final int l3 = l1 - y < i1 ? 0 : k1;
            for (int i2 = x - l3; i2 <= x + l3 && flag; ++i2) {
                for (int j2 = z - l3; j2 <= z + l3 && flag; ++j2) {
                    if (l1 >= 0 && l1 < 256) {
                        final BlockPos p = new BlockPos(i2, l1, j2);
                        final IBlockState state = world.getBlockState(p);
                        if (!state.getBlock().isAir(state, world, p)
                                && !state.getBlock().isLeaves(state, world, p)
                                && state.getBlock() != Blocks.SNOW_LAYER) {
                            flag = false;
                        }
                    } else {
                        flag = false;
                    }
                }
            }
        }

        if (!flag) {
            return false;
        }

        final BlockPos below = new BlockPos(x, y - 1, z);
        final IBlockState soil = world.getBlockState(below);
        final boolean isSoil = soil.getBlock().canSustainPlant(soil, world, below, EnumFacing.UP,
                (net.minecraftforge.common.IPlantable) Blocks.SAPLING);
        if (!isSoil || y >= 256 - l - 1) {
            return false;
        }

        soil.getBlock().onPlantGrow(soil, world, below, new BlockPos(x, y, z));

        int l3 = rand.nextInt(2);
        int i2 = 1;
        int b0 = 0;

        for (int i4 = 0; i4 <= j1; ++i4) {
            final int k2 = y + l - i4;

            for (int l2 = x - l3; l2 <= x + l3; ++l2) {
                final int i3 = l2 - x;
                for (int j3 = z - l3; j3 <= z + l3; ++j3) {
                    final int k3 = j3 - z;
                    final BlockPos p = new BlockPos(l2, k2, j3);
                    final IBlockState state = world.getBlockState(p);
                    if ((Math.abs(i3) != l3 || Math.abs(k3) != l3 || l3 <= 0)
                            && state.getBlock().canBeReplacedByLeaves(state, world, p)) {
                        world.setBlockState(p, this.leaves, 2);
                    }
                }
            }

            if (l3 >= i2) {
                l3 = b0;
                b0 = 1;
                ++i2;
                if (i2 > k1) {
                    i2 = k1;
                }
            } else {
                ++l3;
            }
        }

        final int i4 = rand.nextInt(3);
        for (int k2 = 0; k2 < l - i4; ++k2) {
            final BlockPos p = new BlockPos(x, y + k2, z);
            final IBlockState state = world.getBlockState(p);
            if (state.getBlock().isAir(state, world, p) || state.getBlock().isLeaves(state, world, p)
                    || state.getBlock() == Blocks.SNOW_LAYER) {
                world.setBlockState(p, Blocks.LOG.getDefaultState(), 2);
            }
        }

        if (this.height > 4) {
            this.createTrunk(world, rand, x, y, z);
        }

        return true;
    }

    private void createTrunk(World world, Random rand, int x, int y, int z) {

        final int[] pos = new int[] { 0, 0, 1, 0, 0, 1, -1, 0, 0, -1 };
        for (int t = 0; t < 5; t++) {
            int sh = rand.nextInt(4) + y - 2;
            while (sh > y - 1) {
                final BlockPos p = new BlockPos(x + pos[t * 2], sh, z + pos[t * 2 + 1]);
                if (world.getBlockState(p).getBlock() == Blocks.GRASS) {
                    break;
                }
                world.setBlockState(p, Blocks.LOG.getDefaultState()
                        .withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.NONE), 2);
                sh--;
            }
        }
    }
}
