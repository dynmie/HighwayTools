# MetroHighwayTools
<img alt="GitHub release" src="https://img.shields.io/github/v/release/dynmie/HighwayTools?logo=java&style=for-the-badge"> <img alt="GitHub last commit" src="https://img.shields.io/github/last-commit/dynmie/HighwayTools?style=for-the-badge"> <img alt="GitHub Workflow Status" src="https://img.shields.io/github/actions/workflow/status/dynmie/HighwayTools/gradle.yml?branch=master&logo=github&style=for-the-badge">


[![that's crazy](https://github.com/dynmie/HighwayTools/assets/41315732/d458e693-fc99-4006-9935-3745dead2d1e)](https://github.com/dynmie/HighwayTools)


A port of [Meteor](https://github.com/MeteorDevelopment/meteor-client)'s and [Lambda](https://github.com/lambda-plugins/HighwayTools)'s highway tools.


You can find a demonstration video [here](https://www.youtube.com/watch?v=SsU_WlwD_mo).

## About
An attempt to add a fully automated highway building robot to Meteor client.
Created because the current highway tools built into Meteor is just plain terrible.

> [!CAUTION]
> This addon is not finished! There are bugs, and they are very big. Use at your own risk.

## Setting up

### Dependencies
- [Fabric Loader](https://fabricmc.net/)
- [Meteor Client 26.1](https://meteorclient.com/)
- [Baritone 26.1](https://github.com/cabaletta/baritone) (`baritone-meteor`, published on the [Meteor Maven](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/))

### Installation
1. Install Fabric (requires Java 25)
2. Add Meteor Client, Baritone, and HighwayTools to your `mods` folder
3. Done!

## Storage Management

When the module runs low on material or tools, it restocks automatically. The source is chosen by priority:

1. **Grind obsidian** from ender chests (AutoObsidian) — places an ender chest, mines its 8 obsidian, chains consecutive grinds until the inventory is full of obsidian, then resumes building.
2. A **shulker box** in the inventory holding the needed item — places it, pulls the item out, then breaks and picks the box back up.
3. The player's **ender chest** (shared storage).

Settings live under **Storage Management**:

| Setting | What it does |
| --- | --- |
| `save-material` | Never use the last N material blocks (restock when at/below). |
| `save-tools` | Restock pickaxes when at/below this many. |
| `save-ender` | Keep this many ender chests before grinding/breaking extras. |
| `grind-obsidian` | Grind obsidian from ender chests (AutoObsidian). |
| `restock-from-ender-chest` | Pull material from ender chests when no shulker has it. |
| `keep-free-slots` | Keep this many inventory slots empty during restock. |
| `leave-empty-shulkers` | Close and skip shulkers that are empty (instead of breaking them). |
| `prefer-ender-chests` | Prefer ender chests over shulkers for obsidian. |
| `fast-fill` | Pull as many item stacks as possible during restock. |
| `eject-list` | Items considered trash — the only items the bot will drop/swap away to make room. |

During restock the bot fills genuinely empty slots first and only swaps away a listed trash item when no empty slot remains, so kept items (tools, food, etc.) are preserved. A slot is reserved for the container pickup so ground litter can't occupy it.

## Todo
- [x] Basic mining and placing
- [x] Task shuffle for multi-player building
- [x] Restock from shulker boxes and ender chests
- [x] Grind obsidian from ender chests
- [x] Save minimum amount of materials
- [x] Choose best tool to mine block by score
- [x] Ignore a list of blocks to avoid breaking them
- [x] Highway, Tunnel, and Flat blueprint modes
- [x] Corner blocks
- [x] Intelligent placing by block side
- [x] Deep search for placing
- [x] Scaffold/bridge if block side view is not visible
- [x] Option for impossible placements

## Contributing
If you would like to contribute, create a pull request!
