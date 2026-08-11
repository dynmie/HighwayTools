# MetroHighwayTools — Lambda HighwayTools Feature Port

Date: 2026-08-10

## Purpose

Port the missing capabilities of the old **Lambda HighwayTools** plugin (github.com/lambda-plugins/HighwayTools, source at `C:\Users\misaka\IdeaProjects\LHighwayTools`, runtime = lambda-legacy 1.12.2) into **MetroHighwayTools** (this repo, a Meteor Client 26.1 addon). The Lambda plugin is unmaintained; this project is a from-scratch re-implementation on the modern unobfuscated Minecraft 26.1 / Meteor 26.1 stack.

The work is split into **independent feature branches**, each developed in its own git worktree, per the user's explicit instruction ("All new features should be in separate branches", "create worktrees for this", "use parallel subagents").

## Current state (master, verified compiling)

The addon already has the Lambda **skeleton**:

- `BlueprintGenerator` → `Map<BlockPos, BlueprintTask>` (desired state); `StraightBlueprintProvider` / `DiagonalBlueprintProvider`.
- `BlockTaskManager` → converts blueprint into `BlockTask`s by diffing world state, holds them in a `ConcurrentHashMap`, sorts by distance/shuffle.
- `TaskExecutor` → drives `TaskState` state machine (BREAK → BREAKING → BROKEN → PLACE → PLACED → DONE, plus LIQUID / PENDING_*).
- Handlers: `BreakHandler` (packet mining), `PlaceHandler` (Meteor `BlockUtils.place`), `InventoryHandler` (hotbar prep + best-tool score), `LiquidHandler`.
- Baritone process (`BaritonePathfinder` advances the front; `BaritoneProcess` paths to it; `BaritoneHelper` saves/restores baritone settings).

### Key gaps vs Lambda (what this design ports)

| Capability | Lambda (reference) | Metro (current) |
|---|---|---|
| Impossible / deep placement | `getNeighbourSequence` DFS, `illegalPlacements` (invisible-surface click), `placementSearch` depth | None — `BlockUtils.place` returns early when `getPlaceSide() == null` |
| Long-term inventory | `containerTask` lifecycle (shulker/ender-chest PLACE→OPEN→RESTOCK→BREAK→PICKUP), AutoObsidian grind, `saveMaterial/saveTools/saveEnder`, pure-packet container open, server-transaction clicks | None — hotbar-prep + best-tool-score only |
| Mining throughput | async packet engine, instant-break path, multi-break, packet budget | Sequential START→wait→STOP, no instant path, no budget |
| Blueprint extras | corner blocks, intelligent placing by block side, ignore-list, distance limit | Corner blocks missing, ignore-list missing, distance missing |

## Architecture principle

**"Extract infra as needed"** (user's choice). Do NOT do a front-loaded Lambda-style restructure. Keep the current skeleton; each feature branch extracts shared infrastructure (packet layer, container lifecycle, placement sequence, movement state) only where the feature requires it, in a way that the next feature can reuse.

Each feature branch is **self-contained on master** (the current branch). Branches do not depend on each other. Infra extracted by one branch is available to later branches via merge of master.

## Target versions (unchanged from master)

| Component | Version |
|---|---|
| Minecraft | 26.1.2 (unobfuscated, mojmap names are the class names) |
| Meteor Client | `meteordevelopment:meteor-client:26.1.2-SNAPSHOT` |
| Baritone | `meteordevelopment:baritone:26.1-SNAPSHOT` (id `baritone-meteor`) |
| Fabric Loader | 0.19.3 |
| Loom | `net.fabricmc.fabric-loom` 1.17-SNAPSHOT (non-remap) |
| JDK | 25 (foojay auto-provisioned) |
| Gradle | 9.6.1 |

---

## Branch A — Impossible block place fix

**Branch:** `feature/impossible-place` (reuse the stale branch of the same name, recreated on current master — see Notes).

**Goal:** Place blocks that have no directly-visible, directly-adjacent solid support (e.g. corners, block gaps, "floating" positions the blueprint wants) by finding *any* reachable support face and clicking it — including invisible surfaces when the setting allows, and walking a deep search up to N cells.

### Design

Extract a placement-sequence finder into `highwaytools/place/`:

- **`PlacementContext`** — value carrier: `(supportPos, side, hitVec, placedPos)`.
- **`PlacementSearcher`** — the neighbour-sequence DFS:
  ```
  findSequence(eyePos, targetPos, depth):
    for side in 6 sides:
      neighbor = targetPos.offset(side)
      support = checkNeighbor(neighbor, opposite(side))   // solid, non-replaceable, within reach
      if support != null: return [support]
    if depth > 1:
      for side in 6 sides:
        neighbor = targetPos.offset(side)
        if isPlaceablePath(neighbor):  // block between target and support can be replaced/passed
          sub = findSequence(eyePos, neighbor, depth-1)
          if sub non-empty: return [supportAt(neighbor)] + sub
    return []
  ```
- Settings (new group "Place"):
  - `illegal-placements` (Bool, default false) — when true, `visibleSideCheck=false`: click block faces that are **not visible** to the player (the "invisible surfaces" exploit from Lambda). README-equivalent warning: not for 2b2t-style anticheat.
  - `placement-search` (Int, default 2) — DFS depth for the deep search.
  - `place-through-walls` / retained existing `rotation`, `reach`.
- **`PlaceHandler`** uses the searcher: compute `sequence`, take `sequence.last()` as the click target (`supportPos` + `side`), build a `BlockHitResult`, send `ServerboundUseItemOnPacket` (or Meteor `BlockUtils.place` extended to take an explicit `BlockHitResult`). On empty sequence → the "impossible" case, handled by the `IMPOSSIBLE_PLACE` state.
- **`TaskState.IMPOSSIBLE_PLACE`** (reuse the stale branch's concept) — a task that currently cannot be placed. Behavior: retry with backoff; optionally degrade gracefully (e.g. wait for the pathfinder to move so a support comes into reach, or mark DONE and move on). **No bridge/scaffold in this branch** (Lambda's BRIDGE movement rescue is future work — see "Future").
- The old branch's `PlaceHandler` note (`side == null → return`) is replaced by the searcher; `side == null` is now "no sequence" → `IMPOSSIBLE_PLACE`.

### Out of scope
Scaffold/bridge movement (`shouldBridge`, BRIDGE state) — needs the movement-state infra from the inventory/pathing work; deferred.

---

## Branch B — Long-term inventory management

**Branch:** `feature/inventory-management`

**Goal:** The Lambda README's "Long term inventory management": restock building material and tools from shulker boxes and ender chests, grind obsidian from ender chests, save minimum amounts of material/tools/ender-chests, never open a container with a mouse grab, confirm clicks via server transactions.

### Design

Extract a **container lifecycle** and an **inventory intent layer**:

- **`ContainerTask`** — a special `BlockTask`-like state machine:
  `PLACE → PLACED → OPEN_CONTAINER → RESTOCK → BREAK → PICKUP → DONE`
  driven by `TaskExecutor` with **priority over** regular block tasks (mirrors Lambda's `TaskManager.runTasks` first-branch).
- **`InventoryManager`** (extend `InventoryHandler`):
  - `getShulkerWith(item)` — scan inventory slots for a shulker whose NBT `BlockEntityTag.Items` contains the item; pick the shulker with fewest of it.
  - `getRemotePos()` — a placeable, non-blueprint position near the player to put the container; prefer solid support below, air above, not inside the blueprint build.
  - `findMaterial(item)` — only use the main material if `countBlock(material) > saveMaterial`, else restock fallback.
  - `countBlock(block)` — inventory + ender-chest-aware count.
  - **AutoObsidian / grind**: `grindCycles = (freeSlots - 1 - keepFreeSlots) * 8`; place ender chest → restock obsidian → break chest (drops 8 obsidian) → repeat; when `countBlock(ENDER_CHEST) <= saveEnder`, restock ender chests first.
  - Best-tool scoring: keep the existing score but add the efficiency^2 quadratic bonus (Lambda `speed += eff^2 + 1`) and prefer hotbar tools.
- **Pure-packet container interaction** (the "no mouse grab" requirement):
  - Open: send `ServerboundUseItemOnPacket` at the container with a computed side/hitVec — no `mc.gameMode.useItemOn` GUI path.
  - Clicks: implement a **server-transaction-confirmed click queue** — send `ServerboundContainerClickPacket` / `ServerboundContainerSlotStateChangedPacket` one at a time, wait for `ServerboundContainerAckPacket`/`ClientboundContainerSetSlot` confirm before the next. Pull **one stack per tick** (`stopPull`/`stacksPulled`), with `fastFill` mode that keeps pulling for tools (tunnel) and the main material (paving).
  - Close: `ServerboundContainerClosePacket` after restock; on containers from the blacklist, sneak while clicking so the GUI doesn't open (Lambda's `blockBlacklist` behavior).
- **Settings** (new group "Storage Management"):
  - `save-material` (Int, default 64) — never use the last N material blocks.
  - `save-tools` (Int, default 1) — restock pickaxes when below this.
  - `save-ender` (Int, default 1) — keep this many ender chests.
  - `save-food` (Int) — food floor (future).
  - `grind-obsidian` (Bool) — AutoObsidian.
  - `restock-from-ender-chest` (Bool, default true).
  - `leave-empty-shulkers` (Bool), `keep-free-slots` (Int).
  - `prefer-ender-chests` (Bool).
- **`doRestock` gate in `TaskManager.runTasks()`**: when material/tools are below minimums, the restock path runs instead of normal block tasks (with `moveState`-style freeze of mining during restock).
- **`mineEnderChests`** (existing setting) gains real meaning: after `saveEnder` is satisfied, extra ender chests can be mined for their 8 obsidian.

### Out of scope
Food management (`saveFood`, eat logic) — deferred. Ender-chest *shulker* routing nuances deferred.

---

## Branch C — Mining speed / timing fix

**Branch:** `feature/mining-optimization`

**Goal:** Fix the user-reported bug — *"when the player is mining it waits a tick or two or some before it mines again even though it mines. Actually, when mining with packetmine turned on its actually faster than with it turned off for some reason. More so on creative mode."* — by adopting the best of Meteor's PacketMine and Lambda's engine, **without** instant-break / multi-break / anticheat bypasses (user: "no instant or multi break or other bypasses as those are old and i want to implement it later").

### Root cause analysis

The current `BreakHandler.mine()`:
1. Sets the best tool.
2. Computes `ticksNeeded = ceil(1 / getDestroyProgress(...))`.
3. If `ticksMined > ticksNeeded * 1.1` in BREAKING, resets to BREAK.
4. `mineNormally`:
   - BREAK state → send START once, → BREAKING.
   - BREAKING: **do nothing but swing** until `minedTicks >= ticksRequired`, then send STOP.
   - The block is only considered broken when the server confirms via `doBreaking` checking `isTypeAir`.

Problems:
- **START is sent only once, on the first tick.** If the server needs the client to keep sending START (or re-send it after an abort, or after the player's look/slot changed), the block simply doesn't progress — the mining stalls for multiple ticks while the client swings into the void. This is the "waits a tick or two before it mines again."
- **The STOP is sent only after `ticksRequired` local ticks**, but on many servers the correct model is: send START, wait for the server's block-progress updates, send STOP when the server-side break is done (or when the block is gone). Local tick counting drifts.
- **Creative mode** is fast because with creative, `getDestroyProgress` → instant, so `ticksRequired` computes to 1 and the STOP is sent on the very next tick — but the code path still wastes a tick. Meteor PacketMine's model (send START+STOP immediately on the ready tick, then rely on server `blockState` change) is faster because it doesn't count ticks at all; it checks `isReady()` from `getBreakDelta`.
- **Meteor PacketMine being faster** confirms the model: Meteor sends both packets with `startPrediction` (client-predicted removal), and removes the block from its list when the client-side `blockState` changes or on timeout. The current code waits for the server to *confirm* via a later `isTypeAir` check — a full round-trip.

### The fix (hybrid of Meteor + Lambda, no bypasses)

Rewrite `BreakHandler` around **server-confirmation-driven progression + a fallback timer**, NOT local tick counting:

1. **`PENDING_BREAK` fast path**: when entering BREAK (or when the block is about to be broken — `ticksNeeded` computed), send `START_DESTROY_BLOCK` immediately (via `mc.gameMode.startPrediction` so the client predicts removal), then send `STOP_DESTROY_BLOCK` once the predicted progress would complete it. Set a **fallback timer** (`TaskState.PENDING_BREAK`, threshold from `task-timeout`). If the server confirms (block becomes AIR, or `ClientboundBlockUpdatePacket` for that pos arrives) → BROKEN; else after the timer → back to BREAK.
2. **Progress check**: instead of `minedTicks >= ticksRequired`, compute progress from `getDestroyProgress` * current tick delta like Meteor's `progress() = getBreakDelta(slot, state) * (tickCount - startTime + 1)`, and send STOP when `progress >= 1`. This matches server-side timing far better than counting our own ticks.
3. **Re-send START when needed**: if the block's progress isn't advancing (client-side state unchanged, server not confirming), re-send START — this is the part that fixes the "stall then suddenly mine" behavior.
4. **Creative-mode instant**: if `ticksNeeded == 1` or creative, send START+STOP **in the same tick** (Meteor does this), skip the swing-into-void wait.
5. **`startPrediction`**: use `mc.gameMode.startPrediction(mc.level, seq -> new ServerboundPlayerActionPacket(...))` for both packets — client-predicted removal keeps the local world consistent (no ghost blocks) without any anticheat bypass. This is what `avoid-ghost-blocks` should map to.
6. **Interaction budget** (no bypass, just safety): keep an eye on packets/sec; the current `taskTimeout` fallback stays.
7. Keep the **tool-selection** in `InventoryHandler` (that part is fine), but only switch tools when needed, and restore the previous slot after (Lambda's `swapToSlot`/`moveToHotbar` distinction) to avoid churn.

### Out of scope (deliberate, user-deferred)
- Instant-break path (`ticksNeeded == 1` → fire-and-forget START + multi-break) — **future**.
- `packetFlood` (START+STOP same tick spam), NCP instant-mine priming, `obscureBreakingProgress` abort-spam — **future**.
- The only "speed" this branch adds is *correct* timing: no stall, no wasted ticks, server-confirmation-driven. The user explicitly wants bypasses later.

---

## Branch D — Blueprint extras (corner blocks + ignore list + distance limit)

**Branch:** `feature/blueprint-extras`

**Goal:** Close the remaining README todo items that are blueprint-level, not interaction-level:
- **Corner blocks** (Lambda's `cornerBlock` setting) — a block at the highway's leading corner when width > 2. Currently `StraightBlueprintProvider`/`DiagonalBlueprintProvider` don't emit it.
- **Ignore list** (Lambda `ignoreBlocks` + commands `;ht ignore add/del`) — blocks the engine must never break (signs, portals, banners, bedrock, plus the default shulker set). Currently hardcoded checks for END_PORTAL_FRAME/BEDROCK/NETHER_PORTAL/END_PORTAL in `BlockTaskManager`/`TaskExecutor`; generalize to a user-editable set + command.
- **Distance limit** (Lambda `;ht distance N`) — stop the bot after N blocks along the direction. Currently the module runs until manually toggled.
- **Intelligent placing by block side** — prefer clicking a support side that faces the player / is visible; fold into Branch A's `PlacementSearcher` visibility preference (low priority; may live here as a small heuristic if Branch A lands first).

### Design

- **`corner-block`** setting (Bool, default true, visible when `width > 2`): providers emit one extra `BlueprintTask` at the front-center-corner position (`getFront` extended; `Straight`/`Diagonal` both). The corner is just a build-block target — no new interaction logic.
- **`IgnoreList`** utility (or Meteor `BlockListSetting`): a mutable `Set<Block>`; default = the current hardcoded unbreakables + all 16 shulker boxes. `BlockTaskManager.generateTask` and `TaskExecutor.doBreak` consult it instead of the hardcoded `equals` chain. Wire into `CheckBlocksCommand`? No — add `IgnoreCommand` (`;ht ignore add <block>` / `;ht ignore del <block>`), mirroring Lambda.
- **`distance`** setting (Int, default 0 = unlimited): `BaritonePathfinder`/`HighwayTools.onTick` stop the module (toggle off + stats) once `currentPosition` travel distance along the direction reaches N.
- **`intelligent-placing`** (Bool, default true): when the placement searcher has multiple valid supports, prefer the one whose face is visible to the player / closest to the eye; the searcher already returns best-by-distance; this refines to "visible first." If Branch A's searcher isn't merged yet, this branch adds the preference into the searcher it introduces (keep the searcher interface identical so A and D can coexist — see Shared infra).

---

## Shared infrastructure (extracted as needed, by the branch that needs it)

- **`PlacementSearcher`** (Branch A; Branch D refines). Defined so it's independent: `findSequence(eyePos, target, depth, illegal)` → `List<PlacementStep>`.
- **`ContainerTask` + `InventoryManager`** (Branch B) — the container lifecycle and the server-transaction click queue. Later work (food, ender-chest routing) builds on it.
- **`BreakHandler` rewrite** (Branch C) — server-confirmation-driven mining; later bypasses plug into the same packet layer.
- A **`packet`-safe abstraction** over `mc.getConnection().send(...)` + `startPrediction` so all interaction branches speak one language (extracted by Branch C, used by A/B as they merge).

## Merge strategy

Branches are independent off `master`. Suggested merge order (each is independently testable):
1. **C (mining)** — fixes the most visible bug, lowest risk, standalone.
2. **A (impossible place)** — small, unblocks placing quality.
3. **B (inventory)** — largest; builds on C's packet layer if merged.
4. **D (blueprint extras)** — smallest; can land any time.

Order is a recommendation only; since they're independent worktrees, they can also all be developed in parallel (user's chosen mode) and merged as each passes `./gradlew build`.

## Worktree / branch layout

- Main agent stays on the master checkout; never moves directories.
- Each feature branch gets a manual `git worktree add <path> -b <branch>` under `.worktrees/<branch>/`.
- `.worktrees/` added to `.gitignore` (verified NOT currently ignored) — committed on master.
- Subagents operate inside a worktree by **absolute path** (Read/Write/Edit/Bash with `cd`), no `EnterWorktree` (avoids the main-agent directory-move warning and the `.claude/worktrees` phantom state).

## Notes

- **Stale `feature/impossible-place` branch**: exists locally + origin, but based on pre-26.1 master (1.21.4 yarn, `VirtualThreads`, old mappings). It is **not** a clean ancestor of current master. Per the user ("use that branch but edit as you wish"), recreate it from current master and carry over the reusable concept (`TaskState.IMPOSSIBLE_PLACE`, the "no sequence → deferred placement" idea), not the code.
- No `Co-Authored-By` trailers; no agent-generated claims in commit messages (per AGENTS.md).
- `./gradlew build` is the primary verification (no unit tests in this project).
- `run/` is real user data — never touch it.

## Future (explicitly deferred, noted for later branches)

- Instant-break + multi-break + `packetFlood` + NCP priming (user wants these "implemented later").
- BRIDGE/scaffold movement rescue for impossible placements (needs movement-state infra).
- Food management (`saveFood` / eat), shulker-in-ender-chest routing.
- Anti-cheat bypasses (`obscureBreakingProgress`, lag-based pausing beyond what exists).
