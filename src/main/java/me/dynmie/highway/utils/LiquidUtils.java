package me.dynmie.highway.utils;

import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;

/**
 * @author dynmie
 */
public class LiquidUtils {

    private LiquidUtils() {
        throw new UnsupportedOperationException();
    }

    public static boolean isLiquid(BlockState state) {
        return state.getBlock() instanceof FluidBlock;
    }

}
