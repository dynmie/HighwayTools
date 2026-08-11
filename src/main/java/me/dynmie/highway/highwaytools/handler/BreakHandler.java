package me.dynmie.highway.highwaytools.handler;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.TaskState;
import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * @author dynmie
 */
public class BreakHandler {

    private static final Minecraft mc = Minecraft.getInstance();

    /**
     * MultiPlayerGameMode#startPrediction is private in MC 26.1.2. It sends a packet carrying the
     * current block-state prediction sequence, which keeps the local world consistent with the
     * server (client-predicted removal). Reached via reflection since the addon ships no mixins.
     */
    private static final Method START_PREDICTION = findStartPrediction();

    private static Method findStartPrediction() {
        try {
            Method method = MultiPlayerGameMode.class.getDeclaredMethod("startPrediction", ClientLevel.class, PredictiveAction.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("MultiPlayerGameMode#startPrediction not found", e);
        }
    }

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

        mc.player.getInventory().setSelectedSlot(inventoryHandler.prepareToolInHotbar(blockState));

        int slot = mc.player.getInventory().getSelectedSlot();
        TaskState state = task.getTaskState();

        if (state == TaskState.BREAK) {
            task.updateState(TaskState.BREAKING);
            task.setStartMineTick(mc.player.tickCount);
            sendStartPacket(pos, direction(pos));
            swingHand();
        } else if (state == TaskState.BREAKING) {
            double progress = BlockUtils.getBreakDelta(slot, blockState) * (mc.player.tickCount - task.getStartMineTick() + 1);
            boolean creative = mc.player.getAbilities().instabuild;
            boolean ready = progress >= 1 || creative;

            if (ready) {
                sendStopPacket(pos, direction(pos));
                swingHand();
                if (!tools.getAvoidMineGhostBlocks().get()) {
                    BlockUtils.breakBlock(pos, true);
                }
                task.updateState(TaskState.PENDING_BREAK);
            } else if (mc.player.tickCount - task.getStartMineTick() > 10) {
                // progress stalled for 10+ ticks — re-send START to unstick the dig
                sendStartPacket(pos, direction(pos));
                swingHand();
            } else {
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
        try {
            START_PREDICTION.invoke(mc.gameMode, mc.level, action);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke MultiPlayerGameMode#startPrediction", e);
        }
    }

    private void swingHand() {
        if (mc.player == null) return;
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

}
