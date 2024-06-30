package me.dynmie.highway.utils;

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
public class InventoryUtils {

    private static final MinecraftClient client = MinecraftClient.getInstance();

    public static int prepareItemInHotbar(Item item) {
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

    public static int findFreeHotbarSlot() {
        Objects.requireNonNull(client.player, "player cannot be null");

        FindItemResult hotbarResult = InvUtils.find(ItemStack::isEmpty, 0, 8);

        int bestSlot = client.player.getInventory().selectedSlot + 1;
        if (bestSlot > 8) bestSlot = 0;

        if (hotbarResult.found() && !InvUtils.testInMainHand(ItemStack::isEmpty)) {
            bestSlot = hotbarResult.slot();
        }

        return bestSlot;
    }

    public static FindItemResult findBestTool(BlockState state, boolean preferSilk) {
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

            if (preferSilk) {
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
     * @param preferSilk If silk touch is preferred.
     * @return The slot with the best item.
     */
    public static int prepareToolInHotbar(BlockState state, boolean preferSilk) {
        FindItemResult bestToolResult = findBestTool(state, preferSilk);

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
