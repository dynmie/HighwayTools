package me.dynmie.highway.highwaytools.handler;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.TaskState;
import me.dynmie.highway.mixin.MultiPlayerGameModeAccessor;
import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/**
 * @author dynmie
 */
public class BreakHandler {

    private static final Minecraft mc = Minecraft.getInstance();

    private final HighwayTools tools;
    private final InventoryHandler inventoryHandler;

    public BreakHandler(HighwayTools tools, InventoryHandler inventoryHandler) {
        this.tools = tools;
        this.inventoryHandler = inventoryHandler;
    }

    public void mine(BlockTask task) {
        Objects.requireNonNull(mc.player, "player should not be null");
        Objects.requireNonNull(mc.level, "level should not be null");

        BlockPos pos = task.getBlockPos();
        BlockState blockState = mc.level.getBlockState(pos);
        if (me.dynmie.highway.utils.BlockUtils.isTypeAir(blockState.getBlock())) {
            task.updateState(TaskState.BROKEN);
            return;
        }

        // lazy restock: if pickaxes are at/below the save threshold, request a restock
        // (deferred to next tick) but keep mining with the best available tool in the meantime
        tools.getTaskManager().needsToolsRestockCheck();

        // select the best tool for this block and hold it for the whole mine.
        // prepareToolInHotbar's swap also syncs the held item to the server.
        int slot = inventoryHandler.prepareToolInHotbar(blockState);

        TaskState state = task.getTaskState();

        if (state == TaskState.BREAK) {
            // start mining: mark the START tick once, send the start packet
            task.updateState(TaskState.BREAKING);
            task.setStartMineTick(mc.player.tickCount);

            boolean creative = mc.player.getAbilities().instabuild;
            boolean insta = creative || BlockUtils.canInstaBreak(pos);
            sendStartPacket(pos, direction(pos));
            if (insta) {
                sendStopPacket(pos, direction(pos));
                task.updateState(TaskState.PENDING_BREAK);
            }
            swingHand();
        } else if (state == TaskState.BREAKING) {
            // per-task progress: getDestroyProgress already accounts for the held tool,
            // so the break time is deterministic from the start tick — no shared state.
            int elapsed = mc.player.tickCount - task.getStartMineTick();
            int ticksNeeded = calcTicksToBreakBlock(pos, blockState);

            boolean creative = mc.player.getAbilities().instabuild;
            boolean ready = creative || elapsed >= ticksNeeded;

            if (ready) {
                sendStopPacket(pos, direction(pos));
                swingHand();
                if (!tools.getAvoidMineGhostBlocks().get()) {
                    BlockUtils.breakBlock(pos, true);
                }
                // Keep the tool held: the server finishes the break via its delayed-destroy
                // path, which recomputes getDestroyProgress every server tick using the held
                // item. swapBack() here would resync an empty/non-tool hand, dropping the
                // progress rate ~8x and making mining appear super slow. The tool is released
                // naturally when the next task swaps it away (e.g. PlaceHandler).
                task.updateState(TaskState.PENDING_BREAK);
            } else if (elapsed > 10) {
                // progress stalled for 10+ ticks — re-send START to unstick the dig
                sendStartPacket(pos, direction(pos));
                swingHand();
            } else {
                swingHand();
            }
        }
    }

    /**
     * The number of game ticks to break a block with the currently held tool.
     * Equivalent to the base addon's {@code calcTicksToBreakBlock}.
     */
    public static int calcTicksToBreakBlock(BlockPos pos, BlockState state) {
        return (int) Math.ceil(1 / state.getDestroyProgress(mc.player, mc.level, pos));
    }

    private Direction direction(BlockPos pos) {
        Direction dir = BlockUtils.getDirection(pos);
        return dir == null ? Direction.DOWN : dir;
    }

    private void sendStartPacket(BlockPos pos, Direction direction) {
        if (mc.getConnection() == null) return;
        startPrediction(sequence -> new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence));
    }

    private void sendStopPacket(BlockPos pos, Direction direction) {
        if (mc.getConnection() == null) return;
        startPrediction(sequence -> new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence));
    }

    private void startPrediction(PredictiveAction action) {
        if (mc.gameMode == null || mc.level == null) return;
        ((MultiPlayerGameModeAccessor) mc.gameMode).highway$startPrediction(mc.level, action);
    }

    private void swingHand() {
        if (mc.player == null) return;
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

}
