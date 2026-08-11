package me.dynmie.highway.highwaytools.handler;

import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * @author dynmie
 */
public class InventoryHandler {

    private final Minecraft client = Minecraft.getInstance();
    private final HighwayTools tools;

    private int waitTicks = 0;

    public InventoryHandler(HighwayTools tools) {
        this.tools = tools;
    }

    public int getWaitTicks() {
        return waitTicks;
    }

    public void setWaitTicks(int waitTicks) {
        this.waitTicks = waitTicks;
    }

    public void increaseWaitTicks(int ticks) {
        waitTicks += ticks;
    }

    public void decreaseWaitTicks(int ticks) {
        waitTicks -= ticks;
    }

    /**
     * Finds (and, if not already there, moves into the hotbar) an item and returns the
     * hotbar slot (0-8) that now holds it.
     *
     * <p>The returned slot is what the caller must pass to {@link InvUtils#swap} so the
     * correct stack is held when the placement packet is sent. Using a wrong hotbar index
     * here makes the bot place a different block than the one it just swapped (e.g. it
     * moves obsidian into the hotbar but holds netherrack because the selected slot was
     * never the one the item landed in).
     */
    public int prepareItemInHotbar(Item item) {
        FindItemResult itemResult = InvUtils.find(item);

        if (!itemResult.found()) {
            // trigger restock (deferred to next tick via the runnable queue)
            tools.runNextTick(() -> tools.getTaskManager().needsRestockCheck());
            return -1;
        }

        // Item already in the hotbar: use its slot directly.
        if (itemResult.isHotbar()) {
            return itemResult.slot();
        }

        // Item is in the main inventory (slots 0-35 in the container model that
        // InvUtils.find uses, but these are NOT the same as the hotbar 0-8 indices).
        // Move it to a free hotbar slot and return that hotbar index.
        int sourceSlot = itemResult.slot();
        int hotbarSlot = findFreeHotbarSlot();

        // from() expects the container index (0-35 main, 36-44 hotbar) that InvUtils.find
        // used; to() converts the hotbar index 0-8 to the container id. This move does NOT
        // change the selected slot.
        InvUtils.move().from(sourceSlot).toHotbar(hotbarSlot);

        return hotbarSlot;
    }

    public int findFreeHotbarSlot() {
        Objects.requireNonNull(client.player, "player cannot be null");

        // Empty hotbar slot (indices 0-8). Fall back to the slot right of the current
        // selection so the moved item is close at hand.
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).isEmpty()) {
                return i;
            }
        }

        int bestSlot = client.player.getInventory().getSelectedSlot() + 1;
        if (bestSlot > 8) bestSlot = 0;
        return bestSlot;
    }

    public FindItemResult findBestTool(BlockState state) {
        Objects.requireNonNull(mc.player, "player cannot be null");

        boolean noSilk = state.getBlock() == Blocks.ENDER_CHEST;

        double bestScore = 1;
        int slot = -1;

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);

            if (!stack.isCorrectToolForDrops(state)) continue;

            if (noSilk && Utils.getEnchantmentLevel(stack, Enchantments.SILK_TOUCH) != 0) {
                continue;
            }

            double score = stack.getDestroySpeed(state) * 1001;
            score += Utils.getEnchantmentLevel(stack, Enchantments.UNBREAKING);
            score += Utils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
            score += Utils.getEnchantmentLevel(stack, Enchantments.MENDING);
            score += Utils.getEnchantmentLevel(stack, Enchantments.FORTUNE);

            if (tools.getPreferSilkTouch().get()) {
                score += Utils.getEnchantmentLevel(stack, Enchantments.SILK_TOUCH);
            }

            if (score > bestScore) {
                bestScore = score;
                slot = i;
            }
        }

        return new FindItemResult(slot, 1);
    }

    /**
     * Moves the best item to the best slot in the hotbar and returns it.
     *
     * <p>Uses {@link InvUtils#swap} instead of a bare {@code setSelectedSlot}: the swap
     * also calls {@code meteor$syncSelected()} ({@code ensureHasSentCarriedItem}) so the
     * server learns the newly held item. Without the sync the server keeps breaking at the
     * speed of the previously held slot, making mining appear much slower than the client
     * predicts. No swapBack — the bot controls the hotbar and keeps the tool held until the
     * next action swaps it away.
     *
     * @param state The block state to calculate the best tool.
     * @return The slot with the best item.
     */
    public int prepareToolInHotbar(BlockState state) {
        FindItemResult bestToolResult = findBestTool(state);

        if (!bestToolResult.found()) {
            return 0;
        }

        int slot = bestToolResult.slot();

        if (!bestToolResult.isHotbar()) {
            int bestSlot = findFreeHotbarSlot();

            InvUtils.move().from(slot).to(bestSlot);

            slot = bestSlot;
        }

        InvUtils.swap(slot, false);
        return slot;
    }

}
