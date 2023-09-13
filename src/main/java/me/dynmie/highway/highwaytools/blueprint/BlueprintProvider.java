package me.dynmie.highway.highwaytools.blueprint;

import meteordevelopment.meteorclient.utils.misc.MBlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface BlueprintProvider {
    @NotNull
    List<MBlockPos> getFront(MBlockPos basePosition);

    @NotNull
    List<MBlockPos> getFloor(MBlockPos basePosition);

    @NotNull
    List<MBlockPos> getRailings(MBlockPos basePosition);

    @NotNull
    List<MBlockPos> getAboveRailings(MBlockPos position);

}
