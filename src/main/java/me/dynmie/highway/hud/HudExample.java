package me.dynmie.highway.hud;

import me.dynmie.highway.HighwayAddon;
import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

public class HudExample extends HudElement {
    public static final HudElementInfo<HudExample> INFO = new HudElementInfo<>(HighwayAddon.HUD_GROUP, "highwaydebug", "HUD element example.", HudExample::new);

    public HudExample() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
//
//        int i = 1;
//        Collection<BlockTask> tasks = Modules.get().get(HighwayTools.class).getTaskManager().getBlockTasks().values();
//        for (BlockTask task : tasks) {
//            String text = task + " " + task.getTaskState() + " " + task.getBlockPos();
//            renderer.text(text, x, y + i, Color.WHITE, true);
//            i++;
//        }
//
//        setSize(renderer.textWidth("2344444132123444444444444444444444444444444", true), renderer.textHeight(true, tasks.size()));
    }
}
