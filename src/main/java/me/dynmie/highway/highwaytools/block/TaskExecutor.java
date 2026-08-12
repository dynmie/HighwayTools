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
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
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

    /** The container-menu {@code stateId} when the last click was sent; an ack advances it. */
    private int lastClickStateId = -1;

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
            // The tool stays held through the whole break (see BreakHandler) and is only
            // released when the next task swaps it away (e.g. PlaceHandler's swap).
            // No swapBack here: with several tasks waiting in PENDING_BREAK, restoring one
            // would swap the tool away mid-break for another.
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

    /**
     * Drives the container restock lifecycle:
     * PLACE -> OPEN_CONTAINER -> RESTOCK -> BREAK -> DONE.
     * The container is placed remotely (pure packets, no mouse grab), its slots are pulled
     * from one at a time with server-transaction-confirmed clicks, and the container is
     * broken afterwards (AutoObsidian) so the drops are collected.
     *
     * <p>Only proceeds to open/restock when the player is close to the container — the
     * pathfinder is in RESTOCK state while this runs, so it paths to the container and pauses
     * within reach before the interactions begin.
     */
    public void doContainerTask(ContainerTask task) {
        if (mc.player == null) return;

        switch (task.taskState) {
            case PLACE -> {
                // wait until the pathfinder has brought the player within reach of the container
                if (mc.player.position().distanceTo(task.blockPos.getCenter()) > 3.5) {
                    task.stuckTicks++;
                    // can't get close enough — bail so the module can retry the restock later
                    if (task.stuckTicks > 100) task.taskState = TaskState.DONE;
                    return;
                }
                task.stuckTicks = 0;

                // place the container block this task represents — Lambda's targetBlock: the
                // ender chest for grind/dispatch, or the shulker box holding the restock item
                Item item = task.containerBlock.asItem();
                int slot = inventoryHandler.prepareItemInHotbar(item);
                if (slot == -1) {
                    task.taskState = TaskState.DONE;
                    return;
                }
                BlockUtils.place(task.blockPos, InteractionHand.MAIN_HAND, slot, false, 0, true, true, false);
                // wait until the container block is actually present before moving on (the same
                // gate Lambda's doPlaced uses — it only advances when the placed block is real)
                if (mc.level.getBlockState(task.blockPos).getBlock() instanceof ShulkerBoxBlock
                    || mc.level.getBlockState(task.blockPos).getBlock() == Blocks.ENDER_CHEST) {
                    // Lambda doPlaced: destroy=true (AutoObsidian grind) breaks the chest right
                    // after placing — it never opens or restocks from it, it just mines the 8
                    // obsidian the chest drops. Only the restock paths open the container.
                    task.taskState = task.destroy ? TaskState.BREAK : TaskState.OPEN_CONTAINER;
                }
            }
            case OPEN_CONTAINER -> {
                // only open when close enough to interact with the container
                if (mc.player.position().distanceTo(task.blockPos.getCenter()) > 3.5) {
                    return; // pathfinder is still walking over
                }
                if (!openContainer(task)) {
                    task.stuckTicks++;
                    if (task.stuckTicks > 20) task.taskState = TaskState.DONE;
                } else {
                    task.taskState = TaskState.RESTOCK;
                }
            }
            case RESTOCK -> {
                // leave-empty-shulkers: if the shulker was already empty (nothing pulled and no
                // usable material left inside), close and leave it placed — the ONLY path that
                // leaves a container in the world (Lambda TaskExecutor.kt:109-130). Everything
                // else breaks the container after restocking so the box is picked back up.
                if (tools.getLeaveEmptyShulkers().get() && task.isShulker() && shulkerIsEmpty()) {
                    task.taskState = TaskState.DONE;
                    closeContainer();
                    return;
                }

                // pull one stack per tick until the item is satisfied
                if (pullOneStack(task)) {
                    task.stuckTicks = 0;
                    if (task.stopPull && !shouldKeepPulling(task)) {
                        // Lambda TaskExecutor.kt:137-142 — every container is broken after the
                        // restock (not just destroy=true ones), so a shulker box is picked back
                        // up instead of left behind. leaveEmptyShulkers above is the exception.
                        closeContainer();
                        task.taskState = TaskState.BREAK;
                    }
                } else if (++task.stuckTicks > 60) {
                    // no progress for ~3s: container never opened or ran out of space; bail
                    closeContainer();
                    task.taskState = TaskState.BREAK;
                }
            }
            case BREAK -> {
                // every container reaches BREAK after restocking (Lambda TaskExecutor.kt:137-142),
                // and the AutoObsidian grind reaches it right after placing. Prepare the right
                // tool for what is actually placed: a shulker box breaks by hand (it drops itself
                // regardless of tool), an ender chest needs a non-silk-touch pickaxe (obsidian is
                // unbreakable with a bare hand and silk-touch would keep the chest block instead
                // of yielding the obsidian we grind).
                if (task.isShulker()) {
                    inventoryHandler.prepareToolInHotbar(Blocks.OBSIDIAN.defaultBlockState());
                } else {
                    inventoryHandler.prepareToolInHotbar(task.containerBlock.defaultBlockState());
                }

                // start/continue breaking; wait until the container is actually gone (air)
                BlockUtils.breakBlock(task.blockPos, true);
                if (containerIsAir(task.blockPos)) {
                    task.stuckTicks = 0; // start the pickup render-delay counter fresh
                    task.taskState = task.collect ? TaskState.PICKUP : TaskState.DONE;
                }
            }
            case PICKUP -> {
                // give the drops a few ticks to render after the block breaks before deciding
                // they're all collected (the break produces them server-side)
                if (task.stuckTicks < 5) {
                    task.stuckTicks++;
                    return;
                }

                // walk to the drops and pick them up; eject trash if the inventory is genuinely
                // full — Lambda's doPickup gate (`firstEmpty() == null`). The dropped item can't
                // be re-picked for 20 ticks (throw pickup-delay), so this is naturally bounded
                // and needs no separate timer.
                if (inventoryManager.isInventoryFull()) {
                    int ejectSlot = inventoryManager.findEjectSlot();
                    if (ejectSlot != -1) {
                        meteordevelopment.meteorclient.utils.player.InvUtils.drop().slot(ejectSlot);
                    }
                }
                // once no drops remain (collected), finish
                if (inventoryManager.getCollectingPosition(task.dropItem(), task.blockPos) == null) {
                    task.taskState = TaskState.DONE;
                }
            }
            case DONE -> {
                BlockTaskManager.getInstance().containerTask = null;
            }
            default -> {}
        }
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
        if (mc.player == null) return;
        // player.closeContainer() sends the close packet AND closes the GUI screen
        // (clientSideCloseContainer -> mc.setScreen(null)). Sending only the packet left the
        // container menu on screen, which swallowed the subsequent break/place input.
        mc.player.closeContainer();
        clickQueueBusy = false; // no in-flight click can survive a menu close
        lastClickStateId = -1;
    }

    /** True when the container block at {@code pos} is gone (air) — i.e. the break finished. */
    private boolean containerIsAir(BlockPos pos) {
        if (mc.level == null) return false;
        return me.dynmie.highway.utils.BlockUtils.isTypeAir(mc.level.getBlockState(pos).getBlock());
    }

    /** True when the currently-open shulker menu has no non-empty item slots (0..26). */
    private boolean shulkerIsEmpty() {
        if (mc.player == null || mc.player.containerMenu.containerId == 0) return false;
        for (int slot = 0; slot < 27; slot++) {
            if (!mc.player.containerMenu.getSlot(slot).getItem().isEmpty()) return false;
        }
        return true;
    }

    /**
     * Pulls one stack of {@code task.item} out of the container per tick, mirroring Lambda's
     * {@code Inventory.moveToInventory}: QUICK_MOVE into a mergeable partial stack, else SWAP
     * the container item into a free/empty hotbar slot, else PICKUP the container item into the
     * carry and set it down in a free/empty main slot. Ejectable (trash) slots are used as
     * swap targets first, so ejectables are PRESERVED (swapped into the container) rather than
     * dropped — Lambda never throws away ejectables during a restock pull.
     *
     * <p>Each step is a single {@link ServerboundContainerClickPacket} gated by
     * {@link #clickQueueBusy}. The ack is the server's {@code stateId} advancing past the click
     * (the client's {@code stateId} only moves on the server ack — {@code handleContainerSetSlot}
     * passes the packet's stateId into {@code setItem}). Sending a second click before the ack
     * would carry a stale stateId and a changedSlots map the server hasn't confirmed.
     */
    private boolean pullOneStack(ContainerTask task) {
        if (clickQueueBusy) {
            // ack = the server advanced the stateId past the click we sent. Also treat an empty
            // carry as ack for QUICK_MOVE/SWAP (they never touch the carry), so a stateId
            // change isn't strictly required.
            if (mc.player.containerMenu.getStateId() != lastClickStateId
                || mc.player.containerMenu.getCarried().isEmpty()) {
                clickQueueBusy = false;
            }
            return false;
        }
        if (mc.getConnection() == null || mc.player == null) return false;

        // wait until the container menu is actually open before clicking its slots
        if (mc.player.containerMenu.containerId == 0) return false;

        // finish any in-progress click sequence (one confirmed click per tick)
        if (!task.clickStack.isEmpty()) {
            sendContainerClick(task.clickStack.poll());
            // the second click of a PICKUP carry move was just sent — the stack is now (pending
            // ack) in the inventory. Clear the marker so the next findMove treats it as a fresh
            // pull; count it once here.
            if (task.clickStack.isEmpty() && task.pickupCarry) {
                task.pickupCarry = false;
                task.stacksPulled++;
                task.stopPull = true;
            }
            return false;
        }

        int[] move = findMove(task);
        if (move != null) {
            // a direct single-click move exists (QUICK_MOVE merge / SWAP into a free-or-ejectable
            // hotbar slot) — the pull succeeds, and next tick continues with fastFill
            sendContainerClick(move);
            task.stacksPulled++;
            task.stopPull = true;
            return true;
        }

        // no direct move: place a full container stack into a free/empty main slot via PICKUP
        // (container -> carry -> main slot). The sequence is queued and driven one click per tick.
        int origin = firstSlot(task);
        int dest = mainFreeOrEmpty(task);
        if (origin != -1 && dest != -1 && !task.pickupCarry) {
            task.pickupCarry = true;
            task.clickStack.add(new int[]{origin, 0, 0});
            task.clickStack.add(new int[]{dest, 0, 0});
            sendContainerClick(task.clickStack.poll());
            return false; // the pull completes only when the second click lands
        }

        // no more of the item in the container — close the menu and break the container so the
        // box/chest is picked back up (Lambda TaskExecutor.kt:164-167). leaveEmptyShulkers
        // already handled the "leave placed" case before pulling began.
        closeContainer();
        task.taskState = TaskState.BREAK;
        return false;
    }

    /** Sends one container click, marking the queue busy until the server acks it. */
    private void sendContainerClick(int[] click) {
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundContainerClickPacket(
            mc.player.containerMenu.containerId,
            mc.player.containerMenu.getStateId(),
            (short) click[0],
            (byte) click[1],
            ContainerInput.values()[click[2]],
            new Int2ObjectOpenHashMap<>(),
            HashedStack.create(mc.player.containerMenu.getCarried(), mc.getConnection().decoratedHashOpsGenenerator())));
        clickQueueBusy = true;
        lastClickStateId = mc.player.containerMenu.getStateId();
    }

    /**
     * The single-click move for this pull, or null. Order mirrors Lambda's moveToInventory:
     * QUICK_MOVE into a matching partial that has room for the whole stack, then SWAP into a
     * free/empty hotbar slot. Main slots go through the PICKUP carry fallback.
     *
     * <p>Trash is only ever a last resort: the pull fills genuinely empty slots first and only
     * swaps away an eject-list item when no empty slot remains (and even then, only a hotbar
     * slot — SWAP cannot target a main slot in modern MC). This is the "fill the inventory, only
     * then eject real trash" behavior.
     */
    private int[] findMove(ContainerTask task) {
        if (mc.player == null) return null;
        int origin = firstSlot(task);
        if (origin == -1) return null;
        ItemStack originStack = mc.player.containerMenu.getSlot(origin).getItem();

        for (int slot = 27; slot < 63; slot++) {
            ItemStack stack = mc.player.containerMenu.getSlot(slot).getItem();
            if (!stack.isEmpty()
                && stack.getItem().equals(task.item)
                && stack.getCount() + originStack.getCount() <= stack.getMaxStackSize()) {
                return new int[]{origin, 0, ContainerInput.QUICK_MOVE.ordinal()};
            }
        }
        // SWAP only targets hotbar slots (button is an inventory hotbar index 0..8).
        // Prefer empty hotbar slots; only fall back to eject-list trash when nothing is empty.
        for (int slot = 54; slot < 63; slot++) {
            if (mc.player.containerMenu.getSlot(slot).getItem().isEmpty()) {
                return new int[]{origin, hotbarIndex(slot), ContainerInput.SWAP.ordinal()};
            }
        }
        for (int slot = 54; slot < 63; slot++) {
            if (inventoryManager.isEjectableSlot(slot)) {
                return new int[]{origin, hotbarIndex(slot), ContainerInput.SWAP.ordinal()};
            }
        }
        return null;
    }

    /** The first container slot (0..26) holding {@code task.item}. */
    private int firstSlot(ContainerTask task) {
        if (mc.player == null) return -1;
        for (int slot = 0; slot < 27; slot++) {
            if (mc.player.containerMenu.getSlot(slot).getItem().getItem().equals(task.item)) return slot;
        }
        return -1;
    }

    /** A free (empty) main-inventory slot (menu 27..53) to set a pulled stack down in. */
    private int mainFreeOrEmpty(ContainerTask task) {
        if (mc.player == null) return -1;
        for (int slot = 27; slot < 54; slot++) {
            if (mc.player.containerMenu.getSlot(slot).getItem().isEmpty()) return slot;
        }
        return -1;
    }

    /** The inventory hotbar index (0..8) a menu slot maps to, or -1 if it is not a hotbar slot. */
    private int hotbarIndex(int menuSlot) {
        if (menuSlot >= 54 && menuSlot <= 62) return menuSlot - 54;
        return -1;
    }

    private boolean shouldKeepPulling(ContainerTask task) {
        // fast-fill off: Lambda pulls exactly one stack then breaks the container
        if (!tools.getFastFill().get()) return false;
        // stop when the inventory has no room left for another stack
        if (inventoryManager.freeSlots() <= 0) return false;
        // fastFill: keep pulling for tools and the main building material
        if (task.item.equals(Items.DIAMOND_PICKAXE)) return true;
        return task.item.equals(tools.getMainBlock().get().asItem());
    }

}
