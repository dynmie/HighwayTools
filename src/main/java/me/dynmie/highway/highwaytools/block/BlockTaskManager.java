package me.dynmie.highway.highwaytools.block;

import me.dynmie.highway.highwaytools.blueprint.BlueprintTask;
import me.dynmie.highway.highwaytools.container.ContainerTask;
import me.dynmie.highway.highwaytools.handler.InventoryHandler;
import me.dynmie.highway.highwaytools.handler.InventoryManager;
import me.dynmie.highway.highwaytools.pathing.BaritonePathfinder;
import me.dynmie.highway.highwaytools.place.PlacementSearcher;
import me.dynmie.highway.highwaytools.place.PlacementStep;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.BlockUtils;
import me.dynmie.highway.utils.LiquidUtils;
import me.dynmie.highway.utils.LocationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class BlockTaskManager {

    private static BlockTaskManager instance;

    private final Minecraft mc = Minecraft.getInstance();

    private final Map<BlockPos, BlockTask> blockTasks = new ConcurrentHashMap<>();
    private final Set<BlockTask> sortedTasks = new ConcurrentSkipListSet<>(getBlockTaskComparator());

    private final HighwayTools tools;
    private final InventoryHandler inventoryHandler;
    private final InventoryManager inventoryManager;
    private final PlacementSearcher searcher;

    /** Active container restock task; runs inside the loop while non-null. */
    public ContainerTask containerTask = null;

    /** Remaining ender-chest grinds (each yields 8 obsidian) before the bot stops. */
    public int grindCycles = 0;

    public BlockTaskManager(HighwayTools tools, InventoryHandler inventoryHandler) {
        this.tools = tools;
        this.inventoryHandler = inventoryHandler;
        this.inventoryManager = tools.getInventoryManager();
        this.searcher = new PlacementSearcher(tools);
        instance = this;
    }

    public static BlockTaskManager getInstance() {
        return instance;
    }

    public void updateTasks() {
        tools.getBlueprintGenerator().generate();

        for (Map.Entry<BlockPos, BlueprintTask> entry : tools.getBlueprintGenerator().getBlueprint().entrySet()) {
            BlockPos pos = entry.getKey();
            BlueprintTask blueprintTask = entry.getValue();

            generateTask(pos, blueprintTask);
        }

        for (Map.Entry<BlockPos, BlockTask> entry : blockTasks.entrySet()) {
            if (entry.getValue().getTaskState() != TaskState.DONE) continue;

            if (tools.getCurrentPosition().getCenter().distanceTo(entry.getKey().getCenter()) > tools.getReach().get() + 2) {
                blockTasks.remove(entry.getKey());
            }
        }

    }

    public void generateTask(BlockPos pos, BlueprintTask blueprintTask) {
        if (mc.player == null) return;

        Vec3 eyePos = mc.player.getEyePosition();
        BlockState blockState = mc.player.level().getBlockState(pos);
        Block currentBlock = blockState.getBlock();

        // padding
        if (LocationUtils.isBehind(tools.getStartPosition(), pos, tools.getDirection())) {
            return;
        }


        if (eyePos.distanceTo(pos.getCenter()) >= tools.getReach().get() + 1) return;

        if (tools.getIgnoreList().isIgnored(currentBlock)) {
            BlockTask task = new BlockTask(pos, TaskState.DONE, blueprintTask);
            addTask(task);
            return;
        }

        // liquid fill (mirrors Lambda's liquid branch): only create the task when a reachable,
        // visible placement sequence exists. A liquid with no adjacent solid support, or whose
        // support face the player cannot see (blocked by blocks just placed in front), would
        // never resolve; skipping it lets the front advance so the fill is retried later.
        if (LiquidUtils.isLiquid(blockState)) {
            boolean illegal = tools.getIllegalPlacements().get();
            List<PlacementStep> seq = searcher.findSequence(
                mc.player.getEyePosition(), pos, tools.getPlacementSearch().get(), illegal);

            if (!seq.isEmpty()) {
                BlockTask task = new BlockTask(pos, TaskState.LIQUID, blueprintTask);
                task.setSequence(seq);
                addTask(task);
            }
            return;
        }

        // place
        if (blockState.canBeReplaced() && !BlockUtils.isTypeAir(blueprintTask.getTargetBlock())) {
            if (!meteordevelopment.meteorclient.utils.world.BlockUtils.canPlace(pos)) {
                BlockTask task = new BlockTask(pos, TaskState.DONE, blueprintTask);
                addTask(task);
                return;
            }

            BlockTask task = new BlockTask(pos, TaskState.PLACE, blueprintTask);
            addTask(task);
            return;
        }

        // break
        if (blueprintTask.isFiller()) {
            BlockTask task = new BlockTask(pos, TaskState.DONE, blueprintTask);
            addTask(task);
            return;
        }

        //
        if (blockState.isAir() && BlockUtils.isTypeAir(blueprintTask.getTargetBlock())) {
            BlockTask task = new BlockTask(pos, TaskState.DONE, blueprintTask);
            addTask(task);
            return;
        }

        if (blockState.getBlock().equals(blueprintTask.getTargetBlock())) {
            BlockTask task = new BlockTask(pos, TaskState.DONE, blueprintTask);
            addTask(task);
            return;
        }
        //

        BlockTask task = new BlockTask(pos, TaskState.BREAK, blueprintTask);
//        addTask(task);

        // TODO: check air???
        if (task.getEyeDistance() < tools.getReach().get()) {
            addTask(task);
        }
    }

    public void runTasks() {
        // Container restock has priority over the normal block loop, but the loop keeps
        // ticking (update-only pass) while the container is open, so task states, mining
        // progress and the overlay never freeze. Restock is triggered lazily per-task
        // (see InventoryHandler / BreakHandler), never as a gate in front of this loop.
        if (containerTask != null) {
            // while the container is placed/opened/pulled, path to it (RESTOCK); once it is
            // broken, path to the drops (PICKUP) so the bot walks over and collects them.
            tools.getPathfinder().setMovementState(
                containerTask.taskState == TaskState.PICKUP
                    ? BaritonePathfinder.MovementState.PICKUP
                    : BaritonePathfinder.MovementState.RESTOCK);
            tools.getTaskExecutor().doContainerTask(containerTask);
            updateBlockTasks();
            return;
        }

        // no container running — resume normal pathing
        if (tools.getPathfinder().getMovementState() == BaritonePathfinder.MovementState.RESTOCK) {
            tools.getPathfinder().setMovementState(BaritonePathfinder.MovementState.RUNNING);
        }

        updateBlockTasks();

        // decrement the shared wait counter each tick
        if (inventoryHandler.getWaitTicks() > 0) {
            inventoryHandler.decreaseWaitTicks(1);
        }

        // proactive restock: run when the module needs material/tools, not only on a failed
        // lookup. Mirrors Lambda's every-tick `handleRestock` gate.
        needsRestockCheck();

        sortedTasks.clear();
        sortedTasks.addAll(blockTasks.values());

        int actedIdx = 0;
        for (BlockTask task : sortedTasks) {
            // Placement pacing: only PLACE/PLACED tasks are held back while waitTicks > 0.
            // BREAK/PENDING_BREAK and everything else always act — the waitTicks counter is
            // set by PlaceHandler and must never throttle the break path.
            if ((task.getTaskState() == TaskState.PLACE || task.getTaskState() == TaskState.PLACED)
                && inventoryHandler.getWaitTicks() > 0) {
                continue;
            }

            tools.getTaskExecutor().doTask(task, false);

            if (task.getTaskState() == TaskState.DONE || task.getTaskState() == TaskState.BROKEN || task.getTaskState() == TaskState.PLACED) {
                actedIdx++;
                continue;
            }
            break;
        }
    }

    /** Update-only pass: let every task observe world state and advance its state machine without acting. */
    private void updateBlockTasks() {
        for (BlockTask task : blockTasks.values()) {
            tools.getTaskExecutor().doTask(task, true);

            if (tools.getShuffle().get()) task.shuffle();
        }
    }

    /**
     * Kicks off the restock lifecycle — a faithful port of Lambda's
     * {@code Container.handleRestock / handleEnderChest / dispatchEnderChest}.
     *
     * <p>Order of preference for obtaining {@code item}:
     * <ol>
     *   <li>prefer-ender-chests + obsidian → ender chest (grind or plain),</li>
     *   <li>a shulker box in the inventory holding {@code item},</li>
     *   <li>grind obsidian from an ender chest (AutoObsidian) when the budget allows,</li>
     *   <li>restock ender chests first when low on them,</li>
     *   <li>pull from the player's ender chest as a last resort.</li>
     * </ol>
     */
    private void startRestock() {
        Item item = inventoryManager.restockItem();

        // Case: prefer ender chests for obsidian
        if (tools.getPreferEnderChests().get() && item == Blocks.OBSIDIAN.asItem()) {
            handleEnderChest(item);
            return;
        }

        // Case 1: item is in a shulker in the inventory
        ItemStack shulker = inventoryManager.getShulkerWith(item);
        if (!shulker.isEmpty()) {
            BlockPos pos = inventoryManager.getRemotePos();
            if (pos != null) {
                containerTask = new ContainerTask(pos, TaskState.PLACE, item);
            } else {
                tools.error("Can't find possible container position (Case: 1)");
            }
            return;
        }

        handleEnderChest(item);
    }

    /** Grinds / pulls from ender chests — Lambda's {@code Container.handleEnderChest}. */
    private void handleEnderChest(Item item) {
        boolean obsidian = item == Blocks.OBSIDIAN.asItem();

        if (tools.getGrindObsidian().get() && obsidian) {
            // Case 2: desired item is Obsidian and grinding is allowed

            if (inventoryManager.countBlock(Blocks.ENDER_CHEST) <= tools.getSaveEnder().get()) {
                // not enough ender chests to spare — restock ender chests first
                replenishEnderChests();
                return;
            }

            if (inventoryManager.countBlock(Blocks.ENDER_CHEST) > tools.getSaveEnder().get()) {
                if (grindCycles > 0) {
                    BlockPos pos = inventoryManager.getRemotePos();
                    if (pos != null) {
                        containerTask = new ContainerTask(pos, TaskState.PLACE, Blocks.OBSIDIAN.asItem());
                        containerTask.destroy = true;
                        if (grindCycles > 1) containerTask.collect = false;
                        grindCycles--;
                    } else {
                        tools.error("Can't find possible container position (Case: 3)");
                    }
                } else {
                    // budget exhausted — make room by compressing partial stacks
                    int free = inventoryManager.freeSlots();
                    int cycles = (free - 1) * 8;
                    if (cycles > 0) {
                        grindCycles = cycles;
                    } else {
                        inventoryManager.zipInventory();
                    }
                }
            }
            return;
        }

        // Case 3: last hope is the ender chest
        if (!tools.getRestockFromEnderChest().get()) {
            tools.error("Insufficient material. Enable Storage Management > Restock From Ender Chest to pull from your ender chest.");
            return;
        }

        dispatchEnderChest(item);
    }

    /**
     * Last-hope restock from the player's ender chest — Lambda's {@code dispatchEnderChest}.
     * When the player has ender chests to spare, place one, open it and pull {@code desiredItem}
     * out of the shared ender-chest inventory. Only when out of chests does it go to a shulker
     * holding ender chests.
     */
    private void dispatchEnderChest(Item desiredItem) {
        if (inventoryManager.countBlock(Blocks.ENDER_CHEST) > tools.getSaveEnder().get()) {
            BlockPos pos = inventoryManager.getRemotePos();
            if (pos != null) {
                containerTask = new ContainerTask(pos, TaskState.PLACE, desiredItem);
                containerTask.destroy = true; // place the ender chest itself, break it after pulling
            } else {
                tools.error("Can't find possible container position (Case: 4)");
            }
            return;
        }

        replenishEnderChests();
    }

    /** Restock ender chests from a shulker holding them — Lambda's {@code dispatchEnderChest} else-branch. */
    private void replenishEnderChests() {
        ItemStack shulker = inventoryManager.getShulkerWith(Blocks.ENDER_CHEST.asItem());
        if (!shulker.isEmpty()) {
            BlockPos pos = inventoryManager.getRemotePos();
            if (pos != null) {
                containerTask = new ContainerTask(pos, TaskState.PLACE, Blocks.ENDER_CHEST.asItem());
            } else {
                tools.error("Can't find possible container position (Case: 5)");
            }
            return;
        }

        tools.error("No ender chest was found in inventory.");
    }

    /** Re-check restock need, e.g. from {@link InventoryHandler} when a needed item is missing. */
    public void needsRestockCheck() {
        if (containerTask == null && inventoryManager.needsRestock()) {
            startRestock();
        }
    }

    public void addTask(BlockTask blockTask) {
        BlockTask otherTask = blockTasks.get(blockTask.getBlockPos());
        if (otherTask == null) {
            blockTasks.put(blockTask.getBlockPos().mutable(), blockTask);
            return;
        }

        if (blockTask.getTaskState() == TaskState.LIQUID
            || otherTask.getTaskState() != blockTask.getTaskState()
            && (otherTask.getTaskState() == TaskState.DONE || otherTask.getTaskState() == TaskState.PLACE)
        ) {
            blockTasks.put(blockTask.getBlockPos().mutable(), blockTask);
//            tools.info(blockTask.getTaskState()  + "");
        }

//        blockTasks.put(blockTask.getBlockPos().mutableCopy().toImmutable(), blockTask);
//        for (BlockTask task : blockTasks.values()) {
//            tools.info(task + " " + task.getTaskState());
//        }
    }

    public void clearTasks() {
        blockTasks.clear();
        containerTask = null;
        grindCycles = 0;
    }

    private Comparator<BlockTask> getBlockTaskComparator() {
        return Comparator
            .comparing(BlockTask::getTaskState)
            .thenComparing((a, b) -> {
                // During BRIDGE, prioritize tasks by placement sequence so the support gets
                // built first (empty-sequence tasks sort highest) — mirrors Lambda's
                // blockTaskComparator, which swaps to sequence-ordering while bridging.
                if (tools.getPathfinder().getMovementState() == BaritonePathfinder.MovementState.BRIDGE) {
                    if (a.getSequence().isEmpty() && b.getSequence().isEmpty()) return 0;
                    if (a.getSequence().isEmpty()) return 1;
                    if (b.getSequence().isEmpty()) return -1;
                    return Integer.compare(a.getSequence().size(), b.getSequence().size());
                }
                if (tools.getShuffle().get()) {
                    return Integer.compare(a.getShuffle(), b.getShuffle());
                }
                return Double.compare(tools.getStart().distanceTo(a.getBlockPos().getCenter()), tools.getStart().distanceTo(b.getBlockPos().getCenter()));
            }).thenComparing(BlockTask::getEyeDistance);
    }

    public Map<BlockPos, BlockTask> getBlockTasks() {
        return blockTasks;
    }

}
