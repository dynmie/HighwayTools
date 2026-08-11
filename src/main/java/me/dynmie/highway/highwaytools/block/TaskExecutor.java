package me.dynmie.highway.highwaytools.block;

import me.dynmie.highway.highwaytools.container.ContainerTask;
import me.dynmie.highway.highwaytools.handler.BreakHandler;
import me.dynmie.highway.highwaytools.handler.InventoryHandler;
import me.dynmie.highway.highwaytools.handler.InventoryManager;
import me.dynmie.highway.highwaytools.handler.LiquidHandler;
import me.dynmie.highway.highwaytools.handler.PlaceHandler;
import me.dynmie.highway.highwaytools.pathing.BaritonePathfinder.MovementState;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.LiquidUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public class TaskExecutor {

    private static final Minecraft mc = Minecraft.getInstance();

    private final HighwayTools tools;
    private final BreakHandler breakHandler;
    private final InventoryHandler inventoryHandler;
    private final InventoryManager inventoryManager;
    private final LiquidHandler liquidHandler;
    private final PlaceHandler placeHandler;

    /** Set while a container click is in flight; gates the queue to one click per tick. */
    private boolean clickQueueBusy = false;

    public TaskExecutor(HighwayTools tools, BreakHandler breakHandler, InventoryHandler inventoryHandler, LiquidHandler liquidHandler, PlaceHandler placeHandler) {
        this.tools = tools;
        this.breakHandler = breakHandler;
        this.inventoryHandler = inventoryHandler;
        this.inventoryManager = tools.getInventoryManager();
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
        // Lambda's doImpossiblePlace: when a place task is truly impossible (no reachable
        // support), walk the player forward onto the built floor so the front becomes
        // reachable again — exactly Lambda's `shouldBridge()` gate plus the two extra
        // conditions (no restock in flight, and the player is within 1 block of the front).
        if (tools.getPathfinder().shouldBridge()
            && tools.getPathfinder().getMovementState() != MovementState.BRIDGE
            && tools.getPathfinder().getMovementState() != MovementState.RESTOCK
            && mc.player != null
            && mc.player.position().distanceTo(tools.getCurrentPosition().getCenter()) < 1) {
            tools.getPathfinder().setMovementState(MovementState.BRIDGE);
            return;
        }

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

        if (tools.getIgnoreList().isIgnored(currentBlock)) {
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

        // non-conservative: only the clicked face needs to be clear, not the whole shape.
        // highway front/AIR and railings/floor-into-existing blocks must be placeable.
        if (!BlockUtils.canPlace(task.getBlockPos(), false)) {
            return;
        }

        placeBlock(task);
    }

    private void doPendingBreak(BlockTask task) {
        if (mc.level == null || mc.player == null) return;

        BlockState state = mc.level.getBlockState(task.getBlockPos());

        // server confirmed the break — block became air
        if (me.dynmie.highway.utils.BlockUtils.isTypeAir(state.getBlock())) {
            // [HT-DIAG] ack landed -> block is air
            System.out.println("[HT-DIAG] PENDING_BREAK -> air (ack ok) " + task.getBlockPos()
                + " stuck=" + task.getStuckTicks());
            // The tool stays held through the whole break (see BreakHandler) and is only
            // released when the next task swaps it away (e.g. PlaceHandler's swap/swapBack).
            // No swapBack here: with several tasks waiting in PENDING_BREAK, restoring one
            // would swap the tool away mid-break for another.
            task.updateState(TaskState.BROKEN);
            return;
        }

        task.onStuck();

        // server never confirmed — give up and re-mine
        if (task.getStuckTicks() >= task.getTaskState().getStuckTimeout()) {
            // [HT-DIAG] ack NEVER landed -> re-mine (this is the slow path if it fires a lot)
            System.out.println("[HT-DIAG] PENDING_BREAK TIMEOUT -> BREAK (ack missing) "
                + task.getBlockPos() + " state=" + state.getBlock());
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

    /**
     * Drives the container restock lifecycle:
     * PLACE -> OPEN_CONTAINER -> RESTOCK -> BREAK -> DONE.
     * The container is placed remotely (pure packets, no mouse grab), its slots are pulled
     * from one at a time with server-transaction-confirmed clicks, and the container is
     * broken afterwards (AutoObsidian) so the drops are collected.
     */
    public void doContainerTask(ContainerTask task) {
        if (mc.player == null) return;

        switch (task.taskState) {
            case PLACE -> {
                // ender chest for AutoObsidian, otherwise the shulker holding the restock item
                Item item = task.destroy ? Items.ENDER_CHEST : firstShulkerItem(task);
                int slot = inventoryHandler.prepareItemInHotbar(item);
                if (slot == -1) {
                    task.taskState = TaskState.DONE;
                    return;
                }
                BlockUtils.place(task.blockPos, InteractionHand.MAIN_HAND, slot, false, 0, true, true, false);
                task.taskState = TaskState.OPEN_CONTAINER;
            }
            case OPEN_CONTAINER -> {
                if (!openContainer(task)) {
                    task.stuckTicks++;
                    if (task.stuckTicks > 20) task.taskState = TaskState.DONE;
                } else {
                    task.taskState = TaskState.RESTOCK;
                }
            }
            case RESTOCK -> {
                // pull one stack per tick until the item is satisfied
                if (pullOneStack(task)) {
                    task.stuckTicks = 0;
                    if (task.stopPull && !shouldKeepPulling(task)) {
                        task.taskState = TaskState.BREAK;
                        closeContainer();
                    }
                } else if (++task.stuckTicks > 60) {
                    // no progress for ~3s: container never opened or ran out of space; bail
                    task.taskState = TaskState.BREAK;
                    closeContainer();
                }
            }
            case BREAK -> {
                BlockUtils.breakBlock(task.blockPos, true);
                task.taskState = TaskState.DONE;
                BlockTaskManager.getInstance().containerTask = null;
            }
            case DONE -> {
                BlockTaskManager.getInstance().containerTask = null;
            }
            default -> {}
        }
    }

    /**
     * The shulker to place: prefer the one holding {@code task.item} (fewest of it), otherwise
     * fall back to any shulker box in the inventory.
     */
    private Item firstShulkerItem(ContainerTask task) {
        ItemStack shulker = inventoryManager.getShulkerWith(task.item);
        if (!shulker.isEmpty()) return shulker.getItem();
        if (mc.player == null) return Items.AIR;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) {
                return stack.getItem();
            }
        }
        return Items.AIR;
    }

    /**
     * Opens the container with a raw {@link ServerboundUseItemOnPacket} instead of
     * {@code mc.gameMode.useItemOn}, so the client never grabs the mouse to open the GUI itself.
     */
    private boolean openContainer(ContainerTask task) {
        if (mc.getConnection() == null) return false;
        BlockPos pos = task.blockPos;
        Direction side = Direction.UP;
        Vec3 hitVec = Vec3.atCenterOf(pos).add(0, 0.5, 0);
        BlockHitResult hit = new BlockHitResult(hitVec, side, pos, false);
        mc.getConnection().send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, 0));
        return true;
    }

    private void closeContainer() {
        if (mc.getConnection() == null || mc.player == null) return;
        mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
        mc.player.containerMenu = mc.player.inventoryMenu;
    }

    /**
     * Pulls one stack of {@code task.item} out of the container per tick. The click is sent
     * as a raw {@link ServerboundContainerClickPacket} and gated by {@link #clickQueueBusy};
     * confirmation is polled from {@code containerMenu.getCarried()} (simplified, see plan).
     */
    private boolean pullOneStack(ContainerTask task) {
        if (clickQueueBusy) {
            // QUICK_MOVE leaves the carried item empty, so this gates to one click per tick
            if (mc.player.containerMenu.getCarried().isEmpty()) {
                clickQueueBusy = false;
            }
            return false;
        }
        if (mc.getConnection() == null || mc.player == null) return false;

        // wait until the container menu is actually open before clicking its slots
        if (mc.player.containerMenu.containerId == 0) return false;

        for (int slot = 0; slot < 27; slot++) {
            ItemStack stack = mc.player.containerMenu.getSlot(slot).getItem();
            if (stack.getItem().equals(task.item)) {
                mc.getConnection().send(new ServerboundContainerClickPacket(
                    mc.player.containerMenu.containerId,
                    mc.player.containerMenu.getStateId(),
                    (short) slot,
                    (byte) 0,
                    ContainerInput.QUICK_MOVE,
                    new Int2ObjectOpenHashMap<>(),
                    HashedStack.create(mc.player.containerMenu.getCarried(), mc.getConnection().decoratedHashOpsGenenerator())));
                clickQueueBusy = true;
                task.stacksPulled++;
                task.stopPull = true;
                return true;
            }
        }
        // no more of the item in the container
        task.taskState = TaskState.BREAK;
        return false;
    }

    private boolean shouldKeepPulling(ContainerTask task) {
        // stop when the inventory has no room left for another stack
        if (inventoryManager.freeSlots() <= 0) return false;
        // fastFill: keep pulling for tools and the main building material
        if (task.item.equals(Items.DIAMOND_PICKAXE)) return true;
        return task.item.equals(tools.getMainBlock().get().asItem());
    }

}
