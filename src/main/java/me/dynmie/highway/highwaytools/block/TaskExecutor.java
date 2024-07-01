package me.dynmie.highway.highwaytools.block;

import me.dynmie.highway.highwaytools.handler.BreakHandler;
import me.dynmie.highway.highwaytools.handler.InventoryHandler;
import me.dynmie.highway.highwaytools.handler.LiquidHandler;
import me.dynmie.highway.highwaytools.handler.PlaceHandler;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.BlockUtils;
import me.dynmie.highway.utils.LiquidUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Objects;

public class TaskExecutor {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final HighwayTools tools;
    private final BreakHandler breakHandler;
    private final InventoryHandler inventoryHandler;
    private final LiquidHandler liquidHandler;
    private final PlaceHandler placeHandler;

    public TaskExecutor(HighwayTools tools, BreakHandler breakHandler, InventoryHandler inventoryHandler, LiquidHandler liquidHandler, PlaceHandler placeHandler) {
        this.tools = tools;
        this.breakHandler = breakHandler;
        this.inventoryHandler = inventoryHandler;
        this.liquidHandler = liquidHandler;
        this.placeHandler = placeHandler;
    }

    public void doTask(BlockTask task, boolean check) {
        task.onTick();

        switch (task.getTaskState()) {
            case BREAKING -> doBreaking(task, check);
            case BROKEN -> doBroken(task);
            case PLACED -> doPlaced(task);
            case BREAK -> doBreak(task, check);
            case PLACE, LIQUID -> doPlace(task, check);
            case PENDING_BREAK -> doPendingBreak(task);
            case PENDING_PLACE -> doPendingPlace(task);
            default -> {}
        }
    }

    private void doBreaking(BlockTask task, boolean check) {
        if (mc.world == null || mc.player == null) return;
        BlockState state = mc.world.getBlockState(task.getBlockPos());
        Block block = state.getBlock();

        if (BlockUtils.isTypeAir(block)) {
            task.updateState(TaskState.BROKEN);
            return;
        }

        // check liquid
        if (LiquidUtils.isLiquid(state)) {
            liquidHandler.updateTask(task);
            return;
        }

        if (!mc.player.isOnGround()) return;
        if (check) return;

        mineBlock(task);
    }

    private void doBroken(BlockTask task) {

        if (mc.world == null) return;
        Block block = mc.world.getBlockState(task.getBlockPos()).getBlock();

        if (!BlockUtils.isTypeAir(block)) {
            task.updateState(TaskState.BREAK);
            return;
        }

        Block targetBlock = task.getBlueprintTask().getTargetBlock();
        if (BlockUtils.isTypeAir(targetBlock)) {
            BlockSoundGroup soundGroup = targetBlock.getDefaultState().getSoundGroup();
            mc.player.playSound(soundGroup.getBreakSound(), soundGroup.getVolume(), soundGroup.getPitch());
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

        if ((BlockUtils.blockEqualsAndAirCheck(currentBlock, targetBlock) || task.getBlueprintTask().isFiller()) && !state.isReplaceable()) {
            tools.setBlocksPlaced(tools.getBlocksPlaced() + 1);

            if (tools.getAdaptivePlaceDelay().get() && placeHandler.getExtraPlaceDelay() > 0) {
                if (placeHandler.getExtraPlaceDelay() == 1) {
                    placeHandler.setExtraPlaceDelay(0);
                } else {
                    placeHandler.setExtraPlaceDelay(placeHandler.getExtraPlaceDelay() / 2);
                }
            }

            task.updateState(TaskState.DONE);
            return;
        }

        // break if target block is not the current block and target block is air
        if (BlockUtils.blockEqualsAndAirCheck(currentBlock, targetBlock) && BlockUtils.isTypeAir(targetBlock)) {
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

        Block mainBlock = tools.getMainBlock().get();
        Block fillerBlock = tools.getFillerBlock().get();

        Block targetBlock = task.getBlueprintTask().getTargetBlock();

        if (currentBlock.equals(Blocks.END_PORTAL_FRAME) || currentBlock.equals(Blocks.BEDROCK) || currentBlock.equals(Blocks.NETHER_PORTAL) || currentBlock.equals(Blocks.END_PORTAL)) {
            task.updateState(TaskState.DONE);
            return;
        }

        // one block below player
        if (task.getBlockPos().equals(mc.player.getBlockPos().offset(Direction.DOWN))) {
            task.updateState(TaskState.DONE);
            return;
        }

//        if (targetBlock == fillerBlock) {
//
//            if (currentBlock == mainBlock || !BlockUtils.canPlace(task.getBlockPos(), true)) {
//                task.updateState(TaskState.DONE);
//                return;
//            }
//            return;
//        }
//
//        if (targetBlock == tools.getFillerBlock().get() && state.isAir() || !BlockUtils.canPlace(task.getBlockPos(), true)) {
//            task.updateState(TaskState.DONE);
//            return;
//        }
//
//        if (targetBlock == mainBlock && currentBlock == mainBlock) {
//            task.updateState(TaskState.DONE);
//            return;
//        }

        if (targetBlock == fillerBlock) {
            if (BlockUtils.blockEqualsAndAirCheck(currentBlock, targetBlock)) {
                task.updateState(TaskState.DONE);
                return;
            }
        }

        if (targetBlock == mainBlock) {
            if (BlockUtils.blockEqualsAndAirCheck(currentBlock, targetBlock)) {
                task.updateState(TaskState.DONE);
                return;
            }
        }


        if (BlockUtils.isTypeAir(currentBlock)) {
            if (BlockUtils.isTypeAir(targetBlock)) {
                task.updateState(TaskState.BROKEN);
            } else {
                task.updateState(TaskState.PLACE);
            }
            return;
        }

        if (LiquidUtils.isLiquid(state)) {
            liquidHandler.updateTask(task);
            return;
        }

        if (check) return;
        if (!mc.player.isOnGround()) return;
        if (liquidHandler.handleLiquid(task)) return;

        mineBlock(task);
    }

    private void doPlace(BlockTask task, boolean check) {
        if (mc.world == null || mc.player == null) return;
        BlockState state = mc.world.getBlockState(task.getBlockPos());
        Block block = state.getBlock();
        Block targetBlock = task.getBlueprintTask().getTargetBlock();

        // LIQUID
        if (task.getTaskState() == TaskState.LIQUID && !LiquidUtils.isLiquid(state)) {
            task.updateState(TaskState.DONE);
            return;
        }

        if (block.equals(tools.getMainBlock().get()) && targetBlock.equals(tools.getMainBlock().get())) {
            task.updateState(TaskState.PLACED);
            return;
        }

        if (targetBlock.equals(tools.getFillerBlock().get()) && block.equals(tools.getFillerBlock().get())) {
            task.updateState(TaskState.PLACED);
            return;
        }

        if (BlockUtils.isTypeAir(targetBlock)) {
            if (!LiquidUtils.isLiquid(state)) {
                if (!BlockUtils.isTypeAir(block)) {
                    task.updateState(TaskState.BREAK);
                } else {
                    task.updateState(TaskState.BROKEN);
                }
                return;
            }
        }

//        if (!block.equals(targetBlock)) {
//            if (!block.equals(Blocks.AIR)) {
//                task.updateState(TaskState.BREAK);
//                return;
//            }
//        }

        if (check) return;

        if (!meteordevelopment.meteorclient.utils.world.BlockUtils.canPlace(task.getBlockPos(), true)) {
            return;
        }

        placeBlock(task);
    }

    private void doPendingBreak(BlockTask task) {
        task.onStuck();
    }

    private void doPendingPlace(BlockTask task) {
        Objects.requireNonNull(mc.world, "world should not be null");

        BlockPos pos = task.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);

        Block block = state.getBlock();
        Block targetBlock = task.getBlueprintTask().getTargetBlock();

        if (task.getTaskState() == TaskState.LIQUID && !LiquidUtils.isLiquid(state)) {
            task.updateState(TaskState.DONE);
            return;
        }

        if (block.equals(tools.getMainBlock().get()) && targetBlock.equals(tools.getMainBlock().get())) {
            task.updateState(TaskState.PLACED);
            return;
        }

        if (targetBlock.equals(tools.getFillerBlock().get()) && block.equals(tools.getFillerBlock().get())) {
            task.updateState(TaskState.PLACED);
            return;
        }

        if (BlockUtils.isTypeAir(targetBlock)) {
            if (!LiquidUtils.isLiquid(state)) {
                if (!BlockUtils.isTypeAir(block)) {
                    task.updateState(TaskState.BREAK);
                } else {
                    task.updateState(TaskState.BROKEN);
                }
            }
        }
    }

    private void mineBlock(BlockTask task) {
        BlockPos pos = task.getBlockPos();

        if (tools.getRotation().get().mine && tools.getRotateCamera().get() && mc.player != null) {
            mc.player.setYaw((float) Rotations.getYaw(task.getBlockPos()));
            mc.player.setPitch((float) Rotations.getPitch(task.getBlockPos()));
        }

        if (tools.getRotation().get().mine) {
            Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), () -> {});
        }

        breakHandler.mine(task);
    }

    private void placeBlock(BlockTask task) {
        placeHandler.place(task);
    }

    private boolean place(BlockTask task, int slot) {
        if (tools.getRotation().get().place && tools.getRotateCamera().get() && mc.player != null) {
            mc.player.setPitch((float) Rotations.getPitch(task.getBlockPos()));
            mc.player.setYaw((float) Rotations.getYaw(task.getBlockPos()));
        }
        return meteordevelopment.meteorclient.utils.world.BlockUtils.place(task.getBlockPos(), Hand.MAIN_HAND, slot, tools.getRotation().get().place, 0, true, true, false);
    }

}
