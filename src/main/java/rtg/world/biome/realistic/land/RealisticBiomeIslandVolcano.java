package rtg.world.biome.realistic.land;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.api.util.noise.ContinentalNoise;
import rtg.api.util.noise.SimplexNoise;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceVolcanoAsh;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgBiomeLayout;


/**
 * 火山岛 —— RWG {@code rwg/biomes/realistic/ocean/RealisticBiomeIslandVolcano.java} 的移植。
 *
 * <h2>它是什么</h2>
 *
 * 一座紧凑的火山锥：中心是**宽阔的熔岩口**（{@code CRATER_RADIUS = 26}），外圈是一道岩缘
 * （{@code RIM_RADIUS = 44}，比基准面高 {@code RIM_HEIGHT_OFFSET = 44}），再往外以
 * {@code OUTER_SLOPE_WIDTH = 86} 的宽度收坡回到原地形；{@code LAVA_FILL_RADIUS = 37.5} 以内的
 * 低处会被灌上岩浆（口内是熔岩湖），山体内部还建一个**岩浆房**
 * （{@code generateMagmaChamber}，在黑曜石外壳里，最后建以封住洞穴/结构挖出的口子）。
 *
 * <h2>地形不走 TerrainBase（与其它群系不同，这是 RWG 的写法）</h2>
 *
 * RWG 的这个群系**直接覆写 {@code rNoise}**，用 {@link #rNoiseAt} 自己算锥体，而不是挑一个
 * {@code terrain*} 函数。rtgc 沿用同一写法（{@code RealisticBiomeMountainChain} 也是这种"直接覆写"型）。
 * {@link #initTerrain} 仍然要返回一个对象（基类构造器要求），但它**不会被调用**：全仓没有任何
 * 地方在群系之外取 {@code terrain()} 来算高度，本类也覆写了 {@code rNoise}。
 *
 * <h2>与 RWG 的三处适配（均为 API 差异，不是公式改动）</h2>
 *
 * <ol>
 *   <li><b>噪声</b>：RWG 的 {@code perlin.noise2(a, b)} → rtgc 的
 *       {@code rtgWorld.simplexInstance(0).noise2f(a, b)}。这是全仓既有约定（所有已移植地形函数都这么换），
 *       频率参数（{@code /18f}、{@code /9f}、{@code /24f}、{@code /3f}）逐字保留。</li>
 *   <li><b>方块容器</b>：RWG 的 {@code Block[] blocks} + {@code byte[] metadata} 逐列数组 →
 *       {@code ChunkPrimer}；RWG 的列内索引 {@code (y * 16 + x) * 256 + level}
 *       （x = 局部 x、y = 局部 z）→ {@code primer.get/setBlockState(x, level, z)}。
 *       1.12.2 没有独立的 {@code flowing_water/flowing_lava} 方块（同方块 + {@code LEVEL} 属性），
 *       故 {@link #isReplaceableFluidOrAir} 只判 {@code AIR/WATER/LAVA}。</li>
 *   <li><b>火山渣方块</b>：RWG 显式传入 BOP 的 {@code ash} / {@code ashStone}。
 *       1.12.2 的 BOP **只有 {@code ash_block} 一种**（已核对 jar 的 blockstates：无 ash stone），
 *       而 BOP 火山岛群系自身的 {@code topBlock}/{@code fillerBlock} 本来就是它 ⇒
 *       {@link #initSurface} 直接取 {@code baseBiome().topBlock/fillerBlock}，与 RWG 等效且
 *       **不必把方块从构造器参数里带进来**（基类构造器会先调 {@code initSurface()}，
 *       读构造器参数会读到默认值 —— 见 {@code RealisticBiomeRtgOcean.shallow} 那类隐患）。</li>
 * </ol>
 *
 * <p>本类是 RWG {@code Support.volcanoIsland} 的对应物：由 {@link rtg.init.BiomeInit} 在注册时
 * 赋给静态字段 {@link #volcanoIsland}；没注册（例如没装 BOP）时为 {@code null}，
 * {@code RtgBiomeLayout.canGenerateVolcanoAt} 据此判否（与 RWG 的
 * {@code Support.volcanoIsland instanceof RealisticBiomeIslandVolcano} 同构）。
 *
 * @since 1.0.33（火山内容按用户要求写回）
 */
public class RealisticBiomeIslandVolcano extends RealisticBiomeBase {

    // ===== 与 RWG 逐字一致的常量 =====
    private static final float CRATER_RADIUS = 26f;
    private static final float LAVA_FILL_RADIUS = 37.5f;
    private static final float FULL_UNDERLYING_INFLUENCE_RADIUS = 70f;
    private static final float RIM_RADIUS = 44f;
    private static final float OUTER_SLOPE_WIDTH = 86f;
    private static final float CRATER_FLOOR_OFFSET = 4f;
    private static final float LAVA_LEVEL_OFFSET = 18f;
    private static final float RIM_HEIGHT_OFFSET = 44f;
    private static final float TERRAIN_HEIGHT_RELIEF_FACTOR = .2f;
    private static final float MIN_RIM_HEIGHT_OFFSET = 40f;
    private static final float MAX_RIM_HEIGHT_OFFSET = 60f;
    private static final float SUMMIT_NOISE_ALLOWANCE = 2f;
    private static final float MAX_SAFE_SUMMIT_Y = 250f;
    private static final int VENT_RADIUS = 5;
    private static final int VENT_SHELL_RADIUS = 10;
    private static final int CHAMBER_CENTER_Y = 18;
    private static final int CHAMBER_RADIUS = 46;
    private static final int CHAMBER_HALF_HEIGHT = 10;
    private static final int CHAMBER_SHELL = 3;
    private static final int MIN_GENERATED_Y = 5;

    /** RWG {@code rNoise} 里写死的基准高度（{@code rNoiseAt(perlin, x, y, 61f)}）。 */
    private static final float DEFAULT_BASE_HEIGHT = 61f;

    /**
     * RWG 的 {@code Support.volcanoIsland}。
     * <p>
     * 由 {@code rtg.init.BiomeInit} 在注册火山群系时赋值；未注册时为 {@code null}
     * （RWG 亦然：{@code Support.volcanoIsland = null} 直到 {@code SupportBOP} 建它）。
     */
    public static RealisticBiomeIslandVolcano volcanoIsland;

    public RealisticBiomeIslandVolcano(final Biome biome) {

        super(biome);
    }

    @Override
    public void initConfig() {

        // RWG 的火山岛没有配置项；rtgc 侧保持默认（不额外关河流/湖泊，与 RWG 的默认行为一致）。
    }

    @Override
    public void initDecos() {

        // RWG 的 IslandVolcano 没有自己的装饰组（它不覆写 rDecorate）⇒ 保持空，与 RWG 一致。
    }

    /**
     * 占位：本群系的地形由 {@link #rNoiseAt} 直接给出，RWG 也没有对应的 {@code terrain*} 函数。
     * <p>
     * ⚠ 返回的对象**不会**被调用（{@code rNoise} 已被本类覆写，且全仓没有别处取 {@code terrain()} 算高度）；
     * 仅用于满足基类构造器。若哪天真的被调用，返回 RWG 该群系的基准高度 61，至少不会造出平地。
     */
    @Override
    public TerrainBase initTerrain() {

        return new TerrainVolcanoPlaceholder();
    }

    @Override
    public SurfaceBase initSurface() {

        // RWG: new SurfaceVolcanoAsh(ash, ashStone)（BOP 的火山渣/火山岩）
        // 1.12.2 的 BOP 只有 ash_block，且火山岛群系自身的 top/filler 就是它 ⇒ 直接取群系方块。
        return new SurfaceVolcanoAsh(this.getConfig(), baseBiome().topBlock, baseBiome().fillerBlock);
    }

    // ===== 地形：锥体 / 岩缘 / 熔岩口（RWG rNoiseAt 逐行） =====

    @Override
    public float rNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

        return rNoiseAt(rtgWorld, x, y, DEFAULT_BASE_HEIGHT);
    }

    public float rNoiseAt(RTGWorld rtgWorld, float localX, float localZ, float baseHeight) {

        return rNoiseAt(rtgWorld, localX, localZ, baseHeight, baseHeight);
    }

    public float rNoiseAt(RTGWorld rtgWorld, float localX, float localZ, float baseHeight, float underlyingHeight) {

        final SimplexNoise perlin = rtgWorld.simplexInstance(0);

        float distance = (float) Math.sqrt((double) localX * localX + (double) localZ * localZ);
        distance += perlin.noise2f(localX / 18f, localZ / 18f) * 3f;

        float footBlend = smoothstep((distance - RIM_RADIUS) / OUTER_SLOPE_WIDTH);
        float localBaseHeight = baseHeight + (underlyingHeight - baseHeight) * footBlend;
        float structureStrength = 1f - footBlend;
        float rimHeightOffset = getRimHeightOffset(baseHeight);

        float height;
        if (distance < CRATER_RADIUS) {
            height = localBaseHeight + CRATER_FLOOR_OFFSET + perlin.noise2f(localX / 9f, localZ / 9f) * 1.5f;
        } else if (distance < RIM_RADIUS) {
            float rim = (distance - CRATER_RADIUS) / (RIM_RADIUS - CRATER_RADIUS);
            height = localBaseHeight + CRATER_FLOOR_OFFSET + rim * (rimHeightOffset - CRATER_FLOOR_OFFSET);
        } else {
            float slope = Math.max(0f, 1f - (distance - RIM_RADIUS) / OUTER_SLOPE_WIDTH);
            height = localBaseHeight + slope * rimHeightOffset;
        }

        float volcanoHeight = height + perlin.noise2f(localX / 24f, localZ / 24f) * 2f * structureStrength;
        float underlyingInfluence = smoothstep(
                (distance - LAVA_FILL_RADIUS) / (FULL_UNDERLYING_INFLUENCE_RADIUS - LAVA_FILL_RADIUS));
        return volcanoHeight + Math.max(0f, underlyingHeight - volcanoHeight) * underlyingInfluence;
    }

    private static float getRimHeightOffset(float baseHeight) {

        return Math.max(
                MIN_RIM_HEIGHT_OFFSET,
                Math.min(MAX_RIM_HEIGHT_OFFSET, RIM_HEIGHT_OFFSET + (baseHeight - 63f) * TERRAIN_HEIGHT_RELIEF_FACTOR));
    }

    /** 山顶会顶到世界高度上限时**整个火山不生成**（RWG：宁可没有，也不压平山顶）。 */
    public boolean canGenerateAtHeight(float baseHeight) {

        return baseHeight + getRimHeightOffset(baseHeight) + SUMMIT_NOISE_ALLOWANCE <= MAX_SAFE_SUMMIT_Y;
    }

    public boolean isInsideLavaFill(RTGWorld rtgWorld, float localX, float localZ) {

        float distance = (float) Math.sqrt((double) localX * localX + (double) localZ * localZ);
        distance += rtgWorld.simplexInstance(0).noise2f(localX / 18f, localZ / 18f) * 3f;
        return distance < LAVA_FILL_RADIUS;
    }

    private static float smoothstep(float value) {

        value = Math.max(0f, Math.min(1f, value));
        return value * value * (3f - 2f * value);
    }

    // ===== 地表：火山渣 + 熔岩口灌岩浆（RWG rReplaceAt 逐行） =====

    @Override
    public void rReplace(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld,
                         float[] noise, float river, Biome[] base) {

        rReplaceAt(primer, i, j, x, y, depth, rtgWorld, noise, river, base, i, j);
    }

    public void rReplaceAt(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld,
                           float[] noise, float river, Biome[] base, float localX, float localZ) {

        rReplaceAt(primer, i, j, x, y, depth, rtgWorld, noise, river, base, localX, localZ, DEFAULT_BASE_HEIGHT);
    }

    public void rReplaceAt(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld,
                           float[] noise, float river, Biome[] base, float localX, float localZ, float baseHeight) {

        rReplaceAt(primer, i, j, x, y, depth, rtgWorld, noise, river, base, localX, localZ, baseHeight, baseHeight);
    }

    public void rReplaceAt(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld,
                           float[] noise, float river, Biome[] base, float localX, float localZ, float baseHeight,
                           float underlyingHeight) {

        rReplaceAt(primer, i, j, x, y, depth, rtgWorld, noise, river, base, localX, localZ, baseHeight,
                underlyingHeight, 6);
    }

    public void rReplaceAt(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld,
                           float[] noise, float river, Biome[] base, float localX, float localZ, float baseHeight,
                           float underlyingHeight, int surfaceDepth) {

        ((SurfaceVolcanoAsh) this.surface()).paintTerrain(
                primer, i, j, x, y, depth, rtgWorld, noise, river, base, surfaceDepth);

        final SimplexNoise perlin = rtgWorld.simplexInstance(0);
        float distance = (float) Math.sqrt((double) localX * localX + (double) localZ * localZ);
        distance += perlin.noise2f(localX / 18f, localZ / 18f) * 3f;
        if (distance >= LAVA_FILL_RADIUS) {
            return;
        }

        // RWG: 自上而下跳过流体/空气，找到顶面那一格；在它上面铺黑曜石，再灌岩浆到 baseHeight + 18。
        int surfaceLevel = 255;
        while (surfaceLevel > 1 && isReplaceableFluidOrAir(primer, x, surfaceLevel, y)) {
            surfaceLevel--;
        }
        int lavaLevel = Math.min(254, Math.round(baseHeight + LAVA_LEVEL_OFFSET));
        if (surfaceLevel >= lavaLevel) {
            return;
        }

        primer.setBlockState(x, surfaceLevel, y, Blocks.OBSIDIAN.getDefaultState());
        for (int level = surfaceLevel + 1; level <= lavaLevel; level++) {
            primer.setBlockState(x, level, y, Blocks.LAVA.getDefaultState());
        }
    }

    private static boolean isReplaceableFluidOrAir(ChunkPrimer primer, int x, int level, int z) {

        // RWG 判 air/water/flowing_water/lava/flowing_lava；1.12.2 的流动液体是同方块 + LEVEL 属性，故只判方块。
        return primer.getBlockState(x, level, z).getBlock() == Blocks.AIR
                || primer.getBlockState(x, level, z).getBlock() == Blocks.WATER
                || primer.getBlockState(x, level, z).getBlock() == Blocks.LAVA;
    }

    // ===== 岩浆房（在洞穴/结构之后建，黑曜石外壳封住它们挖出的口子） =====

    /**
     * 建山体内部的岩浆房 + 火山通道（RWG {@code generateMagmaChamber} 逐行）。
     *
     * <p>⚠ 调用时机是**洞穴与结构生成之后**（RWG 的注释明确说明：外壳要能封住它们挖开的口子），
     * 调用点在 {@code ChunkGeneratorRTG}。
     *
     * @param layout 用于查"本列属于哪座火山、火山基准高度"（RWG 的 {@code ChunkManagerRealistic}）
     */
    public void generateMagmaChamber(final ChunkPrimer primer, final int chunkX, final int chunkZ,
                                     final RtgBiomeLayout layout) {

        int outerRadius = CHAMBER_RADIUS + CHAMBER_SHELL;
        int outerHalfHeight = CHAMBER_HALF_HEIGHT + CHAMBER_SHELL;

        for (int localX = 0; localX < 16; localX++) {
            int worldX = chunkX * 16 + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = chunkZ * 16 + localZ;
                long coordinates = layout.getVolcanoCoordinates(worldX, worldZ);
                if (coordinates == Long.MIN_VALUE) {
                    continue;
                }
                float volcanoX = ContinentalNoise.unpackVolcanoX(coordinates);
                float volcanoZ = ContinentalNoise.unpackVolcanoY(coordinates);
                float horizontalDistanceSquared = volcanoX * volcanoX + volcanoZ * volcanoZ;
                int craterFloor = Math.min(254,
                        Math.round(layout.getVolcanoBaseHeight(worldX, worldZ) + CRATER_FLOOR_OFFSET));

                for (int level = Math.max(MIN_GENERATED_Y, CHAMBER_CENTER_Y - outerHalfHeight);
                     level <= craterFloor; level++) {
                    int verticalDistance = level - CHAMBER_CENTER_Y;
                    double outerDistance = horizontalDistanceSquared / (double) (outerRadius * outerRadius)
                            + verticalDistance * verticalDistance / (double) (outerHalfHeight * outerHalfHeight);
                    double innerDistance = horizontalDistanceSquared / (double) (CHAMBER_RADIUS * CHAMBER_RADIUS)
                            + verticalDistance * verticalDistance
                            / (double) (CHAMBER_HALF_HEIGHT * CHAMBER_HALF_HEIGHT);

                    boolean insideChamber = innerDistance <= 1D;
                    boolean insideChamberShell = outerDistance <= 1D;
                    boolean aboveChamberCentre = level >= CHAMBER_CENTER_Y;
                    boolean insideVent = aboveChamberCentre && horizontalDistanceSquared <= VENT_RADIUS * VENT_RADIUS;
                    boolean insideVentShell = aboveChamberCentre
                            && horizontalDistanceSquared <= VENT_SHELL_RADIUS * VENT_SHELL_RADIUS;

                    if (!insideChamber && !insideChamberShell && !insideVent && !insideVentShell) {
                        continue;
                    }

                    primer.setBlockState(localX, level, localZ, (insideChamber || insideVent)
                            ? Blocks.LAVA.getDefaultState()
                            : Blocks.OBSIDIAN.getDefaultState());
                }
            }
        }
    }

    /**
     * {@link #initTerrain} 的占位实现；见该方法的说明，正常路径下不会被调用。
     *
     * <p>它刻意**仍然走一个 RWG 地形函数**（{@code terrainIslandTropical}，热带岛 —— 本群系被替换掉的
     * 那个包装类原先就是这个地形）：这样 {@code tools/terrain-wiring-check.ps1}
     *（"每个群系都要路由进 RWG 地形函数"）不必为本群系开白名单，工具保持严格。
     * 本群系真正的地形来自 {@link #rNoiseAt}。
     */
    public static class TerrainVolcanoPlaceholder extends TerrainBase {

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            return terrainIslandTropical(x, y, rtgWorld, border);
        }
    }
}
