package me.dynmie.highway.highwaytools.handler;

import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Objects;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * @author dynmie
 */
public class InventoryHandler {

    private final MinecraftClient client = MinecraftClient.getInstance();
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

    public int prepareItemInHotbar(Item item) {
        FindItemResult itemResult = InvUtils.find(item);

        if (!itemResult.found()) {
            return -1;
        }

        int slot = itemResult.slot();

        if (!itemResult.isHotbar()) {
            int bestSlot = findFreeHotbarSlot();

            InvUtils.move().from(slot).to(bestSlot);

            slot = bestSlot;
        }

        return slot;
    }

    public int findFreeHotbarSlot() {
        Objects.requireNonNull(client.player, "player cannot be null");

        FindItemResult hotbarResult = InvUtils.find(ItemStack::isEmpty, 0, 8);

        int bestSlot = client.player.getInventory().selectedSlot + 1;
        if (bestSlot > 8) bestSlot = 0;

        if (hotbarResult.found() && !InvUtils.testInMainHand(ItemStack::isEmpty)) {
            bestSlot = hotbarResult.slot();
        }

        return bestSlot;
    }

    public FindItemResult findBestTool(BlockState state) {
        Objects.requireNonNull(mc.player, "player cannot be null");

        boolean noSilk = state.getBlock() == Blocks.ENDER_CHEST;

        double bestScore = 1;
        int slot = -1;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (!stack.isSuitableFor(state)) continue;

            if (EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, stack) != 0 && noSilk) {
                continue;
            }

            double score = stack.getMiningSpeedMultiplier(state);

            score += stack.getMiningSpeedMultiplier(state) * 1000;
            score += EnchantmentHelper.getLevel(Enchantments.UNBREAKING, stack);
            score += EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, stack);
            score += EnchantmentHelper.getLevel(Enchantments.MENDING, stack);
            score += EnchantmentHelper.getLevel(Enchantments.FORTUNE, stack);

            if (tools.getPreferSilkTouch().get()) {
                score += EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, stack);
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

        return slot;
    }

}
