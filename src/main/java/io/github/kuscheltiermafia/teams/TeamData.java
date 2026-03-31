package io.github.kuscheltiermafia.teams;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TeamData {

    private final String name;
    private UUID leader;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> pendingInvites = new HashSet<>();
    private final Map<UUID, TeamRole> roles = new HashMap<>();

    public TeamData(String name, UUID leader) {
        this.name = name;
        this.leader = leader;
        this.members.add(leader);
        this.roles.put(leader, TeamRole.LEADER);
    }

    // Raw constructor for deserialization
    TeamData(String name, UUID leader, Set<UUID> members, Set<UUID> pendingInvites, Map<UUID, TeamRole> roles) {
        this.name = name;
        this.leader = leader;
        this.members.addAll(members);
        this.pendingInvites.addAll(pendingInvites);
        this.roles.putAll(roles);
        this.members.add(leader);
        this.roles.put(leader, TeamRole.LEADER);
        for (UUID member : this.members) {
            this.roles.putIfAbsent(member, TeamRole.MEMBER);
        }
    }

    public String getName() { return name; }
    public UUID getLeader() { return leader; }
    public Set<UUID> getMembers() { return members; }
    public Set<UUID> getPendingInvites() { return pendingInvites; }
    public Map<UUID, TeamRole> getRoles() { return roles; }

    public boolean isLeader(UUID uuid) { return leader.equals(uuid); }
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
        if (newRole == TeamRole.LEADER) {
            roles.put(leader, TeamRole.MEMBER);
            leader = target;
        }
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        roles.remove(uuid);
        if (leader.equals(uuid) && !members.isEmpty()) {
            leader = members.iterator().next();
            roles.put(leader, TeamRole.LEADER);
        }
    }
}
