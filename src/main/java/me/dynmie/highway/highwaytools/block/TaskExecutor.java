package me.dynmie.highway.highwaytools.block;

import me.dynmie.highway.highwaytools.interaction.Break;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.HighwayUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public class TaskExecutor {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final HighwayTools tools;

    public TaskExecutor(HighwayTools tools) {
        this.tools = tools;
    }

    public void doTask(BlockTask task, boolean check) {
        task.onTick();

        switch (task.getTaskState()) {
            case BREAKING -> doBreaking(task, check);
            case BROKEN -> doBroken(task);
            case PLACED -> doPlaced(task);
            case BREAK -> doBreak(task, check);
            case PLACE -> doPlace(task, check);
            default -> {}
        }
    }

    private void doBreaking(BlockTask task, boolean check) {
        if (mc.world == null || mc.player == null) return;
        Block block = mc.world.getBlockState(task.getBlockPos()).getBlock();

        if (HighwayUtils.isTypeAir(block)) {
            task.updateState(TaskState.BROKEN);
            return;
        }

//        tools.info("breaking");

        // check liquid

        if (!mc.player.isOnGround()) return;
        if (check) return;
        mineBlock(task);
    }

    private void doBroken(BlockTask task) {

        if (mc.world == null) return;
        Block block = mc.world.getBlockState(task.getBlockPos()).getBlock();

        if (!HighwayUtils.isTypeAir(block)) {
            task.updateState(TaskState.BREAK);
            return;
        }

        Block targetBlock = task.getBlueprintTask().getTargetBlock();
        if (HighwayUtils.isTypeAir(targetBlock)) {
            BlockSoundGroup soundGroup = targetBlock.getDefaultState().getSoundGroup();
            mc.player.playSound(soundGroup.getBreakSound(), SoundCategory.BLOCKS, soundGroup.getVolume(), soundGroup.getPitch());
            task.updateState(TaskState.DONE);
        } else {
            tools.setBlocksBroken(tools.getBlocksBroken() + 1);
            task.updateState(TaskState.PLACE);
        }

    }

    private void doPlaced(BlockTask task) {
        if (mc.world == null) return;
        BlockState state = mc.world.getBlockState(task.getBlockPos());
        Block currentBlock = state.getBlock();
        Block targetBlock = task.getBlueprintTask().getTargetBlock();

        if ((HighwayUtils.blockEqualsAndAirCheck(currentBlock, targetBlock) || task.getBlueprintTask().isFiller()) && !state.isReplaceable()) {
            tools.setBlocksPlaced(tools.getBlocksPlaced() + 1);
            task.updateState(TaskState.DONE);
            return;
        }

        // break if target block is not the current block and target block is air
        if (HighwayUtils.blockEqualsAndAirCheck(currentBlock, targetBlock) && HighwayUtils.isTypeAir(targetBlock)) {
            task.updateState(TaskState.BREAK);
            return;
        }

        // break if target block is air and current block is not air
        if (targetBlock.getDefaultState().isAir() && !currentBlock.getDefaultState().isAir()) {
            task.updateState(TaskState.BREAK);
            return;
        }

//        tools.info("placed");

        task.updateState(TaskState.PLACE);
    }

    private void doBreak(BlockTask task, boolean check) {
        if (mc.world == null || mc.player == null) return;
        BlockState state = mc.world.getBlockState(task.getBlockPos());
        Block currentBlock = state.getBlock();
        Block targetBlock = task.getBlueprintTask().getTargetBlock();

        if (currentBlock.equals(Blocks.END_PORTAL_FRAME) || currentBlock.equals(Blocks.BEDROCK) || currentBlock.equals(Blocks.NETHER_PORTAL) || currentBlock.equals(Blocks.END_PORTAL)) {
            task.updateState(TaskState.DONE);
            return;
        }

//        if (targetBlock.equals(tools.getFillerBlock().get())) {
//
//            if (block.equals(tools.getMainBlock().get()) || !BlockUtils.canPlace(task.getBlockPos(), true)) {
//                task.updateState(TaskState.DONE);
//                return;
//            }
//            return;
//        }

//        if (targetBlock.equals(tools.getFillerBlock().get()) && (HighwayUtils.isTypeAir(block) || !BlockUtils.canPlace(task.getBlockPos(), true))) {
//            task.updateState(TaskState.DONE);
//            return;
//        }

//        if (targetBlock.equals(tools.getMainBlock().get()) && block.equals(tools.getMainBlock().get())) {
//            task.updateState(TaskState.DONE);
//            return;
//        }

        if (targetBlock.equals(tools.getFillerBlock().get())) {
            if (HighwayUtils.blockEqualsAndAirCheck(currentBlock, targetBlock)) {
                task.updateState(TaskState.DONE);
                return;
            }
        }

        if (targetBlock.equals(tools.getMainBlock().get())) {
            if (HighwayUtils.blockEqualsAndAirCheck(currentBlock, targetBlock)) {
                task.updateState(TaskState.DONE);
                return;
            }
        }



        if (HighwayUtils.isTypeAir(currentBlock)) {
            if (HighwayUtils.isTypeAir(targetBlock)) {
                task.updateState(TaskState.BROKEN);
            } else {
                task.updateState(TaskState.PLACE);
            }
            return;
        }

        // TODO: liquid

        if (!mc.player.isOnGround()) return;
        if (check) return;

        mineBlock(task);
    }

    private void doPlace(BlockTask task, boolean check) {
        if (mc.world == null || mc.player == null) return;
        BlockState state = mc.world.getBlockState(task.getBlockPos());
        Block block = state.getBlock();
        Block targetBlock = task.getBlueprintTask().getTargetBlock();

        // TODO LIQUID

        if (block.equals(tools.getMainBlock().get()) && targetBlock.equals(tools.getMainBlock().get())) {
            task.updateState(TaskState.PLACED);
            return;
        }

        if (targetBlock.equals(tools.getFillerBlock().get()) && block.equals(tools.getFillerBlock().get())) {
            task.updateState(TaskState.PLACED);
            return;
        }

        if (HighwayUtils.isTypeAir(targetBlock)) {
            if (!HighwayUtils.isTypeAir(block)) {
                task.updateState(TaskState.BREAK);
            } else {
                task.updateState(TaskState.BROKEN);
            }
            return;
        }

//        if (!block.equals(targetBlock)) {
//            if (!block.equals(Blocks.AIR)) {
//                task.updateState(TaskState.BREAK);
//                return;
//            }
//        }

        if (!BlockUtils.canPlace(task.getBlockPos(), true)) {
//            task.updateState(TaskState.DONE);
            return;
        }

        if (check) return;
        placeBlock(task);
//        tools.info("place");
    }

    private void mineBlock(BlockTask task) {
        BlockPos pos = task.getBlockPos();

        if (tools.getRotation().get().mine && tools.getRotateCamera().get() && mc.player != null) {
            mc.player.setPitch((float) Rotations.getPitch(task.getBlockPos()));
            mc.player.setYaw((float) Rotations.getYaw(task.getBlockPos()));
        }

        if (tools.getRotation().get().mine) {
            Rotations.rotate(Rotations.getPitch(pos), Rotations.getYaw(pos), () -> {});
        }

        Break.mine(task);
    }

    private void placeBlock(BlockTask task) {
        place(task, 1);
    }

    private boolean place(BlockTask task, int slot) {
        if (tools.getRotation().get().place && tools.getRotateCamera().get() && mc.player != null) {
            mc.player.setPitch((float) Rotations.getPitch(task.getBlockPos()));
            mc.player.setYaw((float) Rotations.getYaw(task.getBlockPos()));
        }
        return BlockUtils.place(task.getBlockPos(), Hand.MAIN_HAND, slot, tools.getRotation().get().place, 0, true, true, false);
    }

}
