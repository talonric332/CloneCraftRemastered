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
2. **Mining:** digs for stone, coal and iron like a player (a staircase down, then tunnels and strip mines, mining the ore it uncovers), makes a furnace, smelts the iron and makes
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

**Home.** A player's clones live together in one house that they design between them. When a clone on the surface has
plenty of spare building blocks and its owner's clones haven't got a house yet, it picks a good flat spot nearby and designs
the first room its own way: how big (5 to 9 across), how high, which way it faces, where the door goes, and its style (walls of
wood, stone, or stone below and wood above; a flat or stepped peaked roof in wood or stone; a wood or stone floor; corner posts
of logs or a contrasting material or none). Inside go a crafting table, a Clone Storage chest, a furnace, a torch and its own
bed. Every other clone that moves in adds its own bed wherever it likes, and if
there's no room left it adds a room onto the side or back of the house (knocking through the wall between). Some add a window
or a torch of their own too. They build in the house's style when they have the materials, and otherwise with whatever they've
got (glass for windows if they have it), lay a proper floor inside (planks, or stone: the grass under the house is dug up), make the doors, chests, beds (from
wool), furnaces and torches it needs, and take blocks from the chest if they run out. If there are none there either, a clone
goes and mines a good load of stone for it, then comes back up and carries on. Clones down a mine come up to work on the house
when it needs building and they're carrying plenty of stone (or, with no house yet, enough to start one). Nobody's mine ever goes
through a house or the ground under it: a clone setting off to dig from the house walks away from it first. Any hole in the house
gets fixed, however it got there. Only owned clones have a home.

In the evening, each clone up on the surface decides for itself whether to go home: always if it has things to put away
(it puts them in the chest), otherwise it might stay out. At home it sleeps in its bed (or any free one), or waits inside, and
gets up in the morning, or if something hits it. Clones mining underground carry on with what they're doing.

Clones on Game Progression fight hostile monsters that come close or come near the house (creepers included), even if they
aren't on their "Entities To Attack" list. If a creeper still blows a hole in the house, the clones rebuild it stronger: planks
are replaced with stone, a spot that's blown up again gets an extra outer layer of wall, and after that obsidian if they have
any. They put torches up outside on the side it came from (creepers spawn in the dark), and it counts against the fighting moves of the clones that were near, so their
fighting brain learns to deal with creepers before they get to the house. Anything you change about the house yourself (a
wall you break, a block you swap) is left as you made it. Any creeper crater near the house (whether or not the house was
hit) is filled in, so nobody falls in and gets stuck.

**Talking.** Clones talk to each other in chat, so you can follow along: planning the house ("I'm adding a room at the back
for my bed." "Good idea, I'll help knock through."), asking each other for things ("Has anyone got cobblestone?" "I've got
some, I'll bring them over."), calling for help when they're hurt (teammates nearby come and fight), sharing finds ("I found
diamonds!") and saying good night and good morning. Each clone only says each kind of thing every so often.

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
  clone (under about a third of its health) runs away from monsters instead of fighting to the death, and fights back if it's
  cornered.
- Digging down, a clone always digs a staircase it can walk back up (never a shaft straight down), and never digs away a
  step it has stood on. The staircase it digs to go mining is shared: other clones (and it, next time) walk down it instead
  of digging their own, then tunnel off in different directions from the bottom; if one needs to go deeper it extends the
  staircase for everyone. Clones also use one to climb back up. No clone ever digs away a step of a shared staircase, and
  clones walk through each other rather than pushing, so they can pass on the stairs.
- Caves: a clone that ends up underground (after mining, or after falling into a cave, pit or ravine) and has work to do up top
  (trees, animals, crops, villagers) gets back out: it walks out if there's a way, and otherwise digs a staircase up to the
  surface, turning away from lava and water. It only digs through natural ground (stone, dirt, sand...), never through
  anything built, and a clone inside a building or under a tree never thinks it's in a cave. When it's looking around on the surface it sticks to spots under open sky
  instead of wandering into cave entrances (wandering clones do the same).
- It only goes after blocks and mobs in loaded chunks, never mines into or next to lava or water, and throws away junk
  (dirt, gravel...) when its inventory fills up.
- It needs **Fight** on to hunt. Blazes are only hunted if the clone is already in the Nether (teleport it there with the
  Clone Remote): it won't build portals, travel by itself, trade with piglins or fight the dragon.
- With **Share Items** on, clones on the same team still hand each other spare gear, food, arrows and torches.

## Clones learn to fight
Every clone has a small **neural network** that picks how to fight, several times a second: press in and hit, jump for
critical hits, hit and back off before the enemy can swing back, circle round it, shoot from range with a bow, or keep clear
(of a creeper about to blow, say). It sees its own and the enemy's health, how far away it is, whether its weapon has
recharged, what kind of enemy it is, how many monsters are about, and its own gear.

It learns by **reinforcement learning**: damage it takes and dying count against a move, kills (the sooner the better) and
damage dealt in its favour, and over many fights it does more of what works against each kind of enemy. A new brain tries
out moves a lot, and less as it gets more experienced. Against zombies (husks) in testing, a new brain mostly stopped walking
straight in within about 20 fights and switched to circling and hitting and backing off: about 4 damage taken per fight,
against about 8 for the old way of fighting.

Clones also reach as far as a player (3 blocks, further than a zombie), and a hit while sprinting knocks the enemy back
further, as for players.

**Mining** is learned the same way. Clones mine like players: they only go after ore they can actually see (a face open to
the air, in a tunnel wall, a cave or a ravine, within 10 blocks and with nothing in the way), never ore buried in the rock. To
find more, a clone mining for iron or diamonds digs in stretches, and a second neural network picks how each one goes: tunnel
straight on, strip mine (a main tunnel with side tunnels off both sides every 3 blocks, which shows the most rock), turn off to
the side, go 8 blocks deeper, go 8 higher, or head for a cave nearby (walking in if it can). It sees its depth compared with
the usual best depth, what it's after, its pickaxe, whether there's a cave close by or it's in one, whether there's lava about,
and how its last few stretches went, and it learns from how much ore each stretch actually turned up per minute (diamonds count
most when it's after diamonds). While it's new it tries every way a few times, so each gets a fair chance before it settles on
what works.

All the clones you own share one brain for each, so they learn from each other's fights and mining, and it's saved with the world, so it isn't lost
when a clone dies. `/clonecraft skill` shows how your clones are doing (fights, wins, damage dealt and taken lately, which
moves they use), and `/clonecraft skill reset` makes them forget everything.

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
`/clonecraft mode <follow|stay|wander>`, `/clonecraft killall`, `/clonecraft skill [reset]` (any player).

## Building the mod
`gradlew build` (Java 25). Output: `build/libs/`.
