package rtg.api.world.gen.feature;

import net.minecraft.block.BlockOldLeaf;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.Random;


/**
 * RWG {@code rwg2.deco.trees.DecoSmallSpruce} 的逐行移植。
 *
 * <p>⚠ RWG 原文的树干写的是 {@code Blocks.log, 0}（**橡木**原木），只有树叶随机取
 * metadata 0/1（橡木/云杉）。这里照抄（不“修好”它），否则就不是逐行移植了。
 */
public class WorldGenSmallSpruce extends WorldGenerator {

    private final int treeSize;

    public WorldGenSmallSpruce(int treeSize) {
        this.treeSize = treeSize;
    }

    @Override
    public boolean generate(World world, Random rand, BlockPos pos) {

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        final IBlockState below = world.getBlockState(pos.down());
        if (below.getBlock() != Blocks.GRASS && below.getBlock() != Blocks.DIRT) {
            return false;
        }

        final IBlockState log = Blocks.LOG.getDefaultState()
                .withProperty(BlockOldLog.VARIANT, BlockPlanks.EnumType.OAK);
        final IBlockState leaves = Blocks.LEAVES.getDefaultState()
                .withProperty(BlockOldLeaf.VARIANT, BlockPlanks.EnumType.byMetadata(rand.nextInt(2)));

        int start = 1;
        int small = 2;
        int large = 0;
        if (this.treeSize == 1) {
            small = 2;
            large = 2;
        } else if (this.treeSize == 2) {
            start = 1 + rand.nextInt(2);
            small = 3;
            large = 3;
        }

        for (int i = 0; i < start; i++) {
            world.setBlockState(new BlockPos(x, y, z), log, 2);
            y++;
        }

        for (int i = 0; i < large; i++) {
            for (int j = -2; j <= 2; j++) {
                for (int k = -2; k <= 2; k++) {
                    if (Math.abs(j) + Math.abs(k) != 4
                            && ((j > -2 && k > -2 && j < 2 && k < 2) || rand.nextInt(4) != 0)) {
                        world.setBlockState(new BlockPos(x + j, y, z + k), leaves, 2);
                    }
                }
            }
            world.setBlockState(new BlockPos(x, y, z), log, 2);
            y++;
        }

        for (int i = 0; i < small; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    if (Math.abs(j) + Math.abs(k) < 2 || (rand.nextInt(4) != 0)) {
                        world.setBlockState(new BlockPos(x + j, y, z + k), leaves, 2);
                    }
                }
            }

            if (i == 0) {
                world.setBlockState(new BlockPos(x + 1, y, z), leaves, 2);
                world.setBlockState(new BlockPos(x - 1, y, z), leaves, 2);
                world.setBlockState(new BlockPos(x, y, z + 1), leaves, 2);
                world.setBlockState(new BlockPos(x, y, z - 1), leaves, 2);
                world.setBlockState(new BlockPos(x + 2, y, z), leaves, 2);
                world.setBlockState(new BlockPos(x - 2, y, z), leaves, 2);
                world.setBlockState(new BlockPos(x, y, z + 2), leaves, 2);
                world.setBlockState(new BlockPos(x, y, z - 2), leaves, 2);
            }

            world.setBlockState(new BlockPos(x, y, z), log, 2);
            y++;
        }

        world.setBlockState(new BlockPos(x, y, z), log, 2);
        world.setBlockState(new BlockPos(x + 1, y, z), leaves, 2);
        world.setBlockState(new BlockPos(x - 1, y, z), leaves, 2);
        world.setBlockState(new BlockPos(x, y, z + 1), leaves, 2);
        world.setBlockState(new BlockPos(x, y, z - 1), leaves, 2);

        world.setBlockState(new BlockPos(x, y + 1, z), leaves, 2);
        world.setBlockState(new BlockPos(x, y + 2, z), leaves, 2);
        return true;
    }
}
