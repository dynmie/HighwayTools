# HighwayTools Upgrade to Meteor Client 26.1 / Minecraft 26.1.2

Date: 2026-08-10

## Purpose

Upgrade the HighwayTools Meteor Client addon from the MC 1.21.4 / yarn / meteor-client 1.21.4-SNAPSHOT / baritone 1.13.1 ecosystem to the modern unobfuscated ecosystem, targeting **Meteor Client 26.1** per the user's explicit requirement (chosen for baritone compatibility).

The outcome: the addon compiles, registers, and behaves the same as it did on 1.21.4, with the same settings, blueprint generator, block-task state machine, handlers, and baritone process — plus targeted modernization of internals that the port surfaces.

## Why 26.1 (not 26.2)

- Minecraft **26.1** is the first *unobfuscated* release. Yarn is officially discontinued for 26.1+; Mojang official names are the class names; no mappings are declared.
- **Meteor Client** publishes `meteordevelopment:meteor-client:26.1.2-SNAPSHOT` for MC 26.1.2, compiled with mojmap names.
- **Baritone** publishes `meteordevelopment:baritone:26.1-SNAPSHOT` (mod id `baritone-meteor`) on the same maven — the baritone meteor 26.1 itself pairs with (meteor's version catalog depends on `baritone:26.1-SNAPSHOT`). It supports MC 26.1/26.1.1/26.1.2 and bundles `nether-pathfinder-1.4.1` jar-in-jar.
- **Baritone 26.2** is a one-off community fork that meteor 26.2 itself doesn't cleanly support (meteor 26.2's PR needs a `net.minecraft.util.Tuple` shim). 26.1 is the coherent baritone-compatible target.

All API claims below were **verified against the actual meteor-client 26.1.2 sources jar and baritone 26.1 jar** (downloaded and inspected during research).

## Scope

The user selected **"Port + modernize internals"**: do the 26.1 port AND apply grounded cleanups that the migration surfaces. Runtime behavior and architecture are preserved; this is a port, not a rewrite.

## Target versions

| Component | Current | Target |
|---|---|---|
| Minecraft | 1.21.4 | 26.1.2 |
| Meteor Client | `libs/meteor-client-1.21.4-42.jar` | `meteordevelopment:meteor-client:26.1.2-SNAPSHOT` |
| Baritone | `libs/baritone-api-fabric-1.13.1.jar` | `meteordevelopment:baritone:26.1-SNAPSHOT` |
| Fabric Loader | 0.18.2 | 0.19.3 |
| Loom | `fabric-loom` 1.15-SNAPSHOT (remap) | `net.fabricmc.fabric-loom` 1.17-SNAPSHOT (no remap) |
| JDK toolchain | 21 | 25 (foojay auto-provisions) |
| Gradle wrapper | 9.3.1 | 9.6.1 |
| nether-pathfinder | `dev.babbaj:nether-pathfinder:1.4.1` | removed (bundled in baritone 26.1) |

## Part 1 — Build system rewrite (Groovy → Kotlin DSL)

- **Delete:** `build.gradle`, `settings.gradle`, old `gradle.properties`.
- **`settings.gradle.kts`:** `pluginManagement` (Fabric maven, mavenCentral, gradlePluginPortal); foojay resolver convention `1.0.0` (auto-provisions JDK 25); `rootProject.name = "highway"`.
- **`gradle/libs.versions.toml`:** `minecraft=26.1.2`, `loader=0.19.3`, `meteor=26.1.2-SNAPSHOT`, `baritone=26.1-SNAPSHOT`; libraries `meteor-client`, `baritone`; plugin `fabric-loom = { id = "net.fabricmc.fabric-loom", version = "1.17-SNAPSHOT" }`.
- **`gradle.properties`:** drop `yarn_mappings`; set `minecraft_version=26.1.2`, `loader_version=0.19.3`, `meteor_version=26.1.2-SNAPSHOT`, `baritone_version=26.1-SNAPSHOT`; keep `mod_version`, `maven_group`, `archives_base_name`.
- **`build.gradle.kts`:** `net.fabricmc.fabric-loom` (NOT `-remap`); JDK 25 toolchain; meteor maven releases + snapshots, jitpack.io; deps `minecraft("com.mojang:minecraft:26.1.2")`, `implementation(fabric-loader)`, `implementation(meteor-client)`, `implementation(baritone)`; `processResources` expanding `version`/`mc_version` into `fabric.mod.json`; JavaCompile UTF-8 + release 25. No `mappings`, no `modImplementation`/`modCompileOnly` (26.1 unobfuscated).
- **Wrapper:** `gradle-9.6.1-bin.zip`.
- **CI `.github/workflows/gradle.yml`:** `actions/setup-java` java-version `'21'` → `'25'` (both steps).

## Part 2 — fabric.mod.json

- `depends`: `"java": ">=25"`, `"baritone": "*"` → `"baritone-meteor": "*"`, add `"fabricloader": ">=0.19.2"`; keep `"minecraft": ">=${mc_version}"`, `"meteor-client": "*"`.
- `addon-template.mixins.json`: `compatibilityLevel` `JAVA_21` → `JAVA_25`.

## Part 3 — Source migration yarn → mojmap

Mojang official names are the real class names. Apply the global substitutions (see plan `clever-forging-pine.md` for the full tables); most files are import+rename only.

Key substitutions (non-exhaustive):

| Yarn | Mojmap 26.1 |
|---|---|
| `net.minecraft.client.MinecraftClient` / `getInstance()` | `net.minecraft.client.Minecraft` / `getInstance()` |
| `net.minecraft.util.math.BlockPos` / `Vec3d` / `Direction` / `Vec3i` | `net.minecraft.core.BlockPos` / `net.minecraft.world.phys.Vec3` / `net.minecraft.core.Direction` / `net.minecraft.core.Vec3i` |
| `net.minecraft.block.{Block,Blocks,BlockState}` | `net.minecraft.world.level.block.{Block,Blocks}` / `...block.state.BlockState` |
| `net.minecraft.block.ShulkerBoxBlock` / `FluidBlock` | `net.minecraft.world.level.block.ShulkerBoxBlock` / `...block.LiquidBlock` |
| `net.minecraft.item.{Item,Items,ItemStack}` | `net.minecraft.world.item.{Item,Items,ItemStack}` |
| `net.minecraft.text.{Text,MutableText}` | `net.minecraft.network.chat.{Component,MutableComponent}`; `Text.literal` → `Component.literal` |
| `net.minecraft.util.Formatting` | `net.minecraft.ChatFormatting` |
| `net.minecraft.sound.BlockSoundGroup` | `net.minecraft.world.level.block.SoundType` |
| `net.minecraft.util.Hand` | `net.minecraft.world.InteractionHand` |
| `net.minecraft.command.CommandSource` | `net.minecraft.client.multiplayer.ClientSuggestionProvider` |
| `PlayerActionC2SPacket` | `net.minecraft.network.protocol.game.ServerboundPlayerActionPacket` |

Method renames (non-exhaustive): `mc.world`→`mc.level`; `mc.interactionManager`→`mc.gameMode`; `mc.getNetworkHandler()`→`mc.getConnection()` (returns `ClientPacketListener`), `sendPacket`→`send`; `getNetworkHandler().getConnection().disconnect(t)`→`getConnection().getConnection().disconnect(t)`; `getPos()`→`position()`, `getBlockPos()`→`blockPosition()`, `getEyePos()`→`getEyePosition()`; `getYaw/setYaw`→`getYRot/setYRot`, `getPitch/setPitch`→`getXRot/setXRot`; `swingHand`→`swing`, `isOnGround`→`onGround`; `toCenterPos`→`getCenter`, `mutableCopy`→`mutable`, `up/down`→`above/below`, `offset(dir)`→`relative(dir)`, `add(...)`→`offset(...)`; `getDefaultState`→`defaultBlockState`, `isReplaceable`→`canBeReplaced`, `getSoundGroup`→`getSoundType`, `isShapeFullCube`→`isShapeFullBlock`; `calcBlockBreakingDelta`→`getDestroyProgress`; `getMiningSpeedMultiplier`→`getDestroySpeed`, `isSuitableFor`→`isCorrectToolForDrops`; `inventory.selectedSlot`→`getSelectedSlot()/setSelectedSlot(int)`, `inventory.size()`→`getContainerSize()`, `getStack(i)`→`getItem(i)`; `dotProduct`→`dot`.

## Part 4 — Localized rewrites (API genuinely changed)

1. **`InventoryHandler.findBestTool`** — the yarn enchantment-registry API is gone. Replace with meteor's `Utils.getEnchantmentLevel(stack, Enchantments.SILK_TOUCH)` (and `UNBREAKING`/`EFFICIENCY`/`MENDING`/`FORTUNE`) over the new `ItemEnchantments`/`Holder<Enchantment>` model; `getDestroySpeed`/`isCorrectToolForDrops`; `getContainerSize()`/`getItem(i)`.
2. **`BreakHandler`** — `mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.X, pos, dir))`; `getDestroyProgress`; `setSelectedSlot(int)`; `mc.gameMode` for `interactionManager`.
3. **`HighwayTools` module** — `Component`/`ChatFormatting`; disconnect chain `mc.getConnection().getConnection().disconnect(text)`; rotation/position renames; `Block.isShapeFullBlock(block.defaultBlockState().getCollisionShape(mc.level, ZERO))`.

## Part 5 — Modernization (user-selected)

- **`InventoryHandler.findBestTool`** → use meteor's **`InvUtils.findFastestTool(BlockState)`** (identical correct-tool + speed scoring) plus silk-touch constraint via `Utils.getEnchantmentLevel(stack, Enchantments.SILK_TOUCH)`. Removes the enchantment-registry scaffolding entirely.
- **Delete confirmed dead code:** `WalkingState.java` (zero usages), `TaskExecutor.place(BlockTask,int)` (private, no callers), unused `TaskState.PICKUP`/`RESTOCK`/`OPEN_CONTAINER` members, `BlockTask.isOpen` (no readers).
- **Fully-qualified imports:** `TaskExecutor`/`PlaceHandler` pull `meteordevelopment.meteorclient.utils.world.BlockUtils` as an import instead of fully-qualified inline refs.

## Preserve `run/`

`run/` is the dev Minecraft game directory for `runClient` (saves, meteor config/profiles/waypoints, baritone settings, options, server list). It is **never deleted or modified**. Loom reuses the existing dir. The 1.21.4 `New World` save is left untouched; opening it in 26.1.2 is the user's call at runtime.

## Verification

1. `./gradlew build` — first run auto-downloads JDK 25 (foojay), Gradle 9.6.1, loom 1.17-SNAPSHOT, MC 26.1.2, meteor/baritone SNAPSHOTs; fix forward per the mapping tables.
2. If toolchain download fails: add `org.gradle.java.installations.auto-download=true` to `gradle.properties` or install JDK 25.
3. If loom 1.17-SNAPSHOT won't resolve, fall back to a stable 1.17.x (1.15+ supports 26.1); keep plugin id `net.fabricmc.fabric-loom`.
4. `javap` the two open signatures against the merged MC jar (`MultiPlayerGameMode.breakBlock`, `SoundType.getBreakSound`) and adjust.
5. `./gradlew runClient` reusing `run/` — module registers, `checkblocks` command works, baritone process active, mining/placing works.
6. Confirm built jar's `fabric.mod.json` expanded with `26.1.2`, no remap/obfuscation references.

## Out of scope / deferred

- Not touching the existing `run/saves/New World` (1.21.4 save).
- Not upgrading to 26.2 (user chose 26.1 for baritone compat).
- No unrelated refactoring beyond the grounded cleanups listed in Part 5.
