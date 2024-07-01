package me.dynmie.highway.highwaytools.handler;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.TaskState;
import me.dynmie.highway.highwaytools.blueprint.BlueprintTask;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.LiquidUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Objects;
import java.util.Optional;

/**
 * @author dynmie
 */
public class LiquidHandler {

    private static final MinecraftClient client = MinecraftClient.getInstance();

    private final HighwayTools tools;

    public LiquidHandler(HighwayTools tools) {
        this.tools = tools;
    }

    public boolean handleLiquid(BlockTask task) {
        Objects.requireNonNull(client.world, "world cannot be null; are you sure you are in a world?");
        Objects.requireNonNull(client.player, "player cannot be null; are you sure you are in a world?");

        boolean liquidFound = false;
        BlockPos pos = task.getBlockPos();

        for (Direction side : Direction.values()) {
            BlockPos offset = pos.offset(side);

            BlockState blockState = client.world.getBlockState(offset);
            if (!LiquidUtils.isLiquid(blockState)) {
                continue;
            }

            if (client.player.getEyePos().distanceTo(offset.toCenterPos()) > tools.getReach().get()) {
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
