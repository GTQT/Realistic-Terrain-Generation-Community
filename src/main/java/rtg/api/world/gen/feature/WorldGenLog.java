package rtg.api.world.gen.feature;

import net.minecraft.block.BlockLog;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;
import rtg.api.util.BlockUtil;
import rtg.api.util.BlockUtil.MatchType;
import rtg.api.util.Logger;

import javax.annotation.Nonnull;
import java.util.Random;


public class WorldGenLog extends WorldGenerator {

    private IBlockState logBlock;
    private IBlockState leavesBlock;
    private int logLength;
    private boolean generateLeaves;

    // 重用MutableBlockPos减少对象创建
    private final MutableBlockPos checkPos = new MutableBlockPos();
    private final MutableBlockPos leafCheckPos = new MutableBlockPos();

    /**
     * @param logBlock
     * @param leavesBlock
     * @param logLength
     */
    public WorldGenLog(IBlockState logBlock, IBlockState leavesBlock, int logLength) {

        this.logBlock = logBlock;
        this.leavesBlock = leavesBlock;
        this.logLength = logLength;

        this.generateLeaves = false;
    }

    @Override
    public boolean generate(@Nonnull World world, @Nonnull Random rand, @Nonnull BlockPos pos) {

        int x = pos.getX(),
                y = pos.getY(),
                z = pos.getZ();

        // 重用MutableBlockPos检查地面
        IBlockState ground = world.getBlockState(checkPos.setPos(x, y - 1, z));
        Material groundMaterial = ground.getMaterial();
        if (groundMaterial != Material.GROUND && groundMaterial != Material.GRASS &&
                groundMaterial != Material.SAND && groundMaterial != Material.ROCK) {
            return false;
        }

        int dir = rand.nextInt(2); // The direction of the log (0 = X; 1 = Z)

        // 提前计算方向偏移
        int xOffset = (dir == 0) ? 1 : 0;
        int zOffset = (dir == 1) ? 1 : 0;

        // 使用数组代替ArrayList，预分配足够空间
        int maxSize = logLength * 2;
        int[] xPositions = new int[maxSize];
        int[] yPositions = new int[maxSize];
        int[] zPositions = new int[maxSize];
        IBlockState[] blocks = new IBlockState[maxSize];
        int placedCount = 0;

        // 重用MutableBlockPos
        MutableBlockPos mpos = new MutableBlockPos(pos);

        // 第一阶段：向后查找起始点
        for (int i = 0; i < logLength; i++) {
            int checkX = x - (xOffset * i);
            int checkZ = z - (zOffset * i);

            IBlockState block = world.getBlockState(mpos.setPos(checkX, y, checkZ));
            Material material = block.getMaterial();

            if (material != Material.AIR && material != Material.VINE && material != Material.PLANTS) {
                break;
            }

            x = checkX;
            z = checkZ;

            if (airCheck(world, x, y, z) > 0) {
                return false;
            }
        }

        // 第二阶段：向前放置日志
        int airCount = 0;
        for (int i = 0; i < maxSize; i++) {
            int checkX = x + (xOffset * i);
            int checkZ = z + (zOffset * i);

            IBlockState block = world.getBlockState(mpos.setPos(checkX, y, checkZ));
            Material material = block.getMaterial();

            if (material != Material.AIR && material != Material.VINE && material != Material.PLANTS) {
                break;
            }

            airCount += airCheck(world, checkX, y, checkZ);
            if (airCount > 2) {
                return false;
            }

            /*
             * Before we place the log block, let's make sure that there's an air block immediately above it.
             */
            if (!BlockUtil.checkVerticalBlocks(MatchType.ALL, world, mpos.setPos(checkX, y, checkZ), 1, Blocks.AIR)) {
                return false;
            }

            // 预计算日志方块方向
            IBlockState logState;
            try {
                logState = logBlock.withProperty(BlockLog.LOG_AXIS,
                        (dir == 0 ? BlockLog.EnumAxis.X : BlockLog.EnumAxis.Z));
            }
            catch (Exception e) {
                return false;
            }

            // 存储位置和方块状态
            xPositions[placedCount] = checkX;
            yPositions[placedCount] = y;
            zPositions[placedCount] = checkZ;
            blocks[placedCount] = logState;
            placedCount++;

            if (this.generateLeaves) {
                addLeaves(world, rand, dir, checkX, y, checkZ);
            }
        }

        // 一次性放置所有日志方块
        for (int i = 0; i < placedCount; i++) {
            world.setBlockState(mpos.setPos(xPositions[i], yPositions[i], zPositions[i]), blocks[i], 2);
        }

        return placedCount > 0;
    }

    private int airCheck(World world, int x, int y, int z) {

        // 重用MutableBlockPos
        IBlockState below1 = world.getBlockState(checkPos.setPos(x, y - 1, z));
        Material material1 = below1.getMaterial();
        if (material1 == Material.AIR || material1 == Material.VINE ||
                material1 == Material.WATER || material1 == Material.PLANTS) {

            IBlockState below2 = world.getBlockState(checkPos.setPos(x, y - 2, z));
            Material material2 = below2.getMaterial();
            if (material2 == Material.AIR || material2 == Material.VINE ||
                    material2 == Material.WATER || material2 == Material.PLANTS) {
                return 99;
            }
            return 1;
        }

        return 0;
    }

    private void addLeaves(World world, Random rand, int dir, int x, int y, int z) {

        // 重用MutableBlockPos
        MutableBlockPos pos = this.leafCheckPos;
        IBlockState block;

        if (dir == 0) {
            // Z方向检查
            block = world.getBlockState(pos.setPos(x, y, z - 1));
            if (isReplaceable(block.getMaterial()) && rand.nextInt(3) == 0) {
                world.setBlockState(pos, leavesBlock, 2);
            }
            block = world.getBlockState(pos.setPos(x, y, z + 1));
            if (isReplaceable(block.getMaterial()) && rand.nextInt(3) == 0) {
                world.setBlockState(pos, leavesBlock, 2);
            }
        }
        else {
            // X方向检查
            block = world.getBlockState(pos.setPos(x - 1, y, z));
            if (isReplaceable(block.getMaterial()) && rand.nextInt(3) == 0) {
                world.setBlockState(pos, leavesBlock, 2);
            }
            block = world.getBlockState(pos.setPos(x + 1, y, z));
            if (isReplaceable(block.getMaterial()) && rand.nextInt(3) == 0) {
                world.setBlockState(pos, leavesBlock, 2);
            }
        }

        // 上方检查
        block = world.getBlockState(pos.setPos(x, y + 1, z));
        if (isReplaceable(block.getMaterial()) && rand.nextInt(3) == 0) {
            world.setBlockState(pos, leavesBlock, 2);
        }
    }

    // 提取可替换材料检查
    private boolean isReplaceable(Material material) {
        return material == Material.AIR || material == Material.VINE || material == Material.PLANTS;
    }

    public IBlockState getLogBlock() {
        return logBlock;
    }

    public WorldGenLog setLogBlock(IBlockState logBlock) {
        this.logBlock = logBlock;
        return this;
    }

    public IBlockState getLeavesBlock() {
        return leavesBlock;
    }

    public WorldGenLog setLeavesBlock(IBlockState leavesBlock) {
        this.leavesBlock = leavesBlock;
        return this;
    }
}