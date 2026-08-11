package me.dynmie.highway.highwaytools.place;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal standalone placement-sequence finder (Branch D's "intelligent placing").
 *
 * <p>The interface mirrors Branch A's {@code PlacementSearcher}: {@link #findSequence}
 * returns the best direct-support placement for a target block, preferring a support
 * face that is visible to the player over an invisible one, breaking ties by distance
 * to the eye. The deeper recursive search for floating targets is left to Branch A's
 * full implementation; this version only covers directly-supported placements.
 */
public class PlacementSearcher {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Direction[] SIDES = Direction.values();

    public List<PlacementStep> findSequence(Vec3 eyePos, BlockPos target, int depth, boolean illegal) {
        if (mc.level == null) return List.of();

        List<PlacementStep> sequence = new ArrayList<>();

        // Direct supports: the target can be placed against any adjacent solid block.
        // Collect the best one — visible faces first, then closest to the eye.
        PlacementStep best = null;
        for (Direction side : SIDES) {
            PlacementStep step = checkSupport(eyePos, target, side, illegal);
            if (step == null) continue;

            if (best == null
                || (step.isVisible() && !best.isVisible())
                || (step.isVisible() == best.isVisible() && eyePos.distanceTo(step.hitVec()) < eyePos.distanceTo(best.hitVec()))) {
                best = step;
            }
        }

        if (best != null) {
            sequence.add(best);
        }

        // depth is reserved for the recursive multi-step search (Branch A); the
        // direct-support pass above never needs more than one step.
        return sequence;
    }

    private PlacementStep checkSupport(Vec3 eyePos, BlockPos target, Direction side, boolean illegal) {
        BlockPos support = target.relative(side);
        BlockState supportState = mc.level.getBlockState(support);

        // The support must be a solid, non-replaceable block we can click against.
        if (supportState.isAir() || supportState.canBeReplaced()) return null;

        // The target itself must be placeable (currently air or replaceable).
        BlockState targetState = mc.level.getBlockState(target);
        if (!targetState.canBeReplaced()) return null;

        Vec3 normal = new Vec3(side.getStepX(), side.getStepY(), side.getStepZ());
        Vec3 hitVec = support.getCenter().add(normal.scale(0.5));

        boolean visible = isFaceVisible(eyePos, support, side, hitVec);
        if (!visible && !illegal) return null;

        return new PlacementStep(target, side, visible, hitVec);
    }

    private boolean isFaceVisible(Vec3 eyePos, BlockPos support, Direction side, Vec3 hitVec) {
        if (mc.player == null) return false;

        BlockHitResult hit = mc.level.clip(new ClipContext(eyePos, hitVec, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK
            && hit.getBlockPos().equals(support)
            && hit.getDirection() == side;
    }
}
