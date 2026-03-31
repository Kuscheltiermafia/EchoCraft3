package io.github.kuscheltiermafia.teams;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TeamData {

    private final String name;
    private String displayName;
    private String teamNameColor;
    private UUID leader;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> pendingInvites = new HashSet<>();
    private final Map<UUID, TeamRole> roles = new HashMap<>();
    private final Set<String> allies = new HashSet<>();
    private final Set<String> pendingAllyInvites = new HashSet<>();

    public TeamData(String name, UUID leader) {
        this.name = name;
        this.displayName = name;
        this.teamNameColor = "yellow";
        this.leader = leader;
        this.members.add(leader);
        this.roles.put(leader, TeamRole.LEADER);
    }

    // Raw constructor for deserialization
    TeamData(String name, String displayName, String teamNameColor, UUID leader, Set<UUID> members, Set<UUID> pendingInvites, Map<UUID, TeamRole> roles,
             Set<String> allies, Set<String> pendingAllyInvites) {
        this.name = name;
        this.displayName = (displayName == null || displayName.isBlank()) ? name : displayName;
        this.teamNameColor = (teamNameColor == null || teamNameColor.isBlank()) ? "yellow" : teamNameColor;
        this.leader = leader;
        this.members.addAll(members);
        this.pendingInvites.addAll(pendingInvites);
        this.roles.putAll(roles);
        this.allies.addAll(allies);
        this.pendingAllyInvites.addAll(pendingAllyInvites);
        this.members.add(leader);
        this.roles.put(leader, TeamRole.LEADER);
        for (UUID member : this.members) {
            this.roles.putIfAbsent(member, TeamRole.MEMBER);
        }
    }

    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getTeamNameColor() { return teamNameColor; }
    public void setDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            this.displayName = name;
            return;
        }
        this.displayName = displayName;
    }

    public void setTeamNameColor(String teamNameColor) {
        if (teamNameColor == null || teamNameColor.isBlank()) {
            this.teamNameColor = "yellow";
            return;
        }
        this.teamNameColor = teamNameColor;
    }
    public UUID getLeader() {
        for (Map.Entry<UUID, TeamRole> entry : roles.entrySet()) {
            if (entry.getValue() == TeamRole.LEADER) return entry.getKey();
        }
        return leader;
    }
    public Set<UUID> getMembers() { return members; }
    public Set<UUID> getPendingInvites() { return pendingInvites; }
    public Map<UUID, TeamRole> getRoles() { return roles; }
    public Set<String> getAllies() { return allies; }
    public Set<String> getPendingAllyInvites() { return pendingAllyInvites; }

    public boolean isLeader(UUID uuid) { return roles.get(uuid) == TeamRole.LEADER; }
    public boolean isModerator(UUID uuid) {
        TeamRole role = roles.get(uuid);
        return role == TeamRole.MODERATOR || role == TeamRole.LEADER;
    }
    public boolean isMember(UUID uuid) { return members.contains(uuid); }
    public boolean isInvited(UUID uuid) { return pendingInvites.contains(uuid); }
    public TeamRole getRole(UUID uuid) { return roles.getOrDefault(uuid, TeamRole.MEMBER); }

    public boolean canManageClaims(UUID uuid) {
        TeamRole role = roles.get(uuid);
        return role == TeamRole.LEADER || role == TeamRole.MODERATOR;
    }

    public void invite(UUID uuid) { pendingInvites.add(uuid); }
    public void acceptInvite(UUID uuid) {
        pendingInvites.remove(uuid);
        members.add(uuid);
        roles.putIfAbsent(uuid, TeamRole.MEMBER);
    }

    public void setRole(UUID actor, UUID target, TeamRole newRole) {
        if (!isLeader(actor)) return;
        if (!members.contains(target)) return;
        roles.put(target, newRole);
        if (newRole == TeamRole.LEADER) leader = target;
    }

    public void inviteAlly(String teamName) {
        pendingAllyInvites.add(teamName.toLowerCase());
    }

    public boolean hasPendingAllyInvite(String teamName) {
        return pendingAllyInvites.contains(teamName.toLowerCase());
    }

    public void acceptAlly(String teamName) {
        String key = teamName.toLowerCase();
        pendingAllyInvites.remove(key);
        allies.add(key);
    }

    public void removeAlly(String teamName) {
        allies.remove(teamName.toLowerCase());
        pendingAllyInvites.remove(teamName.toLowerCase());
    }

    public boolean isAlliedWith(String teamName) {
        return allies.contains(teamName.toLowerCase());
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        roles.remove(uuid);
        boolean hasLeader = false;
        for (UUID member : members) {
            if (roles.get(member) == TeamRole.LEADER) {
                hasLeader = true;
                leader = member;
                break;
            }
        }
        if (!hasLeader && !members.isEmpty()) {
            UUID replacement = members.iterator().next();
            roles.put(replacement, TeamRole.LEADER);
            leader = replacement;
        }
    }
}
