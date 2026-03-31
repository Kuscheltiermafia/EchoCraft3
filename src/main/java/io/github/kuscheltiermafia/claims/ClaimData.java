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

    private boolean explosionsAllowed = false;
    private boolean pvpAllowed = false;
    private boolean foreignBreakAllowed = false;
    private boolean foreignPlaceAllowed = false;
    private boolean foreignInteractAllowed = false;

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

    public boolean isExplosionsAllowed() { return explosionsAllowed; }
    public void setExplosionsAllowed(boolean explosionsAllowed) { this.explosionsAllowed = explosionsAllowed; }
    public boolean isPvpAllowed() { return pvpAllowed; }
    public void setPvpAllowed(boolean pvpAllowed) { this.pvpAllowed = pvpAllowed; }
    public boolean isForeignBreakAllowed() { return foreignBreakAllowed; }
    public void setForeignBreakAllowed(boolean foreignBreakAllowed) { this.foreignBreakAllowed = foreignBreakAllowed; }
    public boolean isForeignPlaceAllowed() { return foreignPlaceAllowed; }
    public void setForeignPlaceAllowed(boolean foreignPlaceAllowed) { this.foreignPlaceAllowed = foreignPlaceAllowed; }
    public boolean isForeignInteractAllowed() { return foreignInteractAllowed; }
    public void setForeignInteractAllowed(boolean foreignInteractAllowed) { this.foreignInteractAllowed = foreignInteractAllowed; }
}
