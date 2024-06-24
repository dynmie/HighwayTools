package me.dynmie.highway.highwaytools.interaction;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.TaskState;
import me.dynmie.highway.highwaytools.blueprint.BlueprintTask;
import me.dynmie.highway.modules.HighwayTools;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Objects;
import java.util.Optional;

/**
 * @author dynmie
 */
public class Liquid {

    private static final MinecraftClient client = MinecraftClient.getInstance();

    public static boolean handleLiquid(HighwayTools tools, BlockTask task) {
        Objects.requireNonNull(client.world, "world cannot be null; are you sure you are in a world?");
        Objects.requireNonNull(client.player, "player cannot be null; are you sure you are in a world?");

        boolean liquidFound = false;
        BlockPos pos = task.getBlockPos();

        for (Direction side : Direction.values()) {
            BlockPos offset = pos.offset(side);

            BlockState blockState = client.world.getBlockState(offset);
            if (!isLiquid(blockState)) {
                continue;
            }

            if (client.player.getEyePos().distanceTo(offset.toCenterPos()) > tools.getReach().get()) {
                task.updateState(TaskState.DONE);
                return true;
            }

            liquidFound = true;

            Optional.ofNullable(tools.getTaskManager().getBlockTasks().get(offset)).ifPresentOrElse(
                Liquid::updateTask,
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

    public static void updateTask(BlockTask task) {
        task.updateState(TaskState.LIQUID);
    }

    public static boolean isLiquid(BlockState state) {
//        FluidState fluidState = state.getFluidState();

//        return !fluidState.isEmpty() || fluidState.isStill();
//        return !fluidState.isEmpty();
        return state.getBlock() instanceof FluidBlock;
    }

}
