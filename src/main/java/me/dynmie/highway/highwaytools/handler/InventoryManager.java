package me.dynmie.highway.highwaytools.handler;

import me.dynmie.highway.modules.HighwayTools;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Locates shulker boxes / ender chests, computes restock need against the save-minimums
 * settings and drives the restock decision for the {@link me.dynmie.highway.highwaytools.block.BlockTaskManager}.
 */
public class InventoryManager {

    private static final Minecraft mc = Minecraft.getInstance();
    private final HighwayTools tools;

    public InventoryManager(HighwayTools tools) {
        this.tools = tools;
    }

    /** Count of {@code block} across the whole inventory (hotbar + main 36 slots). */
    public int countBlock(Block block) {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem bi && bi.getBlock().equals(block)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** First shulker whose contents contain {@code item}, preferring the one with fewest of it. */
    public ItemStack getShulkerWith(Item item) {
        List<ItemStack> shulkers = new ArrayList<>();
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) {
                if (countItemInShulker(stack, item) > 0) {
                    shulkers.add(stack);
                }
            }
        }
        if (shulkers.isEmpty()) return ItemStack.EMPTY;
        // fewest matching count first, so we don't drain the fullest shulker
        return shulkers.stream()
            .min(Comparator.comparingInt(s -> countItemInShulker(s, item)))
            .orElse(ItemStack.EMPTY);
    }

    private int countItemInShulker(ItemStack shulker, Item item) {
        ItemContainerContents contents = shulker.get(DataComponents.CONTAINER);
        if (contents == null) return 0;
        int n = 0;
        for (ItemStackTemplate template : contents.nonEmptyItems()) {
            if (template.item().value().equals(item)) n += template.count();
        }
        return n;
    }

    /** A placeable position near the player, not inside the blueprint, with solid support below. */
    public BlockPos getRemotePos() {
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = mc.player.blockPosition().offset(dx, dy, dz);
                    if (mc.level.getBlockState(pos).canBeReplaced()
                        && !mc.level.getBlockState(pos.below()).canBeReplaced()
                        && !tools.getBlueprintGenerator().getBlueprint().containsKey(pos)) {
                        return pos.immutable();
                    }
                }
            }
        }
        return null;
    }

    public boolean needsRestock() {
        Block mainBlock = tools.getMainBlock().get();
        return countBlock(mainBlock) <= tools.getSaveMaterial().get()
            || findBestPickaxeCount() <= tools.getSaveTools().get();
    }

    /** Tools-only restock need (mirrors Lambda's {@code swapOrMoveBestTool} check). */
    public boolean needsRestockTools() {
        return findBestPickaxeCount() <= tools.getSaveTools().get();
    }

    public Item restockItem() {
        Block mainBlock = tools.getMainBlock().get();
        if (countBlock(mainBlock) <= tools.getSaveMaterial().get()) return mainBlock.asItem();
        return Items.DIAMOND_PICKAXE;
    }

    /** Spare inventory slots (main 27, index 9..35) excluding the keep-free-slots margin. */
    public int freeSlots() {
        int free = 0;
        for (int i = 9; i < mc.player.getInventory().getContainerSize(); i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) free++;
        }
        return Math.max(0, free - tools.getKeepFreeSlots().get());
    }

    /** Number of ender-chests' worth of obsidian (8 each) that fit in the free slots. */
    public int grindCycles() {
        return (freeSlots() - 1) * 8;
    }

    private int findBestPickaxeCount() {
        int best = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.DIAMOND_PICKAXE) best += stack.getCount();
        }
        return best;
    }
}
