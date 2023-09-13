package me.dynmie.highway.highwaytools.blueprint;

import net.minecraft.block.Block;

public class BlueprintTask {

    private final Block targetBlock;
    private final boolean isFiller;

    public BlueprintTask(Block targetBlock, boolean isFiller) {
        this.targetBlock = targetBlock;
        this.isFiller = isFiller;
    }

    public BlueprintTask(Block targetBlock) {
        this.targetBlock = targetBlock;
        this.isFiller = false;
    }

    public Block getTargetBlock() {
        return targetBlock;
    }

    public boolean isFiller() {
        return isFiller;
    }
}
