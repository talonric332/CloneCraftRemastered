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
or **Teleport All**. Claiming a clone hides its Claim button. Clones on Game Progression show what they're doing next to their name.

**Watch** lets you see through a clone's eyes, wherever it is: you go into spectator mode with your view locked to the clone.
You can look around freely with the mouse (the clone keeps looking where it wants), its hotbar, health and food are shown at
the bottom of the screen, and **E** opens its whole inventory and armour. Your other clones keep working while you watch.
Press **Sneak** to stop, and you're put back exactly where you were, in your old game mode (also if the clone dies, or if you log
out or the server stops while watching).

**Bulk Edit** changes many clones at once:
1. Tick the clones to change in the list on the left (all are ticked to start; **All** / **None** to select quickly).
2. Choose what to change with the three buttons on the right. Under each one you can see what will change.
   - **Entities To Attack** and **Blocks To Break** open a searchable list of mobs or blocks. Click an entry to cycle it between
     **Keep**, **Add** and **Remove**, or use the quick buttons (+ Monsters, + Ores, ...). Set **Others** to **Remove** to replace
     each clone's whole list with just the entries you added.
   - **Options** opens every clone option: click one to cycle it between **Keep**, **ON** and **OFF**, and set the **Team**.
   - Anything left on Keep isn't touched on any clone. Press **Done** to go back.
3. Press **Apply**. Nothing changes until you do; **Reset** puts everything back to Keep.

Bulk Edit never changes a clone's name or its Female option, so each clone keeps its own name and skin.

## Mining & farming
Turn on **Break Blocks** and tick blocks in **Blocks To Break** (or use the Ores/Trees buttons). The clone finds the nearest one
it can see within 16 blocks, walks over, picks the right tool and digs it out at player speed.
Turn on **Farm** and it harvests fully grown crops and replants them with seeds from its inventory.

## Game Progression
Turn on **Game Progression** in a clone's Options (or for many clones in Bulk Edit → Options) and an idle clone works its
way through the game by itself:
1. **Wood:** chops trees, makes a crafting table, a wooden pickaxe, and a bed if there are sheep about.
2. **Mining:** digs for stone, coal and iron (staircases down and branch tunnels), makes a furnace, smelts the iron and makes
   full iron armour, a sword, a pickaxe, an axe and a shovel. Armour is put on as soon as it's made.
3. **Food:** whenever it's hungry with nothing to eat, it harvests ripe crops or hunts animals, and cooks the meat.
4. **Dragon prep:** mines diamonds for a diamond pickaxe, mines obsidian it finds nearby, hunts endermen for pearls and
   blazes for rods, and crafts Eyes of Ender.

**Working as a team.** When several of your clones are on Game Progression within 64 blocks of each other, they split the work:
one keeps going for the dragon, and the others become a **Miner** (mines coal, iron and later diamonds), a **Lumberjack**
(chops trees) and a **Hunter** (keeps up to 64 food in storage, and leaves at least 3 of each kind of animal so they keep
breeding). Helpers put what they gather in Clone Storage, and the clone going for the dragon takes what it needs from there.
You can see each clone's job on the Clone Remote.

**Chests.** Clones make chests named **Clone Storage** and put their spare items in them (only there, never in your
chests). When a clone needs wood, saplings, stone, coal, iron, diamonds or food, it checks every chest and barrel within
32 blocks first: Clone Storage, a bonus chest, village chests, or your own storage. It also takes any better pickaxe, sword,
axe or armour it finds. Loot chests fill the first time a clone looks inside, like when you open them. So you can give clones
anything by putting it in a chest near them.

**Trading.** In any world, when a clone is looking for something and a villager or wandering trader within 24 blocks sells
it, the clone trades for it: a toolsmith's pickaxe, an armorer's armour, a farmer's bread, a cleric's Ender Pearls, a wandering
trader's logs or saplings... It pays with emeralds it has (or finds in a chest), and to get more it sells its spare items to
the same trader (sticks to a fletcher, coal to a toolsmith, crops to a farmer, rotten flesh to a cleric). It never sells anything
it still needs. Trades work just like yours: same prices, the trader's stock runs out, and villagers gain experience and level up.
It'll still chop a tree or dig for stone when that's easy; trading is for when it can't easily get something itself.

**Growing trees.** With no trees around, a clone plants saplings (using bone meal if it has some to make them grow) and
chops them when they've grown, keeping the saplings that fall. Dark and pale oak saplings aren't planted, since they need four.

**Superflat worlds.** There are no trees, stone or ores in a superflat world, so clones don't wander off or dig down to
bedrock looking for them. Instead they use what's in nearby chests (start the world with a bonus chest!), grow trees from
saplings, and when they're stuck they say in chat what they need, like "I need cobblestone to keep going. Put some in a chest
near me!", and wait. The Clone Remote shows what each one is waiting for.

Good to know:
- Like a player, a clone needs a placed crafting table for anything bigger than a 2x2 grid (tools, armour, the furnace, a bed,
  a chest). If there isn't one nearby it puts down the one it carries, crafts, and picks it back up. It smelts in a furnace it
  carries; smelting takes as long as a real furnace and burns coal (or spare wood).
- What a clone mines or kills is kept for that clone for 30 seconds, so other clones don't grab it. You can still pick it up.
- It doesn't run while the clone is following you, guarding or building, and fighting always comes first.
- Staying alive: every clone makes a sword before anything else (wood if there's no stone yet, upgraded later). A badly hurt
  clone (under about a third of its health) runs away from monsters instead of fighting to the death. At night clones don't go
  exploring in the open, and a clone with a bed puts it down and sleeps until morning (getting up if something hits it).
- It only goes after blocks and mobs in loaded chunks, never mines into or next to lava or water, and throws away junk
  (dirt, gravel...) when its inventory fills up.
- It needs **Fight** on to hunt. Blazes are only hunted if the clone is already in the Nether (teleport it there with the
  Clone Remote): it won't build portals, travel by itself, trade with piglins or fight the dragon.
- With **Share Items** on, clones on the same team still hand each other spare gear, food, arrows and torches.

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
