package me.dynmie.highway.highwaytools.blueprint;

import me.dynmie.highway.highwaytools.blueprint.impl.StraightBlueprintProvider;
import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.utils.misc.MBlockPos;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlueprintGenerator {

    private final Map<BlockPos, BlueprintTask> blueprint = new ConcurrentHashMap<>();

    private final HighwayTools tools;
    private final Block mainBlock;
    private final Block fillerBlock;
    private final BlueprintProvider provider;

    public BlueprintGenerator(HighwayTools tools) {
        this.tools = tools;
//        provider = tools.getDir().diagonal ? new DiagonalBlueprintProvider(tools) : new StraightBlueprintProvider(tools);
        // TODO: 2023/06/05 not done ahhh
        provider = new StraightBlueprintProvider(tools);

        mainBlock = tools.getMainBlock().get();
        fillerBlock = tools.getFillerBlock().get();
    }

    public void generate() {
        blueprint.clear();

        MBlockPos currentPosition = tools.getPosition();

        int reach = (int) Math.ceil(tools.getReach().get().floatValue());

        // in front of player (r = 1)
//        for (int r = 1; r < reach; r++) {
        for (int r = (int) Math.floor(-reach) * 5; r <= (int) Math.ceil(reach) * 5; r++) {
            MBlockPos pos = new MBlockPos()
                .set(currentPosition)
                .offset(tools.getDir(), r);

            generateFloor(pos);
            if (tools.getRailings().get()) {
                generateRailings(pos);
            }
            if (tools.getRailings().get() && tools.getMineAboveRailings().get()) {
                generateAboveRailings(pos); // if mine above railings then gen
            }
            generateFront(pos);
        }
    }

    private void generateFront(MBlockPos basePosition) {
        List<MBlockPos> positions = provider.getFront(basePosition);

        for (MBlockPos pos : positions) {
            BlueprintTask task = new BlueprintTask(Blocks.AIR);
            blueprint.put(pos.getMcPos().mutableCopy().toImmutable(), task);
        }
    }

    private void generateFloor(MBlockPos basePosition) {
        List<MBlockPos> positions = provider.getFloor(basePosition);

        for (MBlockPos pos : positions) {
            BlueprintTask task = new BlueprintTask(mainBlock);
            blueprint.put(pos.getMcPos().mutableCopy().toImmutable(), task);
        }
    }

    private void generateRailings(MBlockPos basePosition) {
        List<MBlockPos> positions = provider.getRailings(basePosition);

        for (MBlockPos pos : positions) {
            BlueprintTask task = new BlueprintTask(mainBlock);
            blueprint.put(pos.getMcPos().mutableCopy().toImmutable(), task);
        }
    }

    private void generateAboveRailings(MBlockPos basePosition) {
        List<MBlockPos> positions = provider.getAboveRailings(basePosition);

        for (MBlockPos pos : positions) {
            BlueprintTask task = new BlueprintTask(Blocks.AIR);
            blueprint.put(pos.getMcPos().mutableCopy().toImmutable(), task);
        }
    }

    public void removePos(BlockPos pos) {
        blueprint.remove(pos);
    }

    public Map<BlockPos, BlueprintTask> getBlueprint() {
        return blueprint;
    }

}
