package me.dynmie.highway.utils;

import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import net.minecraft.util.math.Vec3i;

/**
 * @author dynmie
 */
public class DirectionUtils {

    public static Vec3i toVec3i(HorizontalDirection direction) {
        return new Vec3i(direction.offsetX, 0, direction.offsetZ);
    }

}
