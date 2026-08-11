package me.dynmie.highway.highwaytools.block;

import me.dynmie.highway.highwaytools.blueprint.BlueprintTask;
import me.dynmie.highway.highwaytools.place.PlacementStep;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Random;

public class BlockTask {

    private static final Random random = new Random();

    private final BlockPos blockPos;
    private TaskState taskState;
    private final BlueprintTask blueprintTask;
    private Item item;

    //
    private int ranTicks = 0;
    private int shuffle = 0;
    private int stuckTicks = 0;
    private int minedTicks = 0;
    private int startMineTick = 0;
    private ItemStack toolToUse = ItemStack.EMPTY;
    private List<PlacementStep> sequence = List.of();

    public BlockTask(BlockPos blockPos, TaskState taskState, BlueprintTask blueprintTask, Item item) {
        this.blockPos = blockPos;
        this.taskState = taskState;
        this.blueprintTask = blueprintTask;
        this.item = item;
    }

    public BlockTask(BlockPos blockPos, TaskState taskState, BlueprintTask blueprintTask) {
        this.blockPos = blockPos;
        this.taskState = taskState;
        this.blueprintTask = blueprintTask;
        this.item = Items.AIR;
    }

    public boolean isShulkerBox() {
        return blueprintTask.getTargetBlock() instanceof ShulkerBoxBlock;
    }

    public void updateState(TaskState state) {
        if (state == taskState) return;
        ranTicks = 0;
        stuckTicks = 0;
        startMineTick = 0;
        taskState = state;
    }

    public void onTick() {
        ranTicks++;
        if (ranTicks > taskState.getStuckThreshold()) {
            stuckTicks++;
        }
    }

    public void onStuck() {
        this.onStuck(1);
    }

    public void onStuck(int weight) {
        this.stuckTicks += weight;
    }

    public int getStuckTicks() {
        return stuckTicks;
    }

    public int getMinedTicks() {
        return minedTicks;
    }

    public int getStartMineTick() {
        return startMineTick;
    }

    public void setStartMineTick(int startMineTick) {
        this.startMineTick = startMineTick;
    }

    public void setStuckTicks(int stuckTicks) {
        this.stuckTicks = stuckTicks;
    }

    public void setMinedTicks(int minedTicks) {
        this.minedTicks = minedTicks;
    }

    public void incrementMinedTicks() {
        minedTicks++;
    }

    public void shuffle() {
        shuffle = random.nextInt(1, 1000);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public double getEyeDistance() {
        return Minecraft.getInstance().player.getEyePosition().distanceTo(getBlockPos().getCenter());
    }

    public TaskState getTaskState() {
        return taskState;
    }

    public Item getItem() {
        return item;
    }

    public int getRanTicks() {
        return ranTicks;
    }

    public int getShuffle() {
        return shuffle;
    }

    public ItemStack getToolToUse() {
        return toolToUse;
    }

    public List<PlacementStep> getSequence() {
        return sequence;
    }

    public void setSequence(List<PlacementStep> sequence) {
        this.sequence = sequence;
    }

    public void setTaskState(TaskState taskState) {
        this.taskState = taskState;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public void setRanTicks(int ranTicks) {
        this.ranTicks = ranTicks;
    }

    public void setShuffle(int shuffle) {
        this.shuffle = shuffle;
    }

    public void setToolToUse(ItemStack toolToUse) {
        this.toolToUse = toolToUse;
    }

    public BlueprintTask getBlueprintTask() {
        return blueprintTask;
    }
}
