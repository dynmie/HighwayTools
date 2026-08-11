package me.dynmie.highway.highwaytools.pathing;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.TaskState;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.DirectionUtils;
import me.dynmie.highway.utils.LocationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Map;

public class BaritonePathfinder {

    private static final Minecraft mc = Minecraft.getInstance();

    private final HighwayTools tools;

    private BlockPos goal;

    public BaritonePathfinder(HighwayTools tools) {
        this.tools = tools;
    }

    public void updatePathing() {
        if (mc.player == null || mc.level == null) return;

        if (goal == null) {
            goal = tools.getCurrentPosition();
        }

        for (Map.Entry<BlockPos, BlockTask> entry : tools.getTaskManager().getBlockTasks().entrySet()) {
            BlockPos blockPos = entry.getKey();
            BlockTask task = entry.getValue();

            if (task.getTaskState() != TaskState.DONE) {
                if (LocationUtils.isBehind(tools.getCurrentPosition(), blockPos, tools.getDirection())) {
                    return;
                }
            }

        }

        if (mc.player.position().distanceTo(tools.getCurrentPosition().getCenter()) > 2) {
            return;
        }

        BlockPos nextPos = tools.getCurrentPosition().offset(DirectionUtils.toVec3i(tools.getDirection()));

//        BlockState upState = mc.level.getBlockState(nextPos.above());
//        BlockState midState = mc.level.getBlockState(nextPos);
//        BlockState downState = mc.level.getBlockState(nextPos.below());

//        if (!upState.isAir()) return;
//        if (!midState.isAir()) return;
//        if (downState.canBeReplaced()) return;

        if (!isDone(nextPos.above())) return;
        if (!isDone(nextPos)) return;
        if (!isDone(nextPos.below())) return;


        tools.setCurrentPosition(nextPos);
        goal = tools.getCurrentPosition();
    }

    private boolean isDone(BlockPos pos) {
        BlockTask task = tools.getTaskManager().getBlockTasks().get(pos);
        if (task == null) {
            return false;
        }

        TaskState ts = task.getTaskState();
        return ts == TaskState.DONE;
    }

    public void resetPathing() {
        goal = null;
    }

    public BlockPos getGoal() {
        return goal;
    }
}
