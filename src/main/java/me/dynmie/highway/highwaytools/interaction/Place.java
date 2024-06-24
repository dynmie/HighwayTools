package me.dynmie.highway.highwaytools.interaction;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.HInvUtils;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * @author dynmie
 */
public class Place {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void place(BlockTask task) {
        HighwayTools tools = Modules.get().get(HighwayTools.class);

        if (tools.getRotation().get().place && tools.getRotateCamera().get() && mc.player != null) {
            mc.player.setPitch((float) Rotations.getPitch(task.getBlockPos()));
            mc.player.setYaw((float) Rotations.getYaw(task.getBlockPos()));
        }

        Item itemToFind = task.getBlueprintTask().getTargetBlock().asItem();
        itemToFind = itemToFind.equals(Items.AIR) ? tools.getFillerBlock().get().asItem() : itemToFind;

        int slot = HInvUtils.prepareItemInHotbar(itemToFind);
        if (slot == -1) {
            return;//todo
        }

        BlockUtils.place(
            task.getBlockPos(),
            Hand.MAIN_HAND,
            slot,
            tools.getRotation().get().place,
            0,
            true,
            true,
            false
        );
    }

}
