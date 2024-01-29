package me.dynmie.highway.highwaytools.interaction;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.TaskState;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * @author dynmie
 */
public class Break {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void mine(BlockTask task) {
        BlockPos pos = task.getBlockPos();
        BlockState blockState = mc.world.getBlockState(pos);
//        blockState.

//        int delta = (int) Math.ceil(BlockUtils.getBreakDelta(InvUtils.findFastestTool(blockState).slot(), blockState));
        int ticksNeeded = Integer.MAX_VALUE - 1000000000;

        if (task.getMinedTicks() > ticksNeeded * 1.1 && task.getTaskState() == TaskState.BREAKING) {
            task.updateState(TaskState.BREAK);
            task.setMinedTicks(0);
        }

        mineNormally(task, ticksNeeded);
        task.incrementMinedTicks();
    }

    private static void mineNormally(BlockTask task, int ticksRequired) {
        TaskState state = task.getTaskState();
        BlockPos pos = task.getBlockPos();
        Direction direction = BlockUtils.getDirection(pos);

//        if (state == TaskState.BREAK) {
//            task.updateState(TaskState.BREAKING);
//            sendStartPacket(pos, direction);
//            swingHand();
//            mc.player.sendMessage(Text.literal("start break"));
//        } else {
//            if (task.getMinedTicks() >= ticksRequired) {
//                sendStopPacket(pos, direction);
//                swingHand();
//                mc.player.sendMessage(Text.literal("stop break"));
//            } else {
//                swingHand();
//                mc.player.sendMessage(Text.literal("swing"));
//            }
//        }

        // temp fix
        sendStartPacket(pos, direction);
        sendStopPacket(pos, direction);
        swingHand();
    }

    private static void swingHand() {
        if (mc.player == null) return;
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private static void sendStopPacket(BlockPos pos, Direction direction) {
        if (mc.player == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
            pos,
            direction
        ));
    }

    private static void sendStartPacket(BlockPos pos, Direction direction) {
        if (mc.player == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
            pos,
            direction
        ));
    }

    private static void sendAbortPacket(BlockPos pos, Direction direction) {
        if (mc.player == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
            pos,
            direction
        ));
    }

}
