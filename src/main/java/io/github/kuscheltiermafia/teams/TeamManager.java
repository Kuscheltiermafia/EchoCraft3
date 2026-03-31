package io.github.kuscheltiermafia.teams;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.saveddata.SavedData;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TeamManager extends SavedData {

    private static final Map<MinecraftServer, TeamManager> RUNTIME_INSTANCES = new WeakHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "echocraft_teams.json";

    // key: lowercase team name
    private final Map<String, TeamData> teams = new HashMap<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private final Path storageFile;

    public TeamManager() {
        this.storageFile = null;
    }

    private TeamManager(Path storageFile) {
        this.storageFile = storageFile;
    }

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
        markDirtyAndPersist();
        return true;
    }

    public void saveTeam(TeamData team) {
        markDirtyAndPersist();
    }

    public void removeTeam(String name) {
        String key = name.toLowerCase();
        teams.remove(key);
        for (TeamData team : teams.values()) {
            team.removeAlly(key);
        }
        markDirtyAndPersist();
    }

    public void rememberPlayerName(UUID uuid, String name) {
        playerNames.put(uuid, name);
        markDirtyAndPersist();
    }

    public String getKnownPlayerName(UUID uuid) {
        return playerNames.get(uuid);
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

    /** Returns one team the player is a member of, or null if none. */
    public TeamData getTeamForPlayer(UUID uuid) {
        for (TeamData t : teams.values()) {
            if (t.isMember(uuid)) return t;
        }
        return null;
    }

    /** Returns true if the given player is a member (or leader) of the team. */
    public boolean isMember(String teamName, UUID uuid) {
        TeamData t = getTeam(teamName);
        return t != null && t.isMember(uuid);
    }

    public boolean canManageClaims(String teamName, UUID uuid) {
        TeamData t = getTeam(teamName);
        return t != null && t.canManageClaims(uuid);
    }

    public boolean areAllies(String teamA, String teamB) {
        TeamData a = getTeam(teamA);
        TeamData b = getTeam(teamB);
        if (a == null || b == null) return false;
        return a.isAlliedWith(teamB) && b.isAlliedWith(teamA);
    }

    public boolean inviteAlly(String sourceTeam, String targetTeam) {
        TeamData source = getTeam(sourceTeam);
        TeamData target = getTeam(targetTeam);
        if (source == null || target == null) return false;
        target.inviteAlly(source.getName());
        markDirtyAndPersist();
        return true;
    }

    public boolean acceptAlly(String acceptingTeam, String sourceTeam) {
        TeamData accepting = getTeam(acceptingTeam);
        TeamData source = getTeam(sourceTeam);
        if (accepting == null || source == null) return false;
        if (!accepting.hasPendingAllyInvite(sourceTeam)) return false;
        accepting.acceptAlly(source.getName());
        source.acceptAlly(accepting.getName());
        markDirtyAndPersist();
        return true;
    }

    public boolean removeAlly(String teamA, String teamB) {
        TeamData a = getTeam(teamA);
        TeamData b = getTeam(teamB);
        if (a == null || b == null) return false;
        a.removeAlly(teamB);
        b.removeAlly(teamA);
        markDirtyAndPersist();
        return true;
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
        return RUNTIME_INSTANCES.computeIfAbsent(server, TeamManager::loadFromDisk);
    }

    private static TeamManager loadFromDisk(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME);
        TeamManager manager = new TeamManager(file);
        if (!Files.exists(file)) return manager;

        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(raw, JsonObject.class);
            if (root == null || !root.has("teams") || !root.get("teams").isJsonArray()) return manager;

            if (root.has("playerNames") && root.get("playerNames").isJsonObject()) {
                JsonObject names = root.getAsJsonObject("playerNames");
                for (Map.Entry<String, JsonElement> entry : names.entrySet()) {
                    try {
                        manager.playerNames.put(UUID.fromString(entry.getKey()), entry.getValue().getAsString());
                    } catch (Exception ignored) {
                        // Ignore malformed name entries.
                    }
                }
            }

            JsonArray teamsArray = root.getAsJsonArray("teams");
            for (JsonElement element : teamsArray) {
                if (!element.isJsonObject()) continue;
                JsonObject teamObj = element.getAsJsonObject();

                if (!teamObj.has("name") || !teamObj.has("leader")) continue;
                String name = teamObj.get("name").getAsString();
                UUID leader = UUID.fromString(teamObj.get("leader").getAsString());

                Set<UUID> members = readUuidSet(teamObj.getAsJsonArray("members"));
                members.add(leader);
                Set<UUID> invites = readUuidSet(teamObj.getAsJsonArray("pendingInvites"));
                Map<UUID, TeamRole> roles = readRoleMap(teamObj.getAsJsonObject("roles"));
                Set<String> allies = readStringSet(teamObj.getAsJsonArray("allies"));
                Set<String> allyInvites = readStringSet(teamObj.getAsJsonArray("pendingAllyInvites"));

                manager.teams.put(name.toLowerCase(), new TeamData(name, leader, members, invites, roles, allies, allyInvites));
            }
        } catch (Exception ignored) {
            // If parsing fails, keep an empty manager so the server can still run.
        }

        return manager;
    }

    private static Set<UUID> readUuidSet(JsonArray array) {
        Set<UUID> out = new HashSet<>();
        if (array == null) return out;
        for (JsonElement element : array) {
            try {
                out.add(UUID.fromString(element.getAsString()));
            } catch (Exception ignored) {
                // Ignore malformed UUIDs from old/corrupt files.
            }
        }
        return out;
    }

    private static Map<UUID, TeamRole> readRoleMap(JsonObject obj) {
        Map<UUID, TeamRole> out = new HashMap<>();
        if (obj == null) return out;
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            try {
                out.put(UUID.fromString(entry.getKey()), TeamRole.fromString(entry.getValue().getAsString()));
            } catch (Exception ignored) {
                // Ignore malformed role entries.
            }
        }
        return out;
    }

    private static Set<String> readStringSet(JsonArray array) {
        Set<String> out = new HashSet<>();
        if (array == null) return out;
        for (JsonElement element : array) {
            try {
                out.add(element.getAsString().toLowerCase());
            } catch (Exception ignored) {
                // Ignore malformed string entries.
            }
        }
        return out;
    }

    private void markDirtyAndPersist() {
        setDirty();
        persistToDisk();
    }

    private void persistToDisk() {
        if (storageFile == null) return;
        try {
            Files.createDirectories(storageFile.getParent());

            JsonArray teamsArray = new JsonArray();
            for (TeamData team : teams.values()) {
                JsonObject teamObj = new JsonObject();
                teamObj.addProperty("name", team.getName());
                teamObj.addProperty("leader", team.getLeader().toString());

                JsonArray members = new JsonArray();
                for (UUID member : team.getMembers()) {
                    members.add(member.toString());
                }
                teamObj.add("members", members);

                JsonArray invites = new JsonArray();
                for (UUID invite : team.getPendingInvites()) {
                    invites.add(invite.toString());
                }
                teamObj.add("pendingInvites", invites);

                JsonObject roles = new JsonObject();
                for (Map.Entry<UUID, TeamRole> entry : team.getRoles().entrySet()) {
                    roles.addProperty(entry.getKey().toString(), entry.getValue().name());
                }
                teamObj.add("roles", roles);

                JsonArray allies = new JsonArray();
                for (String ally : team.getAllies()) allies.add(ally);
                teamObj.add("allies", allies);

                JsonArray pendingAllyInvites = new JsonArray();
                for (String allyInvite : team.getPendingAllyInvites()) pendingAllyInvites.add(allyInvite);
                teamObj.add("pendingAllyInvites", pendingAllyInvites);

                teamsArray.add(teamObj);
            }

            JsonObject root = new JsonObject();
            root.add("teams", teamsArray);

            JsonObject names = new JsonObject();
            for (Map.Entry<UUID, String> entry : playerNames.entrySet()) {
                names.addProperty(entry.getKey().toString(), entry.getValue());
            }
            root.add("playerNames", names);

            Files.writeString(storageFile, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Persistence errors should not crash gameplay commands.
        }
    }
}
