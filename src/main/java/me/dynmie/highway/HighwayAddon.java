package me.dynmie.highway;

import baritone.api.BaritoneAPI;
import com.mojang.logging.LogUtils;
import me.dynmie.highway.commands.CommandExample;
import me.dynmie.highway.highwaytools.pathing.BaritoneProcess;
import me.dynmie.highway.hud.HudExample;
import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class HighwayAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Highway Tools");
    public static final HudGroup HUD_GROUP = new HudGroup("Highway Tools");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Highway Tools");

        HighwayTools tools = new HighwayTools();

        // Modules
        Modules.get().add(tools);
        if (tools.isActive()) tools.toggle();

        // Baritone
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingControlManager().registerProcess(new BaritoneProcess(tools));

        // Commands
        Commands.get().add(new CommandExample());

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
