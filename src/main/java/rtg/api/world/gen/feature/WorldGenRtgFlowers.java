package rtg.api.world.gen.feature;

import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockFlower;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;
import rtg.api.util.BlockUtil;

import java.util.Random;


/**
 * RWG {@code rwg2.deco.DecoFlowers} 的逐行移植。
 *
 * <p>RWG 的 id 表（源码注释原文）：
 * <pre>
 *   0 Rose - 1 Blue Orchid - 2 Allium - 3 Azure Bluet - 4 Red Tulip - 5 Orange Tulip
 *   6 White Tulip - 7 Pink Tulip - 8 Oxeye Daisy - 9 Yellow Flower
 *   10 Sunflower - 11 Lilac - 12 Double Tallgrass - 13 Large Fern - 14 Rose Bush - 15 Peony
 * </pre>
 * 1.12 里 0–8 是 {@code red_flower} 的 {@link BlockFlower.EnumFlowerType}，9 是
 * {@code yellow_flower}，10–15 是 {@code double_plant} 的 6 种类型；{@link BlockUtil#getStateFlower}
 * 已封装前两者。
 */
public class WorldGenRtgFlowers extends WorldGenerator {

    private final int[] flowers;

    public WorldGenRtgFlowers(int[] flowers) {
        this.flowers = flowers;
    }

    @Override
    public boolean generate(World world, Random rand, BlockPos pos) {

        if (this.flowers == null || this.flowers.length == 0) {
            return false;
        }

        final int randomFlower = this.flowers[rand.nextInt(this.flowers.length)];

        if (randomFlower > 9) {
            final BlockDoublePlant.EnumPlantType type = BlockDoublePlant.EnumPlantType.values()[
                    Math.min(BlockDoublePlant.EnumPlantType.values().length - 1, randomFlower - 10)];
            final IBlockState lower = Blocks.DOUBLE_PLANT.getDefaultState()
                    .withProperty(BlockDoublePlant.VARIANT, type)
                    .withProperty(BlockDoublePlant.HALF, BlockDoublePlant.EnumBlockHalf.LOWER);
            for (int l = 0; l < 64; ++l) {
                final BlockPos p = new BlockPos(
                        pos.getX() + rand.nextInt(8) - rand.nextInt(8),
                        pos.getY() + rand.nextInt(4) - rand.nextInt(4),
                        pos.getZ() + rand.nextInt(8) - rand.nextInt(8));
                if (world.isAirBlock(p) && world.isAirBlock(p.up())
                        && Blocks.DOUBLE_PLANT.canPlaceBlockAt(world, p)) {
                    world.setBlockState(p, lower, 2);
                    world.setBlockState(p.up(),
                            lower.withProperty(BlockDoublePlant.HALF, BlockDoublePlant.EnumBlockHalf.UPPER), 2);
                }
            }
        } else {
            final BlockFlower.EnumFlowerType type = randomFlower == 9
                    ? BlockFlower.EnumFlowerType.DANDELION
                    : BlockFlower.EnumFlowerType.getType(BlockFlower.EnumFlowerColor.RED, randomFlower);
            final IBlockState flower = BlockUtil.getStateFlower(type);
            for (int l = 0; l < 64; ++l) {
                final BlockPos p = new BlockPos(
                        pos.getX() + rand.nextInt(8) - rand.nextInt(8),
                        pos.getY() + rand.nextInt(4) - rand.nextInt(4),
                        pos.getZ() + rand.nextInt(8) - rand.nextInt(8));
                if (world.isAirBlock(p) && p.getY() < 255
                        && flower.getBlock().canPlaceBlockAt(world, p)
                        && ((BlockFlower) flower.getBlock()).canBlockStay(world, p, flower)) {
                    world.setBlockState(p, flower, 2);
                }
            }
        }

        return true;
    }
}
