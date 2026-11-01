package rtg.api.world.gen.feature;

import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockOldLeaf;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.Random;


/**
 * RWG {@code rwg2.deco.trees.DecoJungleFat} / {@code DecoJungleTall} /
 * {@code DecoJungleSmall} 的逐行移植（Tall 与 Small 源码完全相同，合并为一个 {@link #fat} = false 实现）。
 *
 * <p>两者差别与 RWG 一致：
 * <ul>
 *   <li>Fat：允许沙地、5 条支柱根、双干（(x,z) 与 (x+1,z+1)）、每分支 2 个叶团 + 1 个小叶团、
 *       叶核半径 1.3；</li>
 *   <li>Tall/Small：只认草/土、3 条支柱根、单干、每分支 1 个叶团、叶核半径 0.6。</li>
 * </ul>
 *
 * <p>RWG 用 {@code Blocks.log, 15}（丛林原木 + 轴 NONE）与 {@code Blocks.leaves, 3}（丛林叶），
 * 这里用 {@link BlockPlanks.EnumType#JUNGLE} 还原。
 */
public class WorldGenRtgJungle extends WorldGenerator {

    private static final IBlockState JUNGLE_LOG = Blocks.LOG.getDefaultState()
            .withProperty(BlockOldLog.VARIANT, BlockPlanks.EnumType.JUNGLE)
            .withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.NONE);
    private static final IBlockState JUNGLE_LEAVES = Blocks.LEAVES.getDefaultState()
            .withProperty(BlockOldLeaf.VARIANT, BlockPlanks.EnumType.JUNGLE);

    private final int base;
    private final int root;
    private final float length;
    private final int branches;
    private final float verStart;
    private final float verRand;
    private final boolean fat;

    /**
     * @param base     树干起点（相对地面 y）
     * @param root     支柱根高度
     * @param length   分支长度
     * @param branches 分支数
     * @param fat      {@code true} = DecoJungleFat，{@code false} = DecoJungleTall/Small
     */
    public WorldGenRtgJungle(int base, int root, float length, int branches, float verStart, float verRand,
            boolean fat) {

        this.base = base;
        this.root = root;
        this.length = length;
        this.branches = branches;
        this.verStart = verStart;
        this.verRand = verRand;
        this.fat = fat;
    }

    @Override
    public boolean generate(World world, Random rand, BlockPos pos) {

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        final IBlockState below = world.getBlockState(pos.down());
        if (this.fat) {
            if (below.getBlock() != Blocks.GRASS && below.getBlock() != Blocks.DIRT
                    && below.getBlock() != Blocks.SAND) {
                return false;
            }
        } else if (below.getBlock() != Blocks.GRASS && below.getBlock() != Blocks.DIRT) {
            return false;
        }

        final float r = rand.nextFloat() * 360f;

        if (this.root > 0) {
            if (this.fat) {
                for (int k = 0; k < 5; k++) {
                    this.generateBranch(world, x + 0.5f, y + this.root, z + 0.5f,
                            (120 * k) - 25 + rand.nextInt(50) + r, 1.6f + rand.nextFloat() * 0.1f, this.root * 1.8f);
                }
            } else {
                for (int k = 0; k < 3; k++) {
                    this.generateBranch(world, x, y + this.root, z,
                            (120 * k) - 40 + rand.nextInt(80), 1.6f + rand.nextFloat() * 0.1f, this.root * 1.7f);
                }
            }
        }

        if (this.fat) {
            for (int i = y + this.root - 2; i < y + this.base + 2; i++) {
                world.setBlockState(new BlockPos(x, i, z), JUNGLE_LOG, 2);
                world.setBlockState(new BlockPos(x + 1, i, z + 1), JUNGLE_LOG, 2);
            }
        } else {
            for (int i = y + this.root; i < y + this.base; i++) {
                world.setBlockState(new BlockPos(x, i, z), JUNGLE_LOG, 2);
            }
        }

        float horDir;
        float verDir;
        int eX;
        int eY;
        int eZ;

        for (int j = 0; j < this.branches; j++) {
            horDir = (this.fat ? 80 * j - 40 + rand.nextInt(80) : 120 * j - 60 + rand.nextInt(120));
            verDir = this.verStart + rand.nextFloat() * this.verRand;

            if (this.fat) {
                this.generateBranch(world, x + 0.5f, y + this.base, z + 0.5f, horDir, verDir, this.length);
                eX = (int) (Math.cos(horDir * Math.PI / 180D) * verDir * this.length);
                eZ = (int) (Math.sin(horDir * Math.PI / 180D) * verDir * this.length);
                eY = (int) ((1f - verDir) * this.length);

                for (int m = 0; m < 2; m++) {
                    this.generateLeaves(world, rand, x + eX - 2 + rand.nextInt(5),
                            y + this.base + eY + rand.nextInt(2), z + eZ - 2 + rand.nextInt(5), 4f, 1.5f);
                }

                eX *= 0.8f;
                eY *= 0.8f;
                eZ *= 0.8f;

                this.generateLeaves(world, rand, x + eX, y + this.base + eY, z + eZ, 3f, 1.5f);
            } else {
                this.generateBranch(world, x, y + this.base, z, horDir, verDir, this.length);
                eX = x + (int) (Math.cos(horDir * Math.PI / 180D) * verDir * this.length);
                eZ = z + (int) (Math.sin(horDir * Math.PI / 180D) * verDir * this.length);
                eY = y + this.base + (int) ((1f - verDir) * this.length);

                this.generateLeaves(world, rand, eX, eY, eZ, 4f, 1.5f);
            }
        }

        return true;
    }

    private void generateBranch(World world, float x, float y, float z, double horDir, float verDir, float length) {

        if (verDir < 0f) {
            verDir = -verDir;
        }

        float c = 0f;
        float velY = 1f - verDir;

        if (verDir > 1f) {
            verDir = 1f - (verDir - 1f);
        }

        float velX = (float) Math.cos(horDir * Math.PI / 180D) * verDir;
        float velZ = (float) Math.sin(horDir * Math.PI / 180D) * verDir;

        while (c < length) {
            world.setBlockState(new BlockPos((int) x, (int) y, (int) z), JUNGLE_LOG, 2);
            x += velX;
            y += velY;
            z += velZ;
            c += 1f;
        }
    }

    private void generateLeaves(World world, Random rand, float x, float y, float z, float size, float width) {

        float dist;
        final int s = (int) (size - 1f);
        final int w = (int) ((size - 1f) * width);
        final int jStart = this.fat ? -s + 1 : -s;
        final float core = this.fat ? 1.3f : 0.6f;

        for (int i = -w; i <= w; i++) {
            for (int j = jStart; j <= s; j++) {
                for (int k = -w; k <= w; k++) {
                    dist = Math.abs((float) i / width) + Math.abs(j) + Math.abs((float) k / width);
                    if (dist <= size - 0.5f || (dist <= size && rand.nextBoolean())) {
                        if (dist < core) {
                            world.setBlockState(new BlockPos((int) x + i, (int) y + j, (int) z + k), JUNGLE_LOG, 2);
                        }
                        if (world.isAirBlock(new BlockPos((int) x + i, (int) y + j, (int) z + k))) {
                            world.setBlockState(new BlockPos((int) x + i, (int) y + j, (int) z + k),
                                    JUNGLE_LEAVES, 2);
                        }
                    }
                }
            }
        }
    }
}
