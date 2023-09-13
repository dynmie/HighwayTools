package me.dynmie.highway.utils;

import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import meteordevelopment.meteorclient.utils.misc.MBlockPos;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.util.Comparator;
import java.util.List;

public class HighwayUtils {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static int getMaxBlockDistance(float reach) {
        return (int) Math.floor(reach);
    }

    public static void sortByReach(List<MBlockPos> blockPosList) {
        if (mc.player == null) return;
        blockPosList.sort(Comparator.comparingDouble(one -> mc.player.getEyePos().distanceTo(one.getMcPos().toCenterPos())));
    }

    public static boolean isBehind(BlockPos origin, BlockPos check, HorizontalDirection direction) {

//        double dotProduct = origin.toCenterPos().dotProduct(check.toCenterPos());
//
//        double idk = Math.cos(direction.yaw) * dotProduct;
//
//        // if funny number is not above zero then in front lol
//        return !(idk > 0);
////        return idk > 0;

        Vec3d oToCDir = origin.toCenterPos().subtract(check.toCenterPos());

        Vec3d dir = new Vec3d(direction.offsetX, 0, direction.offsetZ).normalize();

        double delta = oToCDir.dotProduct(dir);
        return delta > 0;

    }

    public static boolean isTypeAir(Block block) {
        return block.equals(Blocks.AIR) || block.equals(Blocks.CAVE_AIR) || block.equals(Blocks.VOID_AIR);
    }

    public static Block returnAirIfAir(Block block) {
        if (isTypeAir(block)) return Blocks.AIR;
        return block;
    }

    public static boolean isBothSameButAirCheck(Block one, Block two) {
        return returnAirIfAir(one).equals(returnAirIfAir(two));
    }

}
