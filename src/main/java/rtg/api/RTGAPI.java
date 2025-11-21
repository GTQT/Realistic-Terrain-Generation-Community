package rtg.api;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.DimensionType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.layer.GenLayer;
import net.minecraft.world.gen.layer.GenLayerRiverMix;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.Level;
import rtg.api.util.Logger;
import rtg.api.util.UtilityClass;
import rtg.api.util.storage.SparseList;
import rtg.api.world.biome.IRealisticBiome;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.*;

@UtilityClass
public final class RTGAPI {
    public static final SparseList<Map.Entry<Biome, IRealisticBiome>> RTG_BIOMES = new SparseList<>();
    private static final Set<DimensionType> ALLOWED_DIMENSION_TYPES = new ObjectArraySet<>();
    private static boolean rtgBiomesLocked = false;
    private static Path configPath;
    private static Path biomeConfigPath;
    private static IRealisticBiome patchBiome;
    private static IBlockState
            shadowStoneBlock = null,
            shadowDesertBlock = null;

    // ====== 关键优化：生物群系缓存 ======
    /** 缓存0-255生物群系ID，1.12.2原版范围 */
    private static volatile IRealisticBiome[] BIOME_CACHE = null;
    /** 标记缓存是否已初始化 */
    private static volatile boolean cacheInitialized = false;

    private RTGAPI() {
    }

    /**
     * 在生物群系注册完成后调用（由RTG主类调用）
     * 初始化生物群系缓存，大幅提升性能
     */
    public static void lockRtgBiomes() {
        rtgBiomesLocked = true;
        initBiomeCache();
    }

    /** 安全初始化生物群系缓存 */
    private static void initBiomeCache() {
        if (cacheInitialized) return;

        // 1. 确定缓存大小 (1.12.2原版256，JEID扩展65536)
        final int maxSize = getMaxBiomeIDs();
        final int cacheSize = Math.min(maxSize, 256); // 仅缓存0-255高频ID

        // 2. 创建缓存数组
        IRealisticBiome[] newCache = new IRealisticBiome[cacheSize];

        // 3. 填充缓存 (直接操作RTG_BIOMES，避免递归)
        for (int id = 0; id < cacheSize; id++) {
            final Map.Entry<Biome, IRealisticBiome> entry = RTG_BIOMES.get(id);
            newCache[id] = (entry != null) ? entry.getValue() : patchBiome;
        }

        // 4. 原子性发布 (内存屏障保证)
        BIOME_CACHE = newCache;
        cacheInitialized = true;

        Logger.debug("RTG生物群系缓存初始化完成 (大小={}，JEID={})",
                cacheSize, (maxSize > 256));
    }

    public static Path getConfigPath() {
        return configPath;
    }

    public static void setConfigPath(Path path) {
        if (configPath == null) {
            configPath = path;
            biomeConfigPath = path.resolve("biomes");
        }
    }

    public static Path getBiomeConfigPath() {
        return biomeConfigPath;
    }

    public static void addAllowedDimensionType(DimensionType dimType) {
        ALLOWED_DIMENSION_TYPES.add(dimType);
    }

    public static void removeAllowedDimensionType(DimensionType dimType) {
        ALLOWED_DIMENSION_TYPES.remove(dimType);
    }

    public static boolean isAllowedDimensionType(DimensionType dimType) {
        return ALLOWED_DIMENSION_TYPES.contains(dimType) ||
                dimType.getSuffix().equals("_rtg") ||
                dimType.name().startsWith("jed_surface");
    }

    public static boolean isAllowedDimensionType(int dimId) {
        DimensionType type = (DimensionManager.isDimensionRegistered(dimId)) ?
                DimensionManager.getProviderType(dimId) : null;
        return type != null && isAllowedDimensionType(type);
    }

    // ====== 优化后的生物群系获取方法 ======
    public static IRealisticBiome getRTGBiome(@Nonnull Biome biome) {
        return getRTGBiome(Biome.getIdForBiome(biome));
    }

    public static IRealisticBiome getRTGBiome(int biomeId) {
        // 快速路径：使用缓存 (99.9%调用走此路径)
        if (cacheInitialized && biomeId >= 0 && biomeId < 256) {
            final IRealisticBiome cached = BIOME_CACHE[biomeId];
            if (cached != null) {
                return cached;
            }
        }

        // 慢速路径：回退到Map查找 (仅JEID扩展ID或未初始化时)
        final Map.Entry<Biome, IRealisticBiome> entry = RTG_BIOMES.get(biomeId);
        return (entry != null) ? entry.getValue() : patchBiome;
    }

    public static void addRTGBiomes(IRealisticBiome... biomes) {
        if (!rtgBiomesLocked) {
            for (final IRealisticBiome biome : biomes) {
                final Biome baseBiome = biome.baseBiome();
                RTG_BIOMES.set(Biome.getIdForBiome(baseBiome),
                        new AbstractMap.SimpleEntry<>(baseBiome, biome));
            }
        } else {
            Logger.warn("尝试在RTG生物群系锁定后添加生物群系！忽略: {}", Arrays.toString(biomes));
        }
    }

    public static void initPatchBiome(Biome biome) {
        // 确保在设置patchBiome前初始化缓存
        if (!cacheInitialized) {
            initBiomeCache();
        }

        IRealisticBiome rtgBiome = getRTGBiome(biome);
        if (rtgBiome == null) {
            Logger.error("配置中设置的修复生物群系错误: {} (无RTG版本), 使用默认值.",
                    biome.getRegistryName());
            rtgBiome = Objects.requireNonNull(getRTGBiome(Biomes.PLAINS),
                    "找不到minecraft:plains的RTG版本。这应该不可能发生。");
        }
        Logger.debug("设置修复生物群系为: {}", rtgBiome.baseBiomeResLoc());
        patchBiome = rtgBiome;

        // 更新缓存中的patchBiome引用
        if (cacheInitialized && BIOME_CACHE != null) {
            for (int i = 0; i < BIOME_CACHE.length; i++) {
                if (BIOME_CACHE[i] == null) {
                    BIOME_CACHE[i] = patchBiome;
                }
            }
        }
    }

    public static void setShadowBlocks(@Nullable IBlockState stone, @Nullable IBlockState desert) {
        if (shadowStoneBlock == null) {
            shadowStoneBlock = stone != null ? stone : Blocks.STONE.getDefaultState();
        }
        if (shadowDesertBlock == null) {
            shadowDesertBlock = desert != null ? desert : Blocks.SAND.getDefaultState();
        }
    }

    public static IBlockState getShadowStoneBlock() {
        return shadowStoneBlock;
    }

    public static IBlockState getShadowDesertBlock() {
        return shadowDesertBlock;
    }

    public static int getMaxBiomeIDs() {
        return Loader.isModLoaded("jeid") ? 65536 : 256;
    }

    public static void dumpGenLayerStack(@Nonnull final GenLayer layersIn, final Level level) {
        final Collection<String> initialStack = Lists.newArrayList();
        final Collection<String> riverStack = Lists.newArrayList();
        final Collection<String> biomeStack = Lists.newArrayList();

        int count = 0;
        int biomecount;
        int rivercount;
        GenLayer layer = layersIn;
        initialStack.add(String.format("%s. %s", ++count, layer.getClass().getName()));
        while (layer.parent != null) {
            initialStack.add(String.format("%s. %s", ++count, layer.parent.getClass().getName()));
            layer = layer.parent;
        }
        if (layer instanceof GenLayerRiverMix) {
            biomecount = rivercount = count;
            GenLayer biomeLayer = ((GenLayerRiverMix) layer).biomePatternGeneratorChain;
            while (biomeLayer.parent != null) {
                biomeStack.add(String.format("%s. %s", ++biomecount, biomeLayer.parent.getClass().getName()));
                biomeLayer = biomeLayer.parent;
            }
            GenLayer riverLayer = ((GenLayerRiverMix) layer).riverPatternGeneratorChain;
            while (riverLayer.parent != null) {
                riverStack.add(String.format("%s. %s", ++rivercount, riverLayer.parent.getClass().getName()));
                riverLayer = riverLayer.parent;
            }
        }

        if (biomeStack.isEmpty() || riverStack.isEmpty()) {
            Logger.log(level, "\nGenLayer堆栈:\n{}", String.join("\n  ", initialStack));
        } else {
            Logger.log(level, "\n初始GenLayer堆栈:\n  {}\n生物群系GenLayer堆栈:\n  {}\n河流GenLayer堆栈:\n  {}",
                    String.join("\n  ", initialStack),
                    String.join("\n  ", biomeStack),
                    String.join("\n  ", riverStack));
        }
    }

    // ====== 诊断工具 (用于验证缓存效果) ======
    public static void logCacheStats() {
        if (!cacheInitialized) {
            Logger.debug("生物群系缓存未初始化");
            return;
        }

        int nullCount = 0;
        for (IRealisticBiome biome : BIOME_CACHE) {
            if (biome == null) nullCount++;
        }

        Logger.debug("生物群系缓存统计: 大小={}，空值数量={}，patchBiome={}",
                BIOME_CACHE.length, nullCount, patchBiome.baseBiomeResLoc());
    }
}