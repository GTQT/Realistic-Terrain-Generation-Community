package rtg.mixins.core;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeDecorator;
import net.minecraft.world.gen.ChunkGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rtg.RTGConfig;
import rtg.api.util.ChunkGenerationProfiler;
import rtg.api.util.ChunkGenerationProfiler.Category;
import rtg.api.util.FastDecoContext;
import rtg.api.util.FastOreContext;

import java.util.Random;

/**
 * 复用 {@link BiomeDecorator} 的生成器与 {@code generatorOptions} 解析结果。
 *
 * <p>1.12 每次 {@code decorate} 都会重新解析 {@code generatorOptions}，并新建 11 个
 * {@code WorldGenMinable}；这些只依赖世界生成设置，与当前区块无关。第一次仍走原版
 * 并缓存结果，之后 options 相同就只替换 {@code chunkPos}，再调用 {@code genDecorations}。
 *
 * <p>省掉的只是重复初始化；矿脉生成、随机数调用和事件顺序仍由原版
 * {@code genDecorations} 执行。options 字符串是失效键，不同世界或设置会自动回退。
 */
@Mixin(BiomeDecorator.class)
public abstract class BiomeDecoratorMixin {

    @Shadow
    public boolean decorating;

    @Shadow
    public BlockPos chunkPos;

    @Shadow
    public ChunkGeneratorSettings chunkProviderSettings;

    @Shadow
    protected abstract void genDecorations(Biome biomeIn, World worldIn, Random random);

    @Unique
    private String rtgc$cachedOptions;

    @Unique
    private boolean rtgc$cacheReady;

    @Unique
    private final ThreadLocal<Long> rtgc$oreStart = new ThreadLocal<>();

    @Inject(method = "decorate", at = @At("HEAD"))
    private void rtgc$clearFastOre(World worldIn, Random random, Biome biome,
                                   BlockPos pos, CallbackInfo ci) {
        // generateOres 正常在 RETURN 清理；异常路径不能把快速写标记留给下一次装饰。
        FastOreContext.exit();
        FastDecoContext.reset();
    }

    @Inject(method = "genDecorations", at = @At("HEAD"))
    private void rtgc$enterFastDeco(Biome biomeIn, World worldIn, Random random, CallbackInfo ci) {
        FastDecoContext.enter();
    }

    @Inject(method = "genDecorations", at = @At("RETURN"))
    private void rtgc$exitFastDeco(Biome biomeIn, World worldIn, Random random, CallbackInfo ci) {
        FastDecoContext.exit();
    }

    @Inject(method = "generateOres", at = @At("HEAD"))
    private void rtgc$beginOre(World worldIn, Random random, CallbackInfo ci) {
        FastOreContext.enter();
        this.rtgc$oreStart.set(ChunkGenerationProfiler.start(Category.POP_DECO_ORES));
    }

    @Inject(method = "generateOres", at = @At("RETURN"))
    private void rtgc$endOre(World worldIn, Random random, CallbackInfo ci) {
        Long start = this.rtgc$oreStart.get();
        if (start != null) {
            ChunkGenerationProfiler.end(Category.POP_DECO_ORES, start);
            this.rtgc$oreStart.remove();
        }
        FastOreContext.flushStats();
        FastOreContext.exit();
    }

    @Inject(method = "decorate", at = @At("HEAD"), cancellable = true)
    private void rtgc$reuseCachedGenerators(World worldIn, Random random, Biome biome,
                                            BlockPos pos, CallbackInfo ci) {

        if (this.decorating || !this.rtgc$cacheReady || this.chunkProviderSettings == null) {
            ChunkGenerationProfiler.count(ChunkGenerationProfiler.Counter.BIOME_DECORATOR_CACHE_MISSES);
            return;
        }
        String options = worldIn.getWorldInfo().getGeneratorOptions();
        if (options == null || !options.equals(this.rtgc$cachedOptions)) {
            ChunkGenerationProfiler.count(ChunkGenerationProfiler.Counter.BIOME_DECORATOR_CACHE_MISSES);
            return;
        }

        ChunkGenerationProfiler.count(ChunkGenerationProfiler.Counter.BIOME_DECORATOR_CACHE_HITS);

        this.chunkPos = pos;
        this.decorating = true;
        try {
            this.genDecorations(biome, worldIn, random);
        } finally {
            this.decorating = false;
        }
        ci.cancel();
    }

    @Inject(method = "decorate", at = @At("RETURN"))
    private void rtgc$rememberGenerators(World worldIn, Random random, Biome biome,
                                         BlockPos pos, CallbackInfo ci) {

        this.rtgc$cachedOptions = worldIn.getWorldInfo().getGeneratorOptions();
        this.rtgc$cacheReady = true;
    }

    /**
     * 格雷科技整合包：GT 用自己的矿脉取代原版矿物（并默认拒绝 COAL/IRON/GOLD/REDSTONE/
     * DIAMOND/LAPIS/QUARTZ/EMERALD），原版装饰器剩下的 DIRT/GRAVEL/DIORITE/GRANITE/ANDESITE
     * 矿脉纯属浪费——它们在 500 区块测试里占 ore 探针的 92%、耗时的几乎全部。
     *
     * <p>跳过 {@code genStandardOre1/2} 本身而不是整个 {@code generateOres}，这样
     * {@code OreGenEvent.Pre/Post} 仍会照常发出去，其它模组的监听器不受影响。
     * 同时只跳过「原版自己那 11 个生成器」（{@code WorldGenMinable} + 原版方块），
     * 万一有模组用这两个方法生成自有矿石，不会被误伤。
     */
    @Inject(method = "genStandardOre1", at = @At("HEAD"), cancellable = true)
    private void rtgc$skipStandardOre1(World worldIn, Random random, int blockCount,
                                       net.minecraft.world.gen.feature.WorldGenerator generator,
                                       int minHeight, int maxHeight, CallbackInfo ci) {
        if (RTGConfig.skipVanillaOreGen() && rtgc$isVanillaOreGenerator(generator)) {
            ChunkGenerationProfiler.count(ChunkGenerationProfiler.Counter.ORE_GEN_SKIPPED);
            ci.cancel();
        }
    }

    @Inject(method = "genStandardOre2", at = @At("HEAD"), cancellable = true)
    private void rtgc$skipStandardOre2(World worldIn, Random random, int blockCount,
                                       net.minecraft.world.gen.feature.WorldGenerator generator,
                                       int centerHeight, int spread, CallbackInfo ci) {
        if (RTGConfig.skipVanillaOreGen() && rtgc$isVanillaOreGenerator(generator)) {
            ChunkGenerationProfiler.count(ChunkGenerationProfiler.Counter.ORE_GEN_SKIPPED);
            ci.cancel();
        }
    }

    /** 原版 {@code BiomeDecorator} 自己那 11 个生成器：DIRT/GRAVEL/石变种 + 8 种矿石。 */
    @org.spongepowered.asm.mixin.Unique
    private static boolean rtgc$isVanillaOreGenerator(
            net.minecraft.world.gen.feature.WorldGenerator generator) {
        if (!(generator instanceof net.minecraft.world.gen.feature.WorldGenMinable)) {
            return false;
        }
        final net.minecraft.block.Block block =
                ((net.minecraft.world.gen.feature.WorldGenMinable) generator).oreBlock.getBlock();
        return block == net.minecraft.init.Blocks.DIRT
                || block == net.minecraft.init.Blocks.GRAVEL
                || block == net.minecraft.init.Blocks.STONE
                || block == net.minecraft.init.Blocks.COAL_ORE
                || block == net.minecraft.init.Blocks.IRON_ORE
                || block == net.minecraft.init.Blocks.GOLD_ORE
                || block == net.minecraft.init.Blocks.REDSTONE_ORE
                || block == net.minecraft.init.Blocks.DIAMOND_ORE
                || block == net.minecraft.init.Blocks.LAPIS_ORE
                || block == net.minecraft.init.Blocks.QUARTZ_ORE
                || block == net.minecraft.init.Blocks.EMERALD_ORE;
    }
}
