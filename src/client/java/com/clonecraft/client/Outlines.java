package com.clonecraft.client;

import com.clonecraft.CloneCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import org.jspecify.annotations.Nullable;

/**
 * Draws boxes in the world with particles: the Schematic Selector's selection (while you hold it),
 * and a preview of where a clone will build (after pressing Show).
 */
public final class Outlines {
	private Outlines() {}

	private static @Nullable BlockPos selA;
	private static @Nullable BlockPos selB;
	private static @Nullable BlockPos buildA;
	private static @Nullable BlockPos buildB;
	private static int buildTicks;
	private static int tick;

	public static void setSelection(final @Nullable BlockPos a, final @Nullable BlockPos b) {
		selA = a;
		selB = b;
	}

	public static void showBuild(final BlockPos a, final BlockPos b, final int ticks) {
		buildA = a;
		buildB = b;
		buildTicks = ticks;
	}

	public static void tick(final Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null || ++tick % 5 != 0) {
			return;
		}
		boolean holding = minecraft.player.getMainHandItem().is(CloneCraft.SCHEMATIC_SELECTOR) || minecraft.player.getOffhandItem().is(CloneCraft.SCHEMATIC_SELECTOR);
		if (holding && selA != null) {
			drawBox(level, selA, selB == null ? selA : selB, new DustParticleOptions(0x33FF33, 1.0F));
		}
		if (buildTicks > 0 && buildA != null && buildB != null) {
			buildTicks -= 5;
			drawBox(level, buildA, buildB, new DustParticleOptions(0x3399FF, 1.0F));
		}
	}

	private static void drawBox(final ClientLevel level, final BlockPos a, final BlockPos b, final ParticleOptions particle) {
		double x0 = Math.min(a.getX(), b.getX());
		double y0 = Math.min(a.getY(), b.getY());
		double z0 = Math.min(a.getZ(), b.getZ());
		double x1 = Math.max(a.getX(), b.getX()) + 1;
		double y1 = Math.max(a.getY(), b.getY()) + 1;
		double z1 = Math.max(a.getZ(), b.getZ()) + 1;
		double[][] edges = {
			{x0, y0, z0, x1, y0, z0}, {x0, y1, z0, x1, y1, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z1, x1, y1, z1},
			{x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
			{x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}
		};
		for (double[] e : edges) {
			double len = Math.abs(e[3] - e[0]) + Math.abs(e[4] - e[1]) + Math.abs(e[5] - e[2]);
			int steps = (int)Math.min(200, Math.max(1, len * 2));
			for (int i = 0; i <= steps; i++) {
				double t = i / (double)steps;
				level.addParticle(particle, e[0] + (e[3] - e[0]) * t, e[1] + (e[4] - e[1]) * t, e[2] + (e[5] - e[2]) * t, 0, 0, 0);
			}
		}
	}
}
