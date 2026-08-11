package me.dynmie.highway.highwaytools.container;

import me.dynmie.highway.highwaytools.block.TaskState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Lightweight state holder for the container restock lifecycle.
 * Kept separate from {@link me.dynmie.highway.highwaytools.block.BlockTask} so the
 * container lifecycle (PLACE -> PLACED -> OPEN_CONTAINER -> RESTOCK -> BREAK -> PICKUP -> DONE)
 * does not interfere with the highway block tasks.
 */
public class ContainerTask {

    private static final Minecraft mc = Minecraft.getInstance();

    public BlockPos blockPos;
    public TaskState taskState;
    public Item item = Items.AIR;
    public int stacksPulled = 0;
    public boolean stopPull = false;
    /** Whether to break the container after restocking (AutoObsidian: break the ender chest). */
    public boolean destroy = false;
    /** Whether to walk over and pick up the drops after breaking the container. */
    public boolean collect = true;
    public int stuckTicks = 0;

    public ContainerTask(BlockPos blockPos, TaskState taskState, Item item) {
        this.blockPos = blockPos;
        this.taskState = taskState;
        this.item = item;
    }

    public boolean isShulker() {
        return mc.level != null && mc.level.getBlockState(blockPos).getBlock() instanceof ShulkerBoxBlock;
    }

    /**
     * The item that ends up on the ground when this container is broken. Only the destroy paths
     * (grind + dispatch) reach BREAK → PICKUP, and both place an ender chest which is broken
     * with a non-silk-touch pickaxe ({@link me.dynmie.highway.highwaytools.handler.InventoryHandler#findBestTool}
     * excludes silk-touch for ender chests), so it drops {@link net.minecraft.world.level.block.Blocks#OBSIDIAN}
     * — not the chest block. That is the item to walk over and collect.
     */
    public Item dropItem() {
        return Blocks.OBSIDIAN.asItem();
    }
}
