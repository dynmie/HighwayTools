package me.dynmie.highway.utils;

import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * @author dynmie
 */
public class LocationUtils {

    private LocationUtils() {
        throw new UnsupportedOperationException();
    }

    public static boolean isBehind(BlockPos origin, BlockPos check, HorizontalDirection direction) {
        Vec3 oToCDir = origin.getCenter().subtract(check.getCenter());

        Vec3 dir = new Vec3(direction.offsetX, 0, direction.offsetZ).normalize();

        double delta = oToCDir.dot(dir);
        return delta > 0;
    }

}
