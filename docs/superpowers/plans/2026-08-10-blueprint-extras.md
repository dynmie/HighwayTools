# Blueprint Extras Implementation Plan (Branch D)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the remaining blueprint-level features from the Lambda README todo: **corner blocks**, an **ignore list** (blocks never broken) with `;ht ignore add/del` commands, a **distance limit** (`;ht distance N`), and **intelligent placing by block side** (prefer a visible support face).

**Architecture:** Extend the blueprint providers (`StraightBlueprintProvider`/`DiagonalBlueprintProvider`) to emit corner blocks; generalize the hardcoded unbreakable-blocks checks in `BlockTaskManager`/`TaskExecutor` into a mutable `IgnoreList`; add a `distance` setting to `BaritonePathfinder`/`HighwayTools.onTick`; and refine the `PlacementSearcher` from Branch A (or a minimal standalone version) to prefer visible supports. Independent of Branches A/B/C.

**Tech Stack:** Java 25, Minecraft 26.1.2 (mojmap), Meteor Client 26.1.2-SNAPSHOT.

## Global Constraints

- Mojmap names only.
- `IgnoreList` default = current hardcoded unbreakables (END_PORTAL_FRAME, BEDROCK, NETHER_PORTAL, END_PORTAL) + all 16 shulker boxes.
- `distance` = 0 means unlimited (default).
- If Branch A (`PlacementSearcher`) is not merged yet, this branch implements the "intelligent placing" preference as a small standalone heuristic so the branch is self-contained.
- `./gradlew build` is the primary verification; no unit tests.
- No `Co-Authored-By` trailers; no agent-generation claims in commits.
- Worktree branch: `feature/blueprint-extras`.

---

### Task 1: `IgnoreList` utility + generalize unbreakable checks

**Files:**
- Create: `src/main/java/me/dynmie/highway/utils/IgnoreList.java`
- Modify: `src/main/java/me/dynmie/highway/highwaytools/block/BlockTaskManager.java`
- Modify: `src/main/java/me/dynmie/highway/highwaytools/block/TaskExecutor.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `IgnoreList.isIgnored(Block block)` static; `HighwayTools.getIgnoreList()` returns the mutable set.

- [ ] **Step 1: Write `IgnoreList`**

```java
package me.dynmie.highway.utils;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Set;

public class IgnoreList {
    private static final Set<Block> DEFAULT = new HashSet<>(Set.of(
        Blocks.END_PORTAL_FRAME, Blocks.BEDROCK, Blocks.NETHER_PORTAL, Blocks.END_PORTAL
    ));

    private final Set<Block> blocks = new HashSet<>(DEFAULT);

    public Set<Block> getBlocks() { return blocks; }
    public boolean isIgnored(Block block) { return blocks.contains(block); }
    public boolean add(Block block) { return blocks.add(block); }
    public boolean remove(Block block) { return blocks.remove(block); }
}
```

- [ ] **Step 2: Replace hardcoded unbreakable checks**

In `BlockTaskManager.generateTask`, replace:

```java
        if (currentBlock.equals(Blocks.END_PORTAL_FRAME)
            || currentBlock.equals(Blocks.BEDROCK)
            || currentBlock.equals(Blocks.NETHER_PORTAL)
            || currentBlock.equals(Blocks.END_PORTAL)
        ) {
```

with:

```java
        if (tools.getIgnoreList().isIgnored(currentBlock)) {
```

Do the same in `TaskExecutor.doBreak` (the `currentBlock.equals(Blocks.END_PORTAL_FRAME) || ...` chain near the top of the method).

- [ ] **Step 3: Expose on `HighwayTools`**

```java
    private final IgnoreList ignoreList = new IgnoreList();
    public IgnoreList getIgnoreList() { return ignoreList; }
```

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/dynmie/highway/utils/IgnoreList.java src/main/java/me/dynmie/highway/highwaytools/block/BlockTaskManager.java src/main/java/me/dynmie/highway/highwaytools/block/TaskExecutor.java src/main/java/me/dynmie/highway/modules/HighwayTools.java
git commit -m "feat: add block ignore list, generalize unbreakable checks"
```

---

### Task 2: `;ht ignore` command

**Files:**
- Create: `src/main/java/me/dynmie/highway/commands/IgnoreCommand.java`
- Modify: `src/main/java/me/dynmie/highway/HighwayAddon.java`

**Interfaces:**
- Consumes: `HighwayTools.getIgnoreList()`.
- Produces: `IgnoreCommand` registered in `HighwayAddon.onInitialize()`, alias `ht`.

Meteor command pattern (matches `CheckBlocksCommand`):

```java
package me.dynmie.highway.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class IgnoreCommand extends Command {
    private final HighwayTools tools;

    public IgnoreCommand(HighwayTools tools) {
        super("ht", "HighwayTools ignore-list management.");
        this.tools = tools;
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder
            .then(literal("ignore")
                .then(literal("add")
                    .then(argument("block", BlockArgumentType.block())
                        .executes(context -> {
                            Block block = BlockArgumentType.getBlock(context, "block");
                            tools.getIgnoreList().add(block);
                            info("Added " + block.getName().getString() + " to ignore list.");
                            return SINGLE_SUCCESS;
                        })))
                .then(literal("del")
                    .then(argument("block", BlockArgumentType.block())
                        .executes(context -> {
                            Block block = BlockArgumentType.getBlock(context, "block");
                            tools.getIgnoreList().remove(block);
                            info("Removed " + block.getName().getString() + " from ignore list.");
                            return SINGLE_SUCCESS;
                        }))))
            .then(literal("list")
                .executes(context -> {
                    info("Ignored blocks: " + tools.getIgnoreList().getBlocks());
                    return SINGLE_SUCCESS;
                }));
    }
}
```

Register in `HighwayAddon.onInitialize()`:

```java
        Commands.add(new IgnoreCommand(tools));
```

(If `meteordevelopment.meteorclient.commands.arguments.BlockArgumentType` isn't the right import in 26.1, check `meteordevelopment/meteorclient/commands/arguments/` in the jar; `Command.argument()`/`literal()` come from the `Command` base class.)

- [ ] **Step 1: Create, register, build**

Write the command, register it, run `./gradlew build` — expect BUILD SUCCESSFUL.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/me/dynmie/highway/commands/IgnoreCommand.java src/main/java/me/dynmie/highway/HighwayAddon.java
git commit -m "feat: ht ignore add/del/list command"
```

---

### Task 3: `corner-block` setting + provider emission

**Files:**
- Modify: `src/main/java/me/dynmie/highway/modules/HighwayTools.java`
- Modify: `src/main/java/me/dynmie/highway/highwaytools/blueprint/impl/StraightBlueprintProvider.java`
- Modify: `src/main/java/me/dynmie/highway/highwaytools/blueprint/impl/DiagonalBlueprintProvider.java`
- Modify: `src/main/java/me/dynmie/highway/highwaytools/blueprint/BlueprintGenerator.java`

**Interfaces:**
- Produces: `getCornerBlock()` setting accessor.

- [ ] **Step 1: Add the setting**

In `HighwayTools`, in `sgGeneral`:

```java
    private final Setting<Boolean> cornerBlock = sgGeneral.add(new BoolSetting.Builder()
        .name("corner-block")
        .description("Build a corner block at the highway's leading edge when width is larger than 2.")
        .defaultValue(true)
        .visible(() -> width.get() > 2)
        .build()
    );
```

Accessor: `public Setting<Boolean> getCornerBlock() { return cornerBlock; }`.

- [ ] **Step 2: Providers emit the corner**

In both `StraightBlueprintProvider` and `DiagonalBlueprintProvider`, in `getFront`, after the existing loops, emit one corner block at the leading-center edge when enabled. The corner is the front-center column's top corner: `basePosition` offset toward the travel direction + up by `height`. Concretely, add to each `getFront`:

```java
        if (tools.getCornerBlock().get() && tools.getWidth().get() > 2) {
            // leading corner: front-center, at the height of the front wall
            ret.add(basePosition
                .offset(tools.getDirection().offsetX, 0, tools.getDirection().offsetZ)  // one ahead
                .above(height - 1));
        }
```

(Adjust to match each provider's coordinate conventions; the exact corner position should be the top-front-center. Verify by checking where `getFront` positions land relative to `basePosition`.)

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/dynmie/highway/modules/HighwayTools.java src/main/java/me/dynmie/highway/highwaytools/blueprint/impl/StraightBlueprintProvider.java src/main/java/me/dynmie/highway/highwaytools/blueprint/impl/DiagonalBlueprintProvider.java
git commit -m "feat: corner block in straight and diagonal blueprints"
```

---

### Task 4: `distance` setting + stop after N blocks

**Files:**
- Modify: `src/main/java/me/dynmie/highway/modules/HighwayTools.java`
- Modify: `src/main/java/me/dynmie/highway/highwaytools/pathing/BaritonePathfinder.java`

**Interfaces:**
- Produces: `getDistance()` setting accessor; `BaritonePathfinder` stops advancing (and the module toggles off) once traveled distance >= N.

- [ ] **Step 1: Add the setting**

In `HighwayTools`, `sgGeneral`:

```java
    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("distance")
        .description("Stop the bot after this many blocks along the highway direction. 0 = unlimited.")
        .defaultValue(0)
        .min(0)
        .sliderMax(5000)
        .build()
    );
```

Accessor: `public Setting<Integer> getDistance() { return distance; }`.

- [ ] **Step 2: Stop advancing in `BaritonePathfinder`**

In `updatePathing()`, when about to advance `currentPosition`, check traveled distance:

```java
        BlockPos nextPos = tools.getCurrentPosition().offset(DirectionUtils.toVec3i(tools.getDirection()));

        int maxDistance = tools.getDistance().get();
        if (maxDistance > 0) {
            double traveled = nextPos.getCenter().distanceTo(tools.getStartPosition().getCenter());
            if (traveled >= maxDistance) {
                // reached the limit — stop advancing; the module will be toggled off
                tools.runNextTick(() -> tools.toggle());
                return;
            }
        }

        if (!isDone(nextPos.above())) return;
        // ... existing advance logic
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/dynmie/highway/modules/HighwayTools.java src/main/java/me/dynmie/highway/highwaytools/pathing/BaritonePathfinder.java
git commit -m "feat: distance limit stops the bot after N blocks"
```

---

### Task 5: Intelligent placing by block side (prefer visible support)

**Files:**
- Modify: `src/main/java/me/dynmie/highway/highwaytools/place/PlacementSearcher.java` (if Branch A merged) **OR** create a standalone `PlacementSearcher` here if not.

**Interfaces:**
- Produces: the searcher returns the **best** (visible-first, then closest) support when multiple exist.

If Branch A's `PlacementSearcher` is present, this refines `findSequence` to prefer a visible face over an invisible one at the same distance. Add a `visible` field to `PlacementStep` (or score the sequence):

```java
    // In findSequence, when collecting candidate direct supports, prefer visible:
    // - first pass: only visible faces (illegal = false)
    // - if empty and illegal, second pass: any face
```

Concretely, change the direct-support loop to collect all valid steps, then pick the one with lowest distance, breaking ties toward visible faces:

```java
    // in findSequence:
    PlacementStep best = null;
    for (Direction side : SIDES) {
        PlacementStep step = checkNeighbor(eyePos, target, side, illegal, true);
        if (step == null) continue;
        if (best == null || step.isVisible() && !best.isVisible()
            || (step.isVisible() == best.isVisible() && eyePos.distanceTo(step.hitVec()) < eyePos.distanceTo(best.hitVec()))) {
            best = step;
        }
    }
    if (best != null) { sequence.add(best); return sequence; }
```

(Requires `isVisible()` on `PlacementStep` — add a `boolean visible` record component in Branch A's Task 1.)

If Branch A is **not** merged, create a minimal standalone `PlacementSearcher` (same interface as Branch A's Task 1) with just the direct-support pass and the visible-first preference, so this branch is self-contained and the later merge of A is a drop-in.

- [ ] **Step 1: Refine or create the searcher**

Follow the branch-merge situation above. Build:

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/me/dynmie/highway/highwaytools/place/PlacementSearcher.java src/main/java/me/dynmie/highway/highwaytools/place/PlacementStep.java
git commit -m "feat: prefer visible support faces when placing"
```

---

## Self-Review

- **Spec coverage:** Branch D all covered: corner blocks ✓ Task 3; ignore list + command ✓ Task 1/2; distance limit ✓ Task 4; intelligent placing ✓ Task 5; generalization of hardcoded unbreakables ✓ Task 1.
- **Placeholder scan:** All code present. The exact corner position and the Branch A/Branch D merge branch are called out explicitly rather than left vague.
- **Type consistency:** `IgnoreList.isIgnored(Block)` used consistently in `BlockTaskManager`/`TaskExecutor`. `getIgnoreList()`/`getCornerBlock()`/`getDistance()` accessors consistent. `PlacementStep.isVisible()` is added in Branch A's Task 1 (the plan notes the coupling).

## Notes

- **Command registration:** `meteordevelopment.meteorclient.commands.arguments.BlockArgumentType` — verify the package against the meteor jar (`meteordevelopment/meteorclient/commands/arguments/`); if it's not `BlockArgumentType`, the meteor command uses a `RegistryEntryArgumentType` or similar in 26.1. Check with `javap` on the extracted jar.
- **Corner position:** the exact `getFront` corner offset depends on each provider's coordinate convention (straight vs diagonal differ). The plan gives the intent; the implementer should verify against the existing `getFront`/`getFloor` coordinate output and adjust the offset so the corner sits at the front-center top.
