package me.dynmie.highway.highwaytools.place;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * One step of a placement sequence: place a block at {@code pos} by clicking the
 * {@code side} face of the adjacent support block. {@code visible} records whether
 * that clicked face is currently visible to the player, and {@code hitVec} is the
 * exact point on the face that would be clicked.
 *
 * <p>The component layout is kept identical to Branch A's definition so that
 * Branch A's {@code PlacementSearcher} can replace this minimal version as a drop-in.
 */
public record PlacementStep(BlockPos pos, Direction side, boolean visible, Vec3 hitVec) {
    public boolean isVisible() {
        return visible;
    }
}
