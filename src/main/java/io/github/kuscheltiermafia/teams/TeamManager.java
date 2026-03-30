package io.github.kuscheltiermafia.teams;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.*;

public class TeamManager extends PersistentState {

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
        markDirty();
        return true;
    }

    public void saveTeam(TeamData team) {
        markDirty();
    }

    public void removeTeam(String name) {
        teams.remove(name.toLowerCase());
        markDirty();
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
    // PersistentState serialisation
    // -------------------------------------------------------------------------

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtCompound teamsNbt = new NbtCompound();
        for (Map.Entry<String, TeamData> entry : teams.entrySet()) {
            TeamData t = entry.getValue();
            NbtCompound teamNbt = new NbtCompound();
            teamNbt.putString("Name", t.getName());
            teamNbt.putUuid("Leader", t.getLeader());

            NbtList membersList = new NbtList();
            for (UUID member : t.getMembers()) {
                membersList.add(NbtString.of(member.toString()));
            }
            teamNbt.put("Members", membersList);

            NbtList invitesList = new NbtList();
            for (UUID invite : t.getPendingInvites()) {
                invitesList.add(NbtString.of(invite.toString()));
            }
            teamNbt.put("PendingInvites", invitesList);

            teamsNbt.put(entry.getKey(), teamNbt);
        }
        nbt.put("Teams", teamsNbt);
        return nbt;
    }

    public static TeamManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        TeamManager manager = new TeamManager();
        NbtCompound teamsNbt = nbt.getCompound("Teams");
        for (String key : teamsNbt.getKeys()) {
            NbtCompound teamNbt = teamsNbt.getCompound(key);
            String name = teamNbt.getString("Name");
            UUID leader = teamNbt.getUuid("Leader");

            Set<UUID> members = new HashSet<>();
            NbtList membersList = teamNbt.getList("Members", 8); // 8 = NbtString
            for (int i = 0; i < membersList.size(); i++) {
                members.add(UUID.fromString(membersList.getString(i)));
            }

            Set<UUID> pendingInvites = new HashSet<>();
            NbtList invitesList = teamNbt.getList("PendingInvites", 8);
            for (int i = 0; i < invitesList.size(); i++) {
                pendingInvites.add(UUID.fromString(invitesList.getString(i)));
            }

            manager.teams.put(key, new TeamData(name, leader, members, pendingInvites));
        }
        return manager;
    }

    // -------------------------------------------------------------------------
    // Server-wide singleton helper
    // -------------------------------------------------------------------------

    private static final PersistentState.Type<TeamManager> TYPE = new PersistentState.Type<>(
            TeamManager::new,
            TeamManager::fromNbt,
            null
    );

    public static TeamManager get(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        PersistentStateManager stateManager = world.getPersistentStateManager();
        return stateManager.getOrCreate(TYPE, DATA_KEY);
    }
}
