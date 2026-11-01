package rtg.api.world.deco;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import rtg.api.util.ChunkInfo;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.IRealisticBiome;

import java.util.Random;


/**
 * 把 RWG 的 {@code rDecorate(...)} 方法体**原样**装进 RTG 的装饰管线。
 *
 * <p>RWG 的群系装饰是「一个方法体 + 一堆一次性 WorldGenerator」，密度、河距、噪声分支都写在
 * 方法体里（例如 {@code for (int i = 0; i < 15f * strength; i++)}）。RTG 的 {@link DecoBase}
 * 体系是「参数化对象 + 每对象一次 generate」，两者表达力不同；要逐行照抄，最可靠的做法就是
 * 让一个 deco 直接执行那段方法体。
 *
 * <p><b>为什么不需要 strength</b>：RWG 在 {@code ChunkGeneratorRealistic.populate} 里按
 * 9×9 邻域权重 {@code 0.01234569f} 把每个邻近群系的 {@code rDecorate} 调一次，权重以
 * {@code strength} 传入。rtgc 的 {@code ChunkGeneratorRTG.populate} 已经按同一权重
 * **概率性**调用邻近群系的 {@code rDecorate}（见 {@code docs/rwg-port-gaps.md} §17③(c)）——
 * 即「被调到的这一次是满强度」。所以在方法体里取 {@code strength = 1f}，期望密度与 RWG 一致。
 *
 * <p>坐标口径：RWG 的 {@code chunkX/chunkY} 是**区块原点的方块坐标**，方法体里自己
 * {@code + rand.nextInt(16) + 8}。这里同样传 {@code chunkPos.x * 16}，不要传 {@code *16+8}
 * （否则每个装饰都会整体偏移 8 格）。
 */
public class DecoRtgDecorator extends DecoBase {

    @FunctionalInterface
    public interface Body {

        /**
         * @param world   世界
         * @param rand    区块随机源（与 RWG 的 {@code rand} 同源）
         * @param chunkX  区块原点方块 X
         * @param chunkZ  区块原点方块 Z
         * @param river   RWG 的 {@code river}（0 = 河心，1 = 内陆）
         * @param rtgWorld 噪声源
         */
        void decorate(World world, Random rand, int chunkX, int chunkZ, float river, RTGWorld rtgWorld);
    }

    private final Body body;

    public DecoRtgDecorator(final Body body) {

        super();
        this.body = body;
    }

    @Override
    public void generate(final IRealisticBiome biome, final RTGWorld rtgWorld, final Random rand,
            final ChunkPos chunkPos, final float river, final boolean hasVillage, final ChunkInfo chunkInfo) {

        this.body.decorate(rtgWorld.world(), rand, chunkPos.x * 16, chunkPos.z * 16, river, rtgWorld);
    }
}
