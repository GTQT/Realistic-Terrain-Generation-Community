package rtg.api.world.gen.feature;

import net.minecraft.block.BlockNewLeaf;
import net.minecraft.block.BlockNewLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.Random;


/**
 * RWG {@code rwg2.deco.trees.DecoSavannah} 的逐行移植（1.12 方块状态适配）。
 *
 * <p>RWG 用 {@code Blocks.log2} / {@code Blocks.leaves2} 的 metadata 0（金合欢）。1.12 里
 * 对应 {@link Blocks#LOG2} / {@link Blocks#LEAVES2} 的 {@link BlockPlanks.EnumType#ACACIA}。
 * 三种形态（0 = 高瘦、1 = 中、2 = 高）与分支长度、叶团半径全部照抄。
 */
public class WorldGenSavannah extends WorldGenerator {

    private static final IBlockState LOG = Blocks.LOG2.getDefaultState()
            .withProperty(BlockNewLog.VARIANT, BlockPlanks.EnumType.ACACIA);
    private static final IBlockState LEAVES = Blocks.LEAVES2.getDefaultState()
            .withProperty(BlockNewLeaf.VARIANT, BlockPlanks.EnumType.ACACIA);

    private final int type;
    private final boolean sand;

    public WorldGenSavannah(int type) {
        this(type, true);
    }

    public WorldGenSavannah(int type, boolean sand) {
        this.type = type;
        this.sand = sand;
    }

    @Override
    public boolean generate(World world, Random rand, BlockPos pos) {

        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();
        final IBlockState below = world.getBlockState(pos.down());

        if (below.getBlock() != Blocks.GRASS && below.getBlock() != Blocks.DIRT
                && ((this.sand && below.getBlock() != Blocks.SAND) || !this.sand)) {
            return false;
        }

        if (this.type == 0) {
            final int h = 10 + rand.nextInt(5);
            final int bh = h - 6;

            for (int i = 0; i < h; i++) {
                world.setBlockState(new BlockPos(x, y + i, z), LOG, 2);
            }
            this.genLeaves(world, rand, x, y + h, z);

            for (int a = 7 - 1 + rand.nextInt(3); a > -1; a--) {
                int sh = bh + rand.nextInt(6 - 2);
                final int dir = rand.nextInt(360);
                final float xd = (float) Math.cos(dir * Math.PI / 180f) * 2f;
                final float yd = (float) Math.sin(dir * Math.PI / 180f) * 2f;
                float c = 1;

                while (sh < h) {
                    world.setBlockState(new BlockPos(x + (int) (xd * c), y + sh, z + (int) (yd * c)), LOG, 2);
                    sh++;
                    c += 0.5f;
                }
                this.genLeaves(world, rand, x + (int) (xd * c), y + sh, z + (int) (yd * c));
            }
        } else if (this.type == 1) {
            final int h = 6 + rand.nextInt(3);
            final int bh = h - 3;

            for (int i = 0; i < h; i++) {
                world.setBlockState(new BlockPos(x, y + i, z), LOG, 2);
            }
            this.genLeaves(world, rand, x, y + h, z);

            for (int a = 1 + rand.nextInt(2); a > -1; a--) {
                int sh = bh + rand.nextInt(3 - 1);
                final int dir = rand.nextInt(360);
                final float xd = (float) Math.cos(dir * Math.PI / 180f) * 2f;
                final float yd = (float) Math.sin(dir * Math.PI / 180f) * 2f;
                float c = 1;

                while (sh < h) {
                    world.setBlockState(new BlockPos(x + (int) (xd * c), y + sh, z + (int) (yd * c)), LOG, 2);
                    sh++;
                    c += 0.5f;
                }
                this.genLeaves(world, rand, x + (int) (xd * c), y + sh, z + (int) (yd * c));
            }
        } else if (this.type == 2) {
            final int h = 12 + rand.nextInt(5);
            final int bh = h - 3;

            for (int i = 0; i < h; i++) {
                world.setBlockState(new BlockPos(x, y + i, z), LOG, 2);
            }
            this.genLeaves(world, rand, x, y + h, z);

            for (int a = 1 + rand.nextInt(2); a > -1; a--) {
                int sh = bh + rand.nextInt(3 - 1);
                final int dir = rand.nextInt(360);
                final float xd = (float) Math.cos(dir * Math.PI / 180f) * 2f;
                final float yd = (float) Math.sin(dir * Math.PI / 180f) * 2f;
                float c = 1;

                while (sh < h) {
                    world.setBlockState(new BlockPos(x + (int) (xd * c), y + sh, z + (int) (yd * c)), LOG, 2);
                    sh++;
                    c += 0.5f;
                }
                this.genLeaves(world, rand, x + (int) (xd * c), y + sh, z + (int) (yd * c));
            }
        }

        return true;
    }

    private void genLeaves(World world, Random rand, int x, int y, int z) {

        if (this.type == 0) {
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    final BlockPos p = new BlockPos(x + i, y + 1, z + j);
                    if (world.isAirBlock(p) && Math.abs(i) + Math.abs(j) < 4) {
                        world.setBlockState(p, LEAVES, 2);
                    }
                }
            }
            for (int i = -3; i <= 3; i++) {
                for (int j = -3; j <= 3; j++) {
                    final BlockPos p = new BlockPos(x + i, y, z + j);
                    if (world.isAirBlock(p) && Math.abs(i) + Math.abs(j) < 5) {
                        world.setBlockState(p, LEAVES, 2);
                    }
                }
            }
            world.setBlockState(new BlockPos(x, y, z), LOG, 2);
        } else {
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    final BlockPos p = new BlockPos(x + i, y + 1, z + j);
                    if (world.isAirBlock(p)) {
                        world.setBlockState(p, LEAVES, 2);
                    }
                }
            }
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    final BlockPos p = new BlockPos(x + i, y, z + j);
                    if (world.isAirBlock(p) && Math.abs(i) + Math.abs(j) < 4) {
                        world.setBlockState(p, LEAVES, 2);
                    }
                }
            }
            world.setBlockState(new BlockPos(x, y, z), LOG, 2);
        }
    }
}
