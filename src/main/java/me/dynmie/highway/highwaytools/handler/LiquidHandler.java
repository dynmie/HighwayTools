package me.dynmie.highway.highwaytools.handler;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.TaskState;
import me.dynmie.highway.highwaytools.blueprint.BlueprintTask;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.LiquidUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.Optional;

/**
 * @author dynmie
 */
public class LiquidHandler {

    private static final Minecraft client = Minecraft.getInstance();

    private final HighwayTools tools;

    public LiquidHandler(HighwayTools tools) {
        this.tools = tools;
    }

    public boolean handleLiquid(BlockTask task) {
        Objects.requireNonNull(client.level, "world cannot be null; are you sure you are in a world?");
        Objects.requireNonNull(client.player, "player cannot be null; are you sure you are in a world?");

        boolean liquidFound = false;
        BlockPos pos = task.getBlockPos();

        for (Direction side : Direction.values()) {
            BlockPos offset = pos.relative(side);

            BlockState blockState = client.level.getBlockState(offset);
            if (!LiquidUtils.isLiquid(blockState)) {
                continue;
            }

            if (client.player.getEyePosition().distanceTo(offset.getCenter()) > tools.getReach().get()) {
                task.updateState(TaskState.DONE);
                return true;
            }

            liquidFound = true;

            Optional.ofNullable(tools.getTaskManager().getBlockTasks().get(offset)).ifPresentOrElse(
                this::updateTask,
                () -> {
                    Block fillerBlock = tools.getFillerBlock().get();

                    BlueprintTask blueprintTask = new BlueprintTask(fillerBlock, true);
                    BlockTask blockTask = new BlockTask(offset, TaskState.LIQUID, blueprintTask);

                    tools.getTaskManager().addTask(blockTask);
                }
            );
        }

        return liquidFound;
    }

    public void updateTask(BlockTask task) {
        task.updateState(TaskState.LIQUID);
    }

}
