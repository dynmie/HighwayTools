package me.dynmie.highway.highwaytools.handler;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.TaskState;
import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Objects;

/**
 * @author dynmie
 */
public class BreakHandler {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final HighwayTools tools;
    private final InventoryHandler inventoryHandler;

    public BreakHandler(HighwayTools tools, InventoryHandler inventoryHandler) {
        this.tools = tools;
        this.inventoryHandler = inventoryHandler;
    }

    public void mine(BlockTask task) {
        Objects.requireNonNull(mc.player, "player should not be null");
        Objects.requireNonNull(mc.world, "world should not be null");

        BlockPos pos = task.getBlockPos();
        BlockState blockState = mc.world.getBlockState(pos);

        mc.player.getInventory().selectedSlot = inventoryHandler.prepareToolInHotbar(blockState);

        int ticksNeeded = calcTicksToBreakBlock(pos, blockState);

        if (task.getMinedTicks() > ticksNeeded * 1.1 && task.getTaskState() == TaskState.BREAKING) {
            task.updateState(TaskState.BREAK);
            task.setMinedTicks(0);
        }

        mineNormally(task, ticksNeeded);
        task.incrementMinedTicks();
    }

    private void mineNormally(BlockTask task, int ticksRequired) {
        Objects.requireNonNull(mc.interactionManager, "interactionManager should not be null");

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
                    mc.interactionManager.breakBlock(pos);
                }
            } else {
                swingHand();
            }
        }
    }

    private void swingHand() {
        if (mc.player == null) return;
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void sendStopPacket(BlockPos pos, Direction direction) {
        if (mc.getNetworkHandler() == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
            pos,
            direction
        ));
    }

    private void sendStartPacket(BlockPos pos, Direction direction) {
        if (mc.getNetworkHandler() == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
            pos,
            direction
        ));
    }

    private void sendAbortPacket(BlockPos pos, Direction direction) {
        if (mc.getNetworkHandler() == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
            pos,
            direction
        ));
    }

    public static int calcTicksToBreakBlock(BlockPos pos, BlockState state) {
        return (int) Math.ceil(1 / state.calcBlockBreakingDelta(mc.player, mc.world, pos));
    }

}
