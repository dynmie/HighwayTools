package me.dynmie.highway.highwaytools.interaction;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.TaskState;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.InventoryUtils;
import meteordevelopment.meteorclient.systems.modules.Modules;
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
public class Break {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void mine(BlockTask task) {
        Objects.requireNonNull(mc.player, "player should not be null");
        Objects.requireNonNull(mc.world, "world should not be null");

        HighwayTools tools = Modules.get().get(HighwayTools.class);

        BlockPos pos = task.getBlockPos();
        BlockState blockState = mc.world.getBlockState(pos);

        mc.player.getInventory().selectedSlot = InventoryUtils.prepareToolInHotbar(blockState, tools.getPreferSilkTouch().get());

        int ticksNeeded = calcTicksToBreakBlock(pos, blockState);

        if (task.getMinedTicks() > ticksNeeded * 1.1 && task.getTaskState() == TaskState.BREAKING) {
            task.updateState(TaskState.BREAK);
            task.setMinedTicks(0);
        }

        mineNormally(task, ticksNeeded);
        task.incrementMinedTicks();
    }

    private static void mineNormally(BlockTask task, int ticksRequired) {
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

                if (!Modules.get().get(HighwayTools.class).getAvoidMineGhostBlocks().get()) {
                    mc.interactionManager.breakBlock(pos);
                }
            } else {
                swingHand();
            }
        }
    }

    private static void swingHand() {
        if (mc.player == null) return;
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private static void sendStopPacket(BlockPos pos, Direction direction) {
        if (mc.getNetworkHandler() == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
            pos,
            direction
        ));
    }

    private static void sendStartPacket(BlockPos pos, Direction direction) {
        if (mc.getNetworkHandler() == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
            pos,
            direction
        ));
    }

    private static void sendAbortPacket(BlockPos pos, Direction direction) {
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
