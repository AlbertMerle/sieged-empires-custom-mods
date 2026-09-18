package com.siegedempires.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.siegedempires.Siegedempires;
import com.siegedempires.model.DiplomacyRecord;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DataStorage {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    
    private static final String CONFIG_FOLDER = "config/SiegedEmpires";
    private static final String TOWNS_FOLDER = CONFIG_FOLDER + "/TownsNations";
    private static final String EMPIRES_FOLDER = CONFIG_FOLDER + "/Empires";
    private static final String DIPLOMACY_FOLDER = CONFIG_FOLDER + "/Diplomacy";
    
    public static void initialize() {
        try {
            Files.createDirectories(Paths.get(TOWNS_FOLDER));
            Files.createDirectories(Paths.get(EMPIRES_FOLDER));
            Files.createDirectories(Paths.get(DIPLOMACY_FOLDER));
            Siegedempires.LOGGER.info("Created SiegedEmpires config folders");
        } catch (IOException e) {
            Siegedempires.LOGGER.error("Failed to create config folders", e);
        }
    }
    
    public static File getTownsFolder() {
        return new File(TOWNS_FOLDER);
    }
    
    public static void saveTown(TownData town) {
        if (town == null || town.getId() == null) return;
        
        File file = new File(TOWNS_FOLDER, town.getId() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(town, writer);
            Siegedempires.LOGGER.info("Saved town: " + town.getName());
        } catch (IOException e) {
            Siegedempires.LOGGER.error("Failed to save town: " + town.getName(), e);
        }
    }
    
    public static TownData loadTown(String townId) {
        File file = new File(TOWNS_FOLDER, townId + ".json");
        if (!file.exists()) return null;
        
        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, TownData.class);
        } catch (IOException e) {
            Siegedempires.LOGGER.error("Failed to load town: " + townId, e);
            return null;
        }
    }
    
    public static boolean townExists(String townId) {
        return new File(TOWNS_FOLDER, townId + ".json").exists();
    }

    public static boolean deleteTown(String townId) {
        File file = new File(TOWNS_FOLDER, townId + ".json");
        if (!file.exists()) {
            return false;
        }

        if (file.delete()) {
            Siegedempires.LOGGER.info("Deleted town file: " + townId);
            return true;
        }

        Siegedempires.LOGGER.error("Failed to delete town file: " + townId);
        return false;
    }
    
    public static String[] getAllTownIds() {
        File folder = getTownsFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return new String[0];
        
        String[] ids = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            ids[i] = name.substring(0, name.length() - 5);
        }
        return ids;
    }
    
    public static Path getConfigPath() {
        return Paths.get(CONFIG_FOLDER);
    }

    public static File getEmpiresFolder() {
        return new File(EMPIRES_FOLDER);
    }

    public static void saveEmpire(EmpireData empire) {
        if (empire == null || empire.getId() == null) {
            return;
        }

        File file = new File(EMPIRES_FOLDER, empire.getId() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(empire, writer);
            Siegedempires.LOGGER.info("Saved empire: " + empire.getName());
        } catch (IOException e) {
            Siegedempires.LOGGER.error("Failed to save empire: " + empire.getName(), e);
        }
    }

    public static EmpireData loadEmpire(String empireId) {
        File file = new File(EMPIRES_FOLDER, empireId + ".json");
        if (!file.exists()) {
            return null;
        }

        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, EmpireData.class);
        } catch (IOException e) {
            Siegedempires.LOGGER.error("Failed to load empire: " + empireId, e);
            return null;
        }
    }

    public static String[] getAllEmpireIds() {
        File folder = getEmpiresFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return new String[0];
        }

        String[] ids = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            ids[i] = name.substring(0, name.length() - 5);
        }
        return ids;
    }

    public static boolean empireExists(String empireId) {
        return new File(EMPIRES_FOLDER, empireId + ".json").exists();
    }

    public static boolean deleteEmpire(String empireId) {
        File file = new File(EMPIRES_FOLDER, empireId + ".json");
        if (!file.exists()) {
            return false;
        }

        if (file.delete()) {
            Siegedempires.LOGGER.info("Deleted empire file: " + empireId);
            return true;
        }

        Siegedempires.LOGGER.error("Failed to delete empire file: " + empireId);
        return false;
    }

    public static File getDiplomacyFolder() {
        return new File(DIPLOMACY_FOLDER);
    }

    public static void saveDiplomacy(DiplomacyRecord record) {
        if (record == null || record.getEntityType() == null || record.getEntityId() == null) {
            return;
        }

        File file = new File(DIPLOMACY_FOLDER, record.getEntityType() + "_" + record.getEntityId() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(record, writer);
        } catch (IOException e) {
            Siegedempires.LOGGER.error("Failed to save diplomacy: " + record.getEntityId(), e);
        }
    }

    public static DiplomacyRecord loadDiplomacy(String fileKey) {
        File file = new File(DIPLOMACY_FOLDER, fileKey + ".json");
        if (!file.exists()) {
            return null;
        }

        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, DiplomacyRecord.class);
        } catch (IOException e) {
            Siegedempires.LOGGER.error("Failed to load diplomacy: " + fileKey, e);
            return null;
        }
    }

    public static String[] getAllDiplomacyKeys() {
        File folder = getDiplomacyFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return new String[0];
        }

        String[] keys = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            keys[i] = name.substring(0, name.length() - 5);
        }
        return keys;
    }

    public static boolean deleteDiplomacy(String entityType, String entityId) {
        if (entityType == null || entityId == null) {
            return false;
        }
        File file = new File(DIPLOMACY_FOLDER, entityType + "_" + entityId + ".json");
        if (!file.exists()) {
            return false;
        }
        if (file.delete()) {
            Siegedempires.LOGGER.info("Deleted diplomacy file: " + entityType + "_" + entityId);
            return true;
        }
        Siegedempires.LOGGER.error("Failed to delete diplomacy file: " + entityType + "_" + entityId);
        return false;
    }
}