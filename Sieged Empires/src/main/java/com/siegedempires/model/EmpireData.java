package com.siegedempires.model;

import java.util.*;

public class EmpireData {
    private String id;
    private String name;
    private String description;
    private UUID emperorUuid;
    private String emperorName;
    private String emperorTitle; // "Emperor" or "Empress"
    private String capitalTownId;
    private List<String> memberTownIds;
    private List<String> bannerPatterns;
    private String bannerBaseColor;
    /**
     * Custom 20×40 dye-pixel flag ({@link com.siegedempires.banner.CustomBannerDesign}
     * hex encoding). When present, GUI / VoxelMapSync prefer this over patterns.
     */
    private String bannerPixels;
    private Set<String> invitedTowns;
    private boolean empirePublic;
    private Set<String> allies;
    private Set<String> enemies;
    private long foundedTimestamp;

    public EmpireData() {
        this.memberTownIds = new ArrayList<>();
        this.bannerPatterns = new ArrayList<>();
        this.invitedTowns = new HashSet<>();
        this.allies = new HashSet<>();
        this.enemies = new HashSet<>();
        this.foundedTimestamp = System.currentTimeMillis();
    }

    public EmpireData(String id, String name) {
        this();
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getEmperorUuid() { return emperorUuid; }
    public void setEmperorUuid(UUID emperorUuid) { this.emperorUuid = emperorUuid; }

    public String getEmperorName() { return emperorName; }
    public void setEmperorName(String emperorName) { this.emperorName = emperorName; }

    public String getEmperorTitle() { return emperorTitle; }
    public void setEmperorTitle(String emperorTitle) { this.emperorTitle = emperorTitle; }

    public String getCapitalTownId() { return capitalTownId; }
    public void setCapitalTownId(String capitalTownId) { this.capitalTownId = capitalTownId; }

    public List<String> getMemberTownIds() { return memberTownIds; }
    public void setMemberTownIds(List<String> memberTownIds) { this.memberTownIds = memberTownIds; }

    public List<String> getBannerPatterns() { return bannerPatterns; }
    public void setBannerPatterns(List<String> bannerPatterns) { this.bannerPatterns = bannerPatterns; }

    public String getBannerBaseColor() { return bannerBaseColor; }
    public void setBannerBaseColor(String bannerBaseColor) { this.bannerBaseColor = bannerBaseColor; }

    public String getBannerPixels() { return bannerPixels; }
    public void setBannerPixels(String bannerPixels) { this.bannerPixels = bannerPixels; }

    public Set<String> getInvitedTowns() { return invitedTowns; }
    public void setInvitedTowns(Set<String> invitedTowns) { this.invitedTowns = invitedTowns; }

    public boolean isInvited(String townId) {
        return invitedTowns != null && invitedTowns.contains(townId);
    }

    public boolean isEmpirePublic() { return empirePublic; }
    public void setEmpirePublic(boolean empirePublic) { this.empirePublic = empirePublic; }

    public Set<String> getAllies() { return allies; }
    public void setAllies(Set<String> allies) { this.allies = allies; }

    public Set<String> getEnemies() { return enemies; }
    public void setEnemies(Set<String> enemies) { this.enemies = enemies; }

    public long getFoundedTimestamp() { return foundedTimestamp; }
    public void setFoundedTimestamp(long foundedTimestamp) { this.foundedTimestamp = foundedTimestamp; }
}
