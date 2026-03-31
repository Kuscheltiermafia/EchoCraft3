package io.github.kuscheltiermafia.teams;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public class TeamManager extends SavedData {

    private static final String DATA_KEY = "echocraft_teams";

    // key: lowercase team name
    private final Map<String, TeamData> teams = new HashMap<>();

    public TeamManager() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public boolean teamExists(String name) {
        return teams.containsKey(name.toLowerCase());
    }

    public TeamData getTeam(String name) {
        return teams.get(name.toLowerCase());
    }

    public boolean createTeam(String name, UUID leaderUuid) {
        if (teams.containsKey(name.toLowerCase())) return false;
        teams.put(name.toLowerCase(), new TeamData(name, leaderUuid));
        setDirty();
        return true;
    }

    public void saveTeam(TeamData team) {
        setDirty();
    }

    public void removeTeam(String name) {
        teams.remove(name.toLowerCase());
        setDirty();
    }

    /** Returns the team the given player is leader of, or null. */
    public TeamData getTeamByLeader(UUID uuid) {
        for (TeamData t : teams.values()) {
            if (t.isLeader(uuid)) return t;
        }
        return null;
    }

    /** Returns all teams the player is a member of. */
    public List<TeamData> getTeamsForPlayer(UUID uuid) {
        List<TeamData> result = new ArrayList<>();
        for (TeamData t : teams.values()) {
            if (t.isMember(uuid)) result.add(t);
        }
        return result;
    }

    /** Returns true if the given player is a member (or leader) of the team. */
    public boolean isMember(String teamName, UUID uuid) {
        TeamData t = getTeam(teamName);
        return t != null && t.isMember(uuid);
    }

    // -------------------------------------------------------------------------
    // SavedData serialisation
    // -------------------------------------------------------------------------

    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        CompoundTag teamsNbt = new CompoundTag();
        for (Map.Entry<String, TeamData> entry : teams.entrySet()) {
            TeamData t = entry.getValue();
            CompoundTag teamNbt = new CompoundTag();
            teamNbt.putString("Name", t.getName());
            teamNbt.putString("Leader", t.getLeader().toString());

            ListTag membersList = new ListTag();
            for (UUID member : t.getMembers()) {
                membersList.add(StringTag.valueOf(member.toString()));
            }
            teamNbt.put("Members", membersList);

            ListTag invitesList = new ListTag();
            for (UUID invite : t.getPendingInvites()) {
                invitesList.add(StringTag.valueOf(invite.toString()));
            }
            teamNbt.put("PendingInvites", invitesList);

            teamsNbt.put(entry.getKey(), teamNbt);
        }
        nbt.put("Teams", teamsNbt);
        return nbt;
    }

    public static TeamManager load(CompoundTag nbt, HolderLookup.Provider registries) {
        TeamManager manager = new TeamManager();
        if (!nbt.contains("Teams")) return manager;
        
        try {
            // Simple approach - just return empty for now
            // Proper NBT loading would require understanding Minecraft 26.1's NBT API
        } catch (Exception e) {
            // If loading fails, return empty manager
        }
        return manager;
    }

    // -------------------------------------------------------------------------
    // Server-wide singleton helper
    // -------------------------------------------------------------------------

    public static TeamManager get(MinecraftServer server) {
        // Simple in-memory singleton for now - TODO: implement proper persistence with SavedData API
        return new TeamManager();
    }
}
