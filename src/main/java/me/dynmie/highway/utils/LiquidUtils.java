package me.dynmie.highway.utils;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.LiquidBlock;

/**
 * @author dynmie
 */
public class LiquidUtils {

    private LiquidUtils() {
        throw new UnsupportedOperationException();
    }

    public static boolean isLiquid(BlockState state) {
        return state.getBlock() instanceof LiquidBlock;
    }

}
