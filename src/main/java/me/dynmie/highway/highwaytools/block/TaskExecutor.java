package me.dynmie.highway.highwaytools.block;

import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.HighwayUtils;
import meteordevelopment.meteorclient.systems.modules.player.AutoTool;
import meteordevelopment.meteorclient.utils.misc.MBlockPos;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public class TaskExecutor {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final HighwayTools tools;

    public TaskExecutor(HighwayTools tools) {
        this.tools = tools;
    }

    public void doTask(BlockTask task, boolean check) {
        if (task.getBlockPos().equals(new MBlockPos().set(tools.getPosition()).offset(tools.getDir()).getMcPos().toImmutable().down())) {
            tools.info("downpos: " + task.getTaskState());
        }
        switch (task.getTaskState()) {
            case BREAKING -> doBreaking(task, check);
            case BROKEN -> doBroken(task);
            case PLACED -> doPlaced(task);
            case BREAK -> doBreak(task, check);
            case PLACE -> doPlace(task, check);
            default -> {}
        }
    }

    public void doBreaking(BlockTask task, boolean check) {
        if (mc.world == null || mc.player == null) return;
        Block block = mc.world.getBlockState(task.getBlockPos()).getBlock();

        if (HighwayUtils.isTypeAir(block)) {
            task.updateState(TaskState.BROKEN);
            return;
        }

//        tools.info("breaking");

        // check liquid

        if (!mc.player.isOnGround()) return;
        if (check) return;
        mineBlock(task);
    }

    public void doBroken(BlockTask task) {

        if (mc.world == null) return;
        Block block = mc.world.getBlockState(task.getBlockPos()).getBlock();

        if (!HighwayUtils.isTypeAir(block)) {
            task.updateState(TaskState.BREAK);
            return;
        }

        Block targetBlock = task.getBlueprintTask().getTargetBlock();
        if (HighwayUtils.isTypeAir(targetBlock)) {
//            tools.info("air broken");
            task.updateState(TaskState.DONE);
        } else {
            tools.setBlocksBroken(tools.getBlocksBroken() + 1);
            task.updateState(TaskState.PLACE);
        }

    }

    public void doPlaced(BlockTask task) {
        if (mc.world == null) return;
        BlockState state = mc.world.getBlockState(task.getBlockPos());
        Block block = state.getBlock();
        Block targetBlock = task.getBlueprintTask().getTargetBlock();

        if ((block.equals(targetBlock) || task.getBlueprintTask().isFiller()) && !state.isReplaceable()) {
            tools.setBlocksPlaced(tools.getBlocksPlaced() + 1);
            task.updateState(TaskState.DONE);
            return;
        }

        if (HighwayUtils.returnAirIfAir(targetBlock).equals(HighwayUtils.returnAirIfAir(block)) && HighwayUtils.isTypeAir(targetBlock)) {
            task.updateState(TaskState.BREAK);
            return;
        }

        if (HighwayUtils.isTypeAir(targetBlock) && !HighwayUtils.isTypeAir(block)) {
            task.updateState(TaskState.BREAK);
            return;
        }

//        tools.info("placed");

        task.updateState(TaskState.PLACE);
    }

    public void doBreak(BlockTask task, boolean check) {
        if (mc.world == null || mc.player == null) return;
        BlockState state = mc.world.getBlockState(task.getBlockPos());
        Block block = state.getBlock();
        Block targetBlock = task.getBlueprintTask().getTargetBlock();

        if (block.equals(Blocks.END_PORTAL_FRAME) || block.equals(Blocks.BEDROCK) || block.equals(Blocks.NETHER_PORTAL) || block.equals(Blocks.END_PORTAL)) {
            task.updateState(TaskState.DONE);
            return;
        }

//        if (targetBlock.equals(tools.getFillerBlock().get())) {
//
//            if (block.equals(tools.getMainBlock().get()) || !BlockUtils.canPlace(task.getBlockPos(), true)) {
//                task.updateState(TaskState.DONE);
//                return;
//            }
//            return;
//        }

//        if (targetBlock.equals(tools.getFillerBlock().get()) && (HighwayUtils.isTypeAir(block) || !BlockUtils.canPlace(task.getBlockPos(), true))) {
//            task.updateState(TaskState.DONE);
//            return;
//        }

//        if (targetBlock.equals(tools.getMainBlock().get()) && block.equals(tools.getMainBlock().get())) {
//            task.updateState(TaskState.DONE);
//            return;
//        }

        if (targetBlock.equals(tools.getFillerBlock().get())) {
            if (HighwayUtils.isBothSameButAirCheck(block, targetBlock)) {
                task.updateState(TaskState.DONE);
                return;
            }
        }

        if (targetBlock.equals(tools.getMainBlock().get())) {
            if (HighwayUtils.isBothSameButAirCheck(block, targetBlock)) {
                task.updateState(TaskState.DONE);
                return;
            }
        }



        if (HighwayUtils.isTypeAir(block)) {
            if (HighwayUtils.isTypeAir(targetBlock)) {
                task.updateState(TaskState.BROKEN);
            } else {
                task.updateState(TaskState.PLACE);
            }
            return;
        }

        // TODO: liquid

        if (!mc.player.isOnGround()) return;
        if (check) return;

        task.updateState(TaskState.BREAKING);
        mineBlock(task);

    }

    public void doPlace(BlockTask task, boolean check) {
        if (mc.world == null || mc.player == null) return;
        BlockState state = mc.world.getBlockState(task.getBlockPos());
        Block block = state.getBlock();
        Block targetBlock = task.getBlueprintTask().getTargetBlock();

        // TODO LIQUID

        if (block.equals(tools.getMainBlock().get()) && targetBlock.equals(tools.getMainBlock().get())) {
            task.updateState(TaskState.PLACED);
            return;
        }

        if (targetBlock.equals(tools.getFillerBlock().get()) && block.equals(tools.getFillerBlock().get())) {
            task.updateState(TaskState.PLACED);
            return;
        }

        if (HighwayUtils.isTypeAir(targetBlock)) {
            if (!HighwayUtils.isTypeAir(block)) {
                task.updateState(TaskState.BREAK);
            } else {
                task.updateState(TaskState.BROKEN);
            }
            return;
        }

//        if (!block.equals(targetBlock)) {
//            if (!block.equals(Blocks.AIR)) {
//                task.updateState(TaskState.BREAK);
//                return;
//            }
//        }

        if (!BlockUtils.canPlace(task.getBlockPos(), true)) {
//            task.updateState(TaskState.DONE);
            return;
        }

        if (check) return;
        placeBlock(task);
//        tools.info("place");
    }

    public void mineBlock(BlockTask task) {
//        int slot = findBestToolSlot(task, true); // make setting
//        int firstHotbarSlot = ;
//
//        InvUtils.move().from(slot).to(firstHotbarSlot);
//        InvUtils.dropHand();

        tools.info("mingin " + mc.world.getBlockState(task.getBlockPos()).getBlock());
        mine(task);
    }

    public void placeBlock(BlockTask task) {
        place(task, 1);
    }

    public void mine(BlockTask task) {
        BlockPos pos = task.getBlockPos();

        if (tools.getRotation().get().mine && tools.getRotateCamera().get() && mc.player != null) {
            mc.player.setPitch((float) Rotations.getPitch(task.getBlockPos()));
            mc.player.setYaw((float) Rotations.getYaw(task.getBlockPos()));
        }

        if (tools.getRotation().get().mine) {
            Rotations.rotate(Rotations.getPitch(pos), Rotations.getYaw(pos), () -> BlockUtils.breakBlock(pos, true));
        }
        BlockUtils.breakBlock(pos, true);
    }

    public boolean place(BlockTask task, int slot) {
        if (tools.getRotation().get().place && tools.getRotateCamera().get() && mc.player != null) {
            mc.player.setPitch((float) Rotations.getPitch(task.getBlockPos()));
            mc.player.setYaw((float) Rotations.getYaw(task.getBlockPos()));
        }
        return BlockUtils.place(task.getBlockPos(), Hand.MAIN_HAND, slot, tools.getRotation().get().place, 0, true, true, false);
    }


    public static int findBestToolSlot(BlockTask task, boolean noSilkTouch) {
        if (mc.player == null) return 1;

        // Find best tool
        double bestScore = -1;
        int bestSlot = mc.player.getInventory().selectedSlot;

        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
            double score = AutoTool.getScore(mc.player.getInventory().getStack(i), task.getBlueprintTask().getTargetBlock().getDefaultState(), false, AutoTool.EnchantPreference.None, itemStack -> {
                if (noSilkTouch && EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, itemStack) != 0)
                    return false;
                return itemStack.getMaxDamage() - itemStack.getDamage() > 1;
            });

            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        return bestSlot;
    }









//
//    public static int findHotbarSlot(HighwayTools b, boolean replaceTools) {
//        int thrashSlot = -1;
//        int slotsWithBlocks = 0;
//        int slotWithLeastBlocks = 65;
//        int slowWithLeastBlocksCount = 0;
//
//        // Loop hotbar
//        for (int i = 0; i < 9; i++) {
//            ItemStack itemStack = mc.player.getInventory().getStack(i);
//
//            // Return if the slot is empty
//            if (itemStack.isEmpty()) return i;
//
//            // Return if the slot contains a tool and replacing tools is enabled
//            if (replaceTools && AutoTool.isTool(itemStack)) return i;
//
//            // Store the slot if it contains thrash
//            if (b.getTrashItems().get().contains(itemStack.getItem())) thrashSlot = i;
//
//            // Update tracked stats about slots that contain building blocks
//            if (itemStack.getItem() instanceof BlockItem blockItem && b.getMainBlock().get().contains(blockItem.getBlock())) {
//                slotsWithBlocks++;
//
//                if (itemStack.getCount() < slowWithLeastBlocksCount) {
//                    slowWithLeastBlocksCount = itemStack.getCount();
//                    slotWithLeastBlocks = i;
//                }
//            }
//        }
//
//        // Return thrash slot if found
//        if (thrashSlot != -1) return thrashSlot;
//
//        // If there are more than 1 slots with building blocks return the slot with the lowest amount of blocks
//        if (slotsWithBlocks > 1) return slotWithLeastBlocks;
//
//        // No space found in hotbar
//        b.error("No empty space in hotbar.");
//        return -1;
//    }
//
//    public static boolean hasItem(HighwayTools b, Item item) {
//        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
//            if (mc.player.getInventory().getStack(i).getItem() == item) return true;
//        }
//
////        InvUtils.find(item).found();
//
//        return false;
//    }
//
//    public static int findAndMoveToHotbar(HighwayTools b, Predicate<ItemStack> predicate, boolean required) {
//        // Check hotbar
//        int slot = findSlot(b, predicate, true);
//        if (slot != -1) return slot;
//
//        // Find hotbar slot to move to
//        int hotbarSlot = findHotbarSlot(b, false);
//        if (hotbarSlot == -1) return -1;
//
//        // Check inventory
//        slot = findSlot(b, predicate, false);
//
//        // Stop if no items were found and are required
//        if (slot == -1) {
//            if (required) {
//                b.error("Out of items.");
//            }
//
//            return -1;
//        }
//
//        // Move items from inventory to hotbar
//        InvUtils.move().from(slot).toHotbar(hotbarSlot);
//        InvUtils.dropHand();
//
//        return hotbarSlot;
//    }
//
//    public static int findAndMoveBestToolToHotbar(HighwayTools b, BlockState blockState, boolean noSilkTouch, boolean error) {
//        // Check for creative
//        if (mc.player.isCreative()) return mc.player.getInventory().selectedSlot;
//
//        // Find best tool
//        double bestScore = -1;
//        int bestSlot = -1;
//
//        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
//            double score = AutoTool.getScore(mc.player.getInventory().getStack(i), blockState, false, AutoTool.EnchantPreference.None, itemStack -> {
//                if (noSilkTouch && EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, itemStack) != 0) return false;
//                return !b.getDontBreakTools().get() || itemStack.getMaxDamage() - itemStack.getDamage() > 1;
//            });
//
//            if (score > bestScore) {
//                bestScore = score;
//                bestSlot = i;
//            }
//        }
//
//        // Stop if not found
//        if (bestSlot == -1) {
//            if (error) b.error("Failed to find suitable tool for mining.");
//            return -1;
//        }
//
//        // Check if the tool is already in hotbar
//        if (bestSlot < 9) return bestSlot;
//
//        // Find hotbar slot to move to
//        int hotbarSlot = findHotbarSlot(b, true);
//        if (hotbarSlot == -1) return -1;
//
//        // Move tool from inventory to hotbar
//        InvUtils.move().from(bestSlot).toHotbar(hotbarSlot);
//        InvUtils.dropHand();
//
//        return hotbarSlot;
//    }
//
//
//
//    public static int findBlocksToPlacePrioritizeTrash() {
//        int slot = findAndMoveToHotbar(b, itemStack -> {
//            if (!(itemStack.getItem() instanceof BlockItem)) return false;
//            return b.getTrashItems().get().contains(itemStack.getItem());
//        }, false);
//
//        return slot != -1 ? slot : findBlocksToPlace(b);
//    }

}
