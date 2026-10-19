package com.clonecraft.clone;

import net.minecraft.util.RandomSource;

/**
 * New clones get a random name, like the original. A clone's name is also the Minecraft username whose skin it
 * wears, so renaming a clone (for example to "DanTDM") changes its skin.
 */
public final class NameRegistry {
	private NameRegistry() {}

	private static final String[] BOYS = {
		"Noah", "Liam", "Jacob", "Mason", "William", "Ethan", "Michael", "Alexander", "Jayden", "Daniel", "Elijah", "Aiden",
		"James", "Benjamin", "Matthew", "Jackson", "Logan", "David", "Anthony", "Joseph", "Joshua", "Andrew", "Lucas", "Gabriel",
		"Samuel", "Christopher", "John", "Dylan", "Isaac", "Ryan", "Nathan", "Carter", "Caleb", "Luke", "Christian", "Hunter",
		"Henry", "Owen", "Landon", "Jack", "Wyatt", "Jonathan", "Eli", "Isaiah", "Sebastian", "Julian", "Gavin", "Levi", "Aaron",
		"Oliver", "Jordan", "Nicholas", "Evan", "Connor", "Charles", "Cameron", "Adrian", "Thomas", "Robert", "Tyler", "Austin",
		"Dominic", "Brandon", "Kevin", "Zachary", "Parker", "Blake", "Chase", "Jason", "Ian", "Adam", "Xavier", "Cooper", "Justin",
		"Nolan", "Hudson", "Carson", "Nathaniel", "Leo", "Max", "Oscar", "Felix", "Hugo", "Arthur", "George", "Harry", "Alfie"
	};

	private static final String[] GIRLS = {
		"Sophia", "Emma", "Olivia", "Isabella", "Ava", "Mia", "Emily", "Abigail", "Madison", "Elizabeth", "Charlotte", "Avery",
		"Sofia", "Chloe", "Ella", "Harper", "Amelia", "Aubrey", "Addison", "Evelyn", "Natalie", "Grace", "Hannah", "Zoey",
		"Victoria", "Lillian", "Lily", "Brooklyn", "Samantha", "Layla", "Zoe", "Audrey", "Leah", "Allison", "Anna", "Aaliyah",
		"Savannah", "Gabriella", "Camila", "Aria", "Kaylee", "Scarlett", "Hailey", "Arianna", "Riley", "Alexis", "Nevaeh",
		"Sarah", "Claire", "Sadie", "Peyton", "Aubree", "Serenity", "Ariana", "Genesis", "Penelope", "Alyssa", "Bella", "Taylor",
		"Alexa", "Kylie", "Mackenzie", "Caroline", "Kennedy", "Autumn", "Lucy", "Ashley", "Madelyn", "Violet", "Stella", "Brianna",
		"Maya", "Skylar", "Ellie", "Julia", "Sophie", "Katherine", "Mila", "Khloe", "Paisley", "Annabelle", "Alexandra", "Nora"
	};

	public static String boyName(final RandomSource random) {
		return BOYS[random.nextInt(BOYS.length)];
	}

	public static String girlName(final RandomSource random) {
		return GIRLS[random.nextInt(GIRLS.length)];
	}
}
