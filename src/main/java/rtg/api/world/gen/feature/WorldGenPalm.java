package rtg.api.world.gen.feature;

import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.Random;


/**
 * RWG {@code rwg2.deco.trees.DecoPalm} 的逐行移植。
 *
 * <p>RWG 用 {@code Blocks.log, 15}（丛林原木 + 轴 NONE）与 {@code Blocks.leaves, 3}
 * （丛林叶），可可果 metadata 是 {@code 8 + 朝向}。1.12 用
 * {@link BlockPlanks.EnumType#JUNGLE} 与 {@link BlockCocoa} 还原；可可的朝向按
 * 1.7.10 的 0–3 水平索引映射，年龄固定 2（原文只写朝向位，年龄默认 0，这里取成熟态以可见）。
 */
public class WorldGenPalm extends WorldGenerator {

    private static final IBlockState LOG = Blocks.LOG.getDefaultState()
            .withProperty(BlockOldLog.VARIANT, BlockPlanks.EnumType.JUNGLE)
            .withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.NONE);
    private static final IBlockState LEAVES = Blocks.LEAVES.getDefaultState()
            .withProperty(BlockOldLeaf.VARIANT, BlockPlanks.EnumType.JUNGLE);

    private static final int[] LEAVES_POS = new int[] {
            1, 0, 0, 2, 0, 0, 3, -1, 0, 3, -2, 0, -1, 0, 0, -2, 0, 0, -3, -1, 0, -3,
            -2, 0, 0, 0, 1, 0, 0, 2, 0, -1, 3, 0, -2, 3, 0, 0, -1, 0, 0, -2, 0, -1, -3, 0, -2, -3, 0, 1, 0, 1, 1, 1, -1,
            1, -1, -1, 1, 1, 1, 1, -1, 1, 2, 0, -1, 2, 0, 0, 2, 1, 0, 2, -1, 2, 3, 0, 3, 3, 0, 4, 2, 0, -2, 3, 0, -3, 3,
            0, -4, 2, 0, 0, 3, 2, 0, 3, 3, 0, 2, 4, 0, 3, -2, 0, 3, -3, 0, 2, -4, 2, 2, -2, -2, 2, 2, -2, 2, -2, 2, 2,
            2, 3, 2, -3, -3, 2, 3, -3, 2, -3, 3, 2, 3 };

    private static final int[] COCOA_POS = new int[] { 2, 0, -2, 1, 1, 1, -2, 0, 0, 0, -2, -1, 3, -1, -2, 0 };

    @Override
    public boolean generate(World world, Random rand, BlockPos pos) {

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        final IBlockState below = world.getBlockState(pos.down());
        if (below.getBlock() != Blocks.GRASS && below.getBlock() != Blocks.DIRT
                && below.getBlock() != Blocks.SAND) {
            return false;
        }

        final double horDir = this.getRandomDir(rand);
        float verDir = 0.3f + rand.nextFloat() * 0.4f;
        final float length = 15f;
        float posX = x;
        float posY = y;
        float posZ = z;
        float c = 0f;
        float loss;

        if (verDir < 0f) {
            verDir = -verDir;
        }

        float velY = 1f - verDir;

        if (verDir > 1f) {
            verDir = 1f - (verDir - 1f);
        }

        float velX = (float) Math.cos(horDir * Math.PI / 180D) * verDir;
        float velZ = (float) Math.sin(horDir * Math.PI / 180D) * verDir;

        while (c < length) {
            world.setBlockState(new BlockPos((int) posX, (int) posY, (int) posZ), LOG, 2);

            if (c < length - 3) {
                loss = Math.abs(velX) + Math.abs(velZ);
                posX += velX *= 0.9f;
                posZ += velZ *= 0.9f;
                loss = loss - (Math.abs(velX) + Math.abs(velZ));
                posY += velY += loss;
            } else {
                posY += velY;
            }

            c += 1f;
        }

        x = (int) posX;
        y = (int) posY - 1;
        z = (int) posZ;

        for (int j = 0; j < LEAVES_POS.length; j += 3) {
            world.setBlockState(new BlockPos(x + LEAVES_POS[j], y + LEAVES_POS[j + 1], z + LEAVES_POS[j + 2]),
                    LEAVES, 2);
        }

        for (int k = 0; k < COCOA_POS.length; k += 4) {
            if (rand.nextInt(20) == 0) {
                world.setBlockState(new BlockPos(x + COCOA_POS[k + 1], y + COCOA_POS[k + 2], z + COCOA_POS[k + 3]),
                        Blocks.COCOA.getDefaultState()
                                .withProperty(BlockCocoa.AGE, 2)
                                .withProperty(BlockCocoa.FACING,
                                        EnumFacing.byHorizontalIndex(COCOA_POS[k] & 3)),
                        2);
            }
        }

        return true;
    }

    private double getRandomDir(Random rand) {
        switch (rand.nextInt(4)) {
            case 0:
                return -180D;
            case 1:
                return -90D;
            case 2:
                return 0D;
            default:
                return 90D;
        }
    }
}
