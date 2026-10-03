package rtg.event;

import biomesoplenty.api.biome.BOPBiomes;
import biomesoplenty.api.biome.IExtendedBiome;
import biomesoplenty.api.generation.GeneratorStage;
import biomesoplenty.api.generation.IGenerator;
import net.minecraft.world.biome.Biome;
import rtg.api.RTGAPI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * 树木生成接管管理器（移植上游新树系统 / T6，来源 {@code ce51d05:rtg/event/TreeGenerationManager.java}）。
 *
 * <h2>它解决什么</h2>
 *
 * 上游的树有两条腿：{@code IRealisticBiome.rDecorate} 里按 {@code allowVanillaTrees()} 分流，
 * 以及 {@code EventHandlerCommon.takeoverTreeGeneration} 在外来世界类型里拦下原版的 TREE 装饰事件。
 * 两者都需要回答同一个问题：**"这个群系的树由 RTG 管吗？"** 这个问题由本类回答。
 *
 * <h2>为什么 BOP 要单独一套</h2>
 *
 * 原版群系把树挂在 {@code Biome.decorator} 上，关掉 {@code allowVanillaTrees()} 就够了。
 * BOP 用的是自己的 {@code GenerationManager} + {@code GeneratorStage} 体系，
 * 光靠那个开关关不掉 —— 必须把 BOP 该群系 TREE 阶段的生成器**逐个摘掉**
 * （{@link #resuppressBOPBiome}）。所以这里维护两张表：
 * 常规接管的 {@code takenOverBiomes} 与 BOP 专用的 {@code suppressedBOPBiomes}。
 *
 * <h2>相对上游的两处有意偏离（用户授权自行判断）</h2>
 *
 * <ol>
 *   <li>{@link #managingBiome} 加了 {@code realistic == null} 守卫。上游直接
 *       {@code RTGAPI.getRTGBiome(considered).getConfig()}，群系未登记时会 NPE ——
 *       而"未登记的群系"在本仓库是真实存在的（例如 BOP 未被 RTG 包装的群系）。</li>
 *   <li>{@link #resuppressBOPBiome} 在 BOP 未加载时直接返回。上游假定 BOP 必在，
 *       本仓库 BoP 是 {@code compileOnly} 依赖，必须在运行期可选。</li>
 * </ol>
 */
public class TreeGenerationManager {

    private final HashSet<Biome> takenOverBiomes = new HashSet<>();
    private final HashSet<Biome> suppressedBOPBiomes = new HashSet<>();
    private final boolean suppressingBOP = true;
    /*
     * This is separate because BoP has a different system for tree generation so the usual suppression
     * does not work. Suppression is done here because it's convenient to couple it with tree insertion.
     * The system is that the manager keeps a list of BOP biomes to suppress (setup during init)
     * and then gets flagged to activate the suppression while in RTG worlds.
     */

    private final ArrayList<IGenerator> suppressedBOPGenerators = new ArrayList<>();

    public TreeGenerationManager() {
    }

    /**
     * 这个群系的树是否由 RTG 接管。
     *
     * @return 群系被标记为接管、或是被抑制的 BOP 群系时返回 true；
     *         该群系在配置里关掉了 RTG 装饰、或未登记在 RTG 群系表里时返回 false
     */
    public boolean managingBiome(Biome considered) {

        final rtg.api.world.biome.IRealisticBiome realistic = RTGAPI.getRTGBiome(considered);
        // 偏离①：上游此处会 NPE。未登记的群系一律不接管。
        if (realistic == null || realistic.getConfig() == null) {
            return false;
        }
        if (realistic.getConfig().DISABLE_RTG_DECORATIONS.get()) {
            return false;
        }
        if (takenOverBiomes.contains(considered)) {
            return true;
        }
        if (suppressingBOP && suppressedBOPBiomes.contains(considered)) {
            return true;
        }
        return false;
    }

    public void manageBiome(Biome toManage) {
        takenOverBiomes.add(toManage);
    }

    public void suppressBOPBiome(Biome toSuppress) {
        takenOverBiomes.add(toSuppress);
        suppressedBOPBiomes.add(toSuppress);
        resuppressBOPBiome(toSuppress);
    }

    /**
     * 把某个 BOP 群系 TREE（以及 POST 阶段的 {@code sacred_oak_trees}）生成器摘掉。
     *
     * <p>摘两遍是上游原样：BOP 的 {@code removeGenerator} 既认 identifier 也认 name，
     * 而不同生成器注册时用哪一种并不统一，所以两轮都试。
     */
    public void resuppressBOPBiome(Biome toSuppress) {

        if (!suppressedBOPBiomes.contains(toSuppress)) {
            throw new RuntimeException(toSuppress.getBiomeName());
        }
        // 偏离②：本仓库 BoP 是 compileOnly，运行期可能不在。
        if (!net.minecraftforge.fml.common.Loader.isModLoaded("biomesoplenty")) {
            return;
        }
        final IExtendedBiome extendedBiome = BOPBiomes.REG_INSTANCE.getExtendedBiome(toSuppress);
        if (extendedBiome == null) {
            return;
        }

        Collection<IGenerator> generators = extendedBiome.getGenerationManager().getGeneratorsForStage(GeneratorStage.TREE);
        for (IGenerator generator : generators) {
            extendedBiome.getGenerationManager().removeGenerator(generator.getIdentifier());
        }

        generators = extendedBiome.getGenerationManager().getGeneratorsForStage(GeneratorStage.TREE);
        for (IGenerator generator : generators) {
            extendedBiome.getGenerationManager().removeGenerator(generator.getName());
        }

        generators = extendedBiome.getGenerationManager().getGeneratorsForStage(GeneratorStage.POST);
        for (IGenerator generator : generators) {
            if (generator.getName().equals("sacred_oak_trees")) {
                extendedBiome.getGenerationManager().removeGenerator(generator.getName());
            }
        }
    }
}
