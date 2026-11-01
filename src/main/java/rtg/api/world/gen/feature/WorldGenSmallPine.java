package rtg.api.world.gen.feature;

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
 * RWG {@code rwg2.deco.trees.DecoSmallPine} 的逐行移植。
 *
 * <p>RWG 用 {@code Blocks.log} / {@code Blocks.leaves} 的 metadata 0–3（橡木/云杉/白桦/丛林）；
 * 1.12 对应 {@link BlockOldLog#VARIANT} / {@link BlockOldLeaf#VARIANT} 的同一组
 * {@link BlockPlanks.EnumType}。
 */
public class WorldGenSmallPine extends WorldGenerator {

    private final int startHeight;
    private final int treeSize;
    private final IBlockState log;
    private final IBlockState leaves;

    public WorldGenSmallPine(int startHeight, int treeSize, int meta) {
        this.startHeight = startHeight;
        this.treeSize = treeSize;
        this.log = Blocks.LOG.getDefaultState()
                .withProperty(BlockOldLog.VARIANT, BlockPlanks.EnumType.byMetadata(meta & 3));
        this.leaves = Blocks.LEAVES.getDefaultState()
                .withProperty(BlockOldLeaf.VARIANT, BlockPlanks.EnumType.byMetadata(meta & 3));
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

        int i;
        for (i = 0; i < this.startHeight; i++) {
            world.setBlockState(new BlockPos(x, y, z), this.log, 2);
            y++;
        }

        int pX = 0;
        int pZ = 0;
        for (i = 0; i < this.treeSize; i++) {
            if (i < this.treeSize - 2) {
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

                this.buildBranch(world, x, y, z, dX, dZ, i < this.treeSize - 10 ? 2 : 1,
                        i < this.treeSize - 6 ? 2 : 1);
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
}
