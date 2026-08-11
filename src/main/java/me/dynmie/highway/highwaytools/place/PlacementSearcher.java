package me.dynmie.highway.highwaytools.place;

import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class PlacementSearcher {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final Direction[] SIDES = Direction.values();

    private final HighwayTools tools;

    public PlacementSearcher(HighwayTools tools) {
        this.tools = tools;
    }

    /**
     * Find a chain of support positions to place a block at {@code target}.
     * The caller places against {@code result.get(result.size()-1)} (the last step).
     * Returns empty list when no reachable support exists.
     */
    public List<PlacementStep> findSequence(Vec3 eyePos, BlockPos target, int depth, boolean illegal) {
        List<PlacementStep> sequence = new ArrayList<>();

        // 1. Direct support: any adjacent solid, non-replaceable block within reach.
        for (Direction side : SIDES) {
            PlacementStep step = checkNeighbor(eyePos, target, side, illegal, true);
            if (step != null) {
                sequence.add(step);
                return sequence;
            }
        }

        // 2. Deep search: recurse through adjacent positions up to `depth`.
        if (depth > 1) {
            for (Direction side : SIDES) {
                BlockPos neighbor = target.relative(side);
                PlacementStep step = checkNeighbor(eyePos, target, side, illegal, false);
                if (step == null) continue;

                List<PlacementStep> sub = findSequence(eyePos, neighbor, depth - 1, illegal);
                if (!sub.isEmpty()) {
                    sequence.add(step);
                    sequence.addAll(sub);
                    return sequence;
                }
            }
        }

        return sequence;
    }

    /**
     * Check one neighbour of {@code target} as a potential support.
     * If {@code checkReplaceable}, require the neighbour to be solid and non-replaceable.
     */
    private PlacementStep checkNeighbor(Vec3 eyePos, BlockPos target, Direction side, boolean illegal, boolean checkReplaceable) {
        BlockPos supportPos = target.relative(side);
        Direction clickSide = side.getOpposite();

        // illegal=false → require the face to be visible (normal); illegal=true → click invisible faces too
        if (!illegal && !isFaceVisible(supportPos, clickSide)) return null;

        if (checkReplaceable) {
            BlockState supportState = mc.level.getBlockState(supportPos);
            if (supportState.isAir() || supportState.canBeReplaced()) return null;
        }

        Vec3 hitVec = Vec3.atCenterOf(supportPos).add(
            clickSide.getStepX() * 0.5,
            clickSide.getStepY() * 0.5,
            clickSide.getStepZ() * 0.5
        );

        if (eyePos.distanceTo(hitVec) > tools.getReach().get()) return null;
        if (!BlockUtils.canPlace(target)) return null;

        return new PlacementStep(supportPos, clickSide, hitVec, target);
    }

    private boolean isFaceVisible(BlockPos supportPos, Direction side) {
        // Conservative: the face is visible if the adjacent block (supportPos offset by side) is air or replaceable.
        BlockState adj = mc.level.getBlockState(supportPos.relative(side));
        return adj.isAir() || adj.canBeReplaced();
    }
}
