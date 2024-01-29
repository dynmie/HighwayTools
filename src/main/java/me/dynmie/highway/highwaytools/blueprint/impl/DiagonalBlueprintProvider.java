package me.dynmie.highway.highwaytools.blueprint.impl;

import me.dynmie.highway.highwaytools.blueprint.BlueprintProvider;
import me.dynmie.highway.modules.HighwayTools;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class DiagonalBlueprintProvider implements BlueprintProvider {

    private final HighwayTools tools;

    public DiagonalBlueprintProvider(HighwayTools tools) {
        this.tools = tools;
    }

    @Override
    public @NotNull List<BlockPos> getFront(BlockPos basePosition) {
        return Collections.emptyList();
    }

    @Override
    public @NotNull List<BlockPos> getFloor(BlockPos basePosition) {
        return Collections.emptyList();
    }

    @Override
    public @NotNull List<BlockPos> getRailings(BlockPos basePosition) {
        return Collections.emptyList();
    }

    @Override
    public @NotNull List<BlockPos> getAboveRailings(BlockPos position) {
        return Collections.emptyList();
    }

}
