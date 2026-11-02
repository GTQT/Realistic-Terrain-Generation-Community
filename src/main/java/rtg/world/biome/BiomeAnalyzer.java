package rtg.world.biome;


/**
 * 群系数组的坐标辅助。
 *
 * <h2>为什么这里只剩一个 {@code xyinverted()}</h2>
 *
 * <p>RWG 的 {@code ChunkGeneratorRealistic} **没有**「群系修复」这一步：
 * <ul>
 *   <li>群系由 {@code getNewNoise} 里的 {@code randBiome} 一次算定，写进唯一的
 *       {@code biomes[]}（见 {@link rtg.world.gen.ChunkLandscape#biome}）；</li>
 *   <li>河道的群系替换发生在 {@code replaceBlocksForBiome} 里：
 *       {@code base[i*16+j] = biome.riverBiome;} —— 只改"原版群系数组"，
 *       不改 {@code biomes[]}，也不改地表。rtgc 已按同一位置移植，见
 *       {@code ChunkGeneratorRTG#replaceBiomeBlocks}。</li>
 * </ul>
 *
 * <p>原先的 {@code newRepair}（河流/回落替换，并顺带把 {@code landscape.biome} 从
 * 布局群系重算一遍）是 RTG 时代的做法，会覆盖掉 {@code randBiome} 的产物；
 * 为忠实 RWG 已删除。
 */
public final class BiomeAnalyzer {

    /**
     * 1.12.2 的区块群系数组按 {@code x + z * 16} 排布，而 rtgc 的景观数组按
     * {@code x * 16 + z} 排布（沿用 RWG 的转置写法）。这个表只做一次转置，
     * 供 {@code ChunkGeneratorRTG} 写 {@code intBiomeArray} 时用。
     */
    public int[] xyinverted() {
        int[] result = new int[256];
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                result[i * 16 + j] = j * 16 + i;
            }
        }
        for (int i = 0; i < 256; i++) {
            if (result[result[i]] != i) {
                throw new RuntimeException(i + " " + result[i] + " " + result[result[i]]);
            }
        }
        return result;
    }
}
