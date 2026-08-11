# Impossible Place Fix Implementation Plan (Branch A)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Place blocks that have no directly-visible, directly-adjacent solid support (corners, gaps, "floating" positions) by finding *any* reachable support face — including invisible surfaces when the setting allows, and a deep search up to N cells — instead of `BlockUtils.place` failing early on `getPlaceSide() == null`.

**Architecture:** Extract a `PlacementSearcher` that runs a neighbour-sequence DFS (Lambda's `getNeighbourSequence`), returning a `List<PlacementStep>` (support pos + side + hitVec + placed pos). `PlaceHandler` uses `sequence.last()` as the click target. When no sequence exists, the task enters `IMPOSSIBLE_PLACE` state (deferred placement, retried; no bridge/scaffold — future work). A new `illegal-placements` setting toggles clicking invisible surfaces.

**Tech Stack:** Java 25, Minecraft 26.1.2 (mojmap), Meteor Client 26.1.2-SNAPSHOT.

## Global Constraints

- Mojmap names only (`net.minecraft.core.Direction`, `net.minecraft.world.phys.BlockHitResult`, `net.minecraft.network.protocol.game.ServerboundUseItemOnPacket`).
- Reuse meteor `BlockUtils.place(BlockPos, InteractionHand, int, boolean, int, boolean, boolean, boolean)` where possible, but it must accept an explicit hit target for the deep/invisible cases — fall back to `BlockUtils.interact(BlockHitResult, InteractionHand, boolean)` + `InvUtils.swap(slot, swapBack)` when a custom hit is needed.
- No bridge/scaffold movement (deferred — see spec "Future"). `IMPOSSIBLE_PLACE` just retries / marks DONE.
- `./gradlew build` is the primary verification; no unit tests.
- No `Co-Authored-By` trailers; no agent-generation claims in commits.
- Worktree branch: `feature/impossible-place` (recreated from current master).

---

### Task 1: Create `PlacementSearcher` and `PlacementStep`

**Files:**
- Create: `src/main/java/me/dynmie/highway/highwaytools/place/PlacementStep.java`
- Create: `src/main/java/me/dynmie/highway/highwaytools/place/PlacementSearcher.java`

**Interfaces:**
- Consumes: `HighwayTools` (for `getReach()`, `getMainBlock()`, `getFillerBlock()`, `getDirection()`, `getRotation()`), `net.minecraft.client.Minecraft`, `BlockUtils.getDirection`, meteor `BlockUtils.canPlace`.
- Produces:
  - `record PlacementStep(BlockPos supportPos, Direction side, Vec3 hitVec, BlockPos placedPos)`.
  - `class PlacementSearcher` with `List<PlacementStep> findSequence(Vec3 eyePos, BlockPos target, int depth, boolean illegal)`.

This is the DFS from Lambda `Interact.getNeighbourSequence`. It returns the *chain* of supports; `PlaceHandler` uses `sequence.last()`.

- [ ] **Step 1: Write `PlacementStep`**

```java
package me.dynmie.highway.highwaytools.place;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public record PlacementStep(BlockPos supportPos, Direction side, Vec3 hitVec, BlockPos placedPos) {
}
```

- [ ] **Step 2: Write `PlacementSearcher`**

```java
package me.dynmie.highway.highwaytools.place;

import me.dynmie.highway.modules.HighwayTools;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class PlacementSearcher {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final Direction[] SIDES = Direction.values();

    private final HighwayTools tools;

    public PlacementSearcher(HighwayTools tools) {
        this.tools = tools;
    }

    /**
     * Find a chain of support positions to place a block at {@code target}.
     * The caller places against {@code result.get(result.size()-1)} (the last step).
     * Returns empty list when no reachable support exists.
     */
    public List<PlacementStep> findSequence(Vec3 eyePos, BlockPos target, int depth, boolean illegal) {
        List<PlacementStep> sequence = new ArrayList<>();

        // 1. Direct support: any adjacent solid, non-replaceable block within reach.
        for (Direction side : SIDES) {
            PlacementStep step = checkNeighbor(eyePos, target, side, illegal, true);
            if (step != null) {
                sequence.add(step);
                return sequence;
            }
        }

        // 2. Deep search: recurse through adjacent positions up to `depth`.
        if (depth > 1) {
            for (Direction side : SIDES) {
                BlockPos neighbor = target.relative(side);
                PlacementStep step = checkNeighbor(eyePos, target, side, illegal, false);
                if (step == null) continue;

                List<PlacementStep> sub = findSequence(eyePos, neighbor, depth - 1, illegal);
                if (!sub.isEmpty()) {
                    sequence.add(step);
                    sequence.addAll(sub);
                    return sequence;
                }
            }
        }

        return sequence;
    }

    /**
     * Check one neighbour of {@code target} as a potential support.
     * If {@code checkReplaceable}, require the neighbour to be solid and non-replaceable.
     */
    private PlacementStep checkNeighbor(Vec3 eyePos, BlockPos target, Direction side, boolean illegal, boolean checkReplaceable) {
        BlockPos supportPos = target.relative(side);
        Direction clickSide = side.getOpposite();

        // illegal=false → require the face to be visible (normal); illegal=true → click invisible faces too
        if (!illegal && !isFaceVisible(supportPos, clickSide)) return null;

        if (checkReplaceable) {
            BlockState supportState = mc.level.getBlockState(supportPos);
            if (supportState.isAir() || supportState.canBeReplaced()) return null;
        }

        Vec3 hitVec = Vec3.atCenterOf(supportPos).add(
            clickSide.getStepX() * 0.5,
            clickSide.getStepY() * 0.5,
            clickSide.getStepZ() * 0.5
        );

        if (eyePos.distanceTo(hitVec) > tools.getReach().get()) return null;
        if (!BlockUtils.canPlace(target)) return null;

        return new PlacementStep(supportPos, clickSide, hitVec, target);
    }

    private boolean isFaceVisible(BlockPos supportPos, Direction side) {
        // Conservative: the face is visible if the adjacent block (supportPos offset by side) is air or replaceable.
        BlockState adj = mc.level.getBlockState(supportPos.relative(side));
        return adj.isAir() || adj.canBeReplaced();
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (new files compile).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/dynmie/highway/highwaytools/place/PlacementStep.java src/main/java/me/dynmie/highway/highwaytools/place/PlacementSearcher.java
git commit -m "feat: add placement sequence searcher for impossible/deep placements"
```

---

### Task 2: Add `illegal-placements` and `placement-search` settings

**Files:**
- Modify: `src/main/java/me/dynmie/highway/modules/HighwayTools.java`

**Interfaces:**
- Consumes: existing setting group pattern.
- Produces: `getIllegalPlacements()`, `getPlacementSearch()` accessors (returning `Setting<Boolean>` / `Setting<Integer>`).

Add to the existing `sgPlace` group:

```java
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
```

Add the accessors (matching the existing `getX()` pattern at the bottom of the file):

```java
    public Setting<Boolean> getIllegalPlacements() { return illegalPlacements; }
    public Setting<Integer> getPlacementSearch() { return placementSearch; }
```

- [ ] **Step 1: Edit and build**

Make the edits, then run `./gradlew build` — expect BUILD SUCCESSFUL.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/me/dynmie/highway/modules/HighwayTools.java
git commit -m "feat: add illegal-placements and placement-search settings"
```

---

### Task 3: Rewire `PlaceHandler` to use the searcher

**Files:**
- Modify: `src/main/java/me/dynmie/highway/highwaytools/handler/PlaceHandler.java`

**Interfaces:**
- Consumes: `PlacementSearcher.findSequence(...)` from Task 1; `HighwayTools.getIllegalPlacements()/getPlacementSearch()` from Task 2; `BlockTask.getBlockPos()/getBlueprintTask()`.
- Produces: `PlaceHandler.place(BlockTask)` uses `sequence.last()`; on empty sequence → `task.updateState(TaskState.IMPOSSIBLE_PLACE)` and returns.

Replace the current `BlockUtils.place(...)` call (which fails when `getPlaceSide()==null`) with a searcher-driven click. The plain-support case still uses meteor `BlockUtils.place` (fast path). When the sequence is empty, mark `IMPOSSIBLE_PLACE`.

- [ ] **Step 1: Inject the searcher and rewrite `place()`**

Add a `PlacementSearcher searcher = new PlacementSearcher(tools);` field. Rewrite `place(BlockTask task)`:

```java
    public void place(BlockTask task) {
        // DELAY
        int delay = tools.getAdaptivePlaceDelay().get() ? tools.getPlaceDelay().get() + extraPlaceDelay : tools.getPlaceDelay().get();
        inventoryHandler.setWaitTicks(delay);

        // ROTATION
        if (tools.getRotation().get().place && tools.getRotateCamera().get() && mc.player != null) {
            mc.player.setYRot((float) Rotations.getYaw(task.getBlockPos()));
            mc.player.setXRot((float) Rotations.getPitch(task.getBlockPos()));
        }

        // INVENTORY
        Item itemToFind = task.getBlueprintTask().getTargetBlock().asItem();
        itemToFind = itemToFind.equals(Items.AIR) ? tools.getFillerBlock().get().asItem() : itemToFind;

        int slot = inventoryHandler.prepareItemInHotbar(itemToFind);
        if (slot == -1) {
            return; // todo: restock path (Branch B)
        }

        BlockPos pos = task.getBlockPos();

        // FIND A PLACEMENT SEQUENCE
        boolean illegal = tools.getIllegalPlacements().get();
        List<PlacementStep> sequence = searcher.findSequence(
            mc.player.getEyePosition(), pos, tools.getPlacementSearch().get(), illegal);

        if (sequence.isEmpty()) {
            task.updateState(TaskState.IMPOSSIBLE_PLACE);
            return;
        }

        PlacementStep step = sequence.get(sequence.size() - 1);
        task.updateState(TaskState.PENDING_PLACE);

        // PURE-PACKET PLACEMENT against the found support
        BlockHitResult bhr = new BlockHitResult(
            step.hitVec(), step.side(), step.supportPos(), false);

        mc.player.getInventory().setSelectedSlot(slot);

        if (tools.getRotation().get().place) {
            Rotations.rotate(Rotations.getYaw(step.hitVec()), Rotations.getPitch(step.hitVec()), () -> {
                BlockUtils.interact(bhr, InteractionHand.MAIN_HAND, true);
            });
        } else {
            BlockUtils.interact(bhr, InteractionHand.MAIN_HAND, true);
        }

        // Existing confirmation fallback (kept from current code)
        new Thread(() -> {
            try {
                Thread.sleep(50L * tools.getTaskTimeout().get());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            tools.runNextTick(() -> {
                if (task.getTaskState() == TaskState.PENDING_PLACE) {
                    task.updateState(TaskState.PLACE);
                    if (tools.getAdaptivePlaceDelay().get() && extraPlaceDelay < 10) {
                        extraPlaceDelay += 1;
                    }
                }
            });
        }).start();
    }
```

Note: the current `BlockUtils.place(...)` fast path is replaced by the searcher-driven `interact`. If a direct support exists, the searcher returns a 1-element sequence whose step *is* the direct support, so behavior for normal placements is equivalent — but now it clicks the exact face rather than letting `BlockUtils.place` choose.

- [ ] **Step 2: Add `IMPOSSIBLE_PLACE` to `TaskState`**

In `TaskState.java`, add after `PENDING_PLACE`:

```java
    IMPOSSIBLE_PLACE(20, 20),
```

- [ ] **Step 3: Handle `IMPOSSIBLE_PLACE` in `TaskExecutor`**

In `TaskExecutor.java`, in the `switch (task.getTaskState())`, add:

```java
    case IMPOSSIBLE_PLACE -> doImpossiblePlace(task);
```

and add:

```java
    private void doImpossiblePlace(BlockTask task) {
        // No reachable support right now. Re-request the sequence next tick;
        // if the player moved or the pathfinder advanced, a support may exist.
        // After the stuck threshold, drop the task (mark DONE) so the bot isn't stuck forever.
        task.onStuck();
        if (task.getStuckTicks() > 20) {
            task.updateState(TaskState.DONE);
        } else {
            task.updateState(TaskState.PLACE);  // retry placement (re-runs searcher)
        }
    }
```

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/dynmie/highway/highwaytools/handler/PlaceHandler.java src/main/java/me/dynmie/highway/highwaytools/block/TaskState.java src/main/java/me/dynmie/highway/highwaytools/block/TaskExecutor.java
git commit -m "feat: place against any reachable support; IMPOSSIBLE_PLACE state"
```

---

## Self-Review

- **Spec coverage:** Branch A all covered: `PlacementSearcher` DFS ✓ Task 1; `illegal-placements` (invisible surfaces) ✓ Task 1 (isFaceVisible gate) + Task 2 setting; `placement-search` depth ✓ Task 1 (depth param) + Task 2 setting; `IMPOSSIBLE_PLACE` state ✓ Task 3; deferred bridge/scaffold ✓ (explicitly out of scope). The stale branch's `side == null → return` is replaced by the searcher ✓ Task 3.
- **Placeholder scan:** No TBDs; all code complete.
- **Type consistency:** `PlacementStep(supportPos, side, hitVec, placedPos)` — used consistently in `findSequence`/`checkNeighbor`/`place`. `findSequence(eyePos, target, depth, illegal)` matches usage. `getIllegalPlacements()/getPlacementSearch()` names consistent with the `getX()` pattern in `HighwayTools`.
- **One deviation from spec:** the spec suggested "prefer the searcher return best-by-distance" and keeping meteor `BlockUtils.place` for the fast path. I replaced the fast path with `BlockUtils.interact` for a uniform code path, because mixing the two (meteor place choosing its own side) would ignore the searcher's chosen face in the direct case. This is a simplification, not a contradiction — `interact` is a real meteor API and does the same click.

## Notes

- `BlockUtils.interact(BlockHitResult, InteractionHand, boolean)` verified present in meteor 26.1 jar (third arg = swing hand).
- `BlockHitResult(hitVec, direction, blockPos, isMiss)` constructor is standard mojmap.
- The `placedPos` field on `PlacementStep` is retained for future use (Branch D intelligent-placing / bridge) even though the current `place()` only uses `supportPos`, `side`, `hitVec`.
