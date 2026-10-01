package rtg.world.biome;

import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import rtg.api.RTGAPI;
import rtg.api.util.Logger;
import rtg.api.util.storage.SparseList;
import rtg.api.world.RTGWorld;
import rtg.api.world.WaterLevel;
import rtg.api.world.biome.IRealisticBiome;
import rtg.world.gen.ChunkLandscape;

import java.util.*;

public final class BiomeAnalyzer {
    private static final int RIVER_FLAG = 1;
    private static final int OCEAN_FLAG = 2;
    private static final int SWAMP_FLAG = 4;
    private static final int BEACH_FLAG = 8;
    private static final int LAND_FLAG = 16;

    private final List<Integer> biomeIDs = new SparseList<>();


    public BiomeAnalyzer() {
        initBiomes();
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
            } else if (BiomeDictionary.hasType(biome, Type.OCEAN)) {
                biomeFlags |= OCEAN_FLAG;
            } else if (BiomeDictionary.hasType(biome, Type.SWAMP)) {
                biomeFlags |= SWAMP_FLAG;
            } else if (BiomeDictionary.hasType(biome, Type.BEACH)) {
                biomeFlags |= BEACH_FLAG;
            } else {
                biomeFlags |= LAND_FLAG;
            }
            biomeIDs.set(id, biomeFlags);
        }
    }

    public void newRepair(final Biome[] genLayerBiomes, final int[] biomeNeighborhood, final ChunkLandscape landscape) {
        final IRealisticBiome[] jitteredBiomes = landscape.biome;
        final float[] noise = landscape.noise;
        final float[] riverStrength = landscape.river;

        IRealisticBiome realisticBiome;
        int realisticBiomeId;

        // 处理河流
        // Find a fallback land biome for overriding vanilla River biomes on dry terrain
        IRealisticBiome fallbackLand = null;
        for (int j = 0; j < genLayerBiomes.length; j++) {
            int fbId = Biome.getIdForBiome(genLayerBiomes[j]);
            int fbFlags = biomeIDs.get(fbId);
            if ((fbFlags & RIVER_FLAG) == 0 && (fbFlags & OCEAN_FLAG) == 0) {
                fallbackLand = RTGAPI.getRTGBiome(genLayerBiomes[j]);
                break;
            }
        }
        if (fallbackLand == null) {
            fallbackLand = RTGAPI.getRTGBiome(Biomes.PLAINS);
        }

        for (int i = 0; i < genLayerBiomes.length; i++) {
            realisticBiome = RTGAPI.getRTGBiome(genLayerBiomes[i]);
            realisticBiomeId = realisticBiome.baseBiomeId();
            final int biomeFlags = biomeIDs.get(realisticBiomeId);
            final boolean isVanillaRiver = (biomeFlags & RIVER_FLAG) != 0;
            boolean canBeRiver = riverStrength[i] > RTGWorld.RIVER_BIOME_THRESHOLD;
            // 这一步是否**替换**掉了 provider 给的群系（河流/回落）。
            // 只有替换掉的列，地表才必须跟着走 —— 见下方 surfaceBiome 的处理。
            boolean substituted;
            if (noise[i] > WaterLevel.current().cliffBandLow()) {
                // Above water: if vanilla assigned River but RTG detects no river,
                // replace with land biome to prevent dry stone "river" paths
                if (isVanillaRiver && !canBeRiver) {
                    jitteredBiomes[i] = fallbackLand;
                    substituted = true;
                } else {
                    jitteredBiomes[i] = realisticBiome;
                    substituted = false;
                }
            } else {
                // Below water: assign River biome if RTG detects a strong river
                if (canBeRiver && (biomeFlags & OCEAN_FLAG) == 0 && (biomeFlags & SWAMP_FLAG) == 0) {
                    jitteredBiomes[i] = realisticBiome.getRiverBiome();
                    substituted = true;
                } else {
                    jitteredBiomes[i] = realisticBiome;
                    substituted = false;
                }
            }

            // ---- 地表数组（RWG `randBiome` 的产物）----
            //
            // `landscape.surfaceBiome[]` 由 `ChunkGeneratorRTG.getNewerNoise` 按混合权重 + 15 格噪声
            // 预先算好（抖动只在权重混合的过渡带里挑出**另一个**群系）。
            // 但**被替换过的列**（河面下换成河流群系、或旱河回落陆地）必须跟随主群系：
            // 河床的地表要按河流群系刷成沙/砾，不能拿过渡带抖出来的陆地群系去刷 —— 那是回归。
            // 未替换的列保留抖动结果，于是过渡带内 F3（{@code biome[]}）与地表（本数组）不同，
            // 这正是 RWG 的行为，用户已认可。
            if (substituted || landscape.surfaceBiome[i] == null) {
                landscape.surfaceBiome[i] = jitteredBiomes[i];
            }
        }

        // ⚠ RWG 的群系布局**完全接管**群系选择，故这里原本的两步「事后改写」都已删除：
        //
        //   1) 三阶段群系修复（海滩 / 陆地 / 海洋，F-34）—— 这三件事现在由 RtgBiomeLayout
        //      的海洋 / 岛屿 / 滨海 / 海岸分支负责；
        //   2) 风景湖改写（把低于 cliffBandLow 的列换成 RIVER / 风景湖群系）—— **RWG 没有这一步**，
        //      它是 RTG 时代的做法。RWG 的湖泊是**地形特征**：直接写在各群系自己的
        //      `terrainXxx` 高度公式里（例如 `terrainGrasslandFlats` 的湖底项 `l`），
        //      而不是替换群系。
        //
        // 上面已完成：biomeData（**现实主义群系编号**，见 RtgRealisticIndex）→
        // jitteredBiomes（= landscape.biome，别名，非副本）。至此群系不再被本类改动。
        //
        // D2（1.0.14）：随三阶段修复一起留下的**整套死重量**已删除 ——
        //   `SmoothingSearchStatus` 内部类（约 170 行）、`hunt` / `search` / `smoothBiomes` /
        //   `smoothQuadrant` / `addBiome` / `addWeight` / `preferredBiome` / `biomeIndex` /
        //   `clear` / `isAbsent` / `isNotHunted` / `setNotHunted`（都在那个类里）、
        //   `filterForFlag` / `setSearches` / `setupBeachesForBiomes` / `riverAdjusted`，
        //   以及只喂给它们的字段 `preferredBeach` / `flagCache` / `beachSearch` / `landSearch` /
        //   `oceanSearch` 与常量 `NO_BIOME`。本类现在只剩 `initBiomes` + `newRepair` +
        //   `xyinverted` 三个成员（355 → 139 行）。
        //   `biomeNeighborhood` 参数也确认无人使用（其唯一消费者 `hunt` 已删），
        //   保留形参只为不改调用点签名。
    }

}
