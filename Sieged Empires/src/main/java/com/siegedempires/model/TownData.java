package com.siegedempires.model;

import com.google.gson.annotations.SerializedName;

import java.util.*;

public class TownData {
    private String id;
    private String name;
    private String description;
    private boolean isNation;
    private UUID monarchUuid;
    private String monarchName;
    /** "King" or "Queen" — chosen when the town is founded. */
    private String monarchTitle;
    private Map<UUID, String> members;
    private Map<UUID, String> memberNames;
    @SerializedName("Claimed Land")
    private Set<ChunkPosition> claimedChunks;
    private int treasuryBalance;
    private String empireId;
    private boolean townPublic;
    private Set<UUID> invitedPlayers;
    private Set<UUID> lords;
    private Set<String> allies;
    /**
     * Restricted Zones: Monarch, Lord, Trusted Citizen, and Empire emperor may
     * use these plots normally. Regular citizens get outsider (non-member) permissions.
     */
    private Set<ChunkPosition> restrictedChunks = new HashSet<>();
    private Map<UUID, Set<ChunkPosition>> citizenChunks = new HashMap<>();
    /**
     * Chunks granted to a specific citizen via the Give Citizen Land banner.
     * Each entry maps a citizen UUID to the set of chunks that citizen owns;
     * in those chunks the citizen has full autonomy (like outside town).
     */
    private Map<UUID, Set<ChunkPosition>> citizenOwnedChunks = new HashMap<>();

    private Set<String> enemies;
    private long foundedTimestamp;
    private List<String> bannerPatterns;
    private String bannerBaseColor;
    /**
     * Custom 20×40 dye-pixel flag ({@link com.siegedempires.banner.CustomBannerDesign}
     * hex encoding). When present, GUI / VoxelMapSync prefer this over
     * {@code bannerPatterns}. Legacy towns may leave this null.
     * <p>While the town is in an empire, these fields hold the <em>empire</em>
     * flag for claims/maps/inventory. The town's own design is kept in
     * {@code savedBanner*} and restored on leave / empire dissolve.
     */
    private String bannerPixels;
    /**
     * Town's own flag, preserved while {@link #empireId} is set and active
     * {@code banner*} fields show the empire design. Null/empty when independent.
     */
    private List<String> savedBannerPatterns;
    private String savedBannerBaseColor;
    private String savedBannerPixels;
    /** Empire-owned invasion capture land; hidden from public town lists. */
    private boolean warTown;
    /** Source town id when this wartown was created from an invasion capture. */
    private String capturedFromTownId;

    public TownData() {
        this.members = new HashMap<>();
        this.memberNames = new HashMap<>();
        this.claimedChunks = new HashSet<>();
        this.invitedPlayers = new HashSet<>();
        this.lords = new HashSet<>();
        this.allies = new HashSet<>();
        this.enemies = new HashSet<>();
        this.bannerPatterns = new ArrayList<>();
        this.foundedTimestamp = System.currentTimeMillis();
        this.treasuryBalance = 0;
        this.isNation = false;
    }

    public TownData(String id, String name, UUID monarchUuid, String monarchName) {
        this();
        this.id = id;
        this.name = name;
        this.monarchUuid = monarchUuid;
        this.monarchName = monarchName;
        this.members.put(monarchUuid, "Monarch");
        this.memberNames.put(monarchUuid, monarchName);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isNation() { return isNation; }
    public void setNation(boolean nation) { isNation = nation; }

    public void checkNationStatus() {
        if (warTown) {
            this.isNation = false;
            return;
        }
        this.isNation = claimedChunks.size() >= 100;
    }

    public UUID getMonarchUuid() { return monarchUuid; }
    public void setMonarchUuid(UUID monarchUuid) { this.monarchUuid = monarchUuid; }

    public String getMonarchName() { return monarchName; }
    public void setMonarchName(String monarchName) { this.monarchName = monarchName; }

    public String getMonarchTitle() { return monarchTitle; }
    public void setMonarchTitle(String monarchTitle) { this.monarchTitle = monarchTitle; }

    public Map<UUID, String> getMembers() { return members; }
    public void setMembers(Map<UUID, String> members) { this.members = members; }

    public Map<UUID, String> getMemberNames() {
        if (memberNames == null) {
            memberNames = new HashMap<>();
        }
        return memberNames;
    }

    public void setMemberNames(Map<UUID, String> memberNames) {
        this.memberNames = memberNames;
    }

    public String getMemberName(UUID memberUuid) {
        if (memberNames != null && memberNames.containsKey(memberUuid)) {
            return memberNames.get(memberUuid);
        }
        if (monarchUuid != null && monarchUuid.equals(memberUuid)) {
            return monarchName;
        }
        return "Unknown";
    }

    public void setMemberName(UUID memberUuid, String name) {
        getMemberNames().put(memberUuid, name);
    }

    public Set<ChunkPosition> getClaimedChunks() { return claimedChunks; }
    public void setClaimedChunks(Set<ChunkPosition> claimedChunks) { 
        this.claimedChunks = claimedChunks;
        checkNationStatus();
    }

    public int getTreasuryBalance() { return treasuryBalance; }
    public void setTreasuryBalance(int treasuryBalance) { this.treasuryBalance = treasuryBalance; }

    public String getEmpireId() { return empireId; }
    public void setEmpireId(String empireId) { this.empireId = empireId; }

    public boolean isTownPublic() { return townPublic; }
    public void setTownPublic(boolean townPublic) { this.townPublic = townPublic; }

    public Set<UUID> getInvitedPlayers() {
        if (invitedPlayers == null) {
            invitedPlayers = new HashSet<>();
        }
        return invitedPlayers;
    }
    public void setInvitedPlayers(Set<UUID> invitedPlayers) { this.invitedPlayers = invitedPlayers; }

    public boolean isInvited(UUID playerUuid) {
        return invitedPlayers != null && invitedPlayers.contains(playerUuid);
    }

    public Set<UUID> getLords() {
        if (lords == null) {
            lords = new HashSet<>();
        }
        return lords;
    }

    public void setLords(Set<UUID> lords) { this.lords = lords; }

    public boolean isLord(UUID playerUuid) {
        return lords != null && lords.contains(playerUuid);
    }

    public Set<String> getAllies() { return allies; }
    public void setAllies(Set<String> allies) { this.allies = allies; }

    public Set<ChunkPosition> getRestrictedChunks() {
        if (restrictedChunks == null) restrictedChunks = new HashSet<>();
        return restrictedChunks;
    }

    public void setRestrictedChunks(Set<ChunkPosition> restrictedChunks) { this.restrictedChunks = restrictedChunks; }

    public Map<UUID, Set<ChunkPosition>> getCitizenChunks() {
        if (citizenChunks == null) citizenChunks = new HashMap<>();
        return citizenChunks;
    }

    public void setCitizenChunks(Map<UUID, Set<ChunkPosition>> citizenChunks) { this.citizenChunks = citizenChunks; }

    /**
     * @return mutable map of citizen UUID -> chunks owned by that citizen
     *         via the Give Citizen Land banner. Never {@code null}.
     */
    public Map<UUID, Set<ChunkPosition>> getCitizenOwnedChunks() {
        if (citizenOwnedChunks == null) citizenOwnedChunks = new HashMap<>();
        return citizenOwnedChunks;
    }

    public void setCitizenOwnedChunks(Map<UUID, Set<ChunkPosition>> citizenOwnedChunks) {
        this.citizenOwnedChunks = citizenOwnedChunks;
    }

    /**
     * @return true when {@code playerUuid} has been granted autonomy over
     *         {@code chunk} via the Give Citizen Land banner.
     */
    public boolean isCitizenChunkOwner(UUID playerUuid, ChunkPosition chunk) {
        if (citizenOwnedChunks == null || playerUuid == null || chunk == null) return false;
        Set<ChunkPosition> chunks = citizenOwnedChunks.get(playerUuid);
        return chunks != null && chunks.contains(chunk);
    }

    public Set<String> getEnemies() { return enemies; }
    public void setEnemies(Set<String> enemies) { this.enemies = enemies; }

    public long getFoundedTimestamp() { return foundedTimestamp; }
    public void setFoundedTimestamp(long foundedTimestamp) { this.foundedTimestamp = foundedTimestamp; }

    public int getChunkCount() { return claimedChunks.size(); }

    public List<String> getBannerPatterns() { return bannerPatterns; }
    public void setBannerPatterns(List<String> bannerPatterns) { this.bannerPatterns = bannerPatterns; }

    public String getBannerBaseColor() { return bannerBaseColor; }
    public void setBannerBaseColor(String bannerBaseColor) { this.bannerBaseColor = bannerBaseColor; }

    public String getBannerPixels() { return bannerPixels; }
    public void setBannerPixels(String bannerPixels) { this.bannerPixels = bannerPixels; }

    public List<String> getSavedBannerPatterns() {
        if (savedBannerPatterns == null) {
            savedBannerPatterns = new ArrayList<>();
        }
        return savedBannerPatterns;
    }

    public void setSavedBannerPatterns(List<String> savedBannerPatterns) {
        this.savedBannerPatterns = savedBannerPatterns != null
                ? savedBannerPatterns
                : new ArrayList<>();
    }

    public String getSavedBannerBaseColor() { return savedBannerBaseColor; }
    public void setSavedBannerBaseColor(String savedBannerBaseColor) {
        this.savedBannerBaseColor = savedBannerBaseColor;
    }

    public String getSavedBannerPixels() { return savedBannerPixels; }
    public void setSavedBannerPixels(String savedBannerPixels) {
        this.savedBannerPixels = savedBannerPixels;
    }

    /** True when a town-own flag is stored for restore after leaving an empire. */
    public boolean hasSavedOwnBanner() {
        return savedBannerPixels != null && !savedBannerPixels.isEmpty()
                || savedBannerBaseColor != null && !savedBannerBaseColor.isEmpty()
                || (savedBannerPatterns != null && !savedBannerPatterns.isEmpty());
    }

    public void clearSavedOwnBanner() {
        savedBannerPatterns = new ArrayList<>();
        savedBannerBaseColor = null;
        savedBannerPixels = null;
    }

    public boolean isWarTown() { return warTown; }
    public void setWarTown(boolean warTown) { this.warTown = warTown; }

    public String getCapturedFromTownId() { return capturedFromTownId; }
    public void setCapturedFromTownId(String capturedFromTownId) { this.capturedFromTownId = capturedFromTownId; }
}