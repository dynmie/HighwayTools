package me.dynmie.highway.highwaytools.pathing;

import baritone.api.BaritoneAPI;
import me.dynmie.highway.modules.HighwayTools;

public class BaritoneHelper {

    private boolean allowPlace = BaritoneAPI.getSettings().allowPlace.defaultValue;
    private boolean allowBreak = BaritoneAPI.getSettings().allowBreak.defaultValue;
    private boolean renderGoal = BaritoneAPI.getSettings().renderGoal.defaultValue;
    private boolean allowInventory = BaritoneAPI.getSettings().allowInventory.defaultValue;

    private final HighwayTools tools;

    public BaritoneHelper(HighwayTools tools) {
        this.tools = tools;
    }

    public void setupBaritone() {
        allowPlace = BaritoneAPI.getSettings().allowPlace.value;
        allowBreak = BaritoneAPI.getSettings().allowBreak.value;
        renderGoal = BaritoneAPI.getSettings().renderGoal.value;
        allowInventory = BaritoneAPI.getSettings().allowInventory.value;

        BaritoneAPI.getSettings().allowPlace.value = false;
        BaritoneAPI.getSettings().allowBreak.value = false;
        BaritoneAPI.getSettings().renderGoal.value = tools.getRenderGoalPos().get();
        BaritoneAPI.getSettings().allowInventory.value = false;
    }

    public void resetBaritone() {
        BaritoneAPI.getSettings().allowPlace.value = allowPlace;
        BaritoneAPI.getSettings().allowBreak.value = allowBreak;
        BaritoneAPI.getSettings().renderGoal.value = renderGoal;
        BaritoneAPI.getSettings().allowInventory.value = allowInventory;
    }

}
