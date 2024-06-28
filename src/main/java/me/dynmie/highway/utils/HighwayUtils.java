package me.dynmie.highway.utils;

import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class HighwayUtils {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static int getMaxBlockDistance(float reach) {
        return (int) Math.floor(reach);
    }

//    public static void sortByReach(List<MBlockPos> blockPosList) {
//        if (mc.player == null) return;
//        blockPosList.sort(Comparator.comparingDouble(one -> mc.player.getEyePos().distanceTo(one.getMcPos().toCenterPos())));
//    }

    public static boolean isBehind(BlockPos origin, BlockPos check, HorizontalDirection direction) {
        Vec3d oToCDir = origin.toCenterPos().subtract(check.toCenterPos());

        Vec3d dir = new Vec3d(direction.offsetX, 0, direction.offsetZ).normalize();

        double delta = oToCDir.dotProduct(dir);
        return delta > 0;

    }

    public static boolean isTypeAir(Block block) {
        return block.getDefaultState().isAir();
    }

    public static Block returnAirIfAir(Block block) {
        if (isTypeAir(block)) return Blocks.AIR;
        return block;
    }

    public static boolean blockEqualsAndAirCheck(Block one, Block two) {
        return returnAirIfAir(one).equals(returnAirIfAir(two));
    }

}
