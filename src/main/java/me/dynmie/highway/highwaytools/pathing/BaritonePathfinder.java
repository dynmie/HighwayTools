package me.dynmie.highway.highwaytools.pathing;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.TaskState;
import me.dynmie.highway.highwaytools.container.ContainerTask;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.DirectionUtils;
import me.dynmie.highway.utils.LocationUtils;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

import java.util.Map;

/**
 * Advances the highway's building front along {@code direction} and, when the next column
 * has no reachable line-of-sight, walks the player forward onto the built floor (bridging),
 * exactly like Lambda HighwayTools' {@code Pathfinder.MovementState.BRIDGE}.
 */
public class BaritonePathfinder {

    public enum MovementState {
        RUNNING, BRIDGE
    }

    private static final Minecraft mc = Minecraft.getInstance();

    private final HighwayTools tools;

    private BlockPos goal;
    private MovementState movementState = MovementState.RUNNING;

    public BaritonePathfinder(HighwayTools tools) {
        this.tools = tools;
    }

    public void updatePathing() {
        if (!tools.isActive()) return;
        if (mc.player == null || mc.level == null) return;

        if (goal == null) {
            goal = tools.getCurrentPosition();
        }

        switch (movementState) {
            case RUNNING -> updateRunning();
            case BRIDGE -> updateBridge();
        }
    }

    private void updateRunning() {
        // block the front if any non-DONE task is behind the player
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

        int maxDistance = tools.getDistance().get();
        if (maxDistance > 0) {
            double traveled = nextPos.getCenter().distanceTo(tools.getStartPosition().getCenter());
            if (traveled >= maxDistance) {
                // reached the limit — stop advancing; the module will be toggled off
                tools.runNextTick(() -> tools.toggle());
                return;
            }
        }

        if (!isDone(nextPos.above())) return;
        if (!isDone(nextPos)) return;
        if (!isDone(nextPos.below())) return;

        // The next front column is fully done — advance. If the floor ahead of the player is
        // missing (we can't see it to place), enter BRIDGE to walk forward onto the built floor.
        if (shouldBridge()) {
            movementState = MovementState.BRIDGE;
            return;
        }

        tools.setCurrentPosition(nextPos);
        goal = tools.getCurrentPosition();
    }

    private void updateBridge() {
        boolean isAboveAir = mc.level.getBlockState(mc.player.blockPosition().below()).canBeReplaced();

        // keep sneaking while over air so the player doesn't fall off the bridge
        mc.options.keyShift.setDown(isAboveAir);

        if (!shouldBridge()) {
            if (!isAboveAir) {
                movementState = MovementState.RUNNING;
            }
            mc.options.keyShift.setDown(false);
        }
    }

    /**
     * Moves the player forward along the highway while bridging. Registered as a
     * {@link PlayerMoveEvent} handler so the movement survives the player's own
     * {@code aiStep} (like meteor's {@code Strafe} speed mode) and baritone, which is
     * paused while the goal is null.
     */
    public void handleMove(PlayerMoveEvent event) {
        if (movementState != MovementState.BRIDGE) return;
        if (mc.player == null) return;

        Vec3i dir = DirectionUtils.toVec3i(tools.getDirection());
        double speed = tools.getMoveSpeed().get();

        double x = dir.getX() * speed;
        double z = dir.getZ() * speed;

        // preserve the player's own vertical motion (falling / stepping up)
        ((IVec3) event.movement).meteor$setXZ(x, z);
    }

    /**
     * Mirrors Lambda's {@code shouldBridge}: bridge when scaffold is on, no container restock
     * is active, the next step is air above replaceable ground, and no currently-placeable
     * task has a reachable sequence (i.e. the front truly can't be built from where we are).
     */
    private boolean shouldBridge() {
        ContainerTask containerTask = tools.getTaskManager().containerTask;
        if (!tools.getScaffold().get() || containerTask != null) return false;

        BlockPos next = tools.getCurrentPosition().offset(DirectionUtils.toVec3i(tools.getDirection()));

        if (!mc.level.getBlockState(next).isAir()) return false;
        if (!mc.level.getBlockState(next.above()).isAir()) return false;
        if (!mc.level.getBlockState(next.below()).canBeReplaced()) return false;

        for (Map.Entry<BlockPos, BlockTask> entry : tools.getTaskManager().getBlockTasks().entrySet()) {
            BlockTask task = entry.getValue();
            if (task.getTaskState() == TaskState.PENDING_PLACE) return false;

            if (task.getTaskState() == TaskState.PLACE || task.getTaskState() == TaskState.LIQUID) {
                // a task we can still build in place (has a reachable support) — don't bridge yet
                if (!task.getSequence().isEmpty()) return false;
            }
        }

        return true;
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
        movementState = MovementState.RUNNING;
        if (mc.options != null) mc.options.keyShift.setDown(false);
    }

    public BlockPos getGoal() {
        // suppress the baritone goal while bridging so baritone requests a pause
        return movementState == MovementState.BRIDGE ? null : goal;
    }
}
