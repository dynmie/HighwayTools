package me.dynmie.highway.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class CheckBlocksCommand extends Command {

    private final HighwayTools tools;

    public CheckBlocksCommand(HighwayTools tools) {
        super("checkblocks", "Check all block tasks.");

        this.tools = tools;
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            for (BlockTask value : tools.getTaskManager().getBlockTasks().values()) {
                info(value.getBlockPos() + " " + value.getTaskState() + " " + Minecraft.getInstance().level.getBlockState(value.getBlockPos()).getBlock());
            }
            return SINGLE_SUCCESS;
        });
    }

}
