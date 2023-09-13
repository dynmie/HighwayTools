package me.dynmie.highway.highwaytools.pathing;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.TaskState;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.HighwayUtils;
import meteordevelopment.meteorclient.utils.misc.MBlockPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

public class BaritonePathfinder {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final HighwayTools tools;

    private MBlockPos goal;

    public BaritonePathfinder(HighwayTools tools) {
        this.tools = tools;
    }

    public void updatePathing() {
        if (mc.player == null || mc.world == null) return;

        if (goal == null) {
            goal = new MBlockPos().set(tools.getPosition());
        }

        for (Map.Entry<BlockPos, BlockTask> entry : tools.getTaskManager().getBlockTasks().entrySet()) {
            BlockPos blockPos = entry.getKey();
            BlockTask task = entry.getValue();

            if (task.getTaskState() != TaskState.DONE) {
                if (HighwayUtils.isBehind(tools.getPosition().getMcPos(), blockPos, tools.getDir())) {
                    return;
                }
            }

        }

        if (mc.player.getPos().distanceTo(tools.getPosition().getMcPos().toCenterPos()) > 2) {
            return;
        }

        MBlockPos nextPos = new MBlockPos().set(tools.getPosition()).offset(tools.getDir());

//        BlockState upState = mc.world.getBlockState(nextPos.getMcPos().up());
//        BlockState midState = mc.world.getBlockState(nextPos.getMcPos());
//        BlockState downState = mc.world.getBlockState(nextPos.getMcPos().down());
//
//        if (!upState.isAir()) return;
//        if (!midState.isAir()) return;
//        if (downState.isReplaceable()) return;

        if (!isDone(nextPos.getMcPos().up())) return;
        if (!isDone(nextPos.getMcPos())) return;
        if (!isDone(nextPos.getMcPos().down())) return;


        tools.getPosition().offset(tools.getDir());
        goal.set(tools.getPosition());
    }

    private boolean isDone(BlockPos pos) {
        BlockTask task = tools.getTaskManager().getBlockTasks().get(pos);
        if (task == null) {
            tools.warning(pos + " is null!");
            return false;
        }

        TaskState ts = task.getTaskState();
        tools.info(ts + "");
        return ts == TaskState.DONE;
    }

    public void resetPathing() {
        goal = null;
    }

    public MBlockPos getGoal() {
        return goal;
    }
}
