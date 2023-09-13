package me.dynmie.highway.highwaytools.block;

import me.dynmie.highway.highwaytools.blueprint.BlueprintTask;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.HighwayUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class BlockTaskManager {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final Map<BlockPos, BlockTask> blockTasks = new ConcurrentHashMap<>();
    private final Set<BlockTask> sortedTasks = new ConcurrentSkipListSet<>(getBlockTaskComparator());

    private final HighwayTools tools;

    public BlockTaskManager(HighwayTools tools) {
        this.tools = tools;
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

            if (tools.getPosition().getMcPos().toCenterPos().distanceTo(entry.getKey().toCenterPos()) > tools.getReach().get() + 2) {
                blockTasks.remove(entry.getKey());
            }
        }

    }

    public void generateTask(BlockPos pos, BlueprintTask blueprintTask) {
        if (mc.player == null) return;
        Vec3d eyePos = mc.player.getEyePos();
        BlockState blockState = mc.player.world.getBlockState(pos);

        // padding
        if (HighwayUtils.isBehind(tools.getStartPosition().getMcPos(), pos, tools.getDir())) {
            return;
        }


        if (eyePos.distanceTo(pos.toCenterPos()) >= tools.getReach().get() + 1) return;

        // place
        if (blockState.isReplaceable() && !HighwayUtils.isTypeAir(blueprintTask.getTargetBlock())) {
            if (!BlockUtils.canPlace(pos)) {
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
//        if (blockState.isAir() && HighwayUtils.isTypeAir(blueprintTask.getTargetBlock())) {
//            BlockTask task = new BlockTask(pos, TaskState.DONE, blueprintTask);
//            addTask(task);
//            return;
//        }
//
//        if (blockState.getBlock().equals(blueprintTask.getTargetBlock())) {
//            BlockTask task = new BlockTask(pos, TaskState.DONE, blueprintTask);
//            addTask(task);
//            return;
//        }
        //

        BlockTask task = new BlockTask(pos, TaskState.BREAK, blueprintTask);
//        addTask(task);

        // TODO: check air???
        if (task.getEyeDistance() < tools.getReach().get()) {
            addTask(task);
        }
    }

    public void runTasks() {
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

    public void addTask(BlockTask blockTask) {
        BlockTask otherTask = blockTasks.get(blockTask.getBlockPos());
        if (otherTask == null) {
            blockTasks.put(blockTask.getBlockPos().mutableCopy(), blockTask);
            return;
        }

        if (blockTask.getTaskState() == TaskState.LIQUID
            || otherTask.getTaskState() != blockTask.getTaskState()
            && (otherTask.getTaskState() == TaskState.DONE || otherTask.getTaskState() == TaskState.PLACE)
        ) {
            blockTasks.put(blockTask.getBlockPos().mutableCopy(), blockTask);
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
                return Double.compare(tools.getStart().distanceTo(a.getBlockPos().toCenterPos()), tools.getStart().distanceTo(b.getBlockPos().toCenterPos()));
            }).thenComparing(BlockTask::getEyeDistance);
    }

    public Map<BlockPos, BlockTask> getBlockTasks() {
        return blockTasks;
    }

}
