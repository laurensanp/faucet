# Faucet

A standalone Fabric port of the **faucet** block from
[Supplementaries](https://github.com/MehVahdJukaar/supplementaries) for Minecraft 26.2.

Place a faucet on the side of a block that holds liquid and open it: it pours into
whatever is below, one bottle (or one bucket) at a time.

## Features

- **Open and close** by right-clicking. A redstone signal flips the current state.
- **Sources** (the block behind the faucet): water and lava cauldrons, powder snow
  cauldrons, full beehives and bee nests (honey), water source blocks (infinite),
  other fluid source blocks, and any modded tank that uses Fabric's fluid API.
- **Targets** (the block below): cauldrons, beehives, modded tanks. Water also turns
  dirt into mud and concrete powder into concrete.
- **Containers:** an open faucet on a chest or other inventory drops its items out one by one.
- **Infinite Water Faucet:** never runs dry. Crafted from a faucet and a water bucket.
- Pours visibly, with a stream model for water, lava, honey and powder snow.

### Recipes

| Item | Recipe |
|---|---|
| Faucet | stick in the middle of the top row, 3 iron ingots in the middle row, 1 iron ingot bottom right |
| Infinite Water Faucet | faucet + water bucket (shapeless) |

## Building

Needs Java 25.

```bash
cd mod
./gradlew build
```

The mod jar ends up in `mod/build/libs/`. It needs Fabric Loader 0.19.5+ and Fabric API.
Game tests live in `mod/src/gametest/`.

## License

**Private use only.** The behaviour is a port of the Supplementaries faucet, and the
textures, models and sounds are taken from Supplementaries (Supplementaries Team
License). Do not publish or redistribute this mod. See [mod/LICENSE](mod/LICENSE).
