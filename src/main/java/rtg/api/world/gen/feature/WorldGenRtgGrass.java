package rtg.api.world.gen.feature;

import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.Random;


/**
 * RWG {@code rwg2.deco.DecoGrass} 的逐行移植（1.12 方块状态适配）。
 *
 * <p>RWG 的三种模式：{@code double_plant}（双格草/花）、{@code leaves}（只长在草方块上）、
 * 其余（普通单格植物）。先自传入的 y 向下扫到地面，再在 ±8 / ±4 的邻域里做 64/128 次尝试。
 *
 * <p>1.7.10 的 {@code func_149889_c}（放双格植物）在 1.12 没有同名方法，这里按原语义
 * 手动放上/下两半。
 */
public class WorldGenRtgGrass extends WorldGenerator {

    private enum Kind {
        PLANT, DOUBLE, LEAF
    }

    private final Kind kind;
    private final IBlockState state;
    private final BlockDoublePlant.EnumPlantType doubleType;

    /** 普通单格植物（tallgrass 等）。 */
    public WorldGenRtgGrass(IBlockState state) {
        this.kind = Kind.PLANT;
        this.state = state;
        this.doubleType = null;
    }

    /** 双格植物（double_plant 的 metadata 对应的类型）。 */
    public WorldGenRtgGrass(BlockDoublePlant.EnumPlantType type) {
        this.kind = Kind.DOUBLE;
        this.state = Blocks.DOUBLE_PLANT.getDefaultState();
        this.doubleType = type;
    }

    private WorldGenRtgGrass(IBlockState leafState, Kind kind) {
        this.kind = kind;
        this.state = leafState;
        this.doubleType = null;
    }

    /** leaves 模式：只放在草方块上。 */
    public static WorldGenRtgGrass leaves(IBlockState leafState) {
        return new WorldGenRtgGrass(leafState, Kind.LEAF);
    }

    @Override
    public boolean generate(World world, Random rand, BlockPos pos) {

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        // RWG: while (y > 0) { if (!air || isLeaves) break; y--; }
        while (y > 0) {
            final BlockPos p = new BlockPos(x, y, z);
            if (!world.isAirBlock(p) || world.getBlockState(p).getBlock().isLeaves(world.getBlockState(p), world, p)) {
                break;
            }
            y--;
        }

        if (this.kind == Kind.DOUBLE) {
            for (int l = 0; l < 64; ++l) {
                final BlockPos p = new BlockPos(
                        x + rand.nextInt(8) - rand.nextInt(8),
                        y + rand.nextInt(4) - rand.nextInt(4),
                        z + rand.nextInt(8) - rand.nextInt(8));
                this.placeDoublePlant(world, p);
            }
        } else if (this.kind == Kind.LEAF) {
            for (int l = 0; l < 64; ++l) {
                final BlockPos p = new BlockPos(
                        x + rand.nextInt(8) - rand.nextInt(8),
                        y + rand.nextInt(4) - rand.nextInt(4),
                        z + rand.nextInt(8) - rand.nextInt(8));
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
                if (world.isAirBlock(p) && this.state.getBlock().canPlaceBlockAt(world, p)) {
                    world.setBlockState(p, this.state, 2);
                }
            }
        }

        return true;
    }

    private void placeDoublePlant(World world, BlockPos p) {

        if (!world.isAirBlock(p) || !world.isAirBlock(p.up())) {
            return;
        }
        if (!Blocks.DOUBLE_PLANT.canPlaceBlockAt(world, p)) {
            return;
        }
        final IBlockState lower = this.state
                .withProperty(BlockDoublePlant.VARIANT, this.doubleType)
                .withProperty(BlockDoublePlant.HALF, BlockDoublePlant.EnumBlockHalf.LOWER);
        world.setBlockState(p, lower, 2);
        world.setBlockState(p.up(), lower.withProperty(BlockDoublePlant.HALF, BlockDoublePlant.EnumBlockHalf.UPPER), 2);
    }

    /** {@link BlockTallGrass} 的默认状态（RWG 的 {@code Blocks.tallgrass, 1}）。 */
    public static IBlockState tallGrass() {
        return Blocks.TALLGRASS.getDefaultState().withProperty(BlockTallGrass.TYPE, BlockTallGrass.EnumType.GRASS);
    }

    /** RWG 的 {@code Blocks.tallgrass, 2}（蕨）。 */
    public static IBlockState fern() {
        return Blocks.TALLGRASS.getDefaultState().withProperty(BlockTallGrass.TYPE, BlockTallGrass.EnumType.FERN);
    }
}
