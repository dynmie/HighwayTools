package me.dynmie.highway.highwaytools.handler;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.TaskState;
import me.dynmie.highway.highwaytools.place.PlacementSearcher;
import me.dynmie.highway.highwaytools.place.PlacementStep;
import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * @author dynmie
 */
public class PlaceHandler {

    private static int extraPlaceDelay = 0;

    private static final Minecraft mc = Minecraft.getInstance();

    private final HighwayTools tools;
    private final InventoryHandler inventoryHandler;
    private final PlacementSearcher searcher;

    public PlaceHandler(HighwayTools tools, InventoryHandler inventoryHandler) {
        this.tools = tools;
        this.inventoryHandler = inventoryHandler;
        this.searcher = new PlacementSearcher(tools);
    }

    public void place(BlockTask task) {
        // DELAY
        int delay = tools.getAdaptivePlaceDelay().get() ? tools.getPlaceDelay().get() + extraPlaceDelay : tools.getPlaceDelay().get();
        inventoryHandler.setWaitTicks(delay);

        // INVENTORY
        Item itemToFind = task.getBlueprintTask().getTargetBlock().asItem();
        itemToFind = itemToFind.equals(Items.AIR) ? tools.getFillerBlock().get().asItem() : itemToFind;

        int slot = inventoryHandler.prepareItemInHotbar(itemToFind);
        if (slot == -1) {
            return; // todo: restock path (Branch B)
        }

        BlockPos pos = task.getBlockPos();

        // FIND A PLACEMENT SEQUENCE
        boolean illegal = tools.getIllegalPlacements().get();
        List<PlacementStep> sequence = searcher.findSequence(
            mc.player.getEyePosition(), pos, tools.getPlacementSearch().get(), illegal);
        task.setSequence(sequence);

        if (sequence.isEmpty()) {
            task.updateState(TaskState.IMPOSSIBLE_PLACE);
            return;
        }

        PlacementStep step = sequence.get(sequence.size() - 1);
        task.updateState(TaskState.PENDING_PLACE);

        // PURE-PACKET PLACEMENT against the found support
        BlockHitResult bhr = new BlockHitResult(
            step.hitVec(), step.side(), step.supportPos(), false);

        // ROTATION (camera) — preserve the old rotate-camera behavior
        if (tools.getRotation().get().place && tools.getRotateCamera().get() && mc.player != null) {
            mc.player.setYRot((float) Rotations.getYaw(step.hitVec()));
            mc.player.setXRot((float) Rotations.getPitch(step.hitVec()));
        }

        // INVENTORY — switch to the build item (InvUtils.swap syncs the held item to the
        // server), then restore the previous slot after clicking
        InvUtils.swap(slot, true);

        if (tools.getRotation().get().place) {
            Rotations.rotate(Rotations.getYaw(step.hitVec()), Rotations.getPitch(step.hitVec()), () -> {
                BlockUtils.interact(bhr, InteractionHand.MAIN_HAND, true);
                InvUtils.swapBack();
            });
        } else {
            BlockUtils.interact(bhr, InteractionHand.MAIN_HAND, true);
            InvUtils.swapBack();
        }

        // Existing confirmation fallback (kept from current code)
        new Thread(() -> {
            try {
                Thread.sleep(50L * tools.getTaskTimeout().get());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            tools.runNextTick(() -> {
                if (task.getTaskState() == TaskState.PENDING_PLACE) {
                    task.updateState(TaskState.PLACE);

                    if (tools.getAdaptivePlaceDelay().get() && extraPlaceDelay < 10) {
                        extraPlaceDelay += 1;
                    }
                }
            });
        }).start();
    }

    public int getExtraPlaceDelay() {
        return extraPlaceDelay;
    }

    public void setExtraPlaceDelay(int extraPlaceDelay) {
        PlaceHandler.extraPlaceDelay = extraPlaceDelay;
    }
}
