package me.dynmie.highway.highwaytools.blueprint;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface BlueprintProvider {
    @NotNull
    List<BlockPos> getFront(BlockPos basePosition);

    @NotNull
    List<BlockPos> getFloor(BlockPos basePosition);

    @NotNull
    List<BlockPos> getRailings(BlockPos basePosition);

    @NotNull
    List<BlockPos> getAboveRailings(BlockPos position);

}
