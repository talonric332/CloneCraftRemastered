package com.clonecraft.schematic;

import com.clonecraft.CloneCraft;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/** Saves and loads schematics in the world folder (clonecraft_schematics/NAME.nbt). */
public final class SchematicStore {
	private static final LevelResource FOLDER = new LevelResource("clonecraft_schematics");
	private static final Map<String, Schematic> CACHE = new HashMap<>();

	private SchematicStore() {}

	public record Entry(String name, int sizeX, int sizeY, int sizeZ, int blocks) {}

	private static Path folder(final MinecraftServer server) {
		return server.getWorldPath(FOLDER);
	}

	public static String cleanName(final String name) {
		String clean = name.trim().replaceAll("[^A-Za-z0-9 _\\-]", "");
		return clean.length() > 40 ? clean.substring(0, 40) : clean;
	}

	/** Forgets cached schematics, so a different world doesn't see this one's. Called when the server stops. */
	public static void clearCache() {
		CACHE.clear();
	}

	public static boolean exists(final MinecraftServer server, final String name) {
		return Files.exists(folder(server).resolve(cleanName(name) + ".nbt"));
	}

	public static boolean save(final MinecraftServer server, final Schematic schematic) {
		try {
			Path dir = folder(server);
			Files.createDirectories(dir);
			NbtIo.writeCompressed(schematic.save(), dir.resolve(cleanName(schematic.name) + ".nbt"));
			CACHE.put(cleanName(schematic.name), schematic);
			return true;
		} catch (IOException e) {
			CloneCraft.LOGGER.error("Couldn't save schematic {}", schematic.name, e);
			return false;
		}
	}

	public static @Nullable Schematic load(final MinecraftServer server, final String name) {
		String key = cleanName(name);
		Schematic cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		Path file = folder(server).resolve(key + ".nbt");
		if (!Files.exists(file)) {
			return null;
		}
		try {
			CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			Schematic schematic = Schematic.load(tag, server.registryAccess().lookupOrThrow(Registries.BLOCK));
			CACHE.put(key, schematic);
			return schematic;
		} catch (IOException e) {
			CloneCraft.LOGGER.error("Couldn't load schematic {}", name, e);
			return null;
		}
	}

	public static List<Entry> list(final MinecraftServer server) {
		List<Entry> entries = new ArrayList<>();
		Path dir = folder(server);
		if (!Files.isDirectory(dir)) {
			return entries;
		}
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".nbt")).sorted().toList()) {
				String name = file.getFileName().toString();
				name = name.substring(0, name.length() - 4);
				Schematic s = load(server, name);
				if (s != null) {
					entries.add(new Entry(name, s.sizeX, s.sizeY, s.sizeZ, s.countNonAir()));
				}
			}
		} catch (IOException e) {
			CloneCraft.LOGGER.error("Couldn't list schematics", e);
		}
		return entries;
	}
}
