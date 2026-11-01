package rtg.api.world.gen.feature;

import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockFarmland;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.Random;


/**
 * RWG {@code rwg2.deco.DecoWildWheat} 的逐行移植。
 *
 * <p>0 = 马铃薯、1 = 胡萝卜、2 = 小麦。RWG 会在原地铺 30 格农田 + 作物，最后把中心那一格
 * 地面替换成水源（原文如此）。1.7.10 的 metadata 4–7 在 1.12 对应
 * {@code BlockCrops.AGE} / {@code BlockFarmland.MOISTURE} 的同值。
 */
public class WorldGenRtgWildWheat extends WorldGenerator {

    private final IBlockState cropBlock;

    public WorldGenRtgWildWheat(int type) {
        this.cropBlock = type == 0 ? Blocks.POTATOES.getDefaultState()
                : type == 1 ? Blocks.CARROTS.getDefaultState()
                        : Blocks.WHEAT.getDefaultState();
    }

    @Override
    public boolean generate(World world, Random rand, BlockPos pos) {

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        while (y > 0) {
            final IBlockState state = world.getBlockState(new BlockPos(x, y, z));
            if (!world.isAirBlock(new BlockPos(x, y, z))
                    || state.getBlock().isLeaves(state, world, new BlockPos(x, y, z))) {
                break;
            }
            y--;
        }

        final IBlockState ground = world.getBlockState(new BlockPos(x, y, z));
        if (ground.getBlock() != Blocks.GRASS && ground.getBlock() != Blocks.DIRT) {
            return false;
        }

        for (int j = 0; j < 4; j++) {
            final int bx = j == 0 ? x - 1 : j == 1 ? x + 1 : x;
            final int bz = j == 2 ? z - 1 : j == 3 ? z + 1 : z;
            final Material m = world.getBlockState(new BlockPos(bx, y, bz)).getMaterial();
            if (m != Material.GROUND && m != Material.GRASS) {
                return false;
            }
        }

        for (int i = 0; i < 30; i++) {
            final int rx = rand.nextInt(5) - 2;
            final int ry = rand.nextInt(2) - 1;
            final int rz = rand.nextInt(5) - 2;
            final BlockPos p = new BlockPos(x + rx, y + ry, z + rz);
            final IBlockState b = world.getBlockState(p);

            if ((b.getBlock() == Blocks.GRASS || b.getBlock() == Blocks.DIRT)
                    && world.isAirBlock(p.up())) {
                world.setBlockState(p, Blocks.FARMLAND.getDefaultState()
                        .withProperty(BlockFarmland.MOISTURE, rand.nextInt(4) + 4), 2);
                world.setBlockState(p.up(), this.cropBlock
                        .withProperty(BlockCrops.AGE, rand.nextInt(4) + 4), 2);
            }
        }

        world.setBlockState(new BlockPos(x, y, z), Blocks.WATER.getDefaultState(), 2);
        return true;
    }
}
