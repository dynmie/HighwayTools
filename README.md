# MetroHighwayTools
<img alt="GitHub release" src="https://img.shields.io/github/v/release/dynmie/HighwayTools?logo=java&style=for-the-badge"> <img alt="GitHub last commit" src="https://img.shields.io/github/last-commit/dynmie/HighwayTools?style=for-the-badge"> <img alt="GitHub Workflow Status" src="https://img.shields.io/github/actions/workflow/status/dynmie/HighwayTools/gradle.yml?branch=master&logo=github&style=for-the-badge">


[![that's crazy](https://github.com/dynmie/HighwayTools/assets/41315732/d458e693-fc99-4006-9935-3745dead2d1e)](https://github.com/dynmie/HighwayTools)


A port of [Meteor](https://github.com/MeteorDevelopment/meteor-client)'s and [Lambda](https://github.com/lambda-plugins/HighwayTools)'s highway tools.


You can find a demonstration video [here](https://www.youtube.com/watch?v=SsU_WlwD_mo).

## About
An attempt to add a fully automated highway building robot to Meteor client.
Created because the current highway tools built into Meteor is just plain terrible.


MetroHighwayTools is confirmed to work on 6b6t—I have personally verified it.

> [!CAUTION]
> This addon is not finished! There are bugs, and they are very big. Use at your own risk.

## Setting up

### Dependencies
- [Fabric Loader](https://fabricmc.net/)
- [Meteor Client 1.20.4](https://meteorclient.com/)
- [Baritone 1.20.4](https://github.com/cabaletta/baritone)

### Installation
1. Install Fabric
2. Add Meteor Client, Baritone, and MetroHighwayTools to your `mods` folder
3. Done!

## Todo
- [x] Basic mining and placing
- [x] Task shuffle for multi-player building
- [ ] Restock from shulker boxes and ender chests
- [ ] Grind obsidian from ender chests
- [ ] Save minimum amount of materials
- [ ] Choose best tool to mine block by score
- [ ] Ignore a list of blocks to avoid breaking them
- [x] Highway, Tunnel, and Flat blueprint modes
- [ ] Corner blocks
- [ ] Intelligent placing by block side
- [ ] Deep search for placing
- [ ] Scaffold/bridge if block side view is not visible
- [ ] Option for impossible placements 

## Known issues
- Tools not in your hotbar will not be used

## Contributing
If you would like to contribute, create a pull request!
