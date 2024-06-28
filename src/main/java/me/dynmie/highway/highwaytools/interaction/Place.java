package me.dynmie.highway.highwaytools.interaction;

import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.TaskState;
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

    private static int extraPlaceDelay = 0;

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void place(BlockTask task) {
        HighwayTools tools = Modules.get().get(HighwayTools.class);

        // DELAY
        int delay = tools.getAdaptivePlaceDelay().get() ? tools.getPlaceDelay().get() + extraPlaceDelay : tools.getPlaceDelay().get();
        Inventory.setWaitTicks(delay);

        // ROTATION
        if (tools.getRotation().get().place && tools.getRotateCamera().get() && mc.player != null) {
            mc.player.setYaw((float) Rotations.getYaw(task.getBlockPos()));
            mc.player.setPitch((float) Rotations.getPitch(task.getBlockPos()));
        }

        // INVENTORY
        Item itemToFind = task.getBlueprintTask().getTargetBlock().asItem();
        itemToFind = itemToFind.equals(Items.AIR) ? tools.getFillerBlock().get().asItem() : itemToFind;

        int slot = HInvUtils.prepareItemInHotbar(itemToFind);
        if (slot == -1) {
            return;//todo
        }

        // PLACEMENT
        task.updateState(TaskState.PENDING_PLACE);

        // TODO correct block facing avoid impossible place
//
//        BlockPos pos = task.getBlockPos();
//
//        Vec3d hitPos = Vec3d.ofCenter(pos);
//
//        BlockPos neighbour;
//        Direction side = BlockUtils.getPlaceSide(pos);
//
//        if (side == null) {
//            side = Direction.UP;
//            neighbour = pos;
//        } else {
//            neighbour = pos.offset(side);
//            hitPos = hitPos.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
//        }
//
//        BlockHitResult bhr = new BlockHitResult(hitPos, side.getOpposite(), neighbour, false);
//
////        if (rotate) {
////            Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), rotationPriority, () -> {
////                InvUtils.swap(slot, swapBack);
////
////                interact(bhr, hand, swingHand);
////
////                if (swapBack) InvUtils.swapBack();
////            });
////        } else {
//        InvUtils.swap(slot, false);
//        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, bhr, 69));
//////        BlockUtils.interact(bhr, Hand.MAIN_HAND, true);
////
//////        }

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

    public static int getExtraPlaceDelay() {
        return extraPlaceDelay;
    }

    public static void setExtraPlaceDelay(int extraPlaceDelay) {
        Place.extraPlaceDelay = extraPlaceDelay;
    }
}
