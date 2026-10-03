package rtg.api.world.surface;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.api.config.BiomeConfig;
import rtg.api.world.RTGWorld;


/**
 * RWG {@code rwg/biomes/realistic/ocean/RealisticBiomeOcean.java:66-83} 的逐行移植 ——
 * **海底**地表：浅海刷沙、深海刷砾石，都是 6 格厚。
 *
 * <h2>为什么它也需要单独提出来</h2>
 *
 * 与 {@link SurfaceCoastDunes} 同样的情形：RWG 把这份地表**内联在群系类里**
 * （{@code RealisticBiomeOcean.rReplace}），rtgc 只移植了地形那半
 * （{@code TerrainBase.terrainOcean}），地表那半没提 ——
 * 于是各海洋群系各自内联一份（`RealisticBiomeVanillaDeepOcean.SurfaceVanillaDeepOcean` 等）。
 *
 * <p>本轮新增的 4 个 RWG 海洋群系（{@code RtgOceanBiome}）需要它：
 * RWG 的 {@code BaseBiomeOcean} **没有**设置 top/filler 方块，海底完全由这份 {@code rReplace} 决定。
 *
 * <pre>
 * RWG 原文：
 *   surface = shallow ? Blocks.sand : Blocks.gravel;
 *   for (level = 255; level &gt;= 0; level--)
 *       if (block == air || block == water) depth = -1;
 *       else if (block == stone) { depth++; if (depth &lt; 6) blocks[..] = surface; }
 * </pre>
 *
 * <p>唯一的管线适配：rtgc 的地表统一用 {@code ChunkPrimer.setBlockState} 写入，
 * 而不是 RWG 的 {@code Block[]/byte[]} 直写（与其余已移植地表一致）。
 */
public class SurfaceOcean extends SurfaceBase {

    private static final IBlockState SAND = Blocks.SAND.getDefaultState();
    private static final IBlockState GRAVEL = Blocks.GRAVEL.getDefaultState();

    /** RWG 的厚度：{@code depth < 6}。 */
    private static final int SURFACE_DEPTH = 6;

    private final IBlockState surfaceBlock;

    public SurfaceOcean(final BiomeConfig config, final boolean shallow) {

        // super 的 top/filler 只是满足 SurfaceBase 的约定；真正的块由 surfaceBlock 决定
        super(config, shallow ? SAND : GRAVEL, shallow ? SAND : GRAVEL);
        this.surfaceBlock = shallow ? SAND : GRAVEL;
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        for (int k = 255; k > -1; k--) {
            final Block b = primer.getBlockState(x, k, z).getBlock();

            if (b == Blocks.AIR || b == Blocks.WATER) {
                depth = -1;
            } else if (b == Blocks.STONE) {
                depth++;
                if (depth < SURFACE_DEPTH) {
                    primer.setBlockState(x, k, z, this.surfaceBlock);
                }
            }
        }
    }
}
