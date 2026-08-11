package me.dynmie.highway.highwaytools.handler;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.TaskState;
import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.Minecraft;
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

        mc.player.getInventory().setSelectedSlot(inventoryHandler.prepareToolInHotbar(blockState));

        int ticksNeeded = calcTicksToBreakBlock(pos, blockState);

        if (task.getMinedTicks() > ticksNeeded * 1.1 && task.getTaskState() == TaskState.BREAKING) {
            task.updateState(TaskState.BREAK);
            task.setMinedTicks(0);
        }

        mineNormally(task, ticksNeeded);
        task.incrementMinedTicks();
    }

    private void mineNormally(BlockTask task, int ticksRequired) {
        Objects.requireNonNull(mc.gameMode, "gameMode should not be null");

        TaskState state = task.getTaskState();
        BlockPos pos = task.getBlockPos();
        Direction direction = BlockUtils.getDirection(pos);

        if (state == TaskState.BREAK) {
            task.updateState(TaskState.BREAKING);
            sendStartPacket(pos, direction);
            swingHand();
        } else {
            if (task.getMinedTicks() >= ticksRequired) {
                sendStopPacket(pos, direction);
                swingHand();

                if (!tools.getAvoidMineGhostBlocks().get()) {
                    BlockUtils.breakBlock(pos, true);
                }
            } else {
                swingHand();
            }
        }
    }

    private void swingHand() {
        if (mc.player == null) return;
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void sendStopPacket(BlockPos pos, Direction direction) {
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
            pos,
            direction
        ));
    }

    private void sendStartPacket(BlockPos pos, Direction direction) {
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
            pos,
            direction
        ));
    }

    private void sendAbortPacket(BlockPos pos, Direction direction) {
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
            pos,
            direction
        ));
    }

    public static int calcTicksToBreakBlock(BlockPos pos, BlockState state) {
        return (int) Math.ceil(1 / state.getDestroyProgress(mc.player, mc.level, pos));
    }

}
