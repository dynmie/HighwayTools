package me.dynmie.highway.highwaytools.blueprint;

import me.dynmie.highway.highwaytools.blueprint.impl.DiagonalBlueprintProvider;
import me.dynmie.highway.highwaytools.blueprint.impl.StraightBlueprintProvider;
import me.dynmie.highway.modules.HighwayTools;
import me.dynmie.highway.utils.DirectionUtils;
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

        provider = tools.getDirection().diagonal ? new DiagonalBlueprintProvider(tools) : new StraightBlueprintProvider(tools);

        mainBlock = tools.getMainBlock().get();
        fillerBlock = tools.getFillerBlock().get();
    }

    public void generate() {
        blueprint.clear();

        BlockPos currentPosition = tools.getCurrentPosition();

        double reach = Math.ceil(tools.getReach().get().floatValue());

        for (int r = (int) Math.floor(-reach) * 5; r <= (int) Math.ceil(reach) * 5; r++) {
            BlockPos pos = currentPosition.add(DirectionUtils.toVec3i(tools.getDirection()).multiply(r));

            generateFloor(pos);

            if (tools.getBlueprintMode().get() != HighwayTools.BlueprintMode.Flat) {
                if (tools.getRailings().get()) {
                    generateRailings(pos);
                }

                if (tools.getRailings().get()
                    && tools.getMineAboveRailings().get()
                ) {
                    generateAboveRailings(pos); // if mine above railings then gen
                }
            }

            generateFront(pos);
        }
    }

    private void generateFront(BlockPos basePosition) {
        List<BlockPos> positions = provider.getFront(basePosition);

        for (BlockPos pos : positions) {
            BlueprintTask task = new BlueprintTask(Blocks.AIR);
            blueprint.put(pos, task);
        }
    }

    private void generateFloor(BlockPos basePosition) {
        List<BlockPos> positions = provider.getFloor(basePosition);

        boolean filler = tools.getBlueprintMode().get() == HighwayTools.BlueprintMode.Tunnel;
        Block block = filler ? fillerBlock : mainBlock;

        for (BlockPos pos : positions) {
            BlueprintTask task = new BlueprintTask(block, filler);
            blueprint.put(pos, task);
        }
    }

    private void generateRailings(BlockPos basePosition) {
        List<BlockPos> positions = provider.getRailings(basePosition);

        boolean filler = tools.getBlueprintMode().get() == HighwayTools.BlueprintMode.Tunnel;
        Block block = filler ? fillerBlock : mainBlock;

        for (BlockPos pos : positions) {
            BlueprintTask task = new BlueprintTask(block, filler);
            blueprint.put(pos, task);
        }
    }

    private void generateAboveRailings(BlockPos basePosition) {
        List<BlockPos> positions = provider.getAboveRailings(basePosition);

        for (BlockPos pos : positions) {
            BlueprintTask task = new BlueprintTask(Blocks.AIR);
            blueprint.put(pos, task);
        }
    }

    public void removePos(BlockPos pos) {
        blueprint.remove(pos);
    }

    public Map<BlockPos, BlueprintTask> getBlueprint() {
        return blueprint;
    }

    public BlueprintProvider getProvider() {
        return provider;
    }
}
