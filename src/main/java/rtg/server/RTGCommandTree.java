package rtg.server;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.*;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.server.command.CommandTreeBase;
import rtg.RTG;
import rtg.RTGConfig;
import rtg.api.util.Logger;
import rtg.api.world.RTGWorld;
import rtg.api.world.gen.RTGChunkGenSettings;

public final class RTGCommandTree extends CommandTreeBase
{
    private static final int    ACCESS_ALL           = 0; // everyone
    private static final int    ACCESS_ADMIN         = 4; // only admins
    private static final String LANGKEY_BASE         = RTG.MODID+".command";
    private static final String LANGKEY_PREFIX_ERROR = LANGKEY_BASE +".prefix.error";
    private static final Style  STYLE_ERROR          = new Style().setColor(TextFormatting.RED);
    private static final Style  STYLE_GOLD           = new Style().setColor(TextFormatting.GOLD);
    private static final String CMD_ROOT             = "rtg";

    public RTGCommandTree() {
        Logger.debug("Created /{} command", CMD_ROOT);
        this.addSubcommand(new CommandGetWhereAmI(this.getName()));
        this.addSubcommand(new CommandProbe());
    }
    @Override public int getRequiredPermissionLevel() { return ACCESS_ALL; }
    @Override public String getName() { return CMD_ROOT; }

    /** 供各子命令输出一行文本的共用辅助。 */
    private static void line(ICommandSender sender, String format, Object... args) {
        sender.sendMessage(new TextComponentString(String.format(format, args)));
    }
    @Override public String getUsage(ICommandSender sender) {
        final ITextComponent ret = new TextComponentString("");
        getSubCommands().forEach(cmd -> {
            if (cmd instanceof CommandTreeBase) {
                ((CommandTreeBase)cmd).getSubCommands().forEach(rcmd -> ret.appendText("\n").appendText(rcmd.getUsage(sender)));
            } else {
                ret.appendText("\n").appendText(cmd.getUsage(sender));
            }
        });
        return ret.getFormattedText();
    }

    static final class CommandGetWhereAmI extends CommandBase
    {
        private static final String NAME = "whereami";
        private static final String LANGKEY_ERROR = LANGKEY_BASE + "." + NAME + ".error";
        private final String parentName;
        CommandGetWhereAmI(String parentName) { this.parentName = parentName; }
        @Override public int getRequiredPermissionLevel() { return ACCESS_ADMIN; }
        @Override public String getName() { return NAME; }
        @Override public String getUsage(ICommandSender sender) {
            return new TextComponentString("/").appendSibling(new TextComponentString(this.parentName+" "+this.getName())).getFormattedText();
        }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
            final EntityPlayerMP player;
            if (sender instanceof EntityPlayerMP) {
                player = (EntityPlayerMP)sender;
            } else {
                sender.sendMessage(new TextComponentString("  ")
                    .appendSibling(new TextComponentTranslation(LANGKEY_PREFIX_ERROR).setStyle(STYLE_ERROR))
                    .appendText(": ")
                    .appendSibling(new TextComponentTranslation(LANGKEY_ERROR).setStyle(STYLE_GOLD))
                );
                return;
            }

            final BlockPos      pos      = player.getPosition();
            final World         world    = player.getEntityWorld();
            final Chunk         chunk    = world.getChunk(pos);
            final BiomeProvider provider = world.getBiomeProvider();

            final Biome chunkBiome    = chunk.getBiome(pos, provider);
            final Biome providerBiome = provider.getBiome(pos);

            player.sendMessage(new TextComponentString(String.format("Biome @ %s:%s, Chunk: %s, BiomeProvider: %s",
                pos.getX(), pos.getZ(), chunkBiome.getRegistryName(), providerBiome.getRegistryName())));
        }
    }

    /**
     * {@code /rtg probe [x z]} —— **机械验收入口**。
     * <p>
     * 打印指定坐标处地形管线的全部关键数值，并对可判定的项直接给出 {@code PASS}/{@code FAIL}。
     * 目的是让 WP-1（大陆场）、WP-3（隧道）、WP-6（配置）的验收变成"读数字"而不是"看地形像不像"。
     * <p>
     * 用法：站在待测位置执行 {@code /rtg probe}，或指定坐标 {@code /rtg probe 1200 -800}。
     */
    static final class CommandProbe extends CommandBase {
        private static final String NAME = "probe";

        @Override
        public int getRequiredPermissionLevel() {
            return ACCESS_ALL;
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return new TextComponentString("/rtg probe [x z]").getFormattedText();
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
            final World world = sender.getEntityWorld();
            final BlockPos pos;
            if (args.length >= 2) {
                try {
                    pos = new BlockPos(CommandBase.parseInt(args[0]), 0, CommandBase.parseInt(args[1]));
                } catch (Exception ex) {
                    line(sender, "[rtg] 坐标解析失败，用法：/rtg probe [x z]");
                    return;
                }
            } else if (sender instanceof EntityPlayerMP) {
                pos = ((EntityPlayerMP) sender).getPosition();
            } else {
                line(sender, "[rtg] probe 需由玩家执行，或提供 <x> <z>");
                return;
            }

            final int x = pos.getX();
            final int z = pos.getZ();
            final RTGWorld rtgWorld;
            final RTGChunkGenSettings settings;
            try {
                rtgWorld = RTGWorld.getInstance(world);
                settings = rtgWorld.getGeneratorSettings();
            } catch (Throwable t) {
                line(sender, "[rtg] 无法取得 RTGWorld（该维度可能未启用 RTG）：%s", t);
                return;
            }

            line(sender, "=== /rtg probe @ %d,%d (dim %d) ===", x, z, world.provider.getDimension());
            line(sender, "[provider] %s", world.getBiomeProvider().getClass().getSimpleName());

            line(sender, "[config] seaLevel=%d bedrockLayers=%d riverSizeFactor=%.2f",
                    settings.seaLevel, settings.bedrockLayers,
                    RTGConfig.riverSizeFactor());

            // ---- 实际地形 ----
            final Chunk chunk = world.getChunk(pos);
            final int surface = chunk.getHeightValue(x & 15, z & 15);
            line(sender, "[terrain] heightmapY=%d seaLevel=%d -> %s", surface, settings.seaLevel,
                    surface <= settings.seaLevel ? "UNDER_WATER" : "ABOVE_WATER");

            // ---- 群系 ----
            final Biome biome = chunk.getBiome(pos, world.getBiomeProvider());
            final boolean biomeOcean = BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN);
            final Biome providerBiome = world.getBiomeProvider().getBiome(pos);
            line(sender, "[biome] 区块内=%s oceanTagged=%s | provider=%s", biome.getRegistryName(), biomeOcean,
                    providerBiome.getRegistryName());

        }
    }
}
