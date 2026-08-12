package me.dynmie.highway.highwaytools.handler;

import me.dynmie.highway.modules.HighwayTools;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Locates shulker boxes / ender chests and the items *inside* them, computes restock need
 * against the save-minimums settings, and picks a remote container position next to the
 * building front — a faithful port of Lambda HighwayTools' {@code Container} / {@code Inventory}
 * storage management, adapted to meteor's index-based inventory API.
 *
 * <p>Container positions are chosen around {@code tools.getCurrentPosition()} (the front the
 * bot is currently building), never inside the blueprint, preferring the most "secure" spot
 * (surrounded on as many sides as possible by solid blocks), closest to the front, and at or
 * above the front height. This is what lets the bot place the ender chest / shulker where it
 * can actually reach it instead of blocking its own line of sight.
 */
public class InventoryManager {

    private static final Minecraft mc = Minecraft.getInstance();
    private final HighwayTools tools;

    public InventoryManager(HighwayTools tools) {
        this.tools = tools;
    }

    /**
     * Count of {@code block} across the whole inventory (hotbar + main 36 slots).
     */
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

    /**
     * First shulker whose contents contain {@code item}, preferring the one with fewest of it.
     * Returns {@code ItemStack.EMPTY} when no shulker holds the item.
     */
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
        for (net.minecraft.world.item.ItemStackTemplate template : contents.nonEmptyItems()) {
            if (template.item().value().equals(item)) n += template.count();
        }
        return n;
    }

    /**
     * A placeable position for a container (ender chest / shulker), near the building front,
     * not inside the blueprint, with solid support below, an air block above, a visible UP face
     * on the support, and at least {@code min-distance} blocks from the player (so the container
     * does not collide with the bot mid-restock). Mirrors Lambda's {@code Container.getRemotePos()}.
     */
    public BlockPos getRemotePos() {
        if (mc.player == null || mc.level == null) return null;
        BlockPos front = tools.getCurrentPosition();
        double maxReach = tools.getReach().get();
        double minDistance = tools.getMinDistance().get();

        double reach = Math.ceil(maxReach);
        BlockPos origin = front.offset(0, 1, 0);

        return searchSphere(origin, (int) reach).stream()
            .filter(pos ->
                !isInsideBlueprintBuild(pos)
                    && !pos.equals(front)
                    && meteordevelopment.meteorclient.utils.world.BlockUtils.canPlace(pos)
                    && !mc.level.getBlockState(pos.below()).canBeReplaced()
                    && mc.level.getBlockState(pos.above()).isAir()
                    && getVisibleSides(pos.below()).contains(Direction.UP)
                    && mc.player.position().distanceTo(pos.getCenter()) > minDistance
                    && pos.getY() >= front.getY()
            )
            .sorted(Comparator
                .comparingInt(InventoryManager::secureScore).reversed()
                .thenComparingDouble(pos -> pos.getCenter().distanceToSqr(origin.getCenter()))
                .thenComparingInt(pos -> Math.abs(pos.getY() - front.getY()))
            )
            .findFirst()
            .orElse(null);
    }

    /**
     * A position near the dropped items of {@code item} that the player can stand in to
     * collect them — an air block with air above and replaceable ground, closest to the drops.
     * Mirrors Lambda's {@code Container.getCollectingPosition()}.
     */
    public BlockPos getCollectingPosition(Item item, BlockPos nearPos) {
        if (mc.level == null || mc.player == null) return null;
        double range = 8f;

        // nearest item entity of the desired type within range of the broken container
        net.minecraft.world.entity.item.ItemEntity nearest = mc.level.getEntities(
                net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.item.ItemEntity.class),
                new net.minecraft.world.phys.AABB(nearPos).inflate(range),
                e -> !e.getItem().isEmpty() && e.getItem().is(item))
            .stream()
            .min(java.util.Comparator.comparingDouble(e -> e.position().distanceToSqr(mc.player.position())))
            .orElse(null);
        if (nearest == null) return null;

        net.minecraft.world.phys.Vec3 itemVec = nearest.position();
        double reach = Math.ceil(tools.getReach().get());

        return searchSphere(net.minecraft.core.BlockPos.containing(itemVec), (int) reach).stream()
            .filter(pos -> mc.level.getBlockState(pos.above()).isAir()
                && mc.level.getBlockState(pos).isAir()
                && !mc.level.getBlockState(pos.below()).canBeReplaced())
            .sorted(java.util.Comparator.<BlockPos>comparingDouble(pos -> pos.getCenter().distanceToSqr(itemVec))
                .thenComparingInt(BlockPos::getY))
            .findFirst()
            .orElse(null);
    }

    /** Number of solid (non-replaceable) neighbors around the support block — the "secure score". */
    private static int secureScore(BlockPos pos) {
        if (mc.level == null) return 0;
        int safe = 0;
        if (!mc.level.getBlockState(pos.below().north()).canBeReplaced()) safe++;
        if (!mc.level.getBlockState(pos.below().east()).canBeReplaced()) safe++;
        if (!mc.level.getBlockState(pos.below().south()).canBeReplaced()) safe++;
        if (!mc.level.getBlockState(pos.below().west()).canBeReplaced()) safe++;
        return safe;
    }

    /** The directions from which {@code pos} is not occluded by a solid block (simplified). */
    private List<Direction> getVisibleSides(BlockPos pos) {
        List<Direction> sides = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            if (mc.level.getBlockState(pos.relative(dir)).isAir()) sides.add(dir);
        }
        return sides;
    }

    /** True when the blueprint wants a solid (non-air) block at {@code pos} — Lambda's {@code isInsideBlueprintBuild}. */
    private boolean isInsideBlueprintBuild(BlockPos pos) {
        me.dynmie.highway.highwaytools.blueprint.BlueprintTask task =
            tools.getBlueprintGenerator().getBlueprint().get(pos);
        return task != null && !me.dynmie.highway.utils.BlockUtils.isTypeAir(task.getTargetBlock());
    }

    private List<BlockPos> searchSphere(BlockPos center, int radius) {
        List<BlockPos> positions = new ArrayList<>();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                        positions.add(center.offset(dx, dy, dz));
                    }
                }
            }
        }
        return positions;
    }

    /**
     * The item the module should restock next. Tools take priority over material: the bot
     * will never run out of pickaxes while filling obsidian, because each break needs one.
     */
    public Item restockItem() {
        if (findBestPickaxeCount() <= tools.getSaveTools().get()) return Items.DIAMOND_PICKAXE;
        Block mainBlock = tools.getMainBlock().get();
        if (countBlock(mainBlock) <= tools.getSaveMaterial().get()) return mainBlock.asItem();
        return Items.DIAMOND_PICKAXE;
    }

    /**
     * Whether a restock is needed: low on the main material or low on pickaxes. Counts only
     * what is actually in the inventory (hotbar + main), NOT shulker contents — mirroring
     * Lambda's gate ({@code countBlock(material) > saveMaterial} and
     * {@code countItem<ItemPickaxe>() <= saveTools}). A stack sitting inside a shulker does
     * not help place blocks, so it must not suppress the restock; the bot places the shulker
     * and pulls from it instead.
     */
    public boolean needsRestock() {
        return countBlock(tools.getMainBlock().get()) <= tools.getSaveMaterial().get()
            || findBestPickaxeCount() <= tools.getSaveTools().get();
    }

    /**
     * Spare inventory slots (hotbar + main) minus one reserved slot for the container that
     * will be picked back up (shulker box / ender chest drop) minus the keep-free-slots margin
     * — Lambda's {@code count - 1 - keepFreeSlots} in doRestock. Empty slots AND ejectable
     * (trash) slots count as room, because a pull can swap trash away to make space (Lambda's
     * {@code moveToInventory}). Without the reserved slot, a restock that filled the inventory
     * exactly would have nowhere for the box and the pickup would stall.
     */
    public int freeSlots() {
        int free = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || isEjectable(stack)) free++;
        }
        return Math.max(0, free - 1 - tools.getKeepFreeSlots().get());
    }

    /**
     * True when no inventory slot (hotbar + main 36) is empty. This is the gate Lambda's
     * {@code doPickup} uses before ejecting trash — unlike {@link #freeSlots()} it ignores the
     * keep-free margin, because during pickup the room is needed for drops, not for a pull.
     */
    public boolean isInventoryFull() {
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return false;
        }
        return true;
    }

    /**
     * An inventory slot (hotbar + main) holding a "trash" item — anything that is not the main
     * material, not a pickaxe, not a shulker, not an ender chest. Used to free a slot when the
     * inventory is full during drop pickup. Mirrors Lambda's {@code Inventory.getEjectSlot}.
     */
    public int findEjectSlot() {
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (isEjectable(mc.player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    /**
     * True when the stack is a "trash" item — not the main material, not a pickaxe, not a
     * shulker, not an ender chest. Mirrors Lambda's {@code InventoryManager.ejectList} test
     * (the items that are safe to swap away / drop to make room).
     */
    private boolean isEjectable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        Block mainBlock = tools.getMainBlock().get();
        boolean isBlock = item instanceof BlockItem bi && bi.getBlock().equals(mainBlock);
        boolean isPickaxe = stack.get(DataComponents.TOOL) != null
            && stack.get(DataComponents.TOOL).isCorrectForDrops(Blocks.OBSIDIAN.defaultBlockState());
        boolean isShulker = item instanceof BlockItem bi2 && bi2.getBlock() instanceof ShulkerBoxBlock;
        boolean isEnderChest = item == Items.ENDER_CHEST;
        return !isBlock && !isPickaxe && !isShulker && !isEnderChest;
    }

    /**
     * Whether a container-menu slot (27..62) holds an ejectable item. Menu slot ids match the
     * inventory indices (main 27..53 = index 27..35, hotbar 54..62 = index 0..8), so this is
     * just {@link #isEjectable(ItemStack)} on the slot's stack.
     */
    public boolean isEjectableSlot(int menuSlot) {
        if (mc.player == null || menuSlot < 27 || menuSlot > 62) return false;
        return isEjectable(mc.player.containerMenu.getSlot(menuSlot).getItem());
    }

    /**
     * Compress partial stacks of the same item into full stacks (Lambda's {@code zipInventory}),
     * so the inventory has room for a container pull. Returns true if at least one move was made.
     */
    public boolean zipInventory() {
        // collect stacks with room to grow and more than one partial stack of that item
        List<Integer> compressible = new ArrayList<>();
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getCount() < stack.getMaxStackSize()) compressible.add(i);
        }
        if (compressible.isEmpty()) return false;

        boolean moved = false;
        for (int from : compressible) {
            ItemStack src = mc.player.getInventory().getItem(from);
            if (src.isEmpty()) continue;
            int space = src.getMaxStackSize() - src.getCount();
            if (space <= 0) continue;
            for (int to : compressible) {
                if (to == from) continue;
                ItemStack dst = mc.player.getInventory().getItem(to);
                if (dst.isEmpty() || dst.getItem() != src.getItem() || dst.getCount() >= dst.getMaxStackSize()) continue;
                int take = Math.min(space, dst.getCount());
                // merge dst into src
                meteordevelopment.meteorclient.utils.player.InvUtils.move().from(to).to(from);
                moved = true;
                space -= take;
                if (space <= 0) break;
            }
        }
        return moved;
    }

    /** Number of ender chests' worth of obsidian (8 each) that fit in the free slots. */
    private int findBestPickaxeCount() {
        // All pickaxes, not just diamond — mirrors Lambda's `countItem<ItemPickaxe>()`. A held
        // netherite pickaxe must satisfy the save-tools threshold, or the module tries to
        // restock diamond pickaxes on every single break. MC 26.1.2 has no PickaxeItem class
        // (items are component-based), so detect pickaxes the way the game does: a Tool
        // component that is the correct tool for obsidian (only pickaxes can mine it).
        int best = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            Tool tool = stack.get(DataComponents.TOOL);
            if (tool != null && tool.isCorrectForDrops(Blocks.OBSIDIAN.defaultBlockState())) best += stack.getCount();
        }
        return best;
    }
}
