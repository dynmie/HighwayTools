# Mining Optimization Implementation Plan (Branch C)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the mining-timing bug ("waits a tick or two before mining again even though it mines; PacketMine is faster, especially creative") by making block-breaking server-confirmation-driven, with no instant-break / multi-break / anticheat bypasses.

**Architecture:** Rewrite `BreakHandler` around progress-from-`getBreakDelta` + `startPrediction` client-predicted removal + a `PENDING_BREAK` fallback timer, instead of the current local-tick-counting (`minedTicks >= ticksRequired`) model that stalls on a single late START/STOP pair. No new bypasses.

**Tech Stack:** Java 25, Minecraft 26.1.2 (mojmap names), Meteor Client 26.1.2-SNAPSHOT, baritone 26.1-SNAPSHOT.

## Global Constraints

- Minecraft **26.1.2** unobfuscated — mojmap names ARE the class names. No yarn/mojmap remap; use `net.minecraft.core.BlockPos`, `net.minecraft.world.level.block.state.BlockState`, `net.minecraft.network.protocol.game.ServerboundPlayerActionPacket`.
- Meteor helpers: `meteordevelopment.meteorclient.utils.world.BlockUtils.getBreakDelta(int, BlockState)` returns progress/second; `MultiPlayerGameMode.startPrediction(ClientLevel, PredictiveAction)` for client-predicted packets.
- No instant-break, no multi-break, no `packetFlood`, no NCP priming, no `obscureBreakingProgress` — those are explicitly deferred (see spec "Future").
- `./gradlew build` is the primary verification; there are no unit tests in this project.
- No `Co-Authored-By` trailers; do not claim agent generation in commit messages (AGENTS.md).
- Worktree branch: `feature/mining-optimization`.

---

### Task 1: Add a server-confirmation mining loop to `BreakHandler`

**Files:**
- Modify: `src/main/java/me/dynmie/highway/highwaytools/handler/BreakHandler.java`
- Modify: `src/main/java/me/dynmie/highway/highwaytools/block/TaskState.java`

**Interfaces:**
- Consumes: existing `BlockTask.getMinedTicks()/incrementMinedTicks()/setMinedTicks()`, `InventoryHandler.prepareToolInHotbar(BlockState)`, `HighwayTools.getAvoidMineGhostBlocks()/getTaskTimeout()`, meteor `BlockUtils.getDirection(BlockPos)`.
- Produces: `BreakHandler.mine(BlockTask)` rewritten; `TaskState.BREAKING`/`PENDING_BREAK` retained; `BlockTask` gets no new fields.

The current bug: `mineNormally` sends START once on entering BREAKING, then does **nothing but swing** until `minedTicks >= ticksRequired`, then sends STOP. The server never gets a re-START, and if the client-side state drifted (look change, abort, creative), the block stalls for multiple ticks while the client swings into the void. Meteor PacketMine is faster because it doesn't count ticks — it sends START+STOP immediately via `startPrediction` and relies on client-side `blockState` change / timeout.

The fix: keep a START on entering BREAKING, but drive the STOP from **progress** (`getBreakDelta`), re-send START when progress is stalled, and use a `PENDING_BREAK` fallback timer instead of waiting on `minedTicks` alone.

- [ ] **Step 1: Add the `PENDING_BREAK` threshold tuning to `TaskState`**

In `TaskState.java`, `PENDING_BREAK(100, 100)` stays as-is (threshold 100 ticks is far too long for the fallback). Change it to `PENDING_BREAK(10, 10)` so a stuck task re-enters BREAK after ~10 ticks instead of ~100:

```java
    PENDING_BREAK(10, 10),
```

- [ ] **Step 2: Rewrite `mine()` and `mineNormally()` in `BreakHandler`**

Replace the whole `mine`/`mineNormally` pair with a progress-driven version. The `ticksNeeded > ticksNeeded * 1.1` reset in the old code is replaced by the progress check. Key behaviors:
1. On entering `BREAK` → send START immediately (`startPrediction`), transition to `BREAKING`, record `startTime = mc.player.tickCount`.
2. On `BREAKING` → each tick compute `progress = BlockUtils.getBreakDelta(slot, state) * (mc.player.tickCount - startTime + 1)` (like Meteor `MyBlock.progress()`). If `progress >= 1` OR creative (`mc.player.getAbilities().instabuild`) → send STOP (via `startPrediction`), swing, transition to `PENDING_BREAK`.
3. If progress is **not** advancing (client-side state unchanged and we've been in BREAKING for a while without STOP yet — i.e. `mc.player.tickCount - startTime > 10` and progress is still < 1), re-send START (the "re-start stalled dig" fix) and keep the fallback timer in mind.
4. `PENDING_BREAK`: wait for the server. The `BlockTaskManager`'s existing `doBreaking` path transitions to `BROKEN` when the block becomes AIR (`isTypeAir`). If `PENDING_BREAK` hits its stuck threshold (`TaskState.PENDING_BREAK`), `TaskExecutor`'s stuck handling returns it to `BREAK` (existing `doPendingBreak` → `task.onStuck()` → the existing `BlockTaskManager` comparator/task-timeout path).

Use `mc.gameMode.startPrediction(mc.level, sequence -> new ServerboundPlayerActionPacket(...))` for the START and STOP packets (client-predicted removal keeps local world consistent — this is what `avoid-ghost-blocks` should map to). Here is the full rewritten class body for `mine`/`mineNormally`:

```java
public void mine(BlockTask task) {
    Objects.requireNonNull(mc.player, "player should not be null");
    Objects.requireNonNull(mc.level, "level should not be null");

    BlockPos pos = task.getBlockPos();
    BlockState blockState = mc.level.getBlockState(pos);
    if (me.dynmie.highway.utils.BlockUtils.isTypeAir(blockState.getBlock())) {
        task.updateState(TaskState.BROKEN);
        return;
    }

    mc.player.getInventory().setSelectedSlot(inventoryHandler.prepareToolInHotbar(blockState));

    int slot = mc.player.getInventory().getSelectedSlot();
    TaskState state = task.getTaskState();

    if (state == TaskState.BREAK) {
        task.updateState(TaskState.BREAKING);
        task.setStartMineTick(mc.player.tickCount);
        sendStartPacket(pos, direction(pos));
        swingHand();
    } else if (state == TaskState.BREAKING) {
        double progress = BlockUtils.getBreakDelta(slot, blockState) * (mc.player.tickCount - task.getStartMineTick() + 1);
        boolean creative = mc.player.getAbilities().instabuild;
        boolean ready = progress >= 1 || creative;

        if (ready) {
            sendStopPacket(pos, direction(pos));
            swingHand();
            if (!tools.getAvoidMineGhostBlocks().get()) {
                BlockUtils.breakBlock(pos, true);
            }
            task.updateState(TaskState.PENDING_BREAK);
        } else if (mc.player.tickCount - task.getStartMineTick() > 10) {
            // progress stalled for 10+ ticks — re-send START to unstick the dig
            sendStartPacket(pos, direction(pos));
            swingHand();
        } else {
            swingHand();
        }
    }
}

private Direction direction(BlockPos pos) {
    Direction dir = BlockUtils.getDirection(pos);
    return dir == null ? Direction.DOWN : dir;
}

private void sendStartPacket(BlockPos pos, Direction direction) {
    if (mc.getConnection() == null) return;
    mc.gameMode.startPrediction(mc.level, sequence -> new ServerboundPlayerActionPacket(
        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence));
}

private void sendStopPacket(BlockPos pos, Direction direction) {
    if (mc.getConnection() == null) return;
    mc.gameMode.startPrediction(mc.level, sequence -> new ServerboundPlayerActionPacket(
        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence));
}
```

- [ ] **Step 3: Add `startMineTick` to `BlockTask`**

`BlockTask` needs the tick at which mining started (for the progress calc). Add a field + accessor, and reset it in `updateState` alongside `ranTicks`/`stuckTicks`:

```java
    private int startMineTick = 0;

    public int getStartMineTick() { return startMineTick; }
    public void setStartMineTick(int tick) { this.startMineTick = tick; }
```

In `updateState(TaskState state)`, add `startMineTick = 0;` next to the existing resets so a fresh state clears the timer.

- [ ] **Step 4: Remove the old START/STOP/abort helpers**

Delete `sendAbortPacket`, `swingHand` (folded into the new code), and the old `mineNormally` method entirely so there's no dead code. Keep `calcTicksToBreakBlock` (used by `doBreak`'s `ticksNeeded` decision elsewhere) — verify with `grep -rn "calcTicksToBreakBlock"` before deleting; if unused, remove it.

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. If `ServerboundPlayerActionPacket`'s 4-arg constructor isn't what `startPrediction` expects, decompile-check the actual signature with `javap -p` on the extracted jar (see Notes).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/dynmie/highway/highwaytools/handler/BreakHandler.java src/main/java/me/dynmie/highway/highwaytools/block/TaskState.java src/main/java/me/dynmie/highway/highwaytools/block/BlockTask.java
git commit -m "fix: server-confirmation-driven mining with stalled-dig restart"
```

---

### Task 2: Creative-mode / one-tick blocks take the fast path

**Files:**
- Modify: `src/main/java/me/dynmie/highway/highwaytools/handler/BreakHandler.java`

**Interfaces:**
- Consumes: `Task 1`'s rewritten `mine()`; `BlockUtils.calcBlockBreakingDelta2(BlockPos, float)` (Meteor) or `state.getDestroyProgress(player, level, pos)`.
- Produces: creative/one-tick blocks break in a single tick instead of one-per-tick.

With the progress-driven loop, a one-tick block (`ticksNeeded == 1`) or creative computes `progress >= 1` on the *first* BREAKING tick, so it already STOPs on the next tick. That's correct but still costs a tick. The fast path sends START+STOP **in the same tick** when the block is insta-breakable:

- [ ] **Step 1: Add the insta-break fast path**

At the top of the `state == TaskState.BREAK` branch, before sending START alone:

```java
    if (state == TaskState.BREAK) {
        task.updateState(TaskState.BREAKING);
        task.setStartMineTick(mc.player.tickCount);

        boolean insta = creative || BlockUtils.canInstaBreak(pos);
        sendStartPacket(pos, direction(pos));
        if (insta) {
            sendStopPacket(pos, direction(pos));
            task.updateState(TaskState.PENDING_BREAK);
        }
        swingHand();
    }
```

where `creative = mc.player.getAbilities().instabuild` and `BlockUtils.canInstaBreak(BlockPos)` is Meteor's `meteordevelopment.meteorclient.utils.world.BlockUtils.canInstaBreak`.

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/me/dynmie/highway/highwaytools/handler/BreakHandler.java
git commit -m "perf: insta-break and creative blocks break in a single tick"
```

---

### Task 3: Tool-slot restore (avoid hotbar churn)

**Files:**
- Modify: `src/main/java/me/dynmie/highway/highwaytools/handler/InventoryHandler.java`

**Interfaces:**
- Consumes: existing `findBestTool(BlockState)`.
- Produces: `InventoryHandler.prepareToolInHotbar(BlockState)` now returns the *previous* slot so `BreakHandler` can restore it.

The current `prepareToolInHotbar` sets the selected slot to the best tool and returns that slot; the module keeps it selected permanently, churning hotbar selection. Lambda's `swapToSlot`/`moveToHotbar` restores after the interaction. Do the minimal version: record the pre-switch slot in a field, and have `BreakHandler` restore it after the block is broken.

- [ ] **Step 1: Track the previous slot**

In `InventoryHandler`, add:

```java
    private int previousSlot = -1;

    public int getPreviousSlot() { return previousSlot; }
```

In `prepareToolInHotbar`, before the `setSelectedSlot` happens, capture the old slot:

```java
    int prev = mc.player.getInventory().getSelectedSlot();
    mc.player.getInventory().setSelectedSlot(slot);
    previousSlot = prev;
    return slot;
```

- [ ] **Step 2: Restore in `BreakHandler` after `PENDING_BREAK`**

In `Task 1`'s `mine()`, in the `state == TaskState.BREAKING` branch where `ready` is true (we're stopping the dig), after sending STOP, restore the slot:

```java
    if (ready) {
        sendStopPacket(pos, direction(pos));
        swingHand();
        if (!tools.getAvoidMineGhostBlocks().get()) {
            BlockUtils.breakBlock(pos, true);
        }
        int prev = inventoryHandler.getPreviousSlot();
        if (prev != -1) {
            mc.player.getInventory().setSelectedSlot(prev);
        }
        task.updateState(TaskState.PENDING_BREAK);
    }
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/dynmie/highway/highwaytools/handler/InventoryHandler.java src/main/java/me/dynmie/highway/highwaytools/handler/BreakHandler.java
git commit -m "perf: restore previous tool slot after mining to reduce hotbar churn"
```

---

## Self-Review

- **Spec coverage:** Spec Branch C items 1-7 all covered: (1) PENDING_BREAK fast path ✓ Task 1, (2) progress check ✓ Task 1, (3) re-send START when stalled ✓ Task 1, (4) creative/one-tick fast path ✓ Task 2, (5) startPrediction ✓ Task 1, (6) interaction budget / taskTimeout — the existing `TaskState.PENDING_BREAK` stuck timeout covers it ✓ Task 1, (7) tool-slot restore ✓ Task 3.
- **Placeholder scan:** No TBDs. All code blocks are complete.
- **Type consistency:** `startMineTick` field added in Task 1 Step 3, used in Task 1 Step 2 and Task 2 — consistent. `sendStartPacket/sendStopPacket` 2-arg form used consistently. `canInstaBreak(BlockPos)` is a real Meteor static.

## Notes

- If `ServerboundPlayerActionPacket`'s 4-arg constructor (`pos, direction, Action, sequence`) isn't the exact signature in MC 26.1, inspect it: `javap -p` on `net/minecraft/network/protocol/game/ServerboundPlayerActionPacket.class` from `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.1.2/minecraft-merged-deobf-26.1.2.jar`.
- `BlockUtils.getBreakDelta(int slot, BlockState state)` returns per-tick progress (verified present in meteor 26.1 jar).
- This branch deliberately does NOT add instant/multi-break/packetFlood — see spec "Future".
