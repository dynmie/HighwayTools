# MetroHighwayTools
<img alt="GitHub release" src="https://img.shields.io/github/v/release/dynmie/HighwayTools?logo=java&style=for-the-badge"> <img alt="GitHub last commit" src="https://img.shields.io/github/last-commit/dynmie/HighwayTools?style=for-the-badge"> <img alt="GitHub Workflow Status" src="https://img.shields.io/github/actions/workflow/status/dynmie/HighwayTools/gradle.yml?branch=master&logo=github&style=for-the-badge">


[![that's crazy](https://github.com/dynmie/HighwayTools/assets/41315732/d458e693-fc99-4006-9935-3745dead2d1e)](https://github.com/dynmie/HighwayTools)


A port of [Meteor](https://github.com/MeteorDevelopment/meteor-client)'s and [Lambda](https://github.com/lambda-plugins/HighwayTools)'s highway tools.


You can find an (old) demonstration video [here](https://www.youtube.com/watch?v=SsU_WlwD_mo).

## About
An attempt to add a fully automated highway building robot to Meteor client.
Created because the current highway tools built into Meteor is just plain terrible.

> [!WARNING]
> No support will be given to those running a non-latest HighwayTools version. If you are running an old version, please update for merged bug fixes and newer features. If you need to join a server on an older Minecraft version and require an older HighwayTools version for compatibility, consider using [ViaFabric](https://modrinth.com/mod/viafabric) alongside Minecraft 26.1.X.

## Setting up

### Dependencies
- [Fabric Loader](https://fabricmc.net/)
- [Meteor Client 26.1](https://meteorclient.com/)
- [Baritone 26.1](https://github.com/cabaletta/baritone)

### Installation
1. Install Fabric (requires Java 25)
2. Add Meteor Client, Baritone, and HighwayTools to your `mods` folder
3. Done!

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
