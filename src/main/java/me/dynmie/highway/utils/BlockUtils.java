package me.dynmie.highway.utils;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

public class BlockUtils {

    private BlockUtils() {
        throw new UnsupportedOperationException();
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
