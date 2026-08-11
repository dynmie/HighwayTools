package me.dynmie.highway;

import baritone.api.BaritoneAPI;
import me.dynmie.highway.commands.CheckBlocksCommand;
import me.dynmie.highway.highwaytools.pathing.BaritoneProcess;
import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class HighwayAddon extends MeteorAddon {

    public static final Category CATEGORY = new Category("MetroHT");

    @Override
    public void onInitialize() {
        HighwayTools tools = new HighwayTools();

        // Modules
        Modules.get().add(tools);
        if (tools.isActive()) tools.toggle();

        // Baritone
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingControlManager().registerProcess(new BaritoneProcess(tools));

        // Commands
        Commands.add(new CheckBlocksCommand(tools));
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
