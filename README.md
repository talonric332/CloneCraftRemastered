# CloneCraft Remastered (Minecraft 26.3, Fabric)

A rebuild of Jamezo97's **CloneCraft** (the mod DanTDM used to clone himself) for Minecraft 26.3,
following how the original worked. Everything is in the **CloneCraft** creative tab.

## Download
Get the latest jar from the [Releases page](https://github.com/talonric332/CloneCraftRemastered/releases/latest).
Put it in your `mods` folder with [Fabric API](https://modrinth.com/mod/fabric-api). Needs Fabric Loader 0.19.5+ for Minecraft 26.3, and Java 25.

## Making a clone (the original way)
1. **Needle** (`III / I_I / _G_`: iron, gold). Use it in the air to take *your own* blood, or hit/right-click any creature for theirs.
2. **Test Tube** (glass panes or glass in a U shape). Craft the blood needle with an empty test tube → **Test Tube of Blood**.
3. **Centrifuge** (pistons, iron, diamond). Put up to 8 tubes of blood around the edge, fuel in the middle, press **Spin**
   (or give it a redstone pulse). After 80 seconds the blood is **Separated**.
4. Craft a clean needle with separated blood → **DNA Needle**. Craft that with an **Empty Egg** (from an egg) →
   **Unactivated Spawn Egg**.
5. **Life Inducer** (iron, gold, soul sand, chest) with **Antennas** (iron + gold) stacked on top. In rain or a thunderstorm,
   if the antenna is the tallest thing within 15 blocks, lightning strikes it and charges the Life Inducer, which brings
   the eggs to life. (In creative, right-click the antenna to charge it.) A charged Life Inducer zaps anything nearby unless
   it wears leather or diamond boots, and explodes if broken while charged!
6. Place or throw the spawn egg. Rename the egg in an anvil first to name the clone (the name is also whose **skin** it wears,
   e.g. `DanTDM`). Clones hatch as babies and grow up over about 20 minutes.

Used needles and tubes come back **dirty**. Clean them in a **Sterilizer** (iron, bucket, redstone): fill it with a water bucket,
light a fire (or campfire) under it, and drop the dirty items in. After 32 items the water gets dirty; empty it with a bucket.

DNA from any creature works, so you can also make zombie, cow, creeper... spawn eggs.
**Stem Cells** (clean needle + separated *human* blood + bone meal): hit a clone to make it grow faster, or into a giant for a while.

## The clone menu (right-click a clone)
- Its **inventory and armour**: give it tools, weapons, armour, food, arrows. It eats when hungry, wears the best armour,
  uses the best weapon, shoots bows, and uses the right tool for each block.
- **Transfer XP**, **Options**, **Teams**, **Entities To Attack**, **Blocks To Break**, **Things To Build**,
  **Claim Clone**, **Copy Clone** (creative), **Kill** (press three times).
- **Options**: Fight, Sprint, Follow, Pickup Items, Walk To Items, Retaliate, Guard Position, Wander, Jump (critical hits),
  Curious, Stats, Female, Break Blocks, Smart Memory (remembers chests), Farm, Share Items, Commandable, Skip Air Blocks.
  With **Share Items** on, teammates standing together hand each other spare armour (if it beats what the other wears),
  better weapons, bows and tools the other is missing, and even out food, arrows and torches.
  The name box at the top renames the clone and changes its skin.
- **Teams**: Red/Orange/Yellow/Green/Blue/LightBlue/Purple/Pink fight other colours; Good only fights Evil;
  Evil fights everyone (including you); Rampant fights everything.

## Clone Remote
Craft it with redstone, an ender pearl and iron (` R / IEI / III `). Right-click it to see every clone you own (in loaded areas of any
dimension), with buttons to **Manage** a clone from any distance, change its **Mode** (Follow/Stay/Wander), **Teleport** it to you,
or **Teleport All**. Claiming a clone hides its Claim button.

**Bulk Edit** changes many clones at once:
- Tick the clones to change in the list on the left (all are ticked to start; **All** / **None** to select quickly).
- Click an option to cycle it between **Keep**, **ON** and **OFF**. Options left on Keep aren't touched on any clone.
- **Team** sets every ticked clone's team, and **Lists** copies one clone's Entities To Attack and Blocks To Break lists to the rest.
- Press **Apply**. Hover a button to read what it does; **Reset** puts everything back to Keep.

Bulk Edit never changes a clone's name or its Female option, so each clone keeps its own name and skin.

## Mining & farming
Turn on **Break Blocks** and tick blocks in **Blocks To Break** (or use the Ores/Trees buttons). The clone finds the nearest one
it can see within 16 blocks, walks over, picks the right tool and digs it out at player speed.
Turn on **Farm** and it harvests fully grown crops and replants them with seeds from its inventory.

## Building
Use the **Schematic Selector** staff: right-click a block (corner 1), sneak + right-click a block (corner 2), then right-click
the air to name and save the area. In the clone's **Things To Build**, pick the schematic, move/rotate it (press **Show** to
see an outline), then **Build It!**. The clone uses blocks from its inventory (with Smart Memory it fetches more from chests it
knows about) and digs out anything in the way. Creative players can turn on Creative Mode building.

## Talking to clones
Start a chat message with a backslash: `\hello`, `\follow me`, `\stop following`, `\stay here`, `\guard 10 64 -20`,
`\come here`, `\kill 5 zombies`, `\stop`, `\kill yourself please`. Say a clone's name to talk to it, or "everyone".

## Extra commands (not in the original)
`/clonecraft spawn [player] [count]`, `/clonecraft battle <red> <blue> [count]`, `/clonecraft team <team>`,
`/clonecraft mode <follow|stay|wander>`, `/clonecraft killall`.

## Building the mod
`gradlew build` (Java 25). Output: `build/libs/`.
