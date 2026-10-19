package com.clonecraft.schematic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A saved build: every block inside a box, recorded with the Schematic Selector. Clones can build it again. */
public class Schematic {
	public final String name;
	public final int sizeX;
	public final int sizeY;
	public final int sizeZ;
	private final List<BlockState> palette;
	private final int[] blocks;

	public Schematic(final String name, final int sizeX, final int sizeY, final int sizeZ, final List<BlockState> palette, final int[] blocks) {
		this.name = name;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.palette = palette;
		this.blocks = blocks;
	}

	public int size() {
		return this.blocks.length;
	}

	/** Blocks are ordered bottom layer first, so clones build from the ground up. */
	public BlockPos localPos(final int index) {
		int layer = this.sizeX * this.sizeZ;
		int y = index / layer;
		int rest = index % layer;
		return new BlockPos(rest % this.sizeX, y, rest / this.sizeX);
	}

	public BlockState get(final int index) {
		return this.palette.get(this.blocks[index]);
	}

	public int countNonAir() {
		int n = 0;
		for (int b : this.blocks) {
			if (!this.palette.get(b).isAir()) {
				n++;
			}
		}
		return n;
	}

	public static Schematic capture(final Level level, final String name, final BlockPos a, final BlockPos b) {
		BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
		BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
		int sx = max.getX() - min.getX() + 1;
		int sy = max.getY() - min.getY() + 1;
		int sz = max.getZ() - min.getZ() + 1;
		List<BlockState> palette = new ArrayList<>();
		Map<BlockState, Integer> ids = new HashMap<>();
		int[] blocks = new int[sx * sy * sz];
		int i = 0;
		for (int y = 0; y < sy; y++) {
			for (int z = 0; z < sz; z++) {
				for (int x = 0; x < sx; x++) {
					BlockState state = level.getBlockState(min.offset(x, y, z));
					Integer id = ids.get(state);
					if (id == null) {
						id = palette.size();
						palette.add(state);
						ids.put(state, id);
					}
					blocks[i++] = id;
				}
			}
		}
		return new Schematic(name, sx, sy, sz, palette, blocks);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Name", this.name);
		tag.putInt("SizeX", this.sizeX);
		tag.putInt("SizeY", this.sizeY);
		tag.putInt("SizeZ", this.sizeZ);
		ListTag paletteTag = new ListTag();
		for (BlockState state : this.palette) {
			paletteTag.add(NbtUtils.writeBlockState(state));
		}
		tag.put("Palette", paletteTag);
		tag.putIntArray("Blocks", this.blocks);
		return tag;
	}

	public static Schematic load(final CompoundTag tag, final HolderGetter<Block> blocks) {
		List<BlockState> palette = new ArrayList<>();
		ListTag paletteTag = tag.getListOrEmpty("Palette");
		for (int i = 0; i < paletteTag.size(); i++) {
			Tag entry = paletteTag.get(i);
			palette.add(entry instanceof CompoundTag c ? NbtUtils.readBlockState(blocks, c) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		}
		return new Schematic(tag.getStringOr("Name", "?"), tag.getIntOr("SizeX", 1), tag.getIntOr("SizeY", 1), tag.getIntOr("SizeZ", 1), palette,
			tag.getIntArray("Blocks").orElse(new int[0]));
	}
}
