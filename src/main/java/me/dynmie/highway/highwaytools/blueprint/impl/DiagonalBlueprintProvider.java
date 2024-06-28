package me.dynmie.highway.highwaytools.blueprint.impl;

import me.dynmie.highway.highwaytools.blueprint.BlueprintProvider;
import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.utils.misc.MBlockPos;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DiagonalBlueprintProvider implements BlueprintProvider {

    private final HighwayTools tools;

    public DiagonalBlueprintProvider(HighwayTools tools) {
        this.tools = tools;
    }

    @Override
    public @NotNull List<BlockPos> getFront(BlockPos basePosition) {

        int width = tools.getWidth().get();
        int height = tools.getHeight().get();

        MBlockPos rootPos = new MBlockPos()
            .set(basePosition.getX(), basePosition.getY(), basePosition.getZ())
            .offset(tools.getLeftDirection(), tools.getWidthLeftOffset());

        List<BlockPos> ret = new ArrayList<>();

        // loop through all positions by offset from root position
        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                MBlockPos pos = new MBlockPos()
                    .set(rootPos)
                    .add(0, h, 0)
                    .offset(tools.getRightDirection(), w);

                ret.add(new BlockPos(pos.x, pos.y, pos.z));

                if (w != width - 1) {
                    pos.offset(tools.getRightDirection().rotateRight());

                    ret.add(new BlockPos(pos.x, pos.y, pos.z));
                }
            }
        }

        return ret;
    }

    @Override
    public @NotNull List<BlockPos> getFloor(BlockPos basePosition) {

        int width = tools.getWidth().get();

        MBlockPos rootPos = new MBlockPos()
            .set(basePosition.getX(), basePosition.getY(), basePosition.getZ())
            .offset(tools.getLeftDirection(), tools.getWidthLeftOffset())
            .add(0, -1, 0);

        List<BlockPos> ret = new ArrayList<>();

        for (int w = 0; w < width; w++) {
            MBlockPos pos = new MBlockPos()
                .set(rootPos)
                .offset(tools.getRightDirection(), w);

            ret.add(new BlockPos(pos.x, pos.y, pos.z));

            if (w != width - 1) {
                pos.offset(tools.getRightDirection().rotateRight());

                ret.add(new BlockPos(pos.x, pos.y, pos.z));
            }
        }

        return ret;
    }

    @Override
    public @NotNull List<BlockPos> getRailings(BlockPos basePosition) {

        MBlockPos rootPos = new MBlockPos()
            .set(basePosition.getX(), basePosition.getY(), basePosition.getZ());

        List<BlockPos> ret = new ArrayList<>();

        MBlockPos leftRailing = new MBlockPos()
            .set(rootPos)
            .offset(tools.getLeftDirection(), tools.getWidthLeftOffset() + 1)
            .offset(tools.getRightDirection().rotateRight());

        ret.add(new BlockPos(leftRailing.x, leftRailing.y, leftRailing.z));

        MBlockPos rightRailing = new MBlockPos()
            .set(rootPos)
            .offset(tools.getRightDirection(), tools.getWidthRightOffset() + 1)
            .offset(tools.getLeftDirection().rotateLeft());

        ret.add(new BlockPos(rightRailing.x, rightRailing.y, rightRailing.z));

        return ret;
    }

    @Override
    public @NotNull List<BlockPos> getAboveRailings(BlockPos basePosition) {

        int height = tools.getHeight().get() - 1;

        MBlockPos rootPos = new MBlockPos()
            .set(basePosition.getX(), basePosition.getY(), basePosition.getZ())
            .add(0, 1, 0);

        List<BlockPos> ret = new ArrayList<>();

        for (int h = 0; h < height; h++) {
            MBlockPos leftRailing = new MBlockPos()
                .set(rootPos)
                .add(0, h, 0)
                .offset(tools.getLeftDirection(), tools.getWidthLeftOffset() + 1)
                .offset(tools.getRightDirection().rotateRight());

            ret.add(new BlockPos(leftRailing.x, leftRailing.y, leftRailing.z));

            MBlockPos rightRailing = new MBlockPos()
                .set(rootPos)
                .add(0, h, 0)
                .offset(tools.getRightDirection(), tools.getWidthRightOffset() + 1)
                .offset(tools.getLeftDirection().rotateLeft());

            ret.add(new BlockPos(rightRailing.x, rightRailing.y, rightRailing.z));
        }

        return ret;
    }

}
