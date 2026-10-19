package com.clonecraft.clone;

/**
 * In the original mod each clone picked a tune, and every experience orb it collected played the next note.
 * These are public-domain melodies, written as semitones above F#3 (0..24).
 */
public final class Songs {
	private Songs() {}

	private static final int[][] SONGS = {
		// Mozart - Rondo alla Turca
		{11, 9, 8, 9, 12, 14, 12, 11, 12, 16, 17, 16, 15, 16, 23, 21, 20, 21, 23, 21, 20, 21, 24, 21, 24, 23, 21, 19, 21},
		// Beethoven - Ode to Joy
		{10, 10, 11, 13, 13, 11, 10, 8, 6, 6, 8, 10, 10, 8, 8, 10, 10, 11, 13, 13, 11, 10, 8, 6, 6, 8, 10, 8, 6, 6},
		// Twinkle Twinkle Little Star
		{6, 6, 13, 13, 15, 15, 13, 11, 11, 10, 10, 8, 8, 6, 13, 13, 11, 11, 10, 10, 8, 13, 13, 11, 11, 10, 10, 8},
		// Grieg - In the Hall of the Mountain King
		{6, 8, 9, 11, 13, 9, 13, 12, 8, 12, 11, 7, 11, 6, 8, 9, 11, 13, 9, 13, 18, 16, 13, 9, 13, 16},
		// Brahms - Lullaby
		{10, 10, 13, 10, 10, 13, 10, 13, 18, 17, 15, 15, 13, 8, 10, 11, 8, 8, 10, 11, 8, 11, 17, 15, 13, 17, 18}
	};

	public static int count() {
		return SONGS.length;
	}

	public static float pitch(final int song, final int index) {
		int[] notes = SONGS[Math.floorMod(song, SONGS.length)];
		int note = notes[Math.floorMod(index, notes.length)];
		return (float)Math.pow(2.0, (note - 12) / 12.0);
	}
}
