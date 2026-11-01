package rtg.api.world.gen.feature;

import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockOldLeaf;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.Random;


/**
 * RWG {@code rwg2.deco.trees.DecoLargePine} 的逐行移植。
 *
 * <p>比 {@link WorldGenSmallPine} 多四条侧根（{@code buildTrunk}）和更粗的分支。
 * RWG 的 {@code metadataLog}/{@code metadataLeaves} 是 1.7.10 的合并 metadata；1.12 用
 * {@link BlockPlanks.EnumType} 还原。
 */
public class WorldGenLargePine extends WorldGenerator {

    private final int startHeight;
    private final int treeSize;
    private final IBlockState log;
    private final IBlockState leaves;

    public WorldGenLargePine(int startHeight, int treeSize, int logMeta, int leafMeta) {
        this.startHeight = startHeight;
        this.treeSize = treeSize;
        this.log = Blocks.LOG.getDefaultState()
                .withProperty(BlockOldLog.VARIANT, BlockPlanks.EnumType.byMetadata(logMeta & 3));
        this.leaves = Blocks.LEAVES.getDefaultState()
                .withProperty(BlockOldLeaf.VARIANT, BlockPlanks.EnumType.byMetadata(leafMeta & 3));
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

        this.buildTrunk(world, rand, x + 1, y, z);
        this.buildTrunk(world, rand, x - 1, y, z);
        this.buildTrunk(world, rand, x, y, z + 1);
        this.buildTrunk(world, rand, x, y, z - 1);

        int i;
        for (i = 0; i < this.startHeight; i++) {
            world.setBlockState(new BlockPos(x, y, z), this.log, 2);
            if (i > 5 && rand.nextInt(7) == 0) {
                int dX = -1 + rand.nextInt(3);
                int dZ = -1 + rand.nextInt(3);

                if (dX == 0 && dZ == 0) {
                    dX = -1 + rand.nextInt(3);
                    dZ = -1 + rand.nextInt(3);
                }

                this.buildBranch(world, x, y, z, dX, dZ, 1, 1);
            }

            y++;
        }

        int pX = 0;
        int pZ = 0;
        for (i = 0; i < this.treeSize; i++) {
            if (rand.nextInt(i < this.treeSize - 12 && i > 2 ? 2 : 1) == 0 && i < this.treeSize - 2) {
                int dX = -1 + rand.nextInt(3);
                int dZ = -1 + rand.nextInt(3);

                if (dX == 0 && dZ == 0) {
                    dX = -1 + rand.nextInt(3);
                    dZ = -1 + rand.nextInt(3);
                }
                if (pX == dX && rand.nextBoolean()) {
                    dX = -dX;
                }
                if (pZ == dZ && rand.nextBoolean()) {
                    dZ = -dZ;
                }

                pX = dX;
                pZ = dZ;

                this.buildBranch(world, x, y, z, dX, dZ,
                        i < this.treeSize - 12 && i > 3 ? 3 : i < this.treeSize - 8 ? 2 : 1,
                        i < this.treeSize - 5 ? 2 : 1);
            }
            world.setBlockState(new BlockPos(x, y, z), this.log, 2);

            if (i < this.treeSize - 2) {
                if (rand.nextBoolean()) {
                    this.buildLeaves(world, x, y, z + 1);
                }
                if (rand.nextBoolean()) {
                    this.buildLeaves(world, x, y, z - 1);
                }
                if (rand.nextBoolean()) {
                    this.buildLeaves(world, x + 1, y, z);
                }
                if (rand.nextBoolean()) {
                    this.buildLeaves(world, x - 1, y, z);
                }
            }

            y++;
        }

        this.buildLeaves(world, x, y - 1, z + 1);
        this.buildLeaves(world, x, y - 1, z - 1);
        this.buildLeaves(world, x + 1, y - 1, z);
        this.buildLeaves(world, x - 1, y - 1, z);
        this.buildLeaves(world, x, y, z);

        return true;
    }

    private void buildBranch(World world, int x, int y, int z, int dX, int dZ, int logLength, int leaveSize) {

        if (logLength == 3 && Math.abs(dX) + Math.abs(dZ) == 2) {
            logLength--;
        }

        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = 0; k < 2; k++) {
                    if (Math.abs(i) + Math.abs(j) + Math.abs(k) < leaveSize + 1) {
                        this.buildLeaves(world, x + i + (dX * logLength), y + k, z + j + (dZ * logLength));
                    }
                }
            }
        }

        for (int m = 1; m <= logLength; m++) {
            world.setBlockState(new BlockPos(x + (dX * m), y, z + (dZ * m)), this.log, 2);
        }
    }

    private void buildLeaves(World world, int x, int y, int z) {

        final BlockPos p = new BlockPos(x, y, z);
        if (world.getBlockState(p).getMaterial() == Material.AIR) {
            world.setBlockState(p, this.leaves, 2);
        }
    }

    private void buildTrunk(World world, Random rand, int x, int y, int z) {

        int h = (int) Math.ceil(this.startHeight / 4f);
        h = h + rand.nextInt(h * 2);
        for (int i = -1; i < h; i++) {
            world.setBlockState(new BlockPos(x, y + i, z), Blocks.LOG.getDefaultState()
                    .withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.NONE), 2);
        }
    }
}
