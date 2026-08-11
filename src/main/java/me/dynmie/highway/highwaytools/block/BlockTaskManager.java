package me.dynmie.highway.highwaytools.block;

import me.dynmie.highway.highwaytools.blueprint.BlueprintTask;
import me.dynmie.highway.highwaytools.container.ContainerTask;
import me.dynmie.highway.highwaytools.handler.InventoryHandler;
import me.dynmie.highway.highwaytools.handler.InventoryManager;
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

    /** Active container restock task; has priority over regular block tasks while non-null. */
    public ContainerTask containerTask = null;
    public boolean restocking = false;

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

        if (currentBlock.equals(Blocks.END_PORTAL_FRAME)
            || currentBlock.equals(Blocks.BEDROCK)
            || currentBlock.equals(Blocks.NETHER_PORTAL)
            || currentBlock.equals(Blocks.END_PORTAL)
        ) {
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
        if (inventoryHandler.getWaitTicks() > 1) {
            inventoryHandler.decreaseWaitTicks(1);
            return;
        }

        // RESTOCK GATE: container task has priority over normal block tasks
        if (containerTask != null) {
            tools.getTaskExecutor().doContainerTask(containerTask);
            return;
        }

        if (inventoryManager.needsRestock() && !restocking) {
            startRestock();
            return;
        }
        restocking = false;

        for (BlockTask task : blockTasks.values()) {
            tools.getTaskExecutor().doTask(task, true);

            if (tools.getShuffle().get()) task.shuffle();
        }

        sortedTasks.clear();
        sortedTasks.addAll(blockTasks.values());

        for (BlockTask task : sortedTasks) {

//            if (task.getTaskState() != TaskState.DONE)

            tools.getTaskExecutor().doTask(task, false);

            if (task.getTaskState() == TaskState.DONE || task.getTaskState() == TaskState.BROKEN || task.getTaskState() == TaskState.PLACED) {
                continue;
            }
            return;
        }
    }

    /**
     * Kicks off the restock lifecycle: AutoObsidian (place + grind an ender chest) when the
     * player is low on the main block, otherwise place a shulker containing the restock item,
     * or open the ender chest directly when no shulker has the item.
     */
    private void startRestock() {
        restocking = true;
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
        if (inventoryManager.needsRestock()) {
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
