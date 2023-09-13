package me.dynmie.highway.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.systems.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class CommandExample extends Command {
    public CommandExample() {
        super("example", "Sends a message.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            for (BlockTask value : Modules.get().get(HighwayTools.class).getTaskManager().getBlockTasks().values()) {
                info(value.getBlockPos() + " " + value.getTaskState() + " " + mc.world.getBlockState(value.getBlockPos()).getBlock());
            }
            return SINGLE_SUCCESS;
        });
    }
}
