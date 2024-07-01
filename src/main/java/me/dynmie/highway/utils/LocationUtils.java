package me.dynmie.highway.utils;

import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * @author dynmie
 */
public class LocationUtils {

    private LocationUtils() {
        throw new UnsupportedOperationException();
    }

    public static boolean isBehind(BlockPos origin, BlockPos check, HorizontalDirection direction) {
        Vec3d oToCDir = origin.toCenterPos().subtract(check.toCenterPos());

        Vec3d dir = new Vec3d(direction.offsetX, 0, direction.offsetZ).normalize();

        double delta = oToCDir.dotProduct(dir);
        return delta > 0;
    }

}
