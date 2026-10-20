package com.clonecraft.clone.progression;

import com.clonecraft.CloneCraft;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Each player's clones' home: one house they all share, designed between them. The first clone to build picks the
 * main room (its size, how high the walls are, where the door goes) and puts in a crafting table, a Clone Storage
 * chest, a furnace, a torch and its own bed. Each clone that moves in after adds its own bed wherever it likes, and
 * when there's no room left it adds a room onto the side or back for it (the wall between is knocked through). Saved
 * with the world, on the overworld.
 *
 * <p>The house is laid out on a grid relative to its {@link Home#origin} (the front room's back left corner, at floor
 * level), facing out of the front door ({@link Home#facing}): x across (right is positive, looking out of the door),
 * z towards the front. Server thread only.
 */
public final class HomeBase {
	/** What goes at each spot of the house, in the order it's built. */
	public enum Part {
		/** Solid ground under the floor (holes filled in). */
		FLOOR,
		/** A proper floor in the rooms (planks, or stone): grass or dirt there is dug up and replaced. */
		FLOORING,
		/** Room inside: natural blocks, and the walls between rooms, are dug out. */
		CLEAR,
		WALL,
		/** The corners of the walls: posts of a different material (logs, if the clones have some to spare). */
		PILLAR,
		/** Glass if the clones have some, otherwise wall. */
		WINDOW,
		DOOR,
		ROOF,
		/** An extra outer layer of wall, added where creepers have blown holes before. */
		REINFORCE,
		TABLE,
		CHEST,
		FURNACE,
		BED,
		TORCH,
		/** Torches outside, added on sides creepers have come from (they don't spawn in the light). */
		OUTER_TORCH
	}

	/**
	 * How a house should look, chosen by the clone that starts it: walls of wood, stone, or stone at the bottom and
	 * wood above; a flat roof or a stepped, peaked one, in wood or stone; a wood or stone floor; and whether the
	 * corners get posts of a different material. They're what the clones go for when they have it: with nothing
	 * else, they'll build with what they've got.
	 */
	public record Style(int walls, int roof, boolean peaked, int floor, boolean pillars) {
		public static final int WOOD = 0;
		public static final int STONE = 1;
		public static final int MIXED = 2;
		public static final Style PLAIN = new Style(STONE, STONE, false, WOOD, false);

		public static final Codec<Style> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("walls").forGetter(Style::walls), Codec.INT.fieldOf("roof").forGetter(Style::roof),
			Codec.BOOL.fieldOf("peaked").forGetter(Style::peaked), Codec.INT.fieldOf("floor").forGetter(Style::floor),
			Codec.BOOL.fieldOf("pillars").forGetter(Style::pillars)
		).apply(i, Style::new));

		public static Style random(final RandomSource random) {
			return new Style(random.nextInt(3), random.nextInt(2), random.nextBoolean(), random.nextInt(2), random.nextBoolean());
		}

		public String describe() {
			String w = this.walls == WOOD ? "wooden" : this.walls == STONE ? "stone" : "stone and wood";
			return "a " + w + " house with a " + (this.peaked ? "peaked " : "flat ") + (this.roof == WOOD ? "wooden" : "stone") + " roof";
		}
	}

	/** One spot of the house: where, what, which way it faces, and for doors, which half. */
	public record Spot(BlockPos pos, Part part, Direction facing, boolean second) {}

	/** A rectangle of the house (in house grid cells): the outer edge is wall, the rest is room. */
	public record Room(int x, int z, int width, int depth) {
		public static final Codec<Room> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("x").forGetter(Room::x), Codec.INT.fieldOf("z").forGetter(Room::z),
			Codec.INT.fieldOf("width").forGetter(Room::width), Codec.INT.fieldOf("depth").forGetter(Room::depth)
		).apply(i, Room::new));

		boolean contains(final int cx, final int cz) {
			return cx >= this.x && cx < this.x + this.width && cz >= this.z && cz < this.z + this.depth;
		}
	}

	/**
	 * Something a clone put in the house: furniture, a window or a torch, at a house grid spot (y up from the
	 * floor), facing a house direction (0 = out of the front door, then clockwise), and who added it.
	 */
	public record Thing(int x, int y, int z, Part part, int facing, String by) {
		public static final Codec<Thing> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("x").forGetter(Thing::x), Codec.INT.fieldOf("y").forGetter(Thing::y), Codec.INT.fieldOf("z").forGetter(Thing::z),
			Codec.STRING.fieldOf("part").forGetter(t -> t.part().name()), Codec.INT.fieldOf("facing").forGetter(Thing::facing),
			Codec.STRING.optionalFieldOf("by", "").forGetter(Thing::by)
		).apply(i, (x, y, z, part, facing, by) -> new Thing(x, y, z, Part.valueOf(part), facing, by)));
	}

	/** A house. Changed in place: call {@link #changed} after. */
	public static final class Home {
		public final BlockPos origin;
		public final Direction facing;
		public final int height;
		/** How the clone that started it wanted it to look. */
		public Style style = Style.PLAIN;
		public final List<Room> rooms = new ArrayList<>();
		/** The front door, in the front room's front wall (house grid x). */
		public final int doorX;
		public final List<Thing> things = new ArrayList<>();
		/** Spots the clones have built (if one of these goes missing without an explosion, a player took it: leave it). */
		public final Set<BlockPos> placed = new LinkedHashSet<>();
		/** Spots to leave alone (a player changed them). */
		public final Set<BlockPos> skip = new HashSet<>();
		/** How strong each rebuilt spot has to be: 1 = stone, 2 = stone plus an outer layer, 3 = obsidian if there is any. */
		public final Map<BlockPos, Integer> strength = new HashMap<>();
		public final List<BlockPos> reinforce = new ArrayList<>();
		public final List<BlockPos> outerTorches = new ArrayList<>();
		/** Holes in the ground around the house (blast craters), to fill in so no one gets stuck in them. */
		public final List<BlockPos> fill = new ArrayList<>();
		public int blasts;
		public int repaired;
		public boolean built;

		private @Nullable List<Spot> planCache;

		public static final Codec<Home> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("origin").forGetter(h -> h.origin),
			Direction.CODEC.fieldOf("facing").forGetter(h -> h.facing),
			Codec.INT.fieldOf("height").forGetter(h -> h.height),
			Room.CODEC.listOf().fieldOf("rooms").forGetter(h -> List.copyOf(h.rooms)),
			Codec.INT.fieldOf("door").forGetter(h -> h.doorX),
			Thing.CODEC.listOf().optionalFieldOf("things", List.of()).forGetter(h -> List.copyOf(h.things)),
			BlockPos.CODEC.listOf().optionalFieldOf("placed", List.of()).forGetter(h -> List.copyOf(h.placed)),
			// (Saved under a new name: before, holes anything made, clones mining included, went on this list.)
			BlockPos.CODEC.listOf().optionalFieldOf("player_changes", List.of()).forGetter(h -> List.copyOf(h.skip)),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("strength", Map.of()).forGetter(h -> {
				Map<String, Integer> map = new HashMap<>();
				h.strength.forEach((pos, s) -> map.put(pos.getX() + "," + pos.getY() + "," + pos.getZ(), s));
				return map;
			}),
			BlockPos.CODEC.listOf().optionalFieldOf("reinforce", List.of()).forGetter(h -> List.copyOf(h.reinforce)),
			BlockPos.CODEC.listOf().optionalFieldOf("outer_torches", List.of()).forGetter(h -> List.copyOf(h.outerTorches)),
			BlockPos.CODEC.listOf().optionalFieldOf("fill", List.of()).forGetter(h -> List.copyOf(h.fill)),
			Codec.INT.optionalFieldOf("blasts", 0).forGetter(h -> h.blasts),
			Codec.INT.optionalFieldOf("repaired", 0).forGetter(h -> h.repaired),
			Codec.BOOL.optionalFieldOf("built", false).forGetter(h -> h.built),
			Style.CODEC.optionalFieldOf("style", Style.PLAIN).forGetter(h -> h.style)
		).apply(i, Home::fromSaved));

		public Home(final BlockPos origin, final Direction facing, final int height, final Room front, final int doorX) {
			this.origin = origin.immutable();
			this.facing = facing.getAxis().isHorizontal() ? facing : Direction.SOUTH;
			this.height = Math.max(3, Math.min(5, height));
			this.rooms.add(front);
			this.doorX = doorX;
		}

		private static Home fromSaved(final BlockPos origin, final Direction facing, final int height, final List<Room> rooms, final int door,
			final List<Thing> things, final List<BlockPos> placed, final List<BlockPos> skip, final Map<String, Integer> strength,
			final List<BlockPos> reinforce, final List<BlockPos> outerTorches, final List<BlockPos> fill, final int blasts, final int repaired,
			final boolean built, final Style style) {
			Home home = new Home(origin, facing, height, rooms.isEmpty() ? new Room(0, 0, 5, 5) : rooms.get(0), door);
			for (int k = 1; k < rooms.size(); k++) {
				home.rooms.add(rooms.get(k));
			}
			home.things.addAll(things);
			home.placed.addAll(placed);
			home.skip.addAll(skip);
			strength.forEach((key, s) -> {
				String[] parts = key.split(",");
				if (parts.length == 3) {
					try {
						home.strength.put(new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])), s);
					} catch (NumberFormatException ignored) {
					}
				}
			});
			home.reinforce.addAll(reinforce);
			home.outerTorches.addAll(outerTorches);
			home.fill.addAll(fill);
			home.blasts = blasts;
			home.repaired = repaired;
			home.built = built;
			home.style = style;
			return home;
		}

		/** The house's design changed (a room or a thing added): work out the spots again. */
		public void redesigned() {
			this.planCache = null;
		}

		/** A house grid spot in the world. */
		public BlockPos at(final int x, final int y, final int z) {
			return this.origin.relative(this.facing.getClockWise(), x).relative(this.facing, z).above(y);
		}

		/** A house direction (0 = out of the front door, then clockwise) in the world. */
		public Direction dir(final int facing) {
			Direction d = this.facing;
			for (int k = 0; k < Math.floorMod(facing, 4); k++) {
				d = d.getClockWise();
			}
			return d;
		}

		public boolean inFootprint(final int x, final int z) {
			for (Room room : this.rooms) {
				if (room.contains(x, z)) {
					return true;
				}
			}
			return false;
		}

		/** An outside wall: in the house, next to (or diagonally next to) somewhere that isn't. */
		public boolean isWall(final int x, final int z) {
			if (!this.inFootprint(x, z)) {
				return false;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (!this.inFootprint(x + dx, z + dz)) {
						return true;
					}
				}
			}
			return false;
		}

		/** How far in from the outside this spot is: 1 for the walls, 2 for just inside them, and so on. */
		public int stepsIn(final int x, final int z) {
			for (int d = 1; d < 32; d++) {
				for (int dx = -d; dx <= d; dx++) {
					for (int dz = -d; dz <= d; dz++) {
						if ((Math.abs(dx) == d || Math.abs(dz) == d) && !this.inFootprint(x + dx, z + dz)) {
							return d;
						}
					}
				}
			}
			return 32;
		}

		/** A corner of the walls (outside corners, and the inside ones where rooms meet). */
		public boolean isCorner(final int x, final int z) {
			if (!this.isWall(x, z)) {
				return false;
			}
			boolean outX = !this.inFootprint(x - 1, z) || !this.inFootprint(x + 1, z);
			boolean outZ = !this.inFootprint(x, z - 1) || !this.inFootprint(x, z + 1);
			return outX && outZ || !outX && !outZ;
		}

		/** A spot's height above the floor. */
		public int heightOf(final BlockPos pos) {
			return pos.getY() - this.origin.getY();
		}

		public boolean isRoom(final int x, final int z) {
			return this.inFootprint(x, z) && !this.isWall(x, z);
		}

		/** The front door's spot (house grid), in the front wall of the first room. */
		public int doorZ() {
			Room front = this.rooms.get(0);
			return front.z() + front.depth() - 1;
		}

		/** The middle of the floor inside the first room. */
		public BlockPos inside() {
			Room front = this.rooms.get(0);
			return this.at(front.x() + front.width() / 2, 0, front.z() + front.depth() / 2);
		}

		public AABB bounds() {
			AABB box = null;
			for (Room room : this.rooms) {
				AABB r = new AABB(Vec3.atLowerCornerOf(this.at(room.x(), -1, room.z())), Vec3.atLowerCornerOf(this.at(room.x(), -1, room.z())).add(1, 1, 1))
					.minmax(new AABB(this.at(room.x() + room.width() - 1, this.height + (this.style.peaked() ? 4 : 0), room.z() + room.depth() - 1)));
				box = box == null ? r : box.minmax(r);
			}
			return box == null ? new AABB(this.origin) : box;
		}

		/** Inside the house's walls (somewhere a clone would sleep or store things). */
		public boolean isInside(final BlockPos pos) {
			if (pos.getY() < this.origin.getY() || pos.getY() >= this.origin.getY() + this.height) {
				return false;
			}
			for (int x = this.minX(); x <= this.maxX(); x++) {
				for (int z = this.minZ(); z <= this.maxZ(); z++) {
					if (this.isRoom(x, z) && this.at(x, 0, z).getX() == pos.getX() && this.at(x, 0, z).getZ() == pos.getZ()) {
						return true;
					}
				}
			}
			return false;
		}

		int minX() {
			return this.rooms.stream().mapToInt(Room::x).min().orElse(0);
		}

		int maxX() {
			return this.rooms.stream().mapToInt(r -> r.x() + r.width() - 1).max().orElse(0);
		}

		int minZ() {
			return this.rooms.stream().mapToInt(Room::z).min().orElse(0);
		}

		int maxZ() {
			return this.rooms.stream().mapToInt(r -> r.z() + r.depth() - 1).max().orElse(0);
		}

		private boolean isDoor(final int x, final int y, final int z) {
			return x == this.doorX && z == this.doorZ() && y < 2;
		}

		private @Nullable Thing thingAt(final int x, final int y, final int z) {
			for (Thing t : this.things) {
				if (t.x() == x && t.y() == y && t.z() == z) {
					return t;
				}
				// A bed takes two spots: the foot, and the head beyond it.
				if (t.part() == Part.BED && t.y() == y) {
					int[] s = this.step(t.facing());
					if (t.x() + s[0] == x && t.z() + s[1] == z) {
						return t;
					}
				}
			}
			return null;
		}

		/** Every spot of the house, in the order to build them. */
		public List<Spot> plan() {
			if (this.planCache != null) {
				return this.planCache;
			}
			List<Spot> spots = new ArrayList<>();
			int x0 = this.minX();
			int x1 = this.maxX();
			int z0 = this.minZ();
			int z1 = this.maxZ();
			// Solid ground under the walls (the rooms get a proper floor later on).
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					if (this.isWall(x, z)) {
						spots.add(new Spot(this.at(x, -1, z), Part.FLOOR, this.facing, false));
					}
				}
			}
			// Craters around the house, filled from the bottom up.
			this.fill.stream().sorted(java.util.Comparator.comparingInt(BlockPos::getY))
				.forEach(pos -> spots.add(new Spot(pos, Part.FLOOR, this.facing, false)));
			for (int y = 0; y < this.height; y++) {
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						// (Including where furniture goes: done counts furniture there as clear.)
						if (this.isRoom(x, z)) {
							spots.add(new Spot(this.at(x, y, z), Part.CLEAR, this.facing, false));
						}
					}
				}
			}
			spots.add(new Spot(this.at(this.doorX, 0, this.doorZ()), Part.CLEAR, this.facing, false));
			spots.add(new Spot(this.at(this.doorX, 1, this.doorZ()), Part.CLEAR, this.facing, false));
			for (int y = 0; y < this.height; y++) {
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						if (this.isWall(x, z) && !this.isDoor(x, y, z)) {
							Thing thing = this.thingAt(x, y, z);
							Part part = thing != null && thing.part() == Part.WINDOW ? Part.WINDOW
								: this.style.pillars() && this.isCorner(x, z) ? Part.PILLAR : Part.WALL;
							spots.add(new Spot(this.at(x, y, z), part, this.facing, false));
						}
					}
				}
			}
			spots.add(new Spot(this.at(this.doorX, 0, this.doorZ()), Part.DOOR, this.facing, false));
			spots.add(new Spot(this.at(this.doorX, 1, this.doorZ()), Part.DOOR, this.facing, true));
			// The roof: flat across the top of the walls, and for a peaked one, a step in at a time up towards the
			// ridge. (No higher than a clone standing inside can reach: past that, the top is flat.)
			int layers = 1;
			if (this.style.peaked()) {
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						if (this.inFootprint(x, z)) {
							layers = Math.max(layers, this.stepsIn(x, z));
						}
					}
				}
				layers = Math.min(layers, Math.max(1, 6 - this.height));
			}
			for (int layer = 0; layer < layers; layer++) {
				boolean top = layer == layers - 1;
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						int in = this.inFootprint(x, z) ? this.stepsIn(x, z) : 0;
						if (in > 0 && (layer == 0 || in == layer + 1 || top && in > layer)) {
							spots.add(new Spot(this.at(x, this.height + layer, z), Part.ROOF, this.facing, false));
						}
					}
				}
			}
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					if (this.isRoom(x, z) || x == this.doorX && z == this.doorZ()) {
						spots.add(new Spot(this.at(x, -1, z), Part.FLOORING, this.facing, false));
					}
				}
			}
			for (BlockPos pos : this.reinforce) {
				spots.add(new Spot(pos, Part.REINFORCE, this.facing, false));
			}
			for (Part part : List.of(Part.TABLE, Part.CHEST, Part.FURNACE, Part.BED, Part.TORCH)) {
				for (Thing thing : this.things) {
					if (thing.part() == part) {
						spots.add(new Spot(this.at(thing.x(), thing.y(), thing.z()), part, this.dir(thing.facing()), false));
					}
				}
			}
			for (BlockPos pos : this.outerTorches) {
				spots.add(new Spot(pos, Part.OUTER_TORCH, this.facing, false));
			}
			this.planCache = spots;
			return spots;
		}

		public int strengthAt(final BlockPos pos) {
			return this.strength.getOrDefault(pos, 0);
		}

		/** The Clone Storage chest's spot. */
		public @Nullable BlockPos chest() {
			for (Thing t : this.things) {
				if (t.part() == Part.CHEST) {
					return this.at(t.x(), t.y(), t.z());
				}
			}
			return null;
		}

		/** The beds (where their heads are, which is where a clone lies down), and whose each is. */
		public Map<BlockPos, String> beds() {
			Map<BlockPos, String> beds = new HashMap<>();
			for (Thing t : this.things) {
				if (t.part() == Part.BED) {
					beds.put(this.at(t.x(), t.y(), t.z()).relative(this.dir(t.facing())), t.by());
				}
			}
			return beds;
		}

		/** Whose bed goes at this spot (its foot), or "" if it isn't a bed spot. */
		public String bedOwner(final BlockPos foot) {
			for (Thing t : this.things) {
				if (t.part() == Part.BED && this.at(t.x(), t.y(), t.z()).equals(foot)) {
					return t.by();
				}
			}
			return "";
		}

		public boolean hasBedFor(final String name) {
			return this.things.stream().anyMatch(t -> t.part() == Part.BED && t.by().equals(name));
		}

		/** Which way is out from the outside wall this spot is in (null for spots not in an outside wall). */
		public @Nullable Direction outward(final BlockPos pos) {
			for (int x = this.minX(); x <= this.maxX(); x++) {
				for (int z = this.minZ(); z <= this.maxZ(); z++) {
					if (!this.isWall(x, z)) {
						continue;
					}
					BlockPos base = this.at(x, 0, z);
					if (base.getX() != pos.getX() || base.getZ() != pos.getZ()) {
						continue;
					}
					for (int k = 0; k < 4; k++) {
						int[] s = this.step(k);
						if (!this.inFootprint(x + s[0], z + s[1])) {
							return this.dir(k);
						}
					}
				}
			}
			return null;
		}

		// ------------------------------------------------------------ designing together

		/** A house grid step in a house direction (0 = out of the front door, +z; 1 = right, +x; and so on clockwise). */
		private int[] step(final int facing) {
			return switch (Math.floorMod(facing, 4)) {
				case 0 -> new int[]{0, 1};
				case 1 -> new int[]{1, 0};
				case 2 -> new int[]{0, -1};
				default -> new int[]{-1, 0};
			};
		}

		/** Room spots not taken by anything and not in front of the door: where furniture could go. */
		private boolean free(final int x, final int z) {
			return this.isRoom(x, z) && this.thingAt(x, 0, z) == null && !(x == this.doorX && z == this.doorZ() - 1);
		}

		/**
		 * Furniture can go here: everywhere inside can still be walked to, and the left, right or back wall can still be
		 * got at from inside (so a room can always be added on later).
		 */
		private boolean stillWorks() {
			if (!this.allReachable()) {
				return false;
			}
			int x0 = this.minX();
			int x1 = this.maxX();
			int z0 = this.minZ();
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= this.maxZ(); z++) {
					if (!this.isRoom(x, z) || this.thingAt(x, 0, z) != null || x == this.doorX && z == this.doorZ() - 1) {
						continue;
					}
					if (x == x0 + 1 || x == x1 - 1 || z == z0 + 1) {
						return true;
					}
				}
			}
			return false;
		}

		/** Every free room spot can still be walked to from inside the front door (furniture mustn't shut any off). */
		private boolean allReachable() {
			int sx = this.doorX;
			int sz = this.doorZ() - 1;
			Set<Long> seen = new HashSet<>();
			ArrayDeque<int[]> queue = new ArrayDeque<>();
			queue.add(new int[]{sx, sz});
			seen.add(((long)sx << 32) ^ (sz & 0xffffffffL));
			while (!queue.isEmpty()) {
				int[] c = queue.poll();
				for (int k = 0; k < 4; k++) {
					int[] s = this.step(k);
					int nx = c[0] + s[0];
					int nz = c[1] + s[1];
					long key = ((long)nx << 32) ^ (nz & 0xffffffffL);
					if (!seen.contains(key) && this.isRoom(nx, nz) && this.thingAt(nx, 0, nz) == null) {
						seen.add(key);
						queue.add(new int[]{nx, nz});
					}
				}
			}
			for (int x = this.minX(); x <= this.maxX(); x++) {
				for (int z = this.minZ(); z <= this.maxZ(); z++) {
					if (this.isRoom(x, z) && this.thingAt(x, 0, z) == null && !seen.contains(((long)x << 32) ^ (z & 0xffffffffL))) {
						return false;
					}
				}
			}
			return true;
		}

		/** Puts a thing in a free spot against a wall, wherever the clone fancies, without shutting anywhere off. */
		public boolean addAgainstWall(final Part part, final String by, final RandomSource random) {
			List<int[]> options = new ArrayList<>();
			for (int x = this.minX(); x <= this.maxX(); x++) {
				for (int z = this.minZ(); z <= this.maxZ(); z++) {
					if (!this.free(x, z)) {
						continue;
					}
					for (int k = 0; k < 4; k++) {
						int[] s = this.step(k);
						if (this.isWall(x + s[0], z + s[1])) {
							// Faces away from the wall it's against.
							options.add(new int[]{x, z, k + 2});
						}
					}
				}
			}
			java.util.Collections.shuffle(options, new java.util.Random(random.nextLong()));
			for (int[] o : options) {
				Thing thing = new Thing(o[0], 0, o[1], part, Math.floorMod(o[2], 4), by);
				this.things.add(thing);
				if (this.stillWorks()) {
					this.redesigned();
					return true;
				}
				this.things.remove(thing);
			}
			return false;
		}

		/** Puts a clone's bed in: its head against a wall, wherever there's room for it. */
		public boolean addBed(final String by, final RandomSource random) {
			List<int[]> options = new ArrayList<>();
			for (int x = this.minX(); x <= this.maxX(); x++) {
				for (int z = this.minZ(); z <= this.maxZ(); z++) {
					for (int k = 0; k < 4; k++) {
						int[] s = this.step(k);
						// Foot here, head one step on (direction k), and a wall beyond the head.
						if (this.free(x, z) && this.free(x + s[0], z + s[1]) && this.isWall(x + 2 * s[0], z + 2 * s[1])) {
							options.add(new int[]{x, z, k});
						}
					}
				}
			}
			java.util.Collections.shuffle(options, new java.util.Random(random.nextLong()));
			for (int[] o : options) {
				Thing bed = new Thing(o[0], 0, o[1], Part.BED, o[2], by);
				this.things.add(bed);
				if (this.stillWorks()) {
					this.redesigned();
					return true;
				}
				this.things.remove(bed);
			}
			return false;
		}

		/** A torch on a wall inside, about head height. */
		public boolean addTorch(final String by, final RandomSource random) {
			List<int[]> options = new ArrayList<>();
			for (int x = this.minX(); x <= this.maxX(); x++) {
				for (int z = this.minZ(); z <= this.maxZ(); z++) {
					if (!this.isRoom(x, z) || this.thingAt(x, 1, z) != null || this.thingAt(x, 0, z) != null) {
						continue;
					}
					for (int k = 0; k < 4; k++) {
						int[] s = this.step(k);
						if (this.isWall(x + s[0], z + s[1])) {
							options.add(new int[]{x, z, k + 2});
						}
					}
				}
			}
			if (options.isEmpty()) {
				return false;
			}
			int[] o = options.get(random.nextInt(options.size()));
			this.things.add(new Thing(o[0], 1, o[1], Part.TORCH, Math.floorMod(o[2], 4), by));
			this.redesigned();
			return true;
		}

		/** A window in an outside wall, at eye height (not a corner, not next to the door). */
		public boolean addWindow(final String by, final RandomSource random) {
			List<int[]> options = new ArrayList<>();
			for (int x = this.minX(); x <= this.maxX(); x++) {
				for (int z = this.minZ(); z <= this.maxZ(); z++) {
					if (!this.isWall(x, z) || this.thingAt(x, 1, z) != null || Math.abs(x - this.doorX) <= 1 && z == this.doorZ()) {
						continue;
					}
					// A straight bit of wall: room on one side.
					boolean straight = false;
					for (int k = 0; k < 4; k++) {
						int[] s = this.step(k);
						if (this.isRoom(x + s[0], z + s[1]) && !this.inFootprint(x - s[0], z - s[1])) {
							straight = true;
						}
					}
					if (straight) {
						options.add(new int[]{x, z});
					}
				}
			}
			if (options.isEmpty()) {
				return false;
			}
			int[] o = options.get(random.nextInt(options.size()));
			this.things.add(new Thing(o[0], 1, o[1], Part.WINDOW, 0, by));
			this.redesigned();
			return true;
		}

		/**
		 * Adds a room onto the side or back of the house (sharing a wall, which is knocked through), if there's good
		 * ground for it. Returns which side, or null.
		 */
		public @Nullable String addRoom(final RandomSource random, final Predicate<BlockPos> goodGround) {
			List<Room> options = new ArrayList<>();
			int x0 = this.minX();
			int x1 = this.maxX();
			int z0 = this.minZ();
			int z1 = this.maxZ();
			int width = 4 + random.nextInt(3);
			int depth = 4 + random.nextInt(2);
			// Left, right (sharing the side wall) and back (sharing the back wall), lined up with the front room.
			Room front = this.rooms.get(0);
			options.add(new Room(x0 - width + 1, front.z(), width, Math.max(depth, 5)));
			options.add(new Room(x1, front.z(), width, Math.max(depth, 5)));
			options.add(new Room(front.x(), z0 - depth + 1, Math.max(width, 5), depth));
			java.util.Collections.shuffle(options, new java.util.Random(random.nextLong()));
			for (Room room : options) {
				boolean fits = true;
				for (int x = room.x(); x < room.x() + room.width() && fits; x++) {
					for (int z = room.z(); z < room.z() + room.depth() && fits; z++) {
						if (!this.inFootprint(x, z) && !goodGround.test(this.at(x, 0, z))) {
							fits = false;
						}
					}
				}
				if (fits) {
					this.rooms.add(room);
					// It has to open onto somewhere you can walk (not behind a row of beds, say).
					if (!this.allReachable()) {
						this.rooms.remove(this.rooms.size() - 1);
						continue;
					}
					this.redesigned();
					return room.z() < z0 ? "at the back" : room.x() < x0 ? "on the left" : "on the right";
				}
			}
			return null;
		}
	}

	/** Whether the spot is done (or, for spots a player changed, counts as done). */
	public static boolean done(final ServerLevel level, final Home home, final Spot spot) {
		if (home.skip.contains(spot.pos())) {
			return true;
		}
		BlockPos pos = spot.pos();
		BlockState state = level.getBlockState(pos);
		return switch (spot.part()) {
			case FLOOR -> !state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
			// A floor someone made (any full block that isn't the ground it was built on).
			case FLOORING -> state.isCollisionShapeFullBlock(level, pos) && !state.is(net.minecraft.tags.BlockTags.DIRT)
				&& !state.is(net.minecraft.tags.BlockTags.TURNS_INTO_DIRT_PATH) && !state.is(net.minecraft.tags.BlockTags.SAND)
				&& !state.is(Blocks.GRAVEL);
			case CLEAR -> state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty()
				|| state.getBlock() instanceof BedBlock || state.is(Blocks.CHEST) || state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.FURNACE)
				|| state.getBlock() instanceof DoorBlock;
			case WALL, PILLAR, ROOF, REINFORCE, WINDOW -> state.isCollisionShapeFullBlock(level, pos) || state.getBlock() instanceof DoorBlock
				|| state.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags.GLASS_BLOCKS)
				|| state.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags.GLASS_PANES);
			case DOOR -> state.getBlock() instanceof DoorBlock;
			case BED -> state.getBlock() instanceof BedBlock;
			case CHEST -> state.is(Blocks.CHEST) || state.is(Blocks.BARREL);
			case TABLE -> state.is(Blocks.CRAFTING_TABLE);
			case FURNACE -> state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER);
			case TORCH, OUTER_TORCH -> state.getBlock() instanceof TorchBlock || state.is(Blocks.WALL_TORCH) || state.getLightEmission() > 0;
		};
	}

	// ---------------------------------------------------------------- storage

	private static final AttachmentType<Map<UUID, Home>> HOMES = AttachmentRegistry.<Map<UUID, Home>>builder()
		.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Home.CODEC)).buildAndRegister(CloneCraft.id("homes"));

	private HomeBase() {
	}

	/** Loads the class, so the saved data type is registered before any world loads. */
	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> checkBlasts(server.overworld()));
		// Nothing carries over from one world to the next.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
		// A player breaking part of a house means it: the clones leave that spot as the player made it. (Any other
		// hole, however it got there, gets fixed.)
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerLevel level && level == level.getServer().overworld()) {
				playerChanged(level, pos);
			}
		});
	}

	/** A player broke this block: if it's part of a house, leave it to them. */
	private static void playerChanged(final ServerLevel level, final BlockPos pos) {
		for (Home home : all(level).values()) {
			if (!home.bounds().inflate(1.0).contains(Vec3.atCenterOf(pos))) {
				continue;
			}
			for (Spot spot : home.plan()) {
				if (spot.pos().equals(pos) && spot.part() != Part.CLEAR) {
					home.skip.add(pos.immutable());
					home.placed.remove(pos);
					changed(level);
					return;
				}
			}
		}
	}

	private static Map<UUID, Home> all(final ServerLevel anyLevel) {
		ServerLevel overworld = anyLevel.getServer().overworld();
		Map<UUID, Home> map = overworld.getAttached(HOMES);
		if (!(map instanceof HashMap<UUID, Home>)) {
			map = map == null ? new HashMap<>() : new HashMap<>(map);
			overworld.setAttached(HOMES, map);
		}
		return map;
	}

	/** That player's clones' home, if they've started one. Homes are only in the overworld. */
	public static @Nullable Home get(final ServerLevel level, final @Nullable UUID owner) {
		return owner == null ? null : all(level).get(owner);
	}

	public static void set(final ServerLevel level, final UUID owner, final @Nullable Home home) {
		if (home == null) {
			all(level).remove(owner);
		} else {
			all(level).put(owner, home);
		}
		changed(level);
	}

	/**
	 * Part of a house (anyone's), or the ground right under or around one: mining never digs here, so no one's mine
	 * goes through a house.
	 */
	public static boolean protects(final ServerLevel level, final BlockPos pos) {
		if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
			return false;
		}
		Vec3 centre = Vec3.atCenterOf(pos);
		for (Home home : all(level).values()) {
			if (home.bounds().inflate(1.0, 0.0, 1.0).expandTowards(0.0, -3.0, 0.0).contains(centre)) {
				return true;
			}
		}
		return false;
	}

	public static void changed(final ServerLevel level) {
		ServerLevel overworld = level.getServer().overworld();
		overworld.setAttached(HOMES, new HashMap<>(all(level)));
	}

	// ---------------------------------------------------------------- creepers

	/** A creeper going off: where, when, and who it was after. */
	public record Blast(Vec3 pos, long time, @Nullable UUID target) {}

	private static final List<Blast> PENDING = new ArrayList<>();

	/** Called just before a creeper explodes (overworld only: homes are only there). */
	public static void creeperExploding(final ServerLevel level, final Vec3 pos, final @Nullable UUID target) {
		if (level == level.getServer().overworld()) {
			PENDING.add(new Blast(pos, level.getGameTime(), target));
		}
	}

	/** A creeper has just gone off near this home and the damage hasn't been looked at yet. */
	public static boolean blastPending(final Home home) {
		AABB near = home.bounds().inflate(8.0);
		return PENDING.stream().anyMatch(b -> near.contains(b.pos()));
	}

	/**
	 * The owner's clones react to a blast that damaged their home. Those nearby let a creeper get to the house, so
	 * their shared fighting brain counts it against what they were doing (it learns to deal with creepers sooner,
	 * or keep clear and shoot them). One of them tells the others.
	 */
	private static void reactToDamage(final ServerLevel level, final UUID owner, final Home home, final Blast blast, final List<BlockPos> lost) {
		boolean told = false;
		for (com.clonecraft.entity.CloneEntity clone : level.getEntitiesOfClass(com.clonecraft.entity.CloneEntity.class,
			home.bounds().inflate(48.0), c -> owner.equals(c.getOwnerId()))) {
			if (clone.distanceToSqr(blast.pos()) < 24.0 * 24.0) {
				clone.combat.penalize(level, Math.min(4.0F, 1.0F + lost.size() * 0.25F));
			}
			if (!told) {
				told = true;
				CloneTalk.say(clone, CloneTalk.Topic.HOUSE_BLOWN_UP, "A creeper blew a hole in our house! Let's rebuild it stronger.");
			}
		}
	}

	private static boolean open(final ServerLevel level, final BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
	}

	/**
	 * A tick after each creeper blast, sees what it took out of any home nearby: those spots are rebuilt one step
	 * stronger (stone, then an extra outer layer, then obsidian), and torches go up outside on that side.
	 */
	private static void checkBlasts(final ServerLevel level) {
		if (PENDING.isEmpty()) {
			return;
		}
		List<Blast> ready = new ArrayList<>();
		for (Blast blast : PENDING) {
			if (level.getGameTime() > blast.time() + 1) {
				ready.add(blast);
			}
		}
		PENDING.removeAll(ready);
		for (Blast blast : ready) {
			for (Map.Entry<UUID, Home> entry : all(level).entrySet()) {
				Home home = entry.getValue();
				if (!home.bounds().inflate(10.0).contains(blast.pos())) {
					continue;
				}
				// The crater (whether or not the house was hit): the holes it left below where the creeper stood,
				// to fill in so nobody falls in and gets stuck.
				// (A creeper's crater is up to about 3 deep and 4 or so out from where it stood: only open holes there, reaching up to the
				// surface, count, not a cave that happened to be under it.)
				BlockPos at = BlockPos.containing(blast.pos());
				boolean crater = false;
				for (BlockPos pos : BlockPos.betweenClosed(at.offset(-4, -3, -4), at.offset(4, -1, 4))) {
					if (!pos.closerThan(at, 5.0) || !level.isLoaded(pos) || home.fill.contains(pos) || !open(level, pos)) {
						continue;
					}
					boolean openAbove = true;
					for (BlockPos up = pos.above(); up.getY() < at.getY(); up = up.above()) {
						openAbove &= open(level, up);
					}
					if (openAbove) {
						home.fill.add(pos.immutable());
						crater = true;
					}
				}
				if (crater) {
					home.redesigned();
					changed(level);
				}
				List<BlockPos> lost = new ArrayList<>();
				for (Spot spot : home.plan()) {
					if (home.placed.contains(spot.pos()) && !done(level, home, spot)) {
						lost.add(spot.pos());
					}
				}
				if (lost.isEmpty()) {
					continue;
				}
				home.blasts++;
				Set<Direction> sides = new HashSet<>();
				for (BlockPos pos : lost) {
					home.placed.remove(pos);
					int stronger = Math.min(3, home.strengthAt(pos) + 1);
					home.strength.put(pos, stronger);
					Direction out = home.outward(pos);
					if (out != null) {
						sides.add(out);
						// Hit again: an extra layer of wall outside this spot.
						BlockPos layer = pos.relative(out);
						if (stronger >= 2 && !home.reinforce.contains(layer)) {
							home.reinforce.add(layer);
						}
					}
				}
				// Light up the sides it came from, a few blocks out, so creepers don't spawn there.
				AABB box = home.bounds();
				Vec3 centre = box.getCenter();
				for (Direction side : sides) {
					double reach = (side.getAxis() == Direction.Axis.X ? box.getXsize() : box.getZsize()) / 2.0 + 3.0;
					BlockPos torch = BlockPos.containing(centre.x + side.getStepX() * reach, home.origin.getY(), centre.z + side.getStepZ() * reach);
					if (!home.outerTorches.contains(torch)) {
						home.outerTorches.add(torch);
					}
				}
				home.redesigned();
				reactToDamage(level, entry.getKey(), home, blast, lost);
				changed(level);
			}
		}
	}
}
