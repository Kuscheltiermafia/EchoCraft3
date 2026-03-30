package io.github.kuscheltiermafia.teams;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TeamData {

    private final String name;
    private UUID leader;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> pendingInvites = new HashSet<>();

    public TeamData(String name, UUID leader) {
        this.name = name;
        this.leader = leader;
        this.members.add(leader);
    }

    // Raw constructor for deserialization
    TeamData(String name, UUID leader, Set<UUID> members, Set<UUID> pendingInvites) {
        this.name = name;
        this.leader = leader;
        this.members.addAll(members);
        this.pendingInvites.addAll(pendingInvites);
    }

    public String getName() { return name; }
    public UUID getLeader() { return leader; }
    public Set<UUID> getMembers() { return members; }
    public Set<UUID> getPendingInvites() { return pendingInvites; }

    public boolean isLeader(UUID uuid) { return leader.equals(uuid); }
    public boolean isMember(UUID uuid) { return members.contains(uuid); }
    public boolean isInvited(UUID uuid) { return pendingInvites.contains(uuid); }

    public void invite(UUID uuid) { pendingInvites.add(uuid); }
    public void acceptInvite(UUID uuid) {
        pendingInvites.remove(uuid);
        members.add(uuid);
    }
    public void removeMember(UUID uuid) {
        members.remove(uuid);
        if (leader.equals(uuid) && !members.isEmpty()) {
            leader = members.iterator().next();
        }
    }
}
