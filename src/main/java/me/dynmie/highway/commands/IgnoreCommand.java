package me.dynmie.highway.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.world.level.block.Block;

public class IgnoreCommand extends Command {
    private final HighwayTools tools;

    public IgnoreCommand(HighwayTools tools) {
        super("ht", "HighwayTools ignore-list management.");
        this.tools = tools;
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder
            .then(literal("ignore")
                .then(literal("add")
                    .then(argument("block", BlockStateArgument.block(REGISTRY_ACCESS))
                        .executes(context -> {
                            Block block = context.getArgument("block", BlockInput.class).getState().getBlock();
                            tools.getIgnoreList().add(block);
                            info("Added " + block.getName().getString() + " to ignore list.");
                            return SINGLE_SUCCESS;
                        })))
                .then(literal("del")
                    .then(argument("block", BlockStateArgument.block(REGISTRY_ACCESS))
                        .executes(context -> {
                            Block block = context.getArgument("block", BlockInput.class).getState().getBlock();
                            tools.getIgnoreList().remove(block);
                            info("Removed " + block.getName().getString() + " from ignore list.");
                            return SINGLE_SUCCESS;
                        }))))
            .then(literal("list")
                .executes(context -> {
                    info("Ignored blocks: " + tools.getIgnoreList().getBlocks());
                    return SINGLE_SUCCESS;
                }))
            .then(literal("distance")
                .executes(context -> {
                    info("Distance limit: (highlight)%d", tools.getDistance().get());
                    return SINGLE_SUCCESS;
                })
                .then(argument("blocks", IntegerArgumentType.integer(0))
                    .executes(context -> {
                        int blocks = context.getArgument("blocks", Integer.class);
                        tools.getDistance().set(blocks);
                        info("Distance limit set to (highlight)%d%s", blocks, blocks == 0 ? " (unlimited)." : " blocks.");
                        return SINGLE_SUCCESS;
                    })));
    }
}
