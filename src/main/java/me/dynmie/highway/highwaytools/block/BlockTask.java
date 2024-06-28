package me.dynmie.highway.highwaytools.block;

import me.dynmie.highway.highwaytools.blueprint.BlueprintTask;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

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
    private boolean isOpen = false;
    private ItemStack toolToUse = ItemStack.EMPTY;

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
        return MinecraftClient.getInstance().player.getEyePos().distanceTo(getBlockPos().toCenterPos());
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

    public boolean isOpen() {
        return isOpen;
    }

    public ItemStack getToolToUse() {
        return toolToUse;
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

    public void setOpen(boolean open) {
        isOpen = open;
    }

    public void setToolToUse(ItemStack toolToUse) {
        this.toolToUse = toolToUse;
    }

    public BlueprintTask getBlueprintTask() {
        return blueprintTask;
    }
}
