package me.dynmie.highway.highwaytools.pathing;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.TaskState;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.DirectionUtils;
import me.dynmie.highway.utils.LocationUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

public class BaritonePathfinder {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final HighwayTools tools;

    private BlockPos goal;

    public BaritonePathfinder(HighwayTools tools) {
        this.tools = tools;
    }

    public void updatePathing() {
        if (mc.player == null || mc.world == null) return;

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

        if (mc.player.getPos().distanceTo(tools.getCurrentPosition().toCenterPos()) > 2) {
            return;
        }

        BlockPos nextPos = tools.getCurrentPosition().add(DirectionUtils.toVec3i(tools.getDirection()));

//        BlockState upState = mc.world.getBlockState(nextPos.up());
//        BlockState midState = mc.world.getBlockState(nextPos);
//        BlockState downState = mc.world.getBlockState(nextPos.down());

//        if (!upState.isAir()) return;
//        if (!midState.isAir()) return;
//        if (downState.isReplaceable()) return;

        if (!isDone(nextPos.up())) return;
        if (!isDone(nextPos)) return;
        if (!isDone(nextPos.down())) return;


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
