package me.dynmie.highway.utils;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Set;

public class IgnoreList {
    private static final Set<Block> DEFAULT = new HashSet<>(Set.of(
        Blocks.END_PORTAL_FRAME, Blocks.BEDROCK, Blocks.NETHER_PORTAL, Blocks.END_PORTAL,
        Blocks.SHULKER_BOX, Blocks.WHITE_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX, Blocks.MAGENTA_SHULKER_BOX,
        Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX, Blocks.LIME_SHULKER_BOX, Blocks.PINK_SHULKER_BOX,
        Blocks.GRAY_SHULKER_BOX, Blocks.LIGHT_GRAY_SHULKER_BOX, Blocks.CYAN_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX,
        Blocks.BLUE_SHULKER_BOX, Blocks.BROWN_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX, Blocks.RED_SHULKER_BOX,
        Blocks.BLACK_SHULKER_BOX
    ));

    private final Set<Block> blocks = new HashSet<>(DEFAULT);

    public Set<Block> getBlocks() {
        return blocks;
    }

    public boolean isIgnored(Block block) {
        return blocks.contains(block);
    }

    public boolean add(Block block) {
        return blocks.add(block);
    }

    public boolean remove(Block block) {
        return blocks.remove(block);
    }
}
