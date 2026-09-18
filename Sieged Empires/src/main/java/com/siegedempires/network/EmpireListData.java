package com.siegedempires.network;

import java.util.ArrayList;
import java.util.List;

public class EmpireListData {
    public List<EmpireInfo> empires = new ArrayList<>();

    public static class EmpireInfo {
        public String id;
        public String name;
        public String description;
        public String emperorName;
        public String emperorTitle;
        public String capitalTownId;
        public int memberCount;
        public List<String> bannerPatterns;
        public String bannerBaseColor;
        public String bannerPixels;
        public boolean invited;
        public boolean empirePublic;
    }
}