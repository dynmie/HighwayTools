package me.dynmie.highway.modules;

import me.dynmie.highway.HighwayAddon;
import me.dynmie.highway.highwaytools.block.BlockTask;
import me.dynmie.highway.highwaytools.block.BlockTaskManager;
import me.dynmie.highway.highwaytools.block.TaskExecutor;
import me.dynmie.highway.highwaytools.block.TaskState;
import me.dynmie.highway.highwaytools.blueprint.BlueprintGenerator;
import me.dynmie.highway.highwaytools.blueprint.BlueprintTask;
import me.dynmie.highway.highwaytools.handler.BreakHandler;
import me.dynmie.highway.highwaytools.handler.InventoryHandler;
import me.dynmie.highway.highwaytools.handler.InventoryManager;
import me.dynmie.highway.highwaytools.handler.LiquidHandler;
import me.dynmie.highway.highwaytools.handler.PlaceHandler;
import me.dynmie.highway.highwaytools.pathing.BaritoneHelper;
import me.dynmie.highway.highwaytools.pathing.BaritonePathfinder;
import me.dynmie.highway.utils.IgnoreList;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class HighwayTools extends Module {

    public enum BlueprintMode {
        Highway,
        Tunnel,
        Flat
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
    private final SettingGroup sgMine = settings.createGroup("Mine");
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgDebug = settings.createGroup("Debug");
    private final SettingGroup sgStorage = settings.createGroup("Storage Management");

    // General

    private final Setting<Integer> width = sgGeneral.add(new IntSetting.Builder()
        .name("width")
        .description("Width of the highway.")
        .defaultValue(4)
        .range(1, 8)
        .sliderRange(1, 8)
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

    private final Setting<BlueprintMode> blueprintMode = sgGeneral.add(new EnumSetting.Builder<BlueprintMode>()
        .name("blueprint-mode")
        .description("What blueprint mode mode to use.")
        .defaultValue(BlueprintMode.Highway)
        .build()
    );

    private final Setting<Boolean> railings = sgGeneral.add(new BoolSetting.Builder()
        .name("railings")
        .description("Builds railings next to the highway.")
        .defaultValue(true)
        .visible(() -> blueprintMode.get() != BlueprintMode.Flat)
        .build()
    );

    private final Setting<Boolean> mineAboveRailings = sgGeneral.add(new BoolSetting.Builder()
        .name("mine-above-railings")
        .description("Mines blocks above railings.")
        .visible(() -> railings.get() && railings.isVisible())
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cornerBlock = sgGeneral.add(new BoolSetting.Builder()
        .name("corner-block")
        .description("Build a corner block at the highway's leading edge when width is larger than 2.")
        .defaultValue(true)
        .visible(() -> width.get() > 2)
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
        .filter(block -> Block.isShapeFullBlock(block.defaultBlockState().getCollisionShape(mc.level, ZERO)))
        .build()
    );

    private final Setting<Block> fillerBlock = sgGeneral.add(new BlockSetting.Builder()
        .name("filler-block")
        .description("Filler block.")
        .defaultValue(Blocks.NETHERRACK)
        .filter(block -> Block.isShapeFullBlock(block.defaultBlockState().getCollisionShape(mc.level, ZERO)))
        .build()
    );

//    private final Setting<List<Item>> trashItems = sgGeneral.add(new ItemListSetting.Builder()
//        .name("trash-items")
//        .description("Items that are considered trash and can be thrown out.")
//        .defaultValue(Items.NETHERRACK, Items.QUARTZ, Items.GOLD_NUGGET, Items.GLOWSTONE_DUST, Items.BLACKSTONE, Items.BASALT)
//        .build()
//    );
//
//    private final Setting<Boolean> dontBreakTools = sgGeneral.add(new BoolSetting.Builder()
//        .name("dont-break-tools")
//        .description("Don't break tools.")
//        .defaultValue(false)
//        .build()
//    );
//
    private final Setting<Boolean> mineEnderChests = sgGeneral.add(new BoolSetting.Builder()
        .name("mine-ender-chests")
        .description("Mines ender chests for obsidian.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> taskTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("task-timeout")
        .description("Time to wait for the server before trying again.")
        .defaultValue(8)
        .min(0)
        .sliderMax(40)
        .build()
    );

    private final Setting<Boolean> disconnectOnToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-on-toggle")
        .description("Automatically disconnects when the module is turned off, for example for not having enough blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("distance")
        .description("Stop the bot after this many blocks along the highway direction. 0 = unlimited.")
        .defaultValue(0)
        .min(0)
        .sliderMax(5000)
        .build()
    );

    private final Setting<Boolean> scaffold = sgGeneral.add(new BoolSetting.Builder()
        .name("scaffold")
        .description("Walk forward onto the built floor when the next front column has no reachable line-of-sight (Trombone-style bridging).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> moveSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("move-speed")
        .description("Movement speed used while bridging.")
        .defaultValue(0.2d)
        .min(0.01d)
        .sliderMax(0.5d)
        .build()
    );

    // Mine

    private final Setting<Boolean> preferSilkTouch = sgMine.add(new BoolSetting.Builder()
        .name("prefer-silk-touch")
        .description("Prefer silk touch pickaxes when mining blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> avoidMineGhostBlocks = sgMine.add(new BoolSetting.Builder()
        .name("avoid-ghost-blocks")
        .description("Avoid ghost blocks when mining. Disabling will allow faster mining at the cost of increased risk of ghost blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderMine = sgMine.add(new BoolSetting.Builder()
        .name("render-blocks-to-mine")
        .description("Render blocks to be mined.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> renderMineShape = sgMine.add(new EnumSetting.Builder<ShapeMode>()
        .name("mine-shape-mode")
        .description("How the blocks to be mined are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> renderMineSideColor = sgMine.add(new ColorSetting.Builder()
        .name("mine-side-color")
        .description("Color of blocks to be mined.")
        .defaultValue(new SettingColor(225, 0, 0, 26))
        .build()
    );

    private final Setting<SettingColor> renderMineLineColor = sgMine.add(new ColorSetting.Builder()
        .name("mine-line-color")
        .description("Color of blocks to be mined.")
        .defaultValue(new SettingColor(225, 0, 0, 91))
        .build()
    );

    // Place

    private final Setting<Integer> placeDelay = sgPlace.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Change the time between places.")
        .defaultValue(3)
        .min(0)
        .sliderMax(40)
        .build()
    );

    private final Setting<Boolean> adaptivePlaceDelay = sgPlace.add(new BoolSetting.Builder()
        .name("adaptive-place-delay")
        .description("Enable adaptive place delay.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderPlace = sgPlace.add(new BoolSetting.Builder()
        .name("render-blocks-to-place")
        .description("Render blocks to be placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> renderPlaceShape = sgPlace.add(new EnumSetting.Builder<ShapeMode>()
        .name("place-render-mode")
        .description("How the blocks to be placed are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> renderPlaceSideColor = sgPlace.add(new ColorSetting.Builder()
        .name("place-side-color")
        .description("Color of blocks to be placed.")
        .defaultValue(new SettingColor(0, 255, 225, 26))
        .build()
    );

    private final Setting<SettingColor> renderPlaceLineColor = sgPlace.add(new ColorSetting.Builder()
        .name("place-line-color")
        .description("Color of blocks to be placed.")
        .defaultValue(new SettingColor(0, 255, 225, 91))
        .build()
    );

    private final Setting<SettingColor> renderDoneSideColor = sgPlace.add(new ColorSetting.Builder()
        .name("done-side-color")
        .description("Color of blocks to be placed.")
        .defaultValue(new SettingColor(80, 80, 80, 26))
        .build()
    );

    private final Setting<SettingColor> renderDoneLineColor = sgPlace.add(new ColorSetting.Builder()
        .name("done-line-color")
        .description("Color of blocks to be placed.")
        .defaultValue(new SettingColor(80, 80, 80, 91))
        .build()
    );

    private final Setting<Boolean> illegalPlacements = sgPlace.add(new BoolSetting.Builder()
        .name("illegal-placements")
        .description("Click block faces that are not visible to the player to place blocks in impossible positions. Not recommended on strict anti-cheat servers.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> placementSearch = sgPlace.add(new IntSetting.Builder()
        .name("placement-search")
        .description("How many blocks deep to search for a placement support.")
        .defaultValue(2)
        .range(1, 6)
        .sliderRange(1, 6)
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

    // Storage Management

    private final Setting<Integer> saveMaterial = sgStorage.add(new IntSetting.Builder()
        .name("save-material")
        .description("Never use the last N material blocks (restock when at/below).")
        .defaultValue(64).range(0, 1728).sliderRange(0, 1728).build()
    );
    private final Setting<Integer> saveTools = sgStorage.add(new IntSetting.Builder()
        .name("save-tools")
        .description("Restock pickaxes when at/below this many.")
        .defaultValue(1).range(0, 8).sliderRange(0, 8).build()
    );
    private final Setting<Integer> saveEnder = sgStorage.add(new IntSetting.Builder()
        .name("save-ender")
        .description("Keep this many ender chests before grinding/breaking extras.")
        .defaultValue(1).range(0, 16).sliderRange(0, 16).build()
    );
    private final Setting<Boolean> grindObsidian = sgStorage.add(new BoolSetting.Builder()
        .name("grind-obsidian")
        .description("Grind obsidian from ender chests (AutoObsidian).")
        .defaultValue(false).build()
    );
    private final Setting<Boolean> restockFromEnderChest = sgStorage.add(new BoolSetting.Builder()
        .name("restock-from-ender-chest")
        .description("Pull material from ender chests when no shulker has it.")
        .defaultValue(true).build()
    );
    private final Setting<Integer> keepFreeSlots = sgStorage.add(new IntSetting.Builder()
        .name("keep-free-slots")
        .description("Keep this many inventory slots empty during restock.")
        .defaultValue(2).range(0, 8).sliderRange(0, 8).build()
    );
    private final Setting<Boolean> leaveEmptyShulkers = sgStorage.add(new BoolSetting.Builder()
        .name("leave-empty-shulkers")
        .description("Close and skip shulkers that are empty.")
        .defaultValue(true).build()
    );
    private final Setting<Boolean> preferEnderChests = sgStorage.add(new BoolSetting.Builder()
        .name("prefer-ender-chests")
        .description("Prefer ender chests over shulkers for obsidian.")
        .defaultValue(false).build()
    );

    private HorizontalDirection direction = HorizontalDirection.North;

    private BlockPos currentPosition = new BlockPos(0, 64, 0);
    private BlockPos startPosition = new BlockPos(0, 64, 0);

    private Vec3 start = new Vec3(0d, 64d, 0d);
    public int blocksBroken = 0;
    public int blocksPlaced = 0;
    private boolean displayInfo = true;

    private final ConcurrentLinkedQueue<Runnable> runnableQueue = new ConcurrentLinkedQueue<>();

    private final InventoryManager inventoryManager = new InventoryManager(this);
    private final IgnoreList ignoreList = new IgnoreList();
    private final InventoryHandler inventoryHandler = new InventoryHandler(this);
    private final BreakHandler breakHandler = new BreakHandler(this, inventoryHandler);
    private final LiquidHandler liquidHandler = new LiquidHandler(this);
    private final PlaceHandler placeHandler = new PlaceHandler(this, inventoryHandler);

    private final BaritoneHelper baritoneHelper = new BaritoneHelper(this);
    private final BaritonePathfinder pathfinder = new BaritonePathfinder(this);
    private BlueprintGenerator blueprintGenerator = new BlueprintGenerator(this);
    private final BlockTaskManager blockTaskManager = new BlockTaskManager(this, inventoryHandler);
    private final TaskExecutor taskExecutor = new TaskExecutor(this, breakHandler, inventoryHandler, liquidHandler, placeHandler);

    public HighwayTools() {
        super(HighwayAddon.CATEGORY, "highway-tools", "Automatically builds highways.");
    }

    @Override
    public void onActivate() {
        direction = HorizontalDirection.get(mc.player.getYRot());

        start = mc.player.position();
        startPosition = mc.player.blockPosition();
        currentPosition = mc.player.blockPosition();

        blocksBroken = 0;
        blocksPlaced = 0;

        blueprintGenerator = new BlueprintGenerator(this);

        baritoneHelper.setupBaritone();
        blockTaskManager.clearTasks();

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
        if (width.get() < 3 && direction.diagonal) {
            errorEarly("Diagonal highways with width less than 3 are not supported.");
            return;
        }

        blockTaskManager.updateTasks();

        Runnable runnable;
        while ((runnable = runnableQueue.poll()) != null) {
            runnable.run();
        }

        if (checkForPause()) return;

        pathfinder.updatePathing();
        blockTaskManager.runTasks();
    }

    @EventHandler
    private void onPlayerMove(meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent event) {
        pathfinder.handleMove(event);
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
            case 6, 7 -> 3;
            case 8, 9 -> 4;
            case 10, 11 -> 5;
            case 12, 13 -> 6;
        };
    }

    public int getWidthRightOffset() {
        return switch (width.get()) {
            default -> 0;
            case 3, 4 -> 1;
            case 5, 6 -> 2;
            case 7, 8 -> 3;
            case 9, 10 -> 4;
            case 11, 12 -> 5;
        };
    }

    public void disconnect(String message, Object... args) {
        MutableComponent text = Component.literal(String.format("%s[%s%s%s] %s", ChatFormatting.GRAY, ChatFormatting.BLUE, title, ChatFormatting.GRAY, ChatFormatting.RED) + String.format(message, args)).append("\n");
        text.append(getStatsText());

        mc.getConnection().getConnection().disconnect(text);
    }

    public MutableComponent getStatsText() {
        MutableComponent text = Component.literal(String.format("%sDistance: %s%.0f\n", ChatFormatting.GRAY, ChatFormatting.WHITE, mc.player == null ? 0.0f : PlayerUtils.distanceTo(start)));
        text.append(String.format("%sBlocks broken: %s%d\n", ChatFormatting.GRAY, ChatFormatting.WHITE, blocksBroken));
        text.append(String.format("%sBlocks placed: %s%d", ChatFormatting.GRAY, ChatFormatting.WHITE, blocksPlaced));

        return text;
    }

    public void runNextTick(Runnable runnable) {
        runnableQueue.add(runnable);
    }

    public IgnoreList getIgnoreList() {
        return ignoreList;
    }

    public Setting<Integer> getWidth() {
        return width;
    }

    public Setting<Integer> getHeight() {
        return height;
    }

    public Setting<BlueprintMode> getBlueprintMode() {
        return blueprintMode;
    }

    public Setting<Boolean> getRailings() {
        return railings;
    }

    public Setting<Boolean> getMineAboveRailings() {
        return mineAboveRailings;
    }

    public Setting<Boolean> getCornerBlock() {
        return cornerBlock;
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

//    public Setting<List<Item>> getTrashItems() {
//        return trashItems;
//    }
//
//    public Setting<Boolean> getDontBreakTools() {
//        return dontBreakTools;
//    }
//
    public Setting<Boolean> getMineEnderChests() {
        return mineEnderChests;
    }

    public Setting<Boolean> getDisconnectOnToggle() {
        return disconnectOnToggle;
    }

    public Setting<Integer> getDistance() {
        return distance;
    }

    public Setting<Boolean> getScaffold() {
        return scaffold;
    }

    public Setting<Double> getMoveSpeed() {
        return moveSpeed;
    }

    public Setting<Boolean> getPreferSilkTouch() {
        return preferSilkTouch;
    }

    public Setting<Boolean> getAvoidMineGhostBlocks() {
        return avoidMineGhostBlocks;
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

    public Setting<Integer> getTaskTimeout() {
        return taskTimeout;
    }

    public Setting<SettingColor> getRenderMineLineColor() {
        return renderMineLineColor;
    }

    public Setting<Integer> getPlaceDelay() {
        return placeDelay;
    }

    public Setting<Boolean> getAdaptivePlaceDelay() {
        return adaptivePlaceDelay;
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

    public Vec3 getStart() {
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

    public Setting<Boolean> getIllegalPlacements() {
        return illegalPlacements;
    }

    public Setting<Integer> getPlacementSearch() {
        return placementSearch;
    }

    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    public Setting<Integer> getSaveMaterial() {
        return saveMaterial;
    }

    public Setting<Integer> getSaveTools() {
        return saveTools;
    }

    public Setting<Integer> getSaveEnder() {
        return saveEnder;
    }

    public Setting<Boolean> getGrindObsidian() {
        return grindObsidian;
    }

    public Setting<Boolean> getRestockFromEnderChest() {
        return restockFromEnderChest;
    }

    public Setting<Integer> getKeepFreeSlots() {
        return keepFreeSlots;
    }

    public Setting<Boolean> getLeaveEmptyShulkers() {
        return leaveEmptyShulkers;
    }

    public Setting<Boolean> getPreferEnderChests() {
        return preferEnderChests;
    }
}
