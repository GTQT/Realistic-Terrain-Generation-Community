package rtg.api.world.gen.feature;

import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.Random;


/**
 * RWG {@code rwg2.deco.DecoCacti} 的逐行移植。
 *
 * <p>RTG 自带的 {@code WorldGenCacti} 与它同源，但把 1.7.10 的 10 次尝试循环注释掉了
 * （源码里是 {@code //for (int l = 0; l < 10; ++l)}），每次调用只试 1 个位置。RWG 的
 * {@code rDecorate} 里动辄 {@code 12f * strength} 次调用、每次再试 10 个位置，密度差一个数量级；
 * 为忠实照抄，这里恢复 10 次尝试。
 */
public class WorldGenRtgCacti extends WorldGenerator {

    private final boolean sand;
    private final int eHeight;

    public WorldGenRtgCacti(boolean sandOnly) {
        this(sandOnly, 0);
    }

    public WorldGenRtgCacti(boolean sandOnly, int extraHeight) {
        this.sand = sandOnly;
        this.eHeight = extraHeight;
    }

    @Override
    public boolean generate(World world, Random rand, BlockPos pos) {

        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();

        for (int l = 0; l < 10; ++l) {
            final int i1 = x + rand.nextInt(8) - rand.nextInt(8);
            final int j1 = y + rand.nextInt(4) - rand.nextInt(4);
            final int k1 = z + rand.nextInt(8) - rand.nextInt(8);
            final BlockPos p = new BlockPos(i1, j1, k1);

            if (world.isAirBlock(p)) {
                final net.minecraft.block.Block below = world.getBlockState(p.down()).getBlock();
                if (below == Blocks.SAND || (!this.sand && (below == Blocks.GRASS || below == Blocks.DIRT))) {
                    final int l1 = 1 + rand.nextInt(rand.nextInt(3) + 1);
                    if (below == Blocks.GRASS || below == Blocks.DIRT) {
                        world.setBlockState(p.down(), Blocks.SAND.getDefaultState(), 2);
                    }

                    for (int i2 = 0; i2 < l1 + this.eHeight; ++i2) {
                        final BlockPos cactus = p.up(i2);
                        if (Blocks.CACTUS.canBlockStay(world, cactus)) {
                            world.setBlockState(cactus, Blocks.CACTUS.getDefaultState(), 2);
                        }
                    }
                }
            }
        }

        return true;
    }
}
