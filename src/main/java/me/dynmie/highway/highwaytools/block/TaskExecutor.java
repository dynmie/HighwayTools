package me.dynmie.highway.highwaytools.block;

import me.dynmie.highway.highwaytools.handler.BreakHandler;
import me.dynmie.highway.highwaytools.handler.InventoryHandler;
import me.dynmie.highway.highwaytools.handler.LiquidHandler;
import me.dynmie.highway.highwaytools.handler.PlaceHandler;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.LiquidUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

public class TaskExecutor {

    private static final Minecraft mc = Minecraft.getInstance();

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
            case IMPOSSIBLE_PLACE -> doImpossiblePlace(task);
            default -> {}
        }
    }

    private void doImpossiblePlace(BlockTask task) {
        // No reachable support right now. Re-request the sequence next tick;
        // if the player moved or the pathfinder advanced, a support may exist.
        // After the stuck threshold, drop the task (mark DONE) so the bot isn't stuck forever.
        task.onStuck();
        if (task.getStuckTicks() > 20) {
            task.updateState(TaskState.DONE);
        } else {
            task.updateState(TaskState.PLACE);  // retry placement (re-runs searcher)
        }
    }

    private void doBreaking(BlockTask task, boolean check) {
        if (mc.level == null || mc.player == null) return;
        BlockState state = mc.level.getBlockState(task.getBlockPos());
        Block block = state.getBlock();

        if (me.dynmie.highway.utils.BlockUtils.isTypeAir(block)) {
            task.updateState(TaskState.BROKEN);
            return;
        }

        // check liquid
        if (LiquidUtils.isLiquid(state)) {
            liquidHandler.updateTask(task);
            return;
        }

        if (!mc.player.onGround()) return;
        if (check) return;

        mineBlock(task);
    }

    private void doBroken(BlockTask task) {

        if (mc.level == null) return;
        Block block = mc.level.getBlockState(task.getBlockPos()).getBlock();

        if (!me.dynmie.highway.utils.BlockUtils.isTypeAir(block)) {
            task.updateState(TaskState.BREAK);
            return;
        }

        Block targetBlock = task.getBlueprintTask().getTargetBlock();
        if (me.dynmie.highway.utils.BlockUtils.isTypeAir(targetBlock)) {
            SoundType soundType = targetBlock.defaultBlockState().getSoundType();
            mc.player.playSound(soundType.getBreakSound(), soundType.getVolume(), soundType.getPitch());
            task.updateState(TaskState.DONE);
        } else {
            tools.setBlocksBroken(tools.getBlocksBroken() + 1);
            task.updateState(TaskState.PLACE);
        }

    }

    private void doPlaced(BlockTask task) {
        if (mc.level == null) return;
        BlockState state = mc.level.getBlockState(task.getBlockPos());
        Block currentBlock = state.getBlock();
        Block targetBlock = task.getBlueprintTask().getTargetBlock();

        if ((me.dynmie.highway.utils.BlockUtils.blockEqualsAndAirCheck(currentBlock, targetBlock) || task.getBlueprintTask().isFiller()) && !state.canBeReplaced()) {
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
        if (me.dynmie.highway.utils.BlockUtils.blockEqualsAndAirCheck(currentBlock, targetBlock) && me.dynmie.highway.utils.BlockUtils.isTypeAir(targetBlock)) {
            task.updateState(TaskState.BREAK);
            return;
        }

        // break if target block is air and current block is not air
        if (targetBlock.defaultBlockState().isAir() && !currentBlock.defaultBlockState().isAir()) {
            task.updateState(TaskState.BREAK);
            return;
        }

//        tools.info("placed");

        task.updateState(TaskState.PLACE);
    }

    private void doBreak(BlockTask task, boolean check) {
        if (mc.level == null || mc.player == null) return;
        BlockState state = mc.level.getBlockState(task.getBlockPos());
        Block currentBlock = state.getBlock();

        Block mainBlock = tools.getMainBlock().get();
        Block fillerBlock = tools.getFillerBlock().get();

        Block targetBlock = task.getBlueprintTask().getTargetBlock();

        if (currentBlock.equals(Blocks.END_PORTAL_FRAME) || currentBlock.equals(Blocks.BEDROCK) || currentBlock.equals(Blocks.NETHER_PORTAL) || currentBlock.equals(Blocks.END_PORTAL)) {
            task.updateState(TaskState.DONE);
            return;
        }

        // one block below player
        if (task.getBlockPos().equals(mc.player.blockPosition().relative(Direction.DOWN))) {
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
            if (me.dynmie.highway.utils.BlockUtils.blockEqualsAndAirCheck(currentBlock, targetBlock)) {
                task.updateState(TaskState.DONE);
                return;
            }
        }

        if (targetBlock == mainBlock) {
            if (me.dynmie.highway.utils.BlockUtils.blockEqualsAndAirCheck(currentBlock, targetBlock)) {
                task.updateState(TaskState.DONE);
                return;
            }
        }


        if (me.dynmie.highway.utils.BlockUtils.isTypeAir(currentBlock)) {
            if (me.dynmie.highway.utils.BlockUtils.isTypeAir(targetBlock)) {
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
        if (!mc.player.onGround()) return;
        if (liquidHandler.handleLiquid(task)) return;

        mineBlock(task);
    }

    private void doPlace(BlockTask task, boolean check) {
        if (mc.level == null || mc.player == null) return;
        BlockState state = mc.level.getBlockState(task.getBlockPos());
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

        if (me.dynmie.highway.utils.BlockUtils.isTypeAir(targetBlock)) {
            if (!LiquidUtils.isLiquid(state)) {
                if (!me.dynmie.highway.utils.BlockUtils.isTypeAir(block)) {
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

        if (!BlockUtils.canPlace(task.getBlockPos(), true)) {
            return;
        }

        placeBlock(task);
    }

    private void doPendingBreak(BlockTask task) {
        if (mc.level == null || mc.player == null) return;

        BlockState state = mc.level.getBlockState(task.getBlockPos());

        // server confirmed the break — block became air
        if (me.dynmie.highway.utils.BlockUtils.isTypeAir(state.getBlock())) {
            task.updateState(TaskState.BROKEN);
            return;
        }

        task.onStuck();

        // server never confirmed — give up and re-mine
        if (task.getStuckTicks() >= task.getTaskState().getStuckTimeout()) {
            task.updateState(TaskState.BREAK);
        }
    }

    private void doPendingPlace(BlockTask task) {
        Objects.requireNonNull(mc.level, "world should not be null");

        BlockPos pos = task.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

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

        if (me.dynmie.highway.utils.BlockUtils.isTypeAir(targetBlock)) {
            if (!LiquidUtils.isLiquid(state)) {
                if (!me.dynmie.highway.utils.BlockUtils.isTypeAir(block)) {
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
            mc.player.setYRot((float) Rotations.getYaw(task.getBlockPos()));
            mc.player.setXRot((float) Rotations.getPitch(task.getBlockPos()));
        }

        if (tools.getRotation().get().mine) {
            Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), () -> {});
        }

        breakHandler.mine(task);
    }

    private void placeBlock(BlockTask task) {
        placeHandler.place(task);
    }

}
