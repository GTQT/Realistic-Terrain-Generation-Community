package rtg.api.world.gen.feature;

import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.Random;


/**
 * RWG {@code rwg2.deco.DecoWaterGrass} 的逐行移植。
 *
 * <p>沿海滩的“水草带”：先自传入 y 向下扫到地面（低于 {@code minHeight} 就放弃），
 * 再在 ±8 邻域里把“水下的沙子”变成草方块，然后按三种模式放植物：
 * {@code double_plant}（32 次）、{@code leaves}（64 次）、普通单格植物（128 次）。
 *
 * <p>1.7.10 的 {@code Blocks.leaves, 4} 是“橡木叶 + 不衰减”的 metadata；1.12 对应
 * {@code LEAVES(OAK)} 且 {@code DECAYABLE=false}。
 */
public class WorldGenWaterGrass extends WorldGenerator {

    private final IBlockState state;
    private final BlockDoublePlant.EnumPlantType doubleType;
    private final int minHeight;

    public WorldGenWaterGrass(IBlockState state, int minHeight) {
        this.state = state;
        this.doubleType = null;
        this.minHeight = minHeight;
    }

    public WorldGenWaterGrass(BlockDoublePlant.EnumPlantType type, int minHeight) {
        this.state = Blocks.DOUBLE_PLANT.getDefaultState()
                .withProperty(BlockDoublePlant.VARIANT, type);
        this.doubleType = type;
        this.minHeight = minHeight;
    }

    @Override
    public boolean generate(World world, Random rand, BlockPos pos) {

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        while (y > 0) {
            final BlockPos p = new BlockPos(x, y, z);
            final IBlockState s = world.getBlockState(p);
            if (!world.isAirBlock(p) || s.getBlock().isLeaves(s, world, p)) {
                break;
            }
            if (y < this.minHeight) {
                return false;
            }
            y--;
        }

        if (this.doubleType != null) {
            for (int l = 0; l < 32; ++l) {
                final BlockPos p = new BlockPos(
                        x + rand.nextInt(8) - rand.nextInt(8),
                        y + rand.nextInt(2) - rand.nextInt(2),
                        z + rand.nextInt(8) - rand.nextInt(8));
                this.grassify(world, p);

                if (world.isAirBlock(p) && world.isAirBlock(p.up())
                        && Blocks.DOUBLE_PLANT.canPlaceBlockAt(world, p)) {
                    final IBlockState lower = this.state
                            .withProperty(BlockDoublePlant.HALF, BlockDoublePlant.EnumBlockHalf.LOWER);
                    world.setBlockState(p, lower, 2);
                    world.setBlockState(p.up(),
                            lower.withProperty(BlockDoublePlant.HALF, BlockDoublePlant.EnumBlockHalf.UPPER), 2);
                }
            }
        } else if (this.state.getBlock() == Blocks.LEAVES) {
            for (int l = 0; l < 64; ++l) {
                final BlockPos p = new BlockPos(
                        x + rand.nextInt(8) - rand.nextInt(8),
                        y + rand.nextInt(4) - rand.nextInt(4),
                        z + rand.nextInt(8) - rand.nextInt(8));
                this.grassify(world, p);
                if (world.isAirBlock(p) && world.getBlockState(p.down()).getBlock() == Blocks.GRASS) {
                    world.setBlockState(p, this.state, 2);
                }
            }
        } else {
            for (int l = 0; l < 128; ++l) {
                final BlockPos p = new BlockPos(
                        x + rand.nextInt(8) - rand.nextInt(8),
                        y + rand.nextInt(4) - rand.nextInt(4),
                        z + rand.nextInt(8) - rand.nextInt(8));
                this.grassify(world, p);
                if (world.isAirBlock(p) && this.state.getBlock().canPlaceBlockAt(world, p)) {
                    world.setBlockState(p, this.state, 2);
                }
            }
        }
        return true;
    }

    /** 把“水下沙子 / 裸露沙子”的表层换成草方块（RWG 原文）。 */
    private void grassify(World world, BlockPos p) {

        final IBlockState below = world.getBlockState(p.down());
        final boolean waterOverSand = below.getBlock() == Blocks.WATER
                && world.getBlockState(p.down(2)).getBlock() == Blocks.SAND;
        if ((waterOverSand || below.getBlock() == Blocks.SAND) && world.getBlockState(p).getBlock() == Blocks.AIR) {
            world.setBlockState(p.down(), Blocks.GRASS.getDefaultState(), 2);
        }
    }
}
