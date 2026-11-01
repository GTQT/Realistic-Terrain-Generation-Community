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
 * RWG {@code rwg2.deco.trees.DecoMangrove} 的逐行移植。
 *
 * <p>RWG 的构造参数是 1.7.10 的合并 metadata；1.12 用
 * {@link BlockPlanks.EnumType} 还原（默认橡木）。树干 / 侧根 / 叶团算法照抄。
 */
public class WorldGenMangrove extends WorldGenerator {

    private final IBlockState log;
    private final IBlockState leaves;
    private final int base;
    private final int root;
    private final float length;
    private final int branch;
    private final float verStart;
    private final float verRand;

    public WorldGenMangrove(IBlockState log, IBlockState leaves, int baseHeight, int rootHeight,
            float branchLength, int numBranches, float verticalStart, float verticalRand) {
        this.log = log;
        this.leaves = leaves;
        this.base = baseHeight;
        this.root = rootHeight;
        this.length = branchLength;
        this.branch = numBranches;
        this.verStart = verticalStart;
        this.verRand = verticalRand;
    }

    /** 便捷构造：RWG 默认的橡木原木/树叶。 */
    public static WorldGenMangrove oak(int baseHeight, int rootHeight, float branchLength, int numBranches,
            float verticalStart, float verticalRand) {
        return new WorldGenMangrove(
                Blocks.LOG.getDefaultState().withProperty(BlockOldLog.VARIANT, BlockPlanks.EnumType.OAK),
                Blocks.LEAVES.getDefaultState().withProperty(BlockOldLeaf.VARIANT, BlockPlanks.EnumType.OAK),
                baseHeight, rootHeight, branchLength, numBranches, verticalStart, verticalRand);
    }

    @Override
    public boolean generate(World world, Random rand, BlockPos pos) {

        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();

        final IBlockState below = world.getBlockState(new BlockPos(x, y - 1, z));
        if (below.getBlock() != Blocks.GRASS && below.getBlock() != Blocks.DIRT && below.getBlock() != Blocks.SAND) {
            if (!(below.getBlock() == Blocks.WATER
                    && world.getBlockState(new BlockPos(x, y - 2, z)).getBlock() == Blocks.SAND
                    && world.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.AIR)) {
                return false;
            }
        }

        if (this.root > 0f) {
            for (int k = 0; k < 3; k++) {
                this.generateBranch(world, x, y + this.root, z, (120 * k) - 40 + rand.nextInt(80),
                        1.6f + rand.nextFloat() * 0.1f, this.root * 2f, 1f);
            }
        }

        for (int i = y + this.root; i < y + this.base; i++) {
            world.setBlockState(new BlockPos(x, i, z), this.log, 2);
        }

        for (int j = 0; j < this.branch; j++) {
            final float horDir = (120 * j) - 60 + rand.nextInt(120);
            final float verDir = this.verStart + rand.nextFloat() * this.verRand;
            this.generateBranch(world, x, y + this.base, z, horDir, verDir, this.length, 1f);

            final int eX = x + (int) (Math.cos(horDir * Math.PI / 180D) * verDir * this.length);
            final int eZ = z + (int) (Math.sin(horDir * Math.PI / 180D) * verDir * this.length);
            final int eY = y + this.base + (int) ((1f - verDir) * this.length);

            this.generateLeaves(world, rand, eX, eY, eZ, 4f, 1.2f);
        }

        return true;
    }

    private void generateBranch(World world, float x, float y, float z, double horDir, float verDir,
            float length, float speed) {

        if (verDir < 0f) {
            verDir = -verDir;
        }

        float c = 0f;
        float velY = 1f - verDir;

        if (verDir > 1f) {
            verDir = 1f - (verDir - 1f);
        }

        final float velX = (float) Math.cos(horDir * Math.PI / 180D) * verDir;
        final float velZ = (float) Math.sin(horDir * Math.PI / 180D) * verDir;

        while (c < length) {
            world.setBlockState(new BlockPos((int) x, (int) y, (int) z), this.log, 2);
            x += velX;
            y += velY;
            z += velZ;
            c += speed;
        }
    }

    private void generateLeaves(World world, Random rand, int x, int y, int z, float size, float width) {

        final int s = (int) (size - 1f);
        final int w = (int) ((size - 1f) * width);
        for (int i = -w; i <= w; i++) {
            for (int j = -s; j <= s; j++) {
                for (int k = -w; k <= w; k++) {
                    final float dist = Math.abs((float) i / width) + (float) Math.abs(j)
                            + Math.abs((float) k / width);
                    if (dist <= size - 0.5f || (dist <= size && rand.nextBoolean())) {
                        final BlockPos p = new BlockPos(x + i, y + j, z + k);
                        if (dist < 0.6f) {
                            world.setBlockState(p, this.log, 2);
                        }
                        if (world.isAirBlock(p)) {
                            world.setBlockState(p, this.leaves, 2);
                        }
                    }
                }
            }
        }
    }
}
