package rtg.api.world.gen.feature;

import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.Random;


/**
 * RWG {@code rwg2.deco.trees.DecoShrub} 的逐行移植。
 *
 * <p>RWG 的参数是 1.7.10 的合并 metadata：{@code log 0–2 → log}、{@code 3–5 → log2}；
 * {@code leaves 0–3 → leaves}、{@code 4–7 → leaves2}。1.12 用
 * {@link BlockPlanks.EnumType} 还原。
 */
public class WorldGenRtgShrub extends WorldGenerator {

    private final int size;
    private final IBlockState log;
    private final IBlockState leaves;
    private final boolean sand;

    public WorldGenRtgShrub(int size, int logMeta, int leafMeta) {
        this(size, logMeta, leafMeta, false);
    }

    public WorldGenRtgShrub(int size, int logMeta, int leafMeta, boolean sand) {
        this.size = size;
        this.sand = sand;
        this.log = logState(logMeta);
        this.leaves = leafState(leafMeta);
    }

    private static IBlockState logState(int meta) {
        if (meta < 3) {
            return Blocks.LOG.getDefaultState()
                    .withProperty(BlockOldLog.VARIANT, BlockPlanks.EnumType.byMetadata(meta));
        }
        return Blocks.LOG2.getDefaultState()
                .withProperty(BlockNewLog.VARIANT, BlockPlanks.EnumType.byMetadata(meta - 3 + 4));
    }

    private static IBlockState leafState(int meta) {
        if (meta < 4) {
            return Blocks.LEAVES.getDefaultState()
                    .withProperty(BlockOldLeaf.VARIANT, BlockPlanks.EnumType.byMetadata(meta));
        }
        return Blocks.LEAVES2.getDefaultState()
                .withProperty(BlockNewLeaf.VARIANT, BlockPlanks.EnumType.byMetadata(meta));
    }

    @Override
    public boolean generate(World world, Random rand, BlockPos pos) {

        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();

        final int width = Math.min(this.size, 6);
        final int height = this.size > 3 ? 2 : 1;

        for (int i = 0; i < this.size; i++) {
            final int rX = rand.nextInt(width * 2) - width;
            final int rY = rand.nextInt(height);
            final int rZ = rand.nextInt(width * 2) - width;

            if (i == 0 && this.size > 4) {
                this.buildLeaves(world, x + rX, y, z + rZ, 3);
            } else if (i == 1 && this.size > 2) {
                this.buildLeaves(world, x + rX, y, z + rZ, 2);
            } else {
                this.buildLeaves(world, x + rX, y + rY, z + rZ, 1);
            }
        }
        return true;
    }

    private void buildLeaves(World world, int x, int y, int z, int size) {

        final Material m = world.getBlockState(new BlockPos(x, y - 2, z)).getMaterial();
        if (m == Material.GRASS || m == Material.GROUND || (this.sand && m == Material.SAND)) {
            if (world.getBlockState(new BlockPos(x, y - 1, z)).getBlock() != Blocks.WATER) {
                for (int i = -size; i <= size; i++) {
                    for (int j = -1; j <= 1; j++) {
                        for (int k = -size; k <= size; k++) {
                            if (Math.abs(i) + Math.abs(j) + Math.abs(k) <= size) {
                                this.buildBlock(world, x + i, y + j, z + k);
                            }
                        }
                    }
                }
                world.setBlockState(new BlockPos(x, y - 1, z), this.log, 2);
            }
        }
    }

    private void buildBlock(World world, int x, int y, int z) {

        final BlockPos p = new BlockPos(x, y, z);
        final Material m = world.getBlockState(p).getMaterial();
        if (m == Material.AIR || m == Material.VINE || m == Material.PLANTS
                || world.getBlockState(p).getBlock() == Blocks.SNOW_LAYER) {
            world.setBlockState(p, this.leaves, 2);
        }
    }
}
