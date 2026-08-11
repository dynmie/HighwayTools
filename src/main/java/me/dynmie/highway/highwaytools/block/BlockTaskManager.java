package me.dynmie.highway.highwaytools.block;

import me.dynmie.highway.highwaytools.blueprint.BlueprintTask;
import me.dynmie.highway.highwaytools.container.ContainerTask;
import me.dynmie.highway.highwaytools.handler.InventoryHandler;
import me.dynmie.highway.highwaytools.handler.InventoryManager;
import me.dynmie.highway.highwaytools.pathing.BaritonePathfinder;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.BlockUtils;
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

    /** Active container restock task; runs inside the loop while non-null. */
    public ContainerTask containerTask = null;

    public BlockTaskManager(HighwayTools tools, InventoryHandler inventoryHandler) {
        this.tools = tools;
        this.inventoryHandler = inventoryHandler;
        this.inventoryManager = tools.getInventoryManager();
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
        // [HT-DIAG] how often is runTasks actually entered?
        if (mc.player != null && mc.player.tickCount % 20 == 0) {
            System.out.println("[HT-DIAG] RUNTASKS t=" + mc.player.tickCount
                + " waitTicks=" + inventoryHandler.getWaitTicks());
        }

        // Container restock has priority over the normal block loop, but the loop keeps
        // ticking (update-only pass) while the container is open, so task states, mining
        // progress and the overlay never freeze. Restock is triggered lazily per-task
        // (see InventoryHandler / BreakHandler), never as a gate in front of this loop.
        if (containerTask != null) {
            tools.getTaskExecutor().doContainerTask(containerTask);
            updateBlockTasks();
            return;
        }

        updateBlockTasks();

        // [HT-DIAG] once-per-second heartbeat: loop alive + what each task is doing
        if (mc.player != null && mc.player.tickCount % 20 == 0) {
            StringBuilder sb = new StringBuilder("[HT-DIAG] HEARTBEAT t=" + mc.player.tickCount
                + " waitTicks=" + inventoryHandler.getWaitTicks()
                + " tasks=" + blockTasks.size() + " states=");
            Map<TaskState, Integer> counts = new java.util.HashMap<>();
            for (BlockTask t : blockTasks.values()) counts.merge(t.getTaskState(), 1, Integer::sum);
            counts.entrySet().forEach(e -> sb.append(e.getKey()).append("=").append(e.getValue()).append(" "));
            System.out.println(sb);
        }

        // decrement the shared wait counter each tick
        if (inventoryHandler.getWaitTicks() > 0) {
            inventoryHandler.decreaseWaitTicks(1);
        }

        sortedTasks.clear();
        sortedTasks.addAll(blockTasks.values());

        // [HT-DIAG] count BREAK tasks in the pool before acting
        int breakCount = 0;
        for (BlockTask t : blockTasks.values()) if (t.getTaskState() == TaskState.BREAK) breakCount++;
        int firstActed = -1;
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
            firstActed = actedIdx;
            break;
        }

        if (firstActed >= 0 && (mc.player != null && mc.player.tickCount % 4 == 0)) {
            System.out.println("[HT-DIAG] ACTED idx=" + firstActed + " of " + sortedTasks.size()
                + " breakTasks=" + breakCount
                + " firstState=" + sortedTasks.stream().filter(t -> !(t.getTaskState() == TaskState.DONE
                    || t.getTaskState() == TaskState.BROKEN || t.getTaskState() == TaskState.PLACED))
                    .map(BlockTask::getTaskState).findFirst().orElse(null)
                + " waitTicks=" + inventoryHandler.getWaitTicks());
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
     * Kicks off the restock lifecycle: AutoObsidian (place + grind an ender chest) when the
     * player is low on the main block, otherwise place a shulker containing the restock item,
     * or open the ender chest directly when no shulker has the item.
     */
    private void startRestock() {
        Item item = inventoryManager.restockItem();

        // AutoObsidian: place an ender chest, restock from it, then break it for the obsidian
        if (item == tools.getMainBlock().get().asItem() && tools.getGrindObsidian().get()
            && inventoryManager.countBlock(Blocks.ENDER_CHEST) > tools.getSaveEnder().get()) {
            BlockPos pos = inventoryManager.getRemotePos();
            if (pos != null) {
                containerTask = new ContainerTask(pos, TaskState.PLACE, Items.ENDER_CHEST);
                containerTask.item = item;
                containerTask.destroy = true;
            }
            return;
        }

        ItemStack shulker = inventoryManager.getShulkerWith(item);
        if (!shulker.isEmpty()) {
            BlockPos pos = inventoryManager.getRemotePos();
            if (pos != null) {
                containerTask = new ContainerTask(pos, TaskState.PLACE, item);
            }
            return;
        }

        if (tools.getRestockFromEnderChest().get()) {
            BlockPos pos = inventoryManager.getRemotePos();
            if (pos != null) {
                containerTask = new ContainerTask(pos, TaskState.OPEN_CONTAINER, item);
            }
        }
    }

    /** Re-check restock need, e.g. from {@link InventoryHandler} when a needed item is missing. */
    public void needsRestockCheck() {
        if (containerTask == null && inventoryManager.needsRestock()) {
            startRestock();
        }
    }

    /** Lazy tools restock (mirrors Lambda): request one when pickaxes are low, unless already running. */
    public void needsToolsRestockCheck() {
        if (containerTask == null && inventoryManager.needsRestockTools()) {
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
