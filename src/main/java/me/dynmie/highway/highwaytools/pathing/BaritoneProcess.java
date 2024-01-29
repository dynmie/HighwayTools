package me.dynmie.highway.highwaytools.pathing;

import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import me.dynmie.highway.modules.HighwayTools;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public class BaritoneProcess implements IBaritoneProcess {

    private final HighwayTools tools;
    public BaritoneProcess(HighwayTools tools) {
        this.tools = tools;
    }

    @Override
    public boolean isActive() {
        return tools.isActive() && MinecraftClient.getInstance().player != null;
    }

    @Override
    public PathingCommand onTick(boolean b, boolean b1) {

        if (tools.getPathfinder().getGoal() == null) return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);

        BlockPos pos = tools.getPathfinder().getGoal();

        if (pos == null) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        return new PathingCommand(new GoalNear(pos, 0), PathingCommandType.SET_GOAL_AND_PATH);
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    public void onLostControl() {

    }

    @Override
    public double priority() {
        return 2.0;
    }

    @Override
    public String displayName0() {
        return "Trumpet";
    }

    @Override
    public String displayName() {
        return "Trumpet";
    }
}
