package rtg.world.gen;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.api.util.noise.ContinentalNoise;
import rtg.api.util.noise.RwgCellNoise;
import rtg.api.util.noise.SimplexNoise;
import rtg.world.biome.RtgBiomeLayout;


/**
 * RWG {@code rwg/map/LavaCaveLandmark.java}（261 行）的逐行移植：
 * 一座确定性熔岩洞窟、它顶上的 Voronoi 隧道网、以及地表通风口（天窗）的**区块内**渲染器。
 *
 * <h2>逐行对照（差异全部是"逐列数组 → ChunkPrimer"与噪声类型这一类 API 适配）</h2>
 * <ul>
 *   <li>{@code Block[] blocks + byte[] metadata}（RWG 的列内索引
 *       {@code (x*16+z)*256 + y}）→ {@code ChunkPrimer primer}：
 *       {@code blocks[index]} → {@code primer.getBlockState(localX, y, localZ)} /
 *       {@code primer.setBlockState(localX, y, localZ, state)}。
 *       1.12.2 的方块自带状态，没有独立的 metadata 数组，故 {@code metadata} 形参
 *       **删除**（RWG 本类体内对它的每一处写入都是写 {@code 0}，即默认状态）。</li>
 *   <li>{@code ChunkManagerRealistic manager} → {@link RtgBiomeLayout}（RTGC 的等价物）。
 *       本类只按名调用它的 {@code getVolcanoVicinityCoordinates} /
 *       {@code getLavaCaveCoordinates}，两者在 {@code RtgBiomeLayout} 上已存在且语义同源。</li>
 *   <li>{@code NoiseGenerator perlin} → {@code SimplexNoise perlin}：
 *       RWG 的 {@code perlin.noise2(a, b)} → {@code perlin.noise2f(a, b)}。
 *       RTGC 侧由调用方传 {@code rtgWorld.simplexInstance(0)}（与
 *       {@code ChunkGeneratorRTG} 里地形/装饰路径用的是同一个实例）。</li>
 *   <li>{@code CellNoise cell} → {@link RwgCellNoise}（逐行移植，{@code sampleTwo2D} 同源）。</li>
 *   <li>{@code Blocks.air / lava / obsidian / stone / glowstone} → 对应的
 *       {@code IBlockState}。RWG 比的是 {@code Block} 身份（不看 metadata），
 *       故判断统一比 {@code state.getBlock()}。</li>
 *   <li>{@code findSurface} 里的 {@code blocks[column + surface] == null} 分支：
 *       {@code ChunkPrimer} 的格子在未写入时返回 {@code Blocks.AIR} 的默认状态，
 *       不存在 null ⇒ 该分支按"RTGC 不可能成立"省略（判定结果不变）。</li>
 * </ul>
 *
 * <h2>RWG 里的调用点（供 RTGC 接线参考，本类不含调用方）</h2>
 * <ol>
 *   <li>{@code ChunkGeneratorRealistic.java:252}（在 {@code generateChunk} 里，紧跟在
 *       {@code replaceBlocksForBiome} / 洞穴 / 矿道 / 要塞 / 村庄**之后**、构造
 *       {@code Chunk} **之前**，且只在 {@code continental} 时执行）：
 *       {@code LavaCaveLandmark.generate(chunkBlocks, chunkMetadata, cx, cy, cmr, perlin, cell);}</li>
 *   <li>{@code ChunkGeneratorRealistic.java:272}（{@code markLavaCaveOpeningBiome} 内，
 *       生成阶段、写群系数组时）：{@code LavaCaveLandmark.isMarkerBiome(perlin, …)}；</li>
 *   <li>{@code ChunkGeneratorRealistic.java:543}（{@code getNewNoise} 内，逐列地表高度
 *       叠加通风口的锥体）：{@code LavaCaveLandmark.surfaceHeight(perlin, x, z, height)}；</li>
 *   <li>{@code ChunkGeneratorRealistic.java:1022}（{@code decorateBiome} 内，
 *       <b>装饰阶段</b>：群系 {@code rDecorate} 跑完、洞穴藤蔓之后，冰雪 pass 与
 *       原版装饰事件之前）：
 *       {@code LavaCaveLandmark.decorateSurface(worldObj, cmr, perlin, x, y, Support.lavaCaveSmolderingGrass);}</li>
 * </ol>
 *
 * <h2>接线时需要注意的两处缺口（本次不改调用方）</h2>
 * <ul>
 *   <li>{@code cell}：{@code RtgBiomeLayout} 里的 {@code cell} 是 {@code private} 且
 *       <b>没有公开取值器</b>（与 {@code perlin} 同类）。RWG 是在
 *       {@code ChunkGeneratorRealistic} 里自己 {@code new CellNoise(l, (short) 0)}，
 *       并存成一个字段（RWG:149）—— RTGC 若要照抄同一写法，就是在生成器构造时
 *       建一个 {@code new RwgCellNoise(rtgWorld.seed(), (short) 0)} 长期持有；
 *       因为 {@code sampleTwo2D} 不读 {@code useDistance}，它与布局内部那个
 *       （{@code setUseDistance(true)}）**输出逐位相同**，所以两种取法等价。</li>
 *   <li>{@code smolderingGrass}：见 {@link #decorateSurface} 的说明。</li>
 * </ul>
 */
public final class LavaCaveLandmark {

    private static final float MAIN_RADIUS = 88f;
    private static final float NETWORK_RADIUS = 320f;
    private static final int CHAMBER_Y = 29;
    private static final int LAVA_LEVEL = 25;
    private static final float SURFACE_RADIUS = 65f;
    private static final float OPENING_RADIUS = 14f;
    private static final float MARKER_BIOME_RADIUS = 25f;

    private LavaCaveLandmark() {}

    public static float surfaceHeight(SimplexNoise perlin, float localX, float localZ, float underlyingHeight) {
        float x = warpedX(perlin, localX, localZ);
        float z = warpedZ(perlin, localX, localZ);
        float distance = (float) Math.sqrt(x * x + z * z);
        if (distance >= SURFACE_RADIUS || underlyingHeight < 63f) return underlyingHeight;
        float cone = smoothstep(1f - distance / SURFACE_RADIUS) * 20f;
        float vent = smoothstep(1f - distance / (OPENING_RADIUS + 4f));
        float subtleRim = Math.max(0f, 1f - Math.abs(distance - (OPENING_RADIUS + 2f)) / 5f) * 2.5f;
        return underlyingHeight + cone * (1f - vent) + subtleRim;
    }

    public static boolean isSurfaceOpening(SimplexNoise perlin, float localX, float localZ) {
        return warpedDistanceSquared(perlin, localX, localZ) < OPENING_RADIUS * OPENING_RADIUS;
    }

    public static boolean isMarkerBiome(SimplexNoise perlin, float localX, float localZ) {
        return warpedDistanceSquared(perlin, localX, localZ) < MARKER_BIOME_RADIUS * MARKER_BIOME_RADIUS;
    }

    public static boolean isMainChamber(SimplexNoise perlin, float localX, float localZ) {
        return warpedDistanceSquared(perlin, localX, localZ) < MAIN_RADIUS * MAIN_RADIUS;
    }

    private static float warpedDistanceSquared(SimplexNoise perlin, float localX, float localZ) {
        float x = warpedX(perlin, localX, localZ);
        float z = warpedZ(perlin, localX, localZ);
        return x * x + z * z;
    }

    /**
     * RWG {@code decorateSurface}（装饰阶段调用，见类注释第 4 条）。
     *
     * <p><b>{@code smolderingGrass} 的缺口</b>：RWG 传的是
     * {@code Support.lavaCaveSmolderingGrass}，而该字段**全仓只有
     * {@code SupportBOP.init()} 会赋值**（{@code SupportBOP.java:38} =
     * {@code BOPCBlocks.bopGrass}），其余情况下恒为 {@code null}
     * （{@code Support.java:155} 显式置空）—— 也就是说 RWG 在**没有 BoP 时这一步整体不生效**。
     * RTGC 没有 {@code Support} 类，且 BoP 在 1.12.2 的对应方块是
     * {@code biomesoplenty:grass}（可用 {@code Block.getBlockFromName("biomesoplenty:grass")} 取到）。
     * 本次**不发明新方块**，保留形参由调用方给值：传 {@code null} 即与 RWG 无 BoP 时**逐字一致**
     * （首行直接 return）。
     */
    public static void decorateSurface(World world, RtgBiomeLayout manager, SimplexNoise perlin, int chunkX,
            int chunkZ, Block smolderingGrass) {
        if (smolderingGrass == null) return;
        for (int offsetX = 8; offsetX < 24; offsetX++) {
            int worldX = chunkX + offsetX;
            for (int offsetZ = 8; offsetZ < 24; offsetZ++) {
                int worldZ = chunkZ + offsetZ;
                long cave = manager.getLavaCaveCoordinates(worldX, worldZ);
                if (cave == Long.MIN_VALUE || !isMarkerBiome(
                        perlin,
                        ContinentalNoise.unpackVolcanoX(cave),
                        ContinentalNoise.unpackVolcanoY(cave)))
                    continue;
                float patch = perlin.noise2f((worldX + 317f) / 7f, (worldZ - 491f) / 7f)
                        + perlin.noise2f(worldX / 19f, worldZ / 19f) * .4f;
                if (patch <= .35f) continue;
                int surfaceY = highestBlockY(world, worldX, worldZ) - 1;
                if (surfaceY > 0 && world.getBlockState(new BlockPos(worldX, surfaceY, worldZ))
                        .getBlock() == Blocks.GRASS) {
                    world.setBlockState(new BlockPos(worldX, surfaceY, worldZ),
                            smolderingGrass.getStateFromMeta(1), 2);
                }
            }
        }
    }

    /**
     * RWG 的 {@code world.getHeightValue(x, z)}（1.7.10）的 1.12.2 等价物：
     * **该列最上面的非空气方块**的 y。1.12.2 的 {@code World} 没有这个重载
     * （只有 {@code getHeight(Heightmap.Type, BlockPos)} / {@code getTopSolidOrLiquidBlock}，
     * 两者对"草/雪层/树叶"的口径与 1.7.10 的 {@code getHeightValue} 都不一致），
     * 故这里逐列原样扫一遍 —— 语义与 RWG 用的那个方法相同，且只在本列上做。
     */
    private static int highestBlockY(World world, int worldX, int worldZ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 255; y > 0; y--) {
            if (world.getBlockState(pos.setPos(worldX, y, worldZ)).getBlock() != Blocks.AIR) {
                return y;
            }
        }
        return 0;
    }

    /**
     * RWG {@code generate}（生成阶段调用，见类注释第 1 条）。
     *
     * <p>{@code metadata} 形参按 1.12.2 的 API 约定删除（见类注释）。
     */
    public static void generate(ChunkPrimer primer, int chunkX, int chunkZ, RtgBiomeLayout manager,
            SimplexNoise perlin, RwgCellNoise cell) {
        double[] cellSamples = new double[4];
        for (int localX = 0; localX < 16; localX++) {
            int worldX = chunkX * 16 + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = chunkZ * 16 + localZ;
                if (manager.getVolcanoVicinityCoordinates(worldX, worldZ) != Long.MIN_VALUE) continue;
                long packed = manager.getLavaCaveCoordinates(worldX, worldZ);
                if (packed == Long.MIN_VALUE) continue;
                float landmarkX = ContinentalNoise.unpackVolcanoX(packed);
                float landmarkZ = ContinentalNoise.unpackVolcanoY(packed);
                carveColumn(
                        primer,
                        localX,
                        localZ,
                        worldX,
                        worldZ,
                        landmarkX,
                        landmarkZ,
                        perlin,
                        cell,
                        cellSamples);
            }
        }
    }

    private static void carveColumn(ChunkPrimer primer, int localX, int localZ, int worldX, int worldZ,
            float landmarkX, float landmarkZ, SimplexNoise perlin, RwgCellNoise cell, double[] cellSamples) {
        float x = warpedX(perlin, landmarkX, landmarkZ);
        float z = warpedZ(perlin, landmarkX, landmarkZ);
        float distance = (float) Math.sqrt(x * x + z * z);
        boolean main = distance < MAIN_RADIUS;

        int surface = findSurface(primer, localX, localZ);

        float networkScale = 108f;
        float networkWarpX = x + perlin.noise2f((worldX + 1700f) / 130f, (worldZ - 900f) / 130f) * 24f;
        float networkWarpZ = z + perlin.noise2f((worldX - 2300f) / 130f, (worldZ + 1100f) / 130f) * 24f;
        cell.sampleTwo2D(networkWarpX / networkScale, networkWarpZ / networkScale, 1D, cellSamples);
        float edgeDistance = (float) (cellSamples[2] - cellSamples[0]) * networkScale;
        float distanceFade = smoothstep(1f - distance / NETWORK_RADIUS);
        float tunnelRadius = 4f + distanceFade * 19f;
        boolean tunnel = distance < NETWORK_RADIUS && edgeDistance < tunnelRadius;
        if (!main && !tunnel) return;

        float tunnelCenter = CHAMBER_Y + perlin.noise2f(worldX / 105f, worldZ / 105f) * 2.2f
                + perlin.noise2f(worldX / 41f, worldZ / 41f) * 1.1f;
        float horizontal = edgeDistance / tunnelRadius;
        float tunnelHalfHeight = tunnelRadius * (float) Math.sqrt(Math.max(0f, 1f - horizontal * horizontal));
        float tunnelFloor = Math.min(tunnelCenter - tunnelHalfHeight, LAVA_LEVEL - 2f);
        float tunnelCeiling = Math.max(tunnelCenter + tunnelHalfHeight, LAVA_LEVEL + 4f);

        float roughness = perlin.noise2f(worldX / 13f, worldZ / 13f) * 2.2f
                + perlin.noise2f(worldX / 37f, worldZ / 37f) * 2.8f;
        float floor;
        float ceiling;
        int localLavaLevel;
        if (main) {
            float radial = Math.min(1f, distance / MAIN_RADIUS);
            float dome = 1f - smootherstep(radial);
            floor = 11f + smootherstep(radial) * 21f + roughness * .3f;
            float stalactiteNoise = perlin.noise2f((worldX + 947f) / 8f, (worldZ - 613f) / 8f)
                    + perlin.noise2f(worldX / 21f, worldZ / 21f) * .35f;
            float stalactite = (float) Math.pow(Math.max(0f, stalactiteNoise - .48f), 2D) * 16f;
            ceiling = Math.min(45f + dome * 29f + roughness, surface - 8f) - stalactite;
            localLavaLevel = LAVA_LEVEL;
            if (tunnel) {
                // Ease the Voronoi trough into the lake shore instead of cutting a vertical, lava-deep slot at the
                // final tunnel column.
                float channelProfile = (float) Math.sqrt(Math.max(0f, 1f - horizontal * horizontal));
                float channelBlend = smootherstep(channelProfile);
                floor += (Math.min(floor, tunnelFloor) - floor) * channelBlend;
                float tunnelRoof = Math.min(tunnelCeiling + roughness * .25f, surface - 8f);
                ceiling += (Math.max(ceiling, tunnelRoof) - ceiling) * channelBlend;
            }
        } else {
            floor = tunnelFloor;
            ceiling = tunnelCeiling + roughness * .25f;
            localLavaLevel = LAVA_LEVEL;
        }

        int bottom = Math.max(5, (int) Math.ceil(floor));
        int top = Math.min(Math.min(250, (int) Math.floor(ceiling)), surface - 2);
        if (top < bottom) return;
        for (int y = bottom; y <= top; y++) {
            IBlockState state;
            if (main && isBoulder(x, z, y)) {
                state = Blocks.OBSIDIAN.getDefaultState();
            } else {
                state = y <= localLavaLevel ? Blocks.LAVA.getDefaultState() : Blocks.AIR.getDefaultState();
            }
            primer.setBlockState(localX, y, localZ, state);
        }

        if (primer.getBlockState(localX, top + 1, localZ).getBlock() instanceof BlockFalling) {
            primer.setBlockState(localX, top, localZ, Blocks.STONE.getDefaultState());
        }

        float ceilingGlow = perlin.noise2f((worldX - 823f) / 9f, (worldZ + 521f) / 9f)
                + perlin.noise2f((worldX + 191f) / 24f, (worldZ - 337f) / 24f) * .45f;
        boolean glowstonePatch = main && distance < MAIN_RADIUS - 8f && ceilingGlow > .68f;
        if (glowstonePatch && top + 1 < surface) {
            primer.setBlockState(localX, top + 1, localZ, Blocks.GLOWSTONE.getDefaultState());
        }

        // A spaced high-frequency sample creates plentiful isolated sources rather than broad lava patches.
        float drip = perlin.noise2f((worldX + 411f) / 4f, (worldZ - 733f) / 4f);
        boolean isolatedLavaSource = (worldX & 1) == 0 && (worldZ & 1) == 0
                && Math.floorMod(coordinateHash(worldX, worldZ), 10) == 0
                && drip > .18f;
        if (!glowstonePatch && isolatedLavaSource && top + 2 < surface) {
            primer.setBlockState(localX, top + 1, localZ, Blocks.LAVA.getDefaultState());
        }

        if (distance < 28f && surface >= 63 && surface > top) {
            int height = Math.max(1, surface - top);
            for (int y = top + 1; y <= surface; y++) {
                float vertical = (y - top) / (float) height;
                float funnelRadius;
                if (vertical < .58f) {
                    funnelRadius = 28f + (8f - 28f) * smootherstep(vertical / .58f);
                } else {
                    funnelRadius = 8f + (OPENING_RADIUS - 8f) * smootherstep((vertical - .58f) / .42f);
                }
                if (distance < funnelRadius) {
                    primer.setBlockState(localX, y, localZ, Blocks.AIR.getDefaultState());
                }
            }
        }
    }

    private static int coordinateHash(int x, int z) {
        int hash = x * 0x1f1f1f1f ^ z * 0x45d9f3b;
        hash ^= hash >>> 16;
        hash *= 0x45d9f3b;
        return hash ^ hash >>> 16;
    }

    private static int findSurface(ChunkPrimer primer, int localX, int localZ) {
        int surface = 255;
        while (surface > 1
                && primer.getBlockState(localX, surface, localZ).getBlock() == Blocks.AIR) surface--;
        return surface;
    }

    private static boolean isBoulder(float x, float z, int y) {
        return ellipsoid(x + 19f, z - 7f, y - 26f, 7f, 6f) || ellipsoid(x - 14f, z + 18f, y - 25f, 6f, 5f)
                || ellipsoid(x - 27f, z - 11f, y - 24f, 5f, 6f)
                || ellipsoid(x + 5f, z + 25f, y - 25f, 4.5f, 5f);
    }

    private static boolean ellipsoid(float x, float z, float y, float radius, float height) {
        return (x * x + z * z) / (radius * radius) + y * y / (height * height) <= 1f;
    }

    private static float warpedX(SimplexNoise perlin, float x, float z) {
        float anchor = smoothstep((float) Math.sqrt(x * x + z * z) / 48f);
        return x + (perlin.noise2f((x + 1300f) / 175f, (z - 700f) / 175f) * 34f
                + perlin.noise2f((x - 170f) / 58f, (z + 290f) / 58f) * 14f) * anchor;
    }

    private static float warpedZ(SimplexNoise perlin, float x, float z) {
        float anchor = smoothstep((float) Math.sqrt(x * x + z * z) / 48f);
        return z + (perlin.noise2f((x - 2100f) / 175f, (z + 1900f) / 175f) * 34f
                + perlin.noise2f((x + 370f) / 58f, (z - 510f) / 58f) * 14f) * anchor;
    }

    private static float smoothstep(float value) {
        value = Math.max(0f, Math.min(1f, value));
        return value * value * (3f - 2f * value);
    }

    private static float smootherstep(float value) {
        value = Math.max(0f, Math.min(1f, value));
        return value * value * value * (value * (value * 6f - 15f) + 10f);
    }
}
