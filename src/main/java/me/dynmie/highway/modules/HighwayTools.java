package me.dynmie.highway.modules;

import me.dynmie.highway.HighwayAddon;
import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.BlockTaskManager;
import me.dynmie.highway.highwaytools.block.TaskExecutor;
import me.dynmie.highway.highwaytools.block.TaskState;
import me.dynmie.highway.highwaytools.blueprint.BlueprintGenerator;
import me.dynmie.highway.highwaytools.blueprint.BlueprintTask;
import me.dynmie.highway.highwaytools.pathing.BaritoneHelper;
import me.dynmie.highway.highwaytools.pathing.BaritonePathfinder;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;

public class HighwayTools extends Module {

    public enum Floor {
        Replace,
        PlaceMissing
    }

    public enum Rotation {
        None(false, false),
        Mine(true, false),
        Place(false, true),
        Both(true, true);

        public final boolean mine;
        public final boolean place;

        Rotation(boolean mine, boolean place) {
            this.mine = mine;
            this.place = place;
        }
    }

    private static final BlockPos ZERO = new BlockPos(0, 0, 0);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRenderMine = settings.createGroup("Render Mine");
    private final SettingGroup sgRenderPlace = settings.createGroup("Render Place");
    private final SettingGroup sgDebug = settings.createGroup("Debug");

    // General

    private final Setting<Integer> width = sgGeneral.add(new IntSetting.Builder()
        .name("width")
        .description("Width of the highway.")
        .defaultValue(4)
        .range(1, 5)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Integer> height = sgGeneral.add(new IntSetting.Builder()
        .name("height")
        .description("Height of the highway.")
        .defaultValue(3)
        .range(2, 5)
        .sliderRange(2, 5)
        .build()
    );

    private final Setting<Floor> floor = sgGeneral.add(new EnumSetting.Builder<Floor>()
        .name("floor")
        .description("What floor placement mode to use.")
        .defaultValue(Floor.Replace)
        .build()
    );

    private final Setting<Boolean> railings = sgGeneral.add(new BoolSetting.Builder()
        .name("railings")
        .description("Builds railings next to the highway.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> mineAboveRailings = sgGeneral.add(new BoolSetting.Builder()
        .name("mine-above-railings")
        .description("Mines blocks above railings.")
        .visible(railings::get)
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shuffle = sgGeneral.add(new BoolSetting.Builder()
        .name("shuffle")
        .description("Should shuffle tasks.")
        .defaultValue(false)
        .build()
    );


    private final Setting<Rotation> rotation = sgGeneral.add(new EnumSetting.Builder<Rotation>()
        .name("rotation")
        .description("Mode of rotation.")
        .defaultValue(Rotation.Both)
        .build()
    );

    private final Setting<Boolean> rotateCamera = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate-camera")
        .description("Rotate the camera.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> reach = sgGeneral.add(new DoubleSetting.Builder()
        .name("reach")
        .description("How far you can reach.")
        .defaultValue(4d)
        .min(0d)
        .sliderMax(6d)
        .build()
    );

    private final Setting<Block> mainBlock = sgGeneral.add(new BlockSetting.Builder()
        .name("block-to-place")
        .description("Main block to place")
        .defaultValue(Blocks.OBSIDIAN)
        .filter(block -> Block.isShapeFullCube(block.getDefaultState().getCollisionShape(mc.world, ZERO)))
        .build()
    );

    private final Setting<Block> fillerBlock = sgGeneral.add(new BlockSetting.Builder()
        .name("filler-block")
        .description("Filler block.")
        .defaultValue(Blocks.NETHERRACK)
        .filter(block -> Block.isShapeFullCube(block.getDefaultState().getCollisionShape(mc.world, ZERO)))
        .build()
    );

    private final Setting<List<Item>> trashItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("trash-items")
        .description("Items that are considered trash and can be thrown out.")
        .defaultValue(Items.NETHERRACK, Items.QUARTZ, Items.GOLD_NUGGET, Items.GLOWSTONE_DUST, Items.BLACKSTONE, Items.BASALT)
        .build()
    );

    private final Setting<Boolean> dontBreakTools = sgGeneral.add(new BoolSetting.Builder()
        .name("dont-break-tools")
        .description("Don't break tools.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> mineEnderChests = sgGeneral.add(new BoolSetting.Builder()
        .name("mine-ender-chests")
        .description("Mines ender chests for obsidian.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disconnectOnToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-on-toggle")
        .description("Automatically disconnects when the module is turned off, for example for not having enough blocks.")
        .defaultValue(false)
        .build()
    );

    // Render Mine

    private final Setting<Boolean> renderMine = sgRenderMine.add(new BoolSetting.Builder()
        .name("render-blocks-to-mine")
        .description("Render blocks to be mined.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> renderMineShape = sgRenderMine.add(new EnumSetting.Builder<ShapeMode>()
        .name("blocks-to-mine-shape-mode")
        .description("How the blocks to be mined are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> renderMineSideColor = sgRenderMine.add(new ColorSetting.Builder()
        .name("blocks-to-mine-side-color")
        .description("Color of blocks to be mined.")
        .defaultValue(new SettingColor(225, 0, 0, 26))
        .build()
    );

    private final Setting<SettingColor> renderMineLineColor = sgRenderMine.add(new ColorSetting.Builder()
        .name("blocks-to-mine-line-color")
        .description("Color of blocks to be mined.")
        .defaultValue(new SettingColor(225, 255, 0, 91))
        .build()
    );

    // Render Place

    private final Setting<Boolean> renderPlace = sgRenderPlace.add(new BoolSetting.Builder()
        .name("render-blocks-to-place")
        .description("Render blocks to be placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> renderPlaceShape = sgRenderPlace.add(new EnumSetting.Builder<ShapeMode>()
        .name("blocks-to-place-shape-mode")
        .description("How the blocks to be placed are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> renderPlaceSideColor = sgRenderPlace.add(new ColorSetting.Builder()
        .name("blocks-to-place-side-color")
        .description("Color of blocks to be placed.")
        .defaultValue(new SettingColor(0, 255, 225, 26))
        .build()
    );

    private final Setting<SettingColor> renderPlaceLineColor = sgRenderPlace.add(new ColorSetting.Builder()
        .name("blocks-to-place-line-color")
        .description("Color of blocks to be placed.")
        .defaultValue(new SettingColor(0, 255, 225, 91))
        .build()
    );

    private final Setting<SettingColor> renderDoneSideColor = sgRenderPlace.add(new ColorSetting.Builder()
        .name("blocks-to-done-side-color")
        .description("Color of blocks to be placed.")
        .defaultValue(new SettingColor(80, 80, 80, 26))
        .build()
    );

    private final Setting<SettingColor> renderDoneLineColor = sgRenderPlace.add(new ColorSetting.Builder()
        .name("blocks-to-done-line-color")
        .description("Color of blocks to be placed.")
        .defaultValue(new SettingColor(80, 80, 80, 91))
        .build()
    );

    // Debug

    private final Setting<Boolean> renderGoalPos = sgDebug.add(new BoolSetting.Builder()
        .name("render-goal-pos")
        .description("Render the baritone goal position.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> renderGeneratedBlueprint = sgDebug.add(new BoolSetting.Builder()
        .name("render-generated-blueprint")
        .description("Render the generated blueprint.")
        .defaultValue(false)
        .build()
    );

    private HorizontalDirection direction = HorizontalDirection.North;

    private BlockPos currentPosition = new BlockPos(0, 64, 0);
    private BlockPos startPosition = new BlockPos(0, 64, 0);
    ;

    public Vec3d start = new Vec3d(0d, 64d, 0d);
    public int blocksBroken = 0;
    public int blocksPlaced = 0;
    private boolean displayInfo = true;

    private final BaritoneHelper baritoneHelper = new BaritoneHelper(this);
    private final BaritonePathfinder pathfinder = new BaritonePathfinder(this);
    private final BlueprintGenerator blueprintGenerator = new BlueprintGenerator(this);
    private final BlockTaskManager blockTaskManager = new BlockTaskManager(this);
    private final TaskExecutor taskExecutor = new TaskExecutor(this);

    public HighwayTools() {
        super(HighwayAddon.CATEGORY, "highway-tools", "Automatically builds highways.");
    }

    @Override
    public void onActivate() {
        baritoneHelper.setupBaritone();
        blockTaskManager.clearTasks();

        direction = HorizontalDirection.get(mc.player.getYaw());

        start = mc.player.getPos();
        startPosition = mc.player.getBlockPos();
        currentPosition = mc.player.getBlockPos();

        blocksBroken = 0;
        blocksPlaced = 0;

        displayInfo = true;
    }

    @Override
    public void onDeactivate() {
        if (displayInfo) {
            info("Distance: (highlight)%.0f", PlayerUtils.distanceTo(start));
            info("Blocks broken: (highlight)%d", blocksBroken);
            info("Blocks placed: (highlight)%d", blocksPlaced);
        }

        pathfinder.resetPathing();
        baritoneHelper.resetBaritone();

    }

    @Override
    public void error(String message, Object... args) {
        super.error(message, args);
        toggle();

        if (disconnectOnToggle.get()) {
            disconnect(message, args);
        }
    }

    private void errorEarly(String message, Object... args) {
        super.error(message, args);

        displayInfo = false;
        toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
//        if (width.get() < 3 && dir.diagonal) {
//            errorEarly("Diagonal highways with width less than 3 are not supported.");
//            return;
//        }

//        if (checkForPause()) return;

        blockTaskManager.updateTasks();
        if (checkForPause()) return;
        pathfinder.updatePathing();
        blockTaskManager.runTasks();

    }

    public boolean checkForPause() {
        if (Modules.get().get(AutoEat.class).eating) return true;
        if (Modules.get().get(AutoGap.class).isEating()) return true;
        return false;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        for (Map.Entry<BlockPos, BlockTask> entry : blockTaskManager.getBlockTasks().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockTask task = entry.getValue();

            if (task.getBlueprintTask().getTargetBlock().equals(Blocks.AIR) && task.getTaskState() == TaskState.DONE)
                continue;

            if (!(task.getTaskState() == TaskState.BREAK || task.getTaskState() == TaskState.PLACE || task.getTaskState() == TaskState.DONE || task.getTaskState() == TaskState.BREAKING || task.getTaskState() == TaskState.BROKEN)) {
                continue;
            }

            Color sideColor;
            if (task.getTaskState() == TaskState.BREAK) {
                sideColor = renderMineSideColor.get();
            } else if (task.getTaskState() == TaskState.PLACE) {
                sideColor = renderPlaceSideColor.get();
            } else if (task.getTaskState() == TaskState.DONE) {
                sideColor = renderDoneSideColor.get();
            } else if (task.getTaskState() == TaskState.BREAKING) {
                sideColor = new Color(0, 0, 255, 26);
            } else {
                sideColor = new Color(255, 255, 255, 26);
            }

            Color lineColor;
            if (task.getTaskState() == TaskState.BREAK) {
                lineColor = renderMineLineColor.get();
            } else if (task.getTaskState() == TaskState.PLACE) {
                lineColor = renderPlaceLineColor.get();
            } else if (task.getTaskState() == TaskState.DONE) {
                lineColor = renderDoneLineColor.get();
            } else if (task.getTaskState() == TaskState.BREAKING) {
                lineColor = new Color(0, 0, 255, 91);
            } else {
                lineColor = new Color(255, 255, 255, 91);
            }

            event.renderer.box(pos, sideColor, lineColor, ShapeMode.Both, 0);
        }

        // DEBUG
        if (renderGeneratedBlueprint.get()) {
            for (Map.Entry<BlockPos, BlueprintTask> entry : blueprintGenerator.getBlueprint().entrySet()) {
                event.renderer.box(
                    entry.getKey(),
                    new Color(0, 0, 255, 10),
                    new Color(0, 0, 255, 91),
                    ShapeMode.Both,
                    0
                );
            }
        }
    }

    public int getWidthLeftOffset() {
        return switch (width.get()) {
            default -> 0;
            case 2, 3 -> 1;
            case 4, 5 -> 2;
        };
    }

    public int getWidthRightOffset() {
        return switch (width.get()) {
            default -> 0;
            case 3, 4 -> 1;
            case 5 -> 2;
        };
    }

    public void disconnect(String message, Object... args) {
        MutableText text = Text.literal(String.format("%s[%s%s%s] %s", Formatting.GRAY, Formatting.BLUE, title, Formatting.GRAY, Formatting.RED) + String.format(message, args)).append("\n");
        text.append(getStatsText());

        mc.getNetworkHandler().getConnection().disconnect(text);
    }

    public MutableText getStatsText() {
        MutableText text = Text.literal(String.format("%sDistance: %s%.0f\n", Formatting.GRAY, Formatting.WHITE, mc.player == null ? 0.0f : PlayerUtils.distanceTo(start)));
        text.append(String.format("%sBlocks broken: %s%d\n", Formatting.GRAY, Formatting.WHITE, blocksBroken));
        text.append(String.format("%sBlocks placed: %s%d", Formatting.GRAY, Formatting.WHITE, blocksPlaced));

        return text;
    }

    public Setting<Integer> getWidth() {
        return width;
    }

    public Setting<Integer> getHeight() {
        return height;
    }

    public Setting<Floor> getFloor() {
        return floor;
    }

    public Setting<Boolean> getRailings() {
        return railings;
    }

    public Setting<Boolean> getMineAboveRailings() {
        return mineAboveRailings;
    }

    public Setting<Rotation> getRotation() {
        return rotation;
    }

    public Setting<Double> getReach() {
        return reach;
    }

    public Setting<Block> getMainBlock() {
        return mainBlock;
    }

    public Setting<Block> getFillerBlock() {
        return fillerBlock;
    }

    public Setting<List<Item>> getTrashItems() {
        return trashItems;
    }

    public Setting<Boolean> getDontBreakTools() {
        return dontBreakTools;
    }

    public Setting<Boolean> getMineEnderChests() {
        return mineEnderChests;
    }

    public Setting<Boolean> getDisconnectOnToggle() {
        return disconnectOnToggle;
    }

    public Setting<Boolean> getRenderMine() {
        return renderMine;
    }

    public Setting<ShapeMode> getRenderMineShape() {
        return renderMineShape;
    }

    public Setting<SettingColor> getRenderMineSideColor() {
        return renderMineSideColor;
    }

    public Setting<SettingColor> getRenderMineLineColor() {
        return renderMineLineColor;
    }

    public Setting<Boolean> getRenderPlace() {
        return renderPlace;
    }

    public Setting<ShapeMode> getRenderPlaceShape() {
        return renderPlaceShape;
    }

    public Setting<SettingColor> getRenderPlaceSideColor() {
        return renderPlaceSideColor;
    }

    public Setting<SettingColor> getRenderPlaceLineColor() {
        return renderPlaceLineColor;
    }

    public HorizontalDirection getDirection() {
        return direction;
    }

    public HorizontalDirection getLeftDirection() {
        return direction.rotateLeftSkipOne();
    }

    public HorizontalDirection getRightDirection() {
        return getLeftDirection().opposite();
    }

    public BlockPos getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(BlockPos currentPosition) {
        this.currentPosition = currentPosition;
    }

    public BlockPos getStartPosition() {
        return startPosition;
    }

    public Vec3d getStart() {
        return start;
    }

    public int getBlocksBroken() {
        return blocksBroken;
    }

    public int getBlocksPlaced() {
        return blocksPlaced;
    }

    public void setBlocksBroken(int blocksBroken) {
        this.blocksBroken = blocksBroken;
    }

    public void setBlocksPlaced(int blocksPlaced) {
        this.blocksPlaced = blocksPlaced;
    }

    public Setting<Boolean> getRenderGoalPos() {
        return renderGoalPos;
    }

    public BlueprintGenerator getBlueprintGenerator() {
        return blueprintGenerator;
    }

    public BlockTaskManager getTaskManager() {
        return blockTaskManager;
    }

    public Setting<Boolean> getShuffle() {
        return shuffle;
    }

    public BaritonePathfinder getPathfinder() {
        return pathfinder;
    }

    public TaskExecutor getTaskExecutor() {
        return taskExecutor;
    }

    public Setting<Boolean> getRotateCamera() {
        return rotateCamera;
    }
}
