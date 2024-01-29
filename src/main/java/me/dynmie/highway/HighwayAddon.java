package me.dynmie.highway;

import baritone.api.BaritoneAPI;
import com.mojang.logging.LogUtils;
import me.dynmie.highway.commands.ExampleCommand;
import me.dynmie.highway.highwaytools.pathing.BaritoneProcess;
import me.dynmie.highway.hud.HudExample;
import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class HighwayAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("MetroHT");
    public static final HudGroup HUD_GROUP = new HudGroup("MetroHT");

    @Override
    public void onInitialize() {
        HighwayTools tools = new HighwayTools();

        // Modules
        Modules.get().add(tools);
        if (tools.isActive()) tools.toggle();

        // Baritone
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingControlManager().registerProcess(new BaritoneProcess(tools));

        // Commands
        Commands.add(new ExampleCommand());

        // HUD
        Hud.get().register(HudExample.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "me.dynmie.highway";
    }
}
