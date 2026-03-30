package io.github.kuscheltiermafia.claims;

import java.util.UUID;

public class ClaimData {

    private final UUID ownerUuid;
    private final String ownerName;
    private String teamName; // null if not linked to a team
    private final String dimensionId;
    private final int bannerX;
    private final int bannerY;
    private final int bannerZ;

    public ClaimData(UUID ownerUuid, String ownerName, String dimensionId, int bannerX, int bannerY, int bannerZ) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.dimensionId = dimensionId;
        this.bannerX = bannerX;
        this.bannerY = bannerY;
        this.bannerZ = bannerZ;
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getDimensionId() { return dimensionId; }
    public int getBannerX() { return bannerX; }
    public int getBannerY() { return bannerY; }
    public int getBannerZ() { return bannerZ; }
}
