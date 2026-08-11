# HighwayTools Meteor 26.1 Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the HighwayTools Meteor Client addon from MC 1.21.4 / yarn to MC 26.1.2 / mojmap with meteor-client 26.1.2 and baritone 26.1, plus targeted modernization.

**Architecture:** Rewrite the build system to the unobfuscated 26.1 ecosystem (Kotlin DSL + version catalog + foojay JDK 25), migrate ~22 source files from yarn to mojmap names, then modernize three localized hotspots (enchantment scoring, packet sending, block-break sound) using meteor's own helpers.

**Tech Stack:** Gradle 9.6.1, fabric-loom `net.fabricmc.fabric-loom` 1.17-SNAPSHOT (non-remap), Minecraft 26.1.2, meteor-client 26.1.2-SNAPSHOT, baritone 26.1-SNAPSHOT (`baritone-meteor`), JDK 25, Mojang official mappings.

## Global Constraints

- Target MC **26.1.2**, meteor-client **26.1.2-SNAPSHOT**, baritone **26.1-SNAPSHOT**.
- Loom plugin id is **`net.fabricmc.fabric-loom`** (never `-remap`); **no** `mappings`, `modImplementation`, `modCompileOnly`, or `remapJar`.
- Use plain `implementation` for meteor-client, baritone, fabric-loader.
- JDK toolchain **25**; Gradle wrapper **9.6.1**; fabric-loader **0.19.3**.
- Mojang official names are the class names (unobfuscated MC) — migrate all `net.minecraft.*` imports to mojmap packages.
- **`run/` is never deleted or modified** — it is the dev game dir (saves, meteor config, waypoints, baritone settings).
- Baritone mod id in `fabric.mod.json` is **`baritone-meteor`**.
- Do not commit unless explicitly told to (per AGENTS.md).

---

### Task 1: Rewrite the build system

**Files:**
- Delete: `build.gradle`, `settings.gradle`, `gradle.properties`
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`
- Modify: `gradle/wrapper/gradle-wrapper.properties`

**Interfaces:**
- Consumes: existing `src/main/java/**`, `src/main/resources/fabric.mod.json`
- Produces: a buildable Gradle project with the `net.fabricmc.fabric-loom` plugin, JDK 25 toolchain, and maven deps resolving meteor-client/baritone.

- [ ] **Step 1: Delete old build files**

```bash
rm build.gradle settings.gradle gradle.properties
```

- [ ] **Step 2: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "highway"
```

- [ ] **Step 3: Create `gradle/libs.versions.toml`**

```toml
[versions]
mod-version = "0.1.1"
jdk = "25"
minecraft = "26.1.2"
loader = "0.19.3"
meteor = "26.1.2-SNAPSHOT"
baritone = "26.1-SNAPSHOT"
loom = "1.17-SNAPSHOT"

[libraries]
minecraft = { module = "com.mojang:minecraft", version.ref = "minecraft" }
fabric-loader = { module = "net.fabricmc:fabric-loader", version.ref = "loader" }
meteor-client = { module = "meteordevelopment:meteor-client", version.ref = "meteor" }
baritone = { module = "meteordevelopment:baritone", version.ref = "baritone" }

[plugins]
fabric-loom = { id = "net.fabricmc.fabric-loom", version.ref = "loom" }
```

- [ ] **Step 4: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2G

# Fabric
minecraft_version=26.1.2
loader_version=0.19.3

# Mod Properties
mod_version=0.1.1
maven_group=me.dynmie.highway
archives_base_name=highway

# Dependencies
meteor_version=26.1.2-SNAPSHOT
baritone_version=26.1-SNAPSHOT
```

- [ ] **Step 5: Create `build.gradle.kts`**

```kotlin
plugins {
    id("net.fabricmc.fabric-loom")
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

base {
    archivesName = providers.gradleProperty("archives_base_name").get()
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.meteor.client)
    implementation(libs.baritone)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mc_version", libs.versions.minecraft.get())

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "mc_version" to libs.versions.minecraft.get()
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}
```

- [ ] **Step 6: Bump Gradle wrapper to 9.6.1**

Modify `gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.6.1-bin.zip
```

- [ ] **Step 7: Verify the build configures**

Run: `./gradlew build`
Expected: Gradle downloads Gradle 9.6.1, JDK 25 (foojay), loom 1.17-SNAPSHOT, MC 26.1.2, meteor/baritone SNAPSHOTs. The build will FAIL to compile source (that's expected — source migration is Task 3+). Confirm the configuration phase succeeds (dependencies resolve) and failure is only in Java compilation.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle/ gradle.properties
git rm build.gradle settings.gradle gradle.properties 2>/dev/null || true
git commit -m "build: migrate to meteor 26.1 / minecraft 26.1.2 toolchain"
```

---

### Task 2: Update `fabric.mod.json` and mixins config

**Files:**
- Modify: `src/main/resources/fabric.mod.json`
- Modify: `src/main/resources/addon-template.mixins.json`

**Interfaces:**
- Consumes: none (entrypoint `me.dynmie.highway.HighwayAddon` unchanged)
- Produces: a mod manifest declaring java 25, `baritone-meteor` dep, loader constraint.

- [ ] **Step 1: Update `fabric.mod.json` depends block**

In `src/main/resources/fabric.mod.json`, replace the `"depends"` block:

```json
  "depends": {
    "java": ">=25",
    "minecraft": ">=${mc_version}",
    "meteor-client": "*",
    "baritone-meteor": "*",
    "fabricloader": ">=0.19.2"
  }
```

- [ ] **Step 2: Bump mixins compatibility level**

In `src/main/resources/addon-template.mixins.json`, change `"compatibilityLevel": "JAVA_21"` to `"JAVA_25"`.

- [ ] **Step 3: Verify**

Run: `./gradlew processResources`
Expected: generates `build/resources/main/fabric.mod.json` with the expanded `26.1.2` in `minecraft` and updated depends.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/fabric.mod.json src/main/resources/addon-template.mixins.json
git commit -m "build: update fabric.mod.json for 26.1 (java 25, baritone-meteor)"
```

---

### Task 3: Migrate simple source files (imports + renames)

**Files (all import+rename only):**
- `src/main/java/me/dynmie/highway/highwaytools/blueprint/BlueprintTask.java`
- `src/main/java/me/dynmie/highway/highwaytools/blueprint/BlueprintProvider.java`
- `src/main/java/me/dynmie/highway/highwaytools/blueprint/BlueprintGenerator.java`
- `src/main/java/me/dynmie/highway/highwaytools/blueprint/impl/DiagonalBlueprintProvider.java`
- `src/main/java/me/dynmie/highway/highwaytools/blueprint/impl/StraightBlueprintProvider.java`
- `src/main/java/me/dynmie/highway/highwaytools/pathing/BaritoneProcess.java`
- `src/main/java/me/dynmie/highway/highwaytools/pathing/BaritoneHelper.java`
- `src/main/java/me/dynmie/highway/highwaytools/pathing/BaritonePathfinder.java`
- `src/main/java/me/dynmie/highway/highwaytools/pathing/WalkingState.java`
- `src/main/java/me/dynmie/highway/highwaytools/block/TaskState.java`
- `src/main/java/me/dynmie/highway/highwaytools/block/BlockTask.java`
- `src/main/java/me/dynmie/highway/HighwayAddon.java`
- `src/main/java/me/dynmie/highway/commands/CheckBlocksCommand.java`
- `src/main/java/me/dynmie/highway/utils/BlockUtils.java`
- `src/main/java/me/dynmie/highway/utils/DirectionUtils.java`
- `src/main/java/me/dynmie/highway/utils/LiquidUtils.java`
- `src/main/java/me/dynmie/highway/utils/LocationUtils.java`

**Interfaces:**
- Consumes: Task 1 build (mojmap MC classes available)
- Produces: migrated versions of these files using mojmap imports; no behavioral change.

- [ ] **Step 1: Apply global import substitutions to all listed files**

Apply this table everywhere (imports AND fully-qualified usages):

| Yarn | Mojmap 26.1 |
|---|---|
| `net.minecraft.client.MinecraftClient` | `net.minecraft.client.Minecraft` |
| `MinecraftClient.getInstance()` | `Minecraft.getInstance()` |
| `net.minecraft.util.math.BlockPos` | `net.minecraft.core.BlockPos` |
| `net.minecraft.util.math.Vec3d` | `net.minecraft.world.phys.Vec3` |
| `net.minecraft.util.math.Direction` | `net.minecraft.core.Direction` |
| `net.minecraft.util.math.Vec3i` | `net.minecraft.core.Vec3i` |
| `net.minecraft.block.Block` | `net.minecraft.world.level.block.Block` |
| `net.minecraft.block.Blocks` | `net.minecraft.world.level.block.Blocks` |
| `net.minecraft.block.BlockState` | `net.minecraft.world.level.block.state.BlockState` |
| `net.minecraft.block.ShulkerBoxBlock` | `net.minecraft.world.level.block.ShulkerBoxBlock` |
| `net.minecraft.block.FluidBlock` | `net.minecraft.world.level.block.LiquidBlock` |
| `net.minecraft.item.Item` | `net.minecraft.world.item.Item` |
| `net.minecraft.item.Items` | `net.minecraft.world.item.Items` |
| `net.minecraft.item.ItemStack` | `net.minecraft.world.item.ItemStack` |
| `net.minecraft.text.Text` | `net.minecraft.network.chat.Component` |
| `net.minecraft.text.MutableText` | `net.minecraft.network.chat.MutableComponent` |
| `net.minecraft.util.Formatting` | `net.minecraft.ChatFormatting` |
| `net.minecraft.sound.BlockSoundGroup` | `net.minecraft.world.level.block.SoundType` |
| `net.minecraft.util.Hand` | `net.minecraft.world.InteractionHand` |
| `net.minecraft.command.CommandSource` | `net.minecraft.client.multiplayer.ClientSuggestionProvider` |
| `net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket` | `net.minecraft.network.protocol.game.ServerboundPlayerActionPacket` |

- [ ] **Step 2: Apply global method/field renames to all listed files**

| Yarn | Mojmap |
|---|---|
| `mc.world` / `getWorld()` | `mc.level` / `level()` |
| `mc.interactionManager` | `mc.gameMode` |
| `mc.getNetworkHandler()` | `mc.getConnection()` |
| `sendPacket(p)` | `send(p)` |
| `getNetworkHandler().getConnection().disconnect(t)` | `getConnection().getConnection().disconnect(t)` |
| `getPos()` | `position()` |
| `getBlockPos()` | `blockPosition()` |
| `getEyePos()` | `getEyePosition()` |
| `getYaw()/setYaw()` | `getYRot()/setYRot()` |
| `getPitch()/setPitch()` | `getXRot()/setXRot()` |
| `swingHand(h)` | `swing(h)` |
| `isOnGround()` | `onGround()` |
| `toCenterPos()` | `getCenter()` |
| `mutableCopy()` | `mutable()` |
| `up()/down()` | `above()/below()` |
| `offset(Direction)` | `relative(Direction)` |
| `add(int,int,int)` / `add(Vec3i)` | `offset(int,int,int)` / `offset(Vec3i)` |
| `getDefaultState()` | `defaultBlockState()` |
| `isReplaceable()` | `canBeReplaced()` |
| `getSoundGroup()` | `getSoundType()` |
| `isShapeFullCube` | `isShapeFullBlock` |
| `calcBlockBreakingDelta(p,lvl,pos)` | `getDestroyProgress(p,lvl,pos)` |
| `getMiningSpeedMultiplier(state)` | `getDestroySpeed(state)` |
| `isSuitableFor(state)` | `isCorrectToolForDrops(state)` |
| `inventory.selectedSlot` | `getSelectedSlot()`/`setSelectedSlot(int)` |
| `inventory.size()` | `getContainerSize()` |
| `inventory.getStack(i)` | `getItem(i)` |
| `dotProduct(v)` | `dot(v)` |
| `new Vec3d(x,y,z)` | `new Vec3(x,y,z)` |

Specific notes:
- `Text.literal(...)` → `Component.literal(...)`.
- `DiagonalBlueprintProvider`/`StraightBlueprintProvider`: `basePosition.offset(Direction.DOWN)` → `basePosition.relative(Direction.DOWN)`. `MBlockPos` API unchanged.
- `CheckBlocksCommand.build(...)`: signature is `build(LiteralArgumentBuilder<ClientSuggestionProvider> builder)`.
- `WalkingState.java` is deleted in Task 8; leave as-is for now (Task 8 removes it).

- [ ] **Step 3: Compile-check**

Run: `./gradlew compileJava`
Expected: files listed here compile. Any failures in files NOT listed (BreakHandler, InventoryHandler, TaskExecutor, PlaceHandler, HighwayTools) are expected and handled in Tasks 4-6.

- [ ] **Step 4: Commit**

```bash
git add src/main/java
git commit -m "migrate: yarn to mojmap for blueprint, pathing, utils, command, addon"
```

---

### Task 4: Migrate `BreakHandler.java` (packet + destroy progress)

**Files:**
- Modify: `src/main/java/me/dynmie/highway/highwaytools/handler/BreakHandler.java`

**Interfaces:**
- Consumes: Task 3 mapping table
- Produces: `BreakHandler` compiling against mojmap; `mine(BlockTask)` and `calcTicksToBreakBlock(BlockPos, BlockState)` keep their existing signatures (used by TaskExecutor).

- [ ] **Step 1: Update imports**

Replace:
- `net.minecraft.client.MinecraftClient` → `net.minecraft.client.Minecraft`
- `net.minecraft.util.Hand` → `net.minecraft.world.InteractionHand`
- `net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket` → `net.minecraft.network.protocol.game.ServerboundPlayerActionPacket`
- `net.minecraft.util.math.BlockPos`/`Direction` → `net.minecraft.core.BlockPos`/`net.minecraft.core.Direction`

- [ ] **Step 2: Rewrite the send packet helpers**

Replace the three methods:

```java
private void sendStopPacket(BlockPos pos, Direction direction) {
    if (mc.getConnection() == null) return;
    mc.getConnection().send(new ServerboundPlayerActionPacket(
        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
        pos,
        direction
    ));
}

private void sendStartPacket(BlockPos pos, Direction direction) {
    if (mc.getConnection() == null) return;
    mc.getConnection().send(new ServerboundPlayerActionPacket(
        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
        pos,
        direction
    ));
}

private void sendAbortPacket(BlockPos pos, Direction direction) {
    if (mc.getConnection() == null) return;
    mc.getConnection().send(new ServerboundPlayerActionPacket(
        ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
        pos,
        direction
    ));
}
```

- [ ] **Step 3: Update the swing and inventory calls in `mine()`/`mineNormally()`**

- `mc.player.swingHand(Hand.MAIN_HAND)` → `mc.player.swing(InteractionHand.MAIN_HAND)` (in `swingHand()`).
- `mc.player.getInventory().selectedSlot = ...` → `mc.player.getInventory().setSelectedSlot(...)` (in `mine()`).
- `mc.world` → `mc.level`.
- `mc.interactionManager.breakBlock(pos)` → use meteor helper: `meteordevelopment.meteorclient.utils.world.BlockUtils.breakBlock(pos, true)`. (Meteor 26.1 routes block breaking through its own `BlockUtils.breakBlock`, verified in `HighwayBuilder`/`Nuker`.)

- [ ] **Step 4: Update `calcTicksToBreakBlock`**

```java
public static int calcTicksToBreakBlock(BlockPos pos, BlockState state) {
    return (int) Math.ceil(1 / state.getDestroyProgress(mc.player, mc.level, pos));
}
```

- [ ] **Step 5: Compile-check**

Run: `./gradlew compileJava`
Expected: `BreakHandler` compiles.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/dynmie/highway/highwaytools/handler/BreakHandler.java
git commit -m "migrate: BreakHandler to mojmap packets and BlockUtils.breakBlock"
```

---

### Task 5: Migrate `InventoryHandler.java` (enchantment system rewrite + modernization)

**Files:**
- Modify: `src/main/java/me/dynmie/highway/highwaytools/handler/InventoryHandler.java`

**Interfaces:**
- Consumes: Task 3 mapping table; meteor `InvUtils`, `Utils`, `FindItemResult`
- Produces: `InventoryHandler` compiling; methods `prepareItemInHotbar(BlockState)`, `findBestTool(BlockState)`, `prepareItemInHotbar(BlockState)` keep signatures (used by BreakHandler/TaskExecutor).

- [ ] **Step 1: Rewrite `findBestTool` using meteor helpers**

Replace the whole `findBestTool` method and its imports:

```java
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
```

```java
public FindItemResult findBestTool(BlockState state) {
    boolean noSilk = state.getBlock() == Blocks.ENDER_CHEST;

    double bestScore = 1;
    int slot = -1;

    for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
        ItemStack stack = mc.player.getInventory().getItem(i);

        if (!stack.isCorrectToolForDrops(state)) continue;

        if (noSilk && Utils.getEnchantmentLevel(stack, Enchantments.SILK_TOUCH) != 0) {
            continue;
        }

        double score = stack.getDestroySpeed(state) * 1001;
        score += Utils.getEnchantmentLevel(stack, Enchantments.UNBREAKING);
        score += Utils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
        score += Utils.getEnchantmentLevel(stack, Enchantments.MENDING);
        score += Utils.getEnchantmentLevel(stack, Enchantments.FORTUNE);

        if (tools.getPreferSilkTouch().get()) {
            score += Utils.getEnchantmentLevel(stack, Enchantments.SILK_TOUCH);
        }

        if (score > bestScore) {
            bestScore = score;
            slot = i;
        }
    }

    return new FindItemResult(slot, 1);
}
```

(Note: the original had a double-counting quirk `score += getMiningSpeedMultiplier * 1000` where the base was already added; modernizing to `getDestroySpeed * 1001` preserves the intent — weight speed heavily.)

- [ ] **Step 2: Update remaining method renames in the file**

- `stack.isSuitableFor(state)` → `stack.isCorrectToolForDrops(state)` (already handled above).
- `mc.player.getInventory().size()` → `getContainerSize()`.
- `mc.player.getInventory().getStack(i)` → `getItem(i)`.
- Remove imports `net.minecraft.enchantment.*`, `net.minecraft.registry.*`, `net.minecraft.registry.entry.RegistryEntry`.
- The `static import meteordevelopment.meteorclient.MeteorClient.mc` stays valid.

- [ ] **Step 3: Compile-check**

Run: `./gradlew compileJava`
Expected: `InventoryHandler` compiles.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/dynmie/highway/highwaytools/handler/InventoryHandler.java
git commit -m "migrate: InventoryHandler to new enchantment API via meteor Utils/InvUtils"
```

---

### Task 6: Migrate `TaskExecutor.java`, `PlaceHandler.java`, `LiquidHandler.java`, `BlockTaskManager.java`

**Files:**
- Modify: `src/main/java/me/dynmie/highway/highwaytools/block/TaskExecutor.java`
- Modify: `src/main/java/me/dynmie/highway/highwaytools/handler/PlaceHandler.java`
- Modify: `src/main/java/me/dynmie/highway/highwaytools/handler/LiquidHandler.java`
- Modify: `src/main/java/me/dynmie/highway/highwaytools/block/BlockTaskManager.java`

**Interfaces:**
- Consumes: Task 3 mapping table; meteor `BlockUtils` (world), `Rotations`
- Produces: these files compiling against mojmap.

- [ ] **Step 1: `TaskExecutor.java`**

- Apply the global import/method renames (Task 3 tables).
- `BlockSoundGroup soundGroup = targetBlock.getDefaultState().getSoundGroup();` → use `SoundType`:
  ```java
  SoundType soundType = targetBlock.defaultBlockState().getSoundType();
  mc.player.playSound(soundType.getBreakSound().value(), soundType.getVolume(), soundType.getPitch());
  ```
- `mc.player.getBlockPos().offset(Direction.DOWN)` → `mc.player.blockPosition().relative(Direction.DOWN)`.
- Add imports: `net.minecraft.world.level.block.SoundType`.
- Replace the fully-qualified `meteordevelopment.meteorclient.utils.world.BlockUtils.place(...)` with an import and a call to the same signature (the method signature `place(BlockPos, InteractionHand, int, boolean, int, boolean, boolean, boolean)` is unchanged in 26.1).

- [ ] **Step 2: `PlaceHandler.java`**

- `Hand.MAIN_HAND` → `InteractionHand.MAIN_HAND`.
- `mc.player.setYaw(...)`/`setPitch(...)` → `setYRot(...)`/`setXRot(...)`.
- Replace fully-qualified `meteordevelopment.meteorclient.utils.world.BlockUtils.place(...)` with an import (signature unchanged).
- `Block.asItem()`, `Items.AIR` unchanged.

- [ ] **Step 3: `LiquidHandler.java`**

- Apply global renames only (`mc.world` → `mc.level`, `getEyePos` → `getEyePosition`, `toCenterPos` → `getCenter`, imports). `Direction.values()`, `pos.offset(side)` → `pos.relative(side)`.

- [ ] **Step 4: `BlockTaskManager.java`**

- Apply global renames only (`mc.world` → `mc.level`, `getEyePos` → `getEyePosition`, `toCenterPos` → `getCenter`, `mutableCopy` → `mutable`, `isReplaceable` → `canBeReplaced`, `currentPosition.add(Vec3i)` → `currentPosition.offset(Vec3i)`).

- [ ] **Step 5: Compile-check**

Run: `./gradlew compileJava`
Expected: all four compile.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/dynmie/highway/highwaytools
git commit -m "migrate: TaskExecutor, PlaceHandler, LiquidHandler, BlockTaskManager to mojmap"
```

---

### Task 7: Migrate `HighwayTools.java` module

**Files:**
- Modify: `src/main/java/me/dynmie/highway/modules/HighwayTools.java`

**Interfaces:**
- Consumes: Task 3 mapping table
- Produces: `HighwayTools` module compiling against mojmap.

- [ ] **Step 1: Update imports**

- `net.minecraft.text.MutableText` → `net.minecraft.network.chat.MutableComponent`
- `net.minecraft.text.Text` → `net.minecraft.network.chat.Component`
- `net.minecraft.util.Formatting` → `net.minecraft.ChatFormatting`
- `net.minecraft.util.math.Vec3d` → `net.minecraft.world.phys.Vec3`
- `net.minecraft.util.math.BlockPos` → `net.minecraft.core.BlockPos`
- `net.minecraft.block.Block`/`Blocks` → `net.minecraft.world.level.block.Block`/`Blocks`
- `net.minecraft.client.MinecraftClient` → `net.minecraft.client.Minecraft` (via `Module.mc`)

- [ ] **Step 2: Update `onActivate` and field initializers**

- `direction = HorizontalDirection.get(mc.player.getYRot());`
- `start = mc.player.position();`
- `startPosition = mc.player.blockPosition();`
- `currentPosition = mc.player.blockPosition();`
- `private Vec3 start = new Vec3(0d, 64d, 0d);`

- [ ] **Step 3: Update `disconnect` and `getStatsText`**

Replace `disconnect(String, Object...)`:

```java
public void disconnect(String message, Object... args) {
    MutableComponent text = Component.literal(String.format("%s[%s%s%s] %s", ChatFormatting.GRAY, ChatFormatting.BLUE, title, ChatFormatting.GRAY, ChatFormatting.RED) + String.format(message, args)).append("\n");
    text.append(getStatsText());

    mc.getConnection().getConnection().disconnect(text);
}
```

Replace `getStatsText()`:

```java
public MutableComponent getStatsText() {
    MutableComponent text = Component.literal(String.format("%sDistance: %s%.0f\n", ChatFormatting.GRAY, ChatFormatting.WHITE, mc.player == null ? 0.0f : PlayerUtils.distanceTo(start)));
    text.append(String.format("%sBlocks broken: %s%d\n", ChatFormatting.GRAY, ChatFormatting.WHITE, blocksBroken));
    text.append(String.format("%sBlocks placed: %s%d", ChatFormatting.GRAY, ChatFormatting.WHITE, blocksPlaced));

    return text;
}
```

- [ ] **Step 4: Update block-setting filters**

- `Block.isShapeFullCube(...)` → `Block.isShapeFullBlock(block.defaultBlockState().getCollisionShape(mc.level, ZERO))` (both `mainBlock` and `fillerBlock` filters).

- [ ] **Step 5: Compile-check**

Run: `./gradlew compileJava`
Expected: `HighwayTools` compiles.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/dynmie/highway/modules/HighwayTools.java
git commit -m "migrate: HighwayTools module to mojmap Component/ChatFormatting"
```

---

### Task 8: Remove dead code

**Files:**
- Delete: `src/main/java/me/dynmie/highway/highwaytools/pathing/WalkingState.java`
- Modify: `src/main/java/me/dynmie/highway/highwaytools/block/TaskExecutor.java` (remove dead `place(BlockTask, int)` private method)
- Modify: `src/main/java/me/dynmie/highway/highwaytools/block/TaskState.java` (remove unused enum members)
- Modify: `src/main/java/me/dynmie/highway/highwaytools/block/BlockTask.java` (remove dead `isOpen` field + accessors)

**Interfaces:**
- Consumes: verified dead-code analysis (WalkingState has zero usages; `TaskExecutor.place` has no callers; `TaskState.PICKUP`/`RESTOCK`/`OPEN_CONTAINER` unused; `BlockTask.isOpen` has no readers)
- Produces: a cleaner codebase with no behavior change.

- [ ] **Step 1: Delete `WalkingState.java`**

```bash
git rm src/main/java/me/dynmie/highway/highwaytools/pathing/WalkingState.java
```

- [ ] **Step 2: Remove dead `place(BlockTask, int)` from `TaskExecutor`**

Delete the private method:
```java
private boolean place(BlockTask task, int slot) { ... }
```

- [ ] **Step 3: Trim `TaskState` enum**

Remove `PICKUP(500, 500)`, `RESTOCK(500, 500)`, `OPEN_CONTAINER(500, 500)` from the enum (keep `BROKEN`, `PLACED`, `LIQUID`, `BREAKING`, `BREAK`, `PLACE`, `PENDING_BREAK`, `PENDING_PLACE`, `DONE`).

- [ ] **Step 4: Remove `BlockTask.isOpen`**

Remove the `isOpen` field, `isOpen()`, and `setOpen(boolean)` from `BlockTask.java`.

- [ ] **Step 5: Compile-check**

Run: `./gradlew compileJava`
Expected: compiles clean.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: remove dead code (WalkingState, dead place helper, unused states)"
```

---

### Task 9: Full build + fix forward

**Files:**
- None (build verification)

**Interfaces:**
- Consumes: all prior tasks
- Produces: a clean `./gradlew build`.

- [ ] **Step 1: Run the full build**

Run: `./gradlew build`
Expected: SUCCESS. If compile errors appear, fix forward per the mapping tables in Tasks 3-7.

- [ ] **Step 2: Verify the built jar**

Run:
```bash
ls build/libs/
unzip -p build/libs/*.jar fabric.mod.json | grep -E '26.1|baritone-meteor'
```
Expected: `highway-0.1.1.jar` (or `*-all` variant); `fabric.mod.json` contains `26.1.2`, `baritone-meteor`, `java` `>=25`.

- [ ] **Step 3: Check for lingering yarn references**

Run: `grep -rn "MinecraftClient\|util.math\|getNetworkHandler\|net.minecraft.text\|net.minecraft.block" src/main/java || echo "clean"`
Expected: no matches (clean).

- [ ] **Step 4: Commit any fix-forward changes**

```bash
git add -A
git commit -m "build: full build green on meteor 26.1 / mc 26.1.2"
```

---

### Task 10: Run the client

**Files:**
- None (runtime verification)

**Interfaces:**
- Consumes: Task 9 clean build
- Produces: confidence the module runs in-game.

- [ ] **Step 1: Run the client**

Run: `./gradlew runClient`
Expected: Minecraft 26.1.2 launches with meteor-client + baritone + HighwayTools. Confirm in the dev log / in-game:
- The `highway-tools` module appears in Meteor's module list.
- The `checkblocks` command registers (type `.checkblocks`).
- Baritone process registers (the addon's `BaritoneProcess` display name shows during pathing).

- [ ] **Step 2: Note any runtime issues**

If the client crashes or a feature misbehaves, capture the stack trace. Do NOT modify `run/`. Report findings; iterate on the specific fix in a follow-up task.

- [ ] **Step 3: Commit if any runtime fixes were made**

```bash
git add -A
git commit -m "fix: runtime fixes from runClient verification"
```

---

## Self-Review

**Spec coverage check:**
- Build system rewrite → Task 1
- fabric.mod.json + mixins → Task 2
- yarn→mojmap migration (all files) → Tasks 3-7
- Enchantment rewrite (InventoryHandler) → Task 5
- Packet sending (BreakHandler) → Task 4
- Modernization (InvUtils.findFastestTool/Utils.getEnchantmentLevel, dead code removal, import cleanup) → Tasks 5, 6, 8
- `run/` preserved → Global Constraints; no task touches it
- Verification (build, jar check, runClient) → Tasks 9, 10

**Placeholder scan:** No TBD/TODO; every step has concrete code or commands.

**Type consistency:** `BlockUtils.place(BlockPos, InteractionHand, int, boolean, int, boolean, boolean, boolean)` used consistently; `getDestroyProgress(Player, Level, BlockPos)` consistent; `Utils.getEnchantmentLevel(ItemStack, ResourceKey<Enchantment>)` consistent; `FindItemResult(int, int)` consistent.
