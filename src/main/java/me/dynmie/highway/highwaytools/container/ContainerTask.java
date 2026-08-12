package me.dynmie.highway.highwaytools.container;

import me.dynmie.highway.highwaytools.block.TaskState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Lightweight state holder for the container restock lifecycle.
 * Kept separate from {@link me.dynmie.highway.highwaytools.block.BlockTask} so the
 * container lifecycle (PLACE -> PLACED -> OPEN_CONTAINER -> RESTOCK -> BREAK -> PICKUP -> DONE)
 * does not interfere with the highway block tasks.
 */
public class ContainerTask {

    public BlockPos blockPos;
    public TaskState taskState;
    public Item item = Items.AIR;
    public int stacksPulled = 0;
    public boolean stopPull = false;
    /** The block the container task places and later breaks — Lambda's {@code targetBlock}.
     *  {@link net.minecraft.world.level.block.Blocks#ENDER_CHEST} for the grind/dispatch paths,
     *  a shulker box for the restock/replenish paths. Drives what item is placed and whether it
     *  is a shulker (drops itself when broken) or an ender chest (drops 8 obsidian). */
    public Block containerBlock = Blocks.ENDER_CHEST;
    /** Whether to break the container right after placing, WITHOUT opening/restocking it — only
     *  the AutoObsidian grind path (place an ender chest, mine its 8 obsidian). Lambda's
     *  {@code destroy}: every other container is still broken after restocking, but through the
     *  RESTOCK → BREAK transition, not this flag. */
    public boolean destroy = false;
    /** Whether the carry should move a full container stack into a free/empty main slot with
     *  PICKUP clicks. False for SWAP-based pulls that never touch the carry. */
    public boolean pickupCarry = false;
    /** Whether to walk over and pick up the drops after breaking the container. */
    public boolean collect = true;
    public int stuckTicks = 0;

    /** Remaining single-container-click steps for the current PICKUP carry sequence (see
     *  {@code pullOneStack}). Each click must be confirmed by the server before the next is
     *  sent, so the sequence advances one click per tick. */
    public java.util.ArrayDeque<int[]> clickStack = new java.util.ArrayDeque<>();

    public ContainerTask(BlockPos blockPos, TaskState taskState, Item item, Block containerBlock) {
        this.blockPos = blockPos;
        this.taskState = taskState;
        this.item = item;
        this.containerBlock = containerBlock;
    }

    public boolean isShulker() {
        return containerBlock instanceof ShulkerBoxBlock;
    }

    /**
     * The item that ends up on the ground when this container is broken.
     * <ul>
     *   <li>Shulker box → the box item itself (shulkers always drop themselves when broken).</li>
     *   <li>Ender chest → {@link net.minecraft.world.level.block.Blocks#OBSIDIAN}, because the
     *       chest is broken with a non-silk-touch pickaxe
     *       ({@link me.dynmie.highway.highwaytools.handler.InventoryHandler#findBestTool} excludes
     *       silk-touch for ender chests) so it drops 8 obsidian, not the chest block.</li>
     * </ul>
     */
    public Item dropItem() {
        if (containerBlock instanceof ShulkerBoxBlock) return containerBlock.asItem();
        return Blocks.OBSIDIAN.asItem();
    }
}
