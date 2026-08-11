package me.dynmie.highway.utils;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Backed by the {@code ignore-blocks} GUI setting in the module (a BlockListSetting).
 * The default set is the same one the module seeds the setting with: the unbreakable
 * blocks plus all 16 shulker boxes. Read-only over the setting value — mutate the
 * setting (via the ClickGUI) instead of this wrapper.
 */
public class IgnoreList {

    private static final Set<Block> DEFAULT = Set.of(
        Blocks.END_PORTAL_FRAME, Blocks.BEDROCK, Blocks.NETHER_PORTAL, Blocks.END_PORTAL,
        Blocks.SHULKER_BOX, Blocks.WHITE_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX, Blocks.MAGENTA_SHULKER_BOX,
        Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX, Blocks.LIME_SHULKER_BOX, Blocks.PINK_SHULKER_BOX,
        Blocks.GRAY_SHULKER_BOX, Blocks.LIGHT_GRAY_SHULKER_BOX, Blocks.CYAN_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX,
        Blocks.BLUE_SHULKER_BOX, Blocks.BROWN_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX, Blocks.RED_SHULKER_BOX,
        Blocks.BLACK_SHULKER_BOX
    );

    private final Supplier<List<Block>> blocks;

    public IgnoreList(Supplier<List<Block>> blocks) {
        this.blocks = blocks;
    }

    /** The default ignore set — used to seed the GUI setting. */
    public static Set<Block> defaultBlocks() {
        return DEFAULT;
    }

    public List<Block> getBlocks() {
        return blocks.get();
    }

    public boolean isIgnored(Block block) {
        return blocks.get().contains(block);
    }
}
