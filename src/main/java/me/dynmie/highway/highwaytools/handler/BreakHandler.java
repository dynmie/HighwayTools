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

        // NO restock trigger here. Lambda's `swapOrMoveBestTool` starts a tool restock from
        // the tool-selection gate exactly once and then keeps mining with the best tool; the
        // container itself runs at the top of runTasks. Triggering a restock from inside the
        // mine function on every break would set containerTask, which blocks the loop until
        // the container times out (60 ticks), then the next break triggers it again — one
        // break per restock cycle. Restock must not gate mining.

        TaskState state = task.getTaskState();

        if (state == TaskState.BREAK) {
            // select the tool ONCE when the mine starts and hold it for the whole mine.
            // prepareToolInHotbar's swap also syncs the held item to the server.
            inventoryHandler.prepareToolInHotbar(blockState);
            task.updateState(TaskState.BREAKING);
            task.setStartMineTick(mc.player.tickCount);

            boolean creative = mc.player.getAbilities().instabuild;
            boolean insta = creative || BlockUtils.canInstaBreak(pos);

            if (insta) {
                // INSTANT (creative or insta-mine): mirrors Lambda's mineBlockInstant —
                // send START (server does creative destroyAndAck) and go PENDING_BREAK.
                // The block is destroyed server-side and the air is confirmed next tick.
                // Exactly one block per tick is guaranteed by the PENDING_BREAK state, NOT
                // by waitTicks — waitTicks is shared with the placement pace and setting it
                // here would throttle every break to the place-delay cadence.
                sendStartPacket(pos, direction(pos));
                swingHand();
                task.updateState(TaskState.PENDING_BREAK);
                return;
            }

            // NORMAL (survival): START once, then accumulate progress each tick.
            sendStartPacket(pos, direction(pos));
            swingHand();
        } else if (state == TaskState.BREAKING) {
            // Progress from the best tool in inventory (meteor's getBreakDelta), NOT the held
            // item — same as meteor PacketMine's progress()/isReady() and Lambda's
            // ticksNeeded from getPlayerRelativeBlockHardness. Never waits on a server ack.
            int fastestSlot = meteordevelopment.meteorclient.utils.player.InvUtils.findFastestTool(blockState).slot();
            double delta = BlockUtils.getBreakDelta(fastestSlot, blockState);
            int elapsed = mc.player.tickCount - task.getStartMineTick();
            double progress = delta * elapsed;

            if (progress >= 1.0) {
                // finished: send STOP to complete the dig; the server applies the air.
                sendStopPacket(pos, direction(pos));
                swingHand();
                task.updateState(TaskState.BROKEN);
            } else {
                // keep digging: swing for animation; the server accumulates destroy progress
                // from the held item. START is sent once, not re-sent every tick.
                swingHand();
            }
        }
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
