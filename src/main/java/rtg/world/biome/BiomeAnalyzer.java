package rtg.world.biome;

import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import rtg.api.RTGAPI;
import rtg.api.util.CircularSearchCreator;
import rtg.api.util.Logger;
import rtg.api.util.storage.SparseList;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.IRealisticBiome;
import rtg.world.gen.ChunkLandscape;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BiomeAnalyzer {
    // Default anvil storage uses a single byte for biome data but with JustEnoughIDs, the biome ID field is expanded
    // to an integer.
    private static final int NO_BIOME = -1;
    // biome flag constants
    private static final int RIVER_FLAG = 1;
    private static final int OCEAN_FLAG = 2;
    private static final int SWAMP_FLAG = 4;
    private static final int BEACH_FLAG = 8;
    private static final int LAND_FLAG = 16;

    // biomeID -> bitField for biomes [ RIVER_BIOME | OCEAN_BIOME | SWAMP_BIOME | BEACH_BIOME | LAND_BIOME ]
    private final List<Integer> biomeIDs = new SparseList<>();
    private final List<Integer> preferredBeach = new SparseList<>();

    // 缓存 filterForFlag 的结果，因为生物群系定义在运行时不会改变
    private final ConcurrentMap<Integer, List<Boolean>> flagCache = new ConcurrentHashMap<>();

    // hardcoded these because they are world-persistent
    private final IRealisticBiome scenicLakeBiome = RTGAPI.getRTGBiome(Biomes.RIVER);
    private final IRealisticBiome scenicFrozenLakeBiome = RTGAPI.getRTGBiome(Biomes.FROZEN_RIVER);

    private SmoothingSearchStatus beachSearch;
    private SmoothingSearchStatus landSearch;
    private SmoothingSearchStatus oceanSearch;

    public BiomeAnalyzer() {
        initBiomes();
        setupBeachesForBiomes();
        setSearches();
    }

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

    private void initBiomes() {
        Logger.rtgDebug("Initialising biomes.");

        for (Biome biome : ForgeRegistries.BIOMES.getValuesCollection()) {
            int id = Biome.getIdForBiome(biome);
            Integer biomeFlags = biomeIDs.get(id);
            biomeFlags = (biomeFlags == null ? 0 : biomeFlags);

            if (BiomeDictionary.hasType(biome, Type.RIVER)) {
                biomeFlags |= RIVER_FLAG;
                Logger.rtgDebug("Assigning " + biome.getRegistryName() + " to river flag.");
            } else if (BiomeDictionary.hasType(biome, Type.OCEAN)) {
                biomeFlags |= OCEAN_FLAG;
                Logger.rtgDebug("Assigning " + biome.getRegistryName() + " to ocean flag.");
            } else if (BiomeDictionary.hasType(biome, Type.SWAMP)) {
                biomeFlags |= SWAMP_FLAG;
                Logger.rtgDebug("Assigning " + biome.getRegistryName() + " to swamp flag.");
            } else if (BiomeDictionary.hasType(biome, Type.BEACH)) {
                biomeFlags |= BEACH_FLAG;
                Logger.rtgDebug("Assigning " + biome.getRegistryName() + " to beach flag.");
            } else {
                biomeFlags |= LAND_FLAG;
                Logger.rtgDebug("Assigning " + biome.getRegistryName() + " to land flag.");
            }

            biomeIDs.set(id, biomeFlags);
        }
    }

    private void setupBeachesForBiomes() {
        for (Biome biome : ForgeRegistries.BIOMES.getValuesCollection()) {
            if (biome != null) {
                final int id = Biome.getIdForBiome(biome);
                final Map.Entry<Biome, IRealisticBiome> realisticBiome = RTGAPI.RTG_BIOMES.get(id);
                if (realisticBiome != null) {
                    preferredBeach.set(id, realisticBiome.getValue().getBeachBiome().baseBiomeId());
                }
            }
        }
    }

    public void newRepair(final Biome[] genLayerBiomes, final int[] biomeNeighborhood, final ChunkLandscape landscape) {
        final IRealisticBiome[] jitteredBiomes = landscape.biome;
        final float[] noise = landscape.noise;
        final float[] riverStrength = landscape.river;

        // 预计算边界条件，避免在循环中重复计算
        final boolean[] isInternalPoint = new boolean[256];
        for (int i = 0; i < 256; i++) {
            int x = i >> 4;  // i / 16
            int z = i & 15;  // i % 16
            isInternalPoint[i] = (x >= 1 && x <= 14 && z >= 1 && z <= 14);
        }

        IRealisticBiome realisticBiome;
        int realisticBiomeId;

        // 处理河流
        for (int i = 0; i < genLayerBiomes.length; i++) {
            realisticBiome = RTGAPI.getRTGBiome(genLayerBiomes[i]);
            realisticBiomeId = realisticBiome.baseBiomeId();

            boolean canBeRiver = riverStrength[i] > 0.7;

            if (noise[i] > 61.5) {
                // 高海拔区域保持原生物群系
                jitteredBiomes[i] = realisticBiome;
            } else {
                // 低海拔区域检查是否应转换为河流
                final int biomeFlags = biomeIDs.get(realisticBiomeId);
                if (canBeRiver && (biomeFlags & OCEAN_FLAG) == 0 && (biomeFlags & SWAMP_FLAG) == 0) {
                    jitteredBiomes[i] = realisticBiome.getRiverBiome();
                } else {
                    jitteredBiomes[i] = realisticBiome;
                }
            }
        }

        // 处理海滩
        beachSearch.setNotHunted();
        beachSearch.setAbsent();
        float beachTop = 64.5f;
        for (int i = 0; i < genLayerBiomes.length; i++) {
            if (beachSearch.isAbsent()) {
                break; // 无需继续
            }

            float beachBottom = 61.5f;
            float adjustedBeachTop = riverAdjusted(beachTop, riverStrength[i]);

            // 单次计算并存储结果，避免重复计算
            boolean isBeachLevel = (noise[i] >= beachBottom && noise[i] <= adjustedBeachTop);
            int biomeID = Biome.getIdForBiome(jitteredBiomes[i].baseBiome());
            boolean isSwamp = ((biomeIDs.get(biomeID) & SWAMP_FLAG) != 0);

            if (!isBeachLevel || isSwamp) {
                continue; // 此区块不是海滩高度或为沼泽
            }

            if (beachSearch.isNotHunted()) {
                beachSearch.hunt(biomeNeighborhood);
                landSearch.hunt(biomeNeighborhood);
            }

            int foundBiome = beachSearch.biomeIDs.get(i);
            if (foundBiome != NO_BIOME) {
                int nearestLandBiome = landSearch.biomeIDs.get(i);
                if (nearestLandBiome > -1) {
                    foundBiome = preferredBeach.get(nearestLandBiome);
                }
                jitteredBiomes[i] = RTGAPI.getRTGBiome(foundBiome);
            }
        }

        // 处理陆地
        landSearch.setAbsent();
        landSearch.setNotHunted();
        for (int i = 0; i < genLayerBiomes.length; i++) {
            if (landSearch.isAbsent() && beachSearch.isAbsent()) {
                break; // 无需继续
            }

            float adjustedBeachTop = riverAdjusted(64.5f, riverStrength[i]);
            if (noise[i] < adjustedBeachTop) {
                continue; // 低于海滩高度
            }

            int biomeID = Biome.getIdForBiome(jitteredBiomes[i].baseBiome());
            final int biomeFlags = biomeIDs.get(biomeID);

            // 已经是陆地或沼泽（可接受高于水位）
            if (((biomeFlags & LAND_FLAG) != 0) || ((biomeFlags & SWAMP_FLAG) != 0)) {
                continue;
            }

            if (landSearch.isNotHunted()) {
                landSearch.hunt(biomeNeighborhood);
            }

            int foundBiome = landSearch.biomeIDs.get(i);
            if (foundBiome == NO_BIOME && !beachSearch.isAbsent()) {
                if (beachSearch.isNotHunted()) {
                    beachSearch.hunt(biomeNeighborhood);
                }
                foundBiome = beachSearch.biomeIDs.get(i);
            }

            if (foundBiome != NO_BIOME) {
                jitteredBiomes[i] = RTGAPI.getRTGBiome(foundBiome);
            }
        }

        // 处理海洋
        oceanSearch.setAbsent();
        oceanSearch.setNotHunted();
        for (int i = 0; i < genLayerBiomes.length; i++) {
            if (oceanSearch.isAbsent()) {
                break; // 无需继续
            }

            if (noise[i] > 61.5f) {
                continue; // 高度过高
            }

            int biomeID = Biome.getIdForBiome(jitteredBiomes[i].baseBiome());
            final int biomeFlags = biomeIDs.get(biomeID);

            // 已经是海洋、沼泽或河流
            if (((biomeFlags & OCEAN_FLAG) != 0) ||
                    ((biomeFlags & SWAMP_FLAG) != 0) ||
                    ((biomeFlags & RIVER_FLAG) != 0)) {
                continue;
            }

            if (oceanSearch.isNotHunted()) {
                oceanSearch.hunt(biomeNeighborhood);
            }

            int foundBiome = oceanSearch.biomeIDs.get(i);
            if (foundBiome != NO_BIOME) {
                jitteredBiomes[i] = RTGAPI.getRTGBiome(foundBiome);
            }
        }

        // 转换剩余低于海平面的区域为湖泊生物群系
        for (int i = 0; i < genLayerBiomes.length; i++) {
            int biomeID = Biome.getIdForBiome(jitteredBiomes[i].baseBiome());
            final int biomeFlags = biomeIDs.get(biomeID);

            if (noise[i] <= 61.5 && (biomeFlags & RIVER_FLAG) == 0) {
                // 检查是否为海洋、沼泽或海滩
                if ((biomeFlags & OCEAN_FLAG) == 0 &&
                        (biomeFlags & SWAMP_FLAG) == 0 &&
                        (biomeFlags & BEACH_FLAG) == 0) {

                    int riverReplacementID = jitteredBiomes[i].getRiverBiome().baseBiomeId();
                    jitteredBiomes[i] = (riverReplacementID == Biome.getIdForBiome(Biomes.FROZEN_RIVER)) ?
                            scenicFrozenLakeBiome : scenicLakeBiome;
                }
            }
        }
    }

    private List<Boolean> filterForFlag(final int flag) {
        // 从缓存中获取，避免重复计算
        return flagCache.computeIfAbsent(flag, f -> {
            List<Boolean> result = new SparseList<>();
            for (Integer biomeId : biomeIDs) {
                if (biomeId != null) {
                    result.set(biomeId, (biomeId & flag) != 0);
                }
            }
            return result;
        });
    }

    private void setSearches() {
        beachSearch = new SmoothingSearchStatus(filterForFlag(BEACH_FLAG));
        landSearch = new SmoothingSearchStatus(filterForFlag(LAND_FLAG));
        oceanSearch = new SmoothingSearchStatus(filterForFlag(OCEAN_FLAG));
    }

    private float riverAdjusted(float top, float river) {
        if (river >= 1.0f) {
            return top;
        }

        // 简化计算，避免重复操作
        float adjustedRiver = Math.min(river, RTGWorld.ACTUAL_RIVER_PROPORTION);
        return top * (1.0f - adjustedRiver) + 62.0f * adjustedRiver;
    }

    private static final class SmoothingSearchStatus {
        private final int upperLeftFinding = 0;
        private final int upperRightFinding = 3;
        private final int lowerLeftFinding = 1;
        private final int lowerRightFinding = 4;

        private final int[] quadrantBiome = new int[4];
        private final float[] quadrantBiomeWeighting = new float[4];
        private final List<Boolean> desired;
        private final int[] findings = new int[3 * 3];
        private final float[] weightings = new float[3 * 3];

        public List<Integer> biomeIDs = new SparseList<>();
        private boolean absent = false;
        private boolean notHunted;
        private int arraySize;
        private int[] pattern;
        private int biomeCount;

        private SmoothingSearchStatus(final List<Boolean> desired) {
            this.desired = desired;
        }

        private int size() {
            return 3;
        }

        private void hunt(int[] biomeNeighborhood) {
            clear();
            int oldArraySize = arraySize;
            arraySize = (int) Math.sqrt(biomeNeighborhood.length);
            if (arraySize * arraySize != biomeNeighborhood.length) {
                throw new RuntimeException("non-square array");
            }

            if (arraySize != oldArraySize) {
                pattern = new CircularSearchCreator().pattern(arraySize / 2f - 1, arraySize);
            }

            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    search(xOffset, zOffset, biomeNeighborhood);
                }
            }
            smoothBiomes();
        }

        private void search(int xOffset, int zOffset, int[] biomeNeighborhood) {
            int offset = xOffset * arraySize + zOffset;
            int location = (xOffset + 1) * size() + zOffset + 1;

            findings[location] = NO_BIOME;
            weightings[location] = 2.0f;

            for (int i = 0; i < pattern.length; i++) {
                int biome = biomeNeighborhood[pattern[i] + offset];
                if (biome >= 0 && biome < desired.size() && Boolean.TRUE.equals(desired.get(biome))) {
                    findings[location] = biome;
                    weightings[location] = (float) Math.sqrt(pattern.length) - (float) Math.sqrt(i) + 2.0f;
                    break;
                }
            }
        }

        private void smoothBiomes() {
            smoothQuadrant(biomeIndex(0, 0), upperLeftFinding);
            smoothQuadrant(biomeIndex(8, 0), upperRightFinding);
            smoothQuadrant(biomeIndex(0, 8), lowerLeftFinding);
            smoothQuadrant(biomeIndex(8, 8), lowerRightFinding);
        }

        private void smoothQuadrant(int biomesOffset, int findingsOffset) {
            int upperLeft = findings[upperLeftFinding + findingsOffset];
            int upperRight = findings[upperRightFinding + findingsOffset];
            int lowerLeft = findings[lowerLeftFinding + findingsOffset];
            int lowerRight = findings[lowerRightFinding + findingsOffset];

            // 检查是否统一
            if (upperLeft == upperRight && upperLeft == lowerLeft && upperLeft == lowerRight) {
                for (int x = 0; x < 8; x++) {
                    for (int z = 0; z < 8; z++) {
                        biomeIDs.set(biomeIndex(x, z) + biomesOffset, upperLeft);
                    }
                }
                return;
            }

            // 预计算常用表达式
            float weightUL = weightings[upperLeftFinding + findingsOffset];
            float weightUR = weightings[upperRightFinding + findingsOffset];
            float weightLL = weightings[lowerLeftFinding + findingsOffset];
            float weightLR = weightings[lowerRightFinding + findingsOffset];

            biomeCount = 0;
            addBiome(upperLeft);
            addBiome(upperRight);
            addBiome(lowerLeft);
            addBiome(lowerRight);

            for (int x = 0; x < 8; x++) {
                float term1 = 7.0f - x;
                for (int z = 0; z < 8; z++) {
                    float term2 = 7.0f - z;

                    // 重置权重
                    for (int i = 0; i < 4; i++) {
                        quadrantBiomeWeighting[i] = 0.0f;
                    }

                    // 预计算权重
                    addWeight(upperLeft, weightUL * term1 * term2);
                    addWeight(upperRight, weightUR * x * term2);
                    addWeight(lowerLeft, weightLL * term1 * z);
                    addWeight(lowerRight, weightLR * x * z);

                    biomeIDs.set(biomeIndex(x, z) + biomesOffset, preferredBiome());
                }
            }
        }

        private void addBiome(int biome) {
            if (biome == NO_BIOME) return;

            for (int i = 0; i < biomeCount; i++) {
                if (biome == quadrantBiome[i]) {
                    return;
                }
            }
            // 未找到，添加
            if (biomeCount < 4) {
                quadrantBiome[biomeCount++] = biome;
            }
        }

        private void addWeight(int biome, float weight) {
            if (biome == NO_BIOME || weight <= 0.0f) return;

            for (int i = 0; i < biomeCount; i++) {
                if (biome == quadrantBiome[i]) {
                    quadrantBiomeWeighting[i] += weight;
                    return;
                }
            }
        }

        private int preferredBiome() {
            float bestWeight = -1.0f;
            int result = NO_BIOME;

            for (int i = 0; i < biomeCount; i++) {
                if (quadrantBiomeWeighting[i] > bestWeight) {
                    bestWeight = quadrantBiomeWeighting[i];
                    result = quadrantBiome[i];
                }
            }
            return result;
        }

        private int biomeIndex(int x, int z) {
            return x * 16 + z;
        }

        private void clear() {
            Arrays.fill(findings, NO_BIOME);
        }

        private boolean isAbsent() {
            return absent;
        }

        private void setAbsent() {
            this.absent = true;
        }

        private boolean isNotHunted() {
            return notHunted;
        }

        private void setNotHunted() {
            this.notHunted = true;
        }
    }
}