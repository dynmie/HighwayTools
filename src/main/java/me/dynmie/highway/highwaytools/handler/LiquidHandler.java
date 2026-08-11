package me.dynmie.highway.highwaytools.handler;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.TaskState;
import me.dynmie.highway.highwaytools.blueprint.BlueprintTask;
import me.dynmie.highway.highwaytools.place.PlacementSearcher;
import me.dynmie.highway.highwaytools.place.PlacementStep;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.LiquidUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author dynmie
 */
public class LiquidHandler {

    private static final Minecraft client = Minecraft.getInstance();

    private final HighwayTools tools;
    private final PlacementSearcher searcher;

    public LiquidHandler(HighwayTools tools) {
        this.tools = tools;
        this.searcher = new PlacementSearcher(tools);
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

            // Lambda Liquid.kt: skip this liquid when it is out of reach OR has no reachable,
            // visible placement sequence. Trying to fill it now would fail to find a support
            // or fail the line-of-sight check; drop the parent so the loop keeps moving instead
            // of churning on an impossible fill. It is retried once the front advances.
            boolean illegal = tools.getIllegalPlacements().get();
            List<PlacementStep> sequence = searcher.findSequence(
                client.player.getEyePosition(), offset, tools.getPlacementSearch().get(), illegal);

            if (client.player.getEyePosition().distanceTo(offset.getCenter()) > tools.getReach().get()
                || sequence.isEmpty()) {
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
