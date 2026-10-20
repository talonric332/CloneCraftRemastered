package com.clonecraft.network;

import com.clonecraft.CloneCraft;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Every message the clone screens send to the server, and the server sends back. */
public final class Payloads {
	private Payloads() {}

	// ---------------------------------------------------------------- client -> server

	/** A button on a clone screen. */
	public record CloneAction(int entityId, int action, int value) implements CustomPacketPayload {
		public static final int TRANSFER_XP = 0;
		public static final int KILL = 1;
		public static final int CLAIM = 2;
		public static final int COPY = 3;
		public static final int SET_TEAM = 4;
		public static final int SET_OPTIONS = 5;
		public static final int OPEN_MAIN = 6;
		public static final int REQUEST_LISTS = 7;
		public static final int STOP_BUILDING = 8;
		public static final int REQUEST_SCHEMATICS = 9;

		public static final Type<CloneAction> TYPE = new Type<>(CloneCraft.id("clone_action"));
		public static final StreamCodec<ByteBuf, CloneAction> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CloneAction::entityId, ByteBufCodecs.VAR_INT, CloneAction::action, ByteBufCodecs.VAR_INT, CloneAction::value, CloneAction::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record SetName(int entityId, String name) implements CustomPacketPayload {
		public static final Type<SetName> TYPE = new Type<>(CloneCraft.id("set_name"));
		public static final StreamCodec<ByteBuf, SetName> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SetName::entityId, ByteBufCodecs.stringUtf8(32), SetName::name, SetName::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Replaces the "Entities To Attack" (list 0) or "Blocks To Break" (list 1) list. */
	public record SetList(int entityId, int list, List<Identifier> ids) implements CustomPacketPayload {
		public static final Type<SetList> TYPE = new Type<>(CloneCraft.id("set_list"));
		public static final StreamCodec<ByteBuf, SetList> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SetList::entityId, ByteBufCodecs.VAR_INT, SetList::list, Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), SetList::ids, SetList::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Build(int entityId, String schematic, BlockPos origin, int rotation, boolean creative, int delay) implements CustomPacketPayload {
		public static final Type<Build> TYPE = new Type<>(CloneCraft.id("build"));
		public static final StreamCodec<ByteBuf, Build> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Build::entityId, ByteBufCodecs.stringUtf8(64), Build::schematic, BlockPos.STREAM_CODEC, Build::origin,
			ByteBufCodecs.VAR_INT, Build::rotation, ByteBufCodecs.BOOL, Build::creative, ByteBufCodecs.VAR_INT, Build::delay, Build::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record SaveSchematic(String name, BlockPos a, BlockPos b, boolean overwrite) implements CustomPacketPayload {
		public static final Type<SaveSchematic> TYPE = new Type<>(CloneCraft.id("save_schematic"));
		public static final StreamCodec<ByteBuf, SaveSchematic> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), SaveSchematic::name, BlockPos.STREAM_CODEC, SaveSchematic::a, BlockPos.STREAM_CODEC, SaveSchematic::b,
			ByteBufCodecs.BOOL, SaveSchematic::overwrite, SaveSchematic::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A button on the Clone Remote screen. The clone is picked by UUID, since it may be far away or in another dimension. */
	public record RemoteAction(UUID cloneId, int action, int value) implements CustomPacketPayload {
		public static final int REFRESH = 0;
		public static final int TELEPORT = 1;
		public static final int TELEPORT_ALL = 2;
		public static final int SET_MODE = 3;
		public static final int MANAGE = 4;

		public static final Type<RemoteAction> TYPE = new Type<>(CloneCraft.id("remote_action"));
		public static final StreamCodec<ByteBuf, RemoteAction> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, RemoteAction::cloneId, ByteBufCodecs.VAR_INT, RemoteAction::action, ByteBufCodecs.VAR_INT, RemoteAction::value, RemoteAction::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * A change to an "Entities To Attack" or "Blocks To Break" list. Entries in add are added and entries in remove are removed;
	 * anything else is left alone. With replace on, the list becomes exactly the add entries instead.
	 */
	public record ListEdit(List<Identifier> add, List<Identifier> remove, boolean replace) {
		public static final int MAX_ENTRIES = 4096;
		public static final ListEdit NONE = new ListEdit(List.of(), List.of(), false);

		public static final StreamCodec<ByteBuf, ListEdit> CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), ListEdit::add, Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), ListEdit::remove,
			ByteBufCodecs.BOOL, ListEdit::replace, ListEdit::new);

		public boolean isEmpty() {
			return !this.replace && this.add.isEmpty() && this.remove.isEmpty();
		}
	}

	/**
	 * The Clone Remote's bulk editor: applies the same changes to many clones at once. Options in setMask are turned on and
	 * options in clearMask turned off (others are left alone). team is a CloneTeam ordinal, or -1 to leave it. attackEdit
	 * and breakEdit change the "Entities To Attack" and "Blocks To Break" lists.
	 */
	public record BulkEdit(List<UUID> clones, int setMask, int clearMask, int team, ListEdit attackEdit, ListEdit breakEdit)
		implements CustomPacketPayload {
		public static final int MAX_CLONES = 1024;

		public static final Type<BulkEdit> TYPE = new Type<>(CloneCraft.id("bulk_edit"));
		public static final StreamCodec<ByteBuf, BulkEdit> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CLONES)), BulkEdit::clones, ByteBufCodecs.VAR_INT, BulkEdit::setMask,
			ByteBufCodecs.VAR_INT, BulkEdit::clearMask, ByteBufCodecs.VAR_INT, BulkEdit::team,
			ListEdit.CODEC, BulkEdit::attackEdit, ListEdit.CODEC, BulkEdit::breakEdit, BulkEdit::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	// ---------------------------------------------------------------- server -> client

	/** One owned clone, as shown on the Clone Remote screen. Mode: 0 none, 1 follow, 2 stay, 3 wander. */
	public record RemoteClone(UUID uuid, int entityId, String name, float health, float maxHealth, String dimension, BlockPos pos,
		boolean sameLevel, int mode) {
		public static final int MODE_NONE = 0;
		public static final int MODE_FOLLOW = 1;
		public static final int MODE_STAY = 2;
		public static final int MODE_WANDER = 3;

		public static final StreamCodec<ByteBuf, RemoteClone> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, RemoteClone::uuid, ByteBufCodecs.VAR_INT, RemoteClone::entityId, ByteBufCodecs.stringUtf8(64), RemoteClone::name,
			ByteBufCodecs.FLOAT, RemoteClone::health, ByteBufCodecs.FLOAT, RemoteClone::maxHealth, ByteBufCodecs.stringUtf8(256), RemoteClone::dimension,
			BlockPos.STREAM_CODEC, RemoteClone::pos, ByteBufCodecs.BOOL, RemoteClone::sameLevel, ByteBufCodecs.VAR_INT, RemoteClone::mode, RemoteClone::new);
	}

	/** The player's clones for the Clone Remote screen. "open" opens the screen; otherwise it just refreshes an open one. */
	public record RemoteList(List<RemoteClone> clones, boolean open) implements CustomPacketPayload {
		public static final Type<RemoteList> TYPE = new Type<>(CloneCraft.id("remote_list"));
		public static final StreamCodec<ByteBuf, RemoteList> CODEC = StreamCodec.composite(
			RemoteClone.CODEC.apply(ByteBufCodecs.list()), RemoteList::clones, ByteBufCodecs.BOOL, RemoteList::open, RemoteList::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record CloneLists(int entityId, List<Identifier> attack, List<Identifier> breakBlocks) implements CustomPacketPayload {
		public static final Type<CloneLists> TYPE = new Type<>(CloneCraft.id("clone_lists"));
		public static final StreamCodec<ByteBuf, CloneLists> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CloneLists::entityId, Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), CloneLists::attack,
			Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), CloneLists::breakBlocks, CloneLists::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record SchematicInfo(String name, int sizeX, int sizeY, int sizeZ, int blocks) {
		public static final StreamCodec<ByteBuf, SchematicInfo> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), SchematicInfo::name, ByteBufCodecs.VAR_INT, SchematicInfo::sizeX, ByteBufCodecs.VAR_INT, SchematicInfo::sizeY,
			ByteBufCodecs.VAR_INT, SchematicInfo::sizeZ, ByteBufCodecs.VAR_INT, SchematicInfo::blocks, SchematicInfo::new);
	}

	public record SchematicList(int entityId, List<SchematicInfo> schematics) implements CustomPacketPayload {
		public static final Type<SchematicList> TYPE = new Type<>(CloneCraft.id("schematic_list"));
		public static final StreamCodec<ByteBuf, SchematicList> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SchematicList::entityId, SchematicInfo.CODEC.apply(ByteBufCodecs.list()), SchematicList::schematics, SchematicList::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Opens the "save schematic" screen after selecting two corners with the Schematic Selector. */
	public record OpenSaveSchematic(BlockPos a, BlockPos b) implements CustomPacketPayload {
		public static final Type<OpenSaveSchematic> TYPE = new Type<>(CloneCraft.id("open_save_schematic"));
		public static final StreamCodec<ByteBuf, OpenSaveSchematic> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, OpenSaveSchematic::a, BlockPos.STREAM_CODEC, OpenSaveSchematic::b, OpenSaveSchematic::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Tells the client which corners are selected, so it can outline the box. */
	public record Selection(Optional<BlockPos> a, Optional<BlockPos> b) implements CustomPacketPayload {
		public static final Type<Selection> TYPE = new Type<>(CloneCraft.id("selection"));
		public static final StreamCodec<ByteBuf, Selection> CODEC = StreamCodec.composite(
			ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Selection::a, ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Selection::b, Selection::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	@SuppressWarnings("unused")
	private static StreamCodec<RegistryFriendlyByteBuf, Integer> unused() {
		return null;
	}
}
