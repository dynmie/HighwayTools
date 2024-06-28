package me.dynmie.highway.utils;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Objects;

/**
 * @author dynmie
 */
public class HInvUtils {

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

        int bestSlot = client.player.getInventory().selectedSlot;

        if (hotbarResult.found() && !InvUtils.testInMainHand(ItemStack::isEmpty)) {
            bestSlot = hotbarResult.slot();
        }

        return bestSlot;
    }

}
