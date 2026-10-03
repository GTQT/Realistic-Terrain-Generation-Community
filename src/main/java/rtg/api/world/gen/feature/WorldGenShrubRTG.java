package rtg.api.world.gen.feature;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;
import rtg.RTGConfig;

import java.util.Random;
import java.util.function.Function;


public class WorldGenShrubRTG extends WorldGenerator {

    private int varSize;
    private IBlockState logBlock;
    private IBlockState leaveBlock;
    private boolean varSand;

    /**
     * 移植上游新树系统（BOP 群系接线需要）：叶子可按 Random **逐块**选取，
     * 而不是整丛同一种叶子。上游有 5 参构造器与这个字段。
     *
     * <p>默认实现返回 {@link #leaveBlock}，所以**不传 leafChoice 的老调用点行为完全不变**。
     * 本仓库额外保留了下面的 {@code reset(...)}（对象重用池），上游没有这个优化。
     */
    protected Function<Random, IBlockState> leafChoice = new Function<Random, IBlockState>() {
        public IBlockState apply(Random applied) {
            return leaveBlock;
        }
    };

    public WorldGenShrubRTG(int size, IBlockState log, IBlockState leav, boolean sand) {

        reset(size, log, leav, sand);
    }

    /**
     * 上游同款 5 参构造器（带逐块叶子选择）。
     */
    public WorldGenShrubRTG(int size, IBlockState log, IBlockState leav, boolean sand,
                            Function<Random, IBlockState> _leafChoice) {

        reset(size, log, leav, sand);
        leafChoice = _leafChoice;
    }

    // ====== 新增：重置参数以复用对象，避免反复new ======
    public void reset(int size, IBlockState log, IBlockState leav, boolean sand) {
        varSize = size;
        varSand = sand;

        logBlock = log;
        leaveBlock = leav;
    }

    /**
     * 带逐块叶子选择的重置重载（供 {@code DecoShrub} 复用池使用）。
     */
    public void reset(int size, IBlockState log, IBlockState leav, boolean sand,
                      Function<Random, IBlockState> _leafChoice) {
        reset(size, log, leav, sand);
        leafChoice = _leafChoice;
    }

    @Override
    public boolean generate(World world, Random rand, BlockPos pos) {

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        int width = varSize > 6 ? 6 : varSize;
        int height = varSize > 3 ? 2 : 1;

        for (int i = 0; i < varSize; i++) {
            int rX = rand.nextInt(width * 2) - width;
            int rY = rand.nextInt(height);
            int rZ = rand.nextInt(width * 2) - width;

            if (i == 0 && varSize > 4) {
                buildLeaves(world, rand, x + rX, y, z + rZ, 3);
            }
            else if (i == 1 && varSize > 2) {
                buildLeaves(world, rand, x + rX, y, z + rZ, 2);
            }
            else {
                buildLeaves(world, rand, x + rX, y + rY, z + rZ, 1);
            }
        }
        return true;
    }

    public void buildLeaves(World world, Random rand, int x, int y, int z, int size) {

        IBlockState b = world.getBlockState(new BlockPos(x, y - 2, z));
        IBlockState b1 = world.getBlockState(new BlockPos(x, y - 1, z));

        if ((b == Blocks.SAND.getDefaultState() || b1 == Blocks.SAND.getDefaultState()) && !RTGConfig.treesCanGenerateOnSand()) {
            return;
        }

        if (b.getMaterial() == Material.GRASS || b.getMaterial() == Material.GROUND || (varSand && b.getMaterial() == Material.SAND)) {
            if (b1 != Blocks.WATER.getDefaultState()) {
                if (!RTGConfig.shrubsBelowSurface()) {

                    if (b1.getMaterial() != Material.AIR &&
                        b1.getMaterial() != Material.VINE &&
                        b1.getMaterial() != Material.PLANTS &&
                        b1 != Blocks.SNOW_LAYER.getDefaultState()) {
                        return;
                    }
                }

                for (int i = -size; i <= size; i++) {
                    for (int j = -1; j <= 1; j++) {
                        for (int k = -size; k <= size; k++) {
                            if (Math.abs(i) + Math.abs(j) + Math.abs(k) <= size) {
                                buildBlock(world, x + i, y + j, z + k, leafChoice.apply(rand));
                            }
                        }
                    }
                }
                world.setBlockState(new BlockPos(x, y - 1, z), logBlock, 0);
            }
        }
    }

    public void buildBlock(World world, int x, int y, int z, IBlockState block) {

        IBlockState b = world.getBlockState(new BlockPos(x, y, z));

        // We don't want shrubs generating in the middle of sugarcane, so let's add a special check for that here.
        if (b.getBlock() == Blocks.REEDS) {
            return;
        }

        if (b.getMaterial() == Material.AIR || b.getMaterial() == Material.VINE || b.getMaterial() == Material.PLANTS || b == Blocks.SNOW_LAYER) {
            world.setBlockState(new BlockPos(x, y, z), block, 0);
        }
    }
}
