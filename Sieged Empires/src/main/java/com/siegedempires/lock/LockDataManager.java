package com.siegedempires.lock;

import com.siegedempires.Siegedempires;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class LockDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("config","SiegedEmpires","locks.json");
    private static final Map<String,Map<String,LockEntry>> locks = new ConcurrentHashMap<>();

    private record LockEntry(String password, String placedBy) {}
    private static String k(BlockPos p) { return p.getX()+","+p.getY()+","+p.getZ(); }

    public static void lock(ServerLevel lvl, BlockPos pos, String pw, String by) {
        locks.computeIfAbsent(d(lvl),d->new ConcurrentHashMap<>()).put(k(pos),new LockEntry(pw,by)); save(); }
    public static void unlock(ServerLevel lvl, BlockPos pos) {
        var m = locks.get(d(lvl)); if(m!=null) { m.remove(k(pos)); save(); } }
    public static boolean isLocked(ServerLevel lvl, BlockPos pos) {
        var m = locks.get(d(lvl)); return m!=null && m.containsKey(k(pos)); }
    public static String getPassword(ServerLevel lvl, BlockPos pos) {
        var m = locks.get(d(lvl)); if(m==null) return null; var e=m.get(k(pos)); return e!=null?e.password:null; }
    public static boolean checkKey(ServerLevel lvl, BlockPos pos, String kpw) {
        String s = getPassword(lvl, pos); return s!=null && s.equals(kpw); }
    public static boolean isLockable(String blockId) {
        return blockId.contains("chest")||blockId.contains("barrel")||blockId.contains("door")||blockId.contains("trapdoor")||blockId.contains("fence_gate"); }

    /** Returns all locked positions grouped by dimension, for network sync. */
    public static Map<String, List<String>> getAllLockedPositions() {
        Map<String, List<String>> result = new HashMap<>();
        for (var dimEntry : locks.entrySet()) {
            result.put(dimEntry.getKey(), new ArrayList<>(dimEntry.getValue().keySet()));
        }
        return result;
    }

    /** Returns locked positions for a specific level, for broadcast sync. */
    public static Map<String, List<String>> getAllLockedPositions(ServerLevel level) {
        Map<String, List<String>> result = new HashMap<>();
        String dim = d(level);
        var m = locks.get(dim);
        if (m != null) {
            result.put(dim, new ArrayList<>(m.keySet()));
        }
        return result;
    }

    private static String d(ServerLevel l) { return l.dimension().identifier().toString(); }
public static void load() {
        if(!Files.exists(FILE)) return;
        try(Reader r=Files.newBufferedReader(FILE)){
            java.lang.reflect.Type type = new TypeToken<Map<String,Map<String,LockEntry>>>(){}.getType();
            Map<String,Map<String,LockEntry>> loaded = GSON.fromJson(r, type);
            if(loaded!=null){locks.clear();locks.putAll(loaded);}
        }catch(IOException e){Siegedempires.LOGGER.error("Lock load fail",e);}
    }
    public static void save(){
        try{Files.createDirectories(FILE.getParent());Writer w=Files.newBufferedWriter(FILE);GSON.toJson(locks,w);w.close();}
        catch(IOException e){Siegedempires.LOGGER.error("Lock save fail",e);}
    }
}