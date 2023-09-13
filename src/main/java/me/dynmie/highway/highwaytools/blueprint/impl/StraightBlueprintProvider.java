package me.dynmie.highway.highwaytools.blueprint.impl;

import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.highwaytools.blueprint.BlueprintProvider;
import me.dynmie.highway.utils.HighwayUtils;
import meteordevelopment.meteorclient.utils.misc.MBlockPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class StraightBlueprintProvider implements BlueprintProvider {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final MBlockPos rootPos = new MBlockPos();
    private final MBlockPos pos2 = new MBlockPos();

    private final HighwayTools tools;

    public StraightBlueprintProvider(HighwayTools tools) {
        this.tools = tools;
    }

    @Override
    public @NotNull List<MBlockPos> getFront(MBlockPos basePosition) {

        int width = tools.getWidth().get();
        int height = tools.getHeight().get();

        rootPos.set(basePosition)
            .offset(tools.getLeftDir(), tools.getWidthLeftOffset());

        List<MBlockPos> ret = new ArrayList<>();

        // loop through all positions by offset from root position
        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                MBlockPos pos = new MBlockPos()
                    .set(rootPos)
                    .add(0, h, 0)
                    .offset(tools.getRightDir(), w);

                ret.add(pos);
            }
        }

        return ret;
    }

    @Override
    public @NotNull List<MBlockPos> getFloor(MBlockPos basePosition) {

        int width = tools.getWidth().get();

        rootPos.set(basePosition)
            .offset(tools.getLeftDir(), tools.getWidthLeftOffset())
            .add(0, -1, 0);

        List<MBlockPos> ret = new ArrayList<>();

        for (int w = 0; w < width; w++) {
            MBlockPos pos = new MBlockPos()
                .set(rootPos)
                .offset(tools.getRightDir(), w);

            ret.add(pos);
        }

        return ret;
    }

    @Override
    public @NotNull List<MBlockPos> getRailings(MBlockPos basePosition) {

        rootPos.set(basePosition);

        List<MBlockPos> ret = new ArrayList<>();

        MBlockPos leftRailing = new MBlockPos()
            .set(rootPos)
            .offset(tools.getLeftDir(), tools.getWidthLeftOffset() + 1);

        ret.add(leftRailing);

        MBlockPos rightRailing = new MBlockPos()
            .set(rootPos)
            .offset(tools.getRightDir(), tools.getWidthRightOffset() + 1);

        ret.add(rightRailing);

        return ret;
    }

    @Override
    public @NotNull List<MBlockPos> getAboveRailings(MBlockPos basePosition) {

        int height = tools.getHeight().get() - 1;

        rootPos.set(basePosition)
            .add(0, 1, 0);

        List<MBlockPos> ret = new ArrayList<>();

        for (int h = 0; h < height; h++) {
            MBlockPos leftRailing = new MBlockPos()
                .set(rootPos)
                .add(0, h, 0)
                .offset(tools.getLeftDir(), tools.getWidthLeftOffset() + 1);

            ret.add(leftRailing);

            MBlockPos rightRailing = new MBlockPos()
                .set(rootPos)
                .add(0, h, 0)
                .offset(tools.getRightDir(), tools.getWidthRightOffset() + 1);

            ret.add(rightRailing);
        }

        return ret;
    }

}
