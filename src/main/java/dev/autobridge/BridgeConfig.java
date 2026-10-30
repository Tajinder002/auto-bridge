package dev.autobridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BridgeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("auto-bridge.json");

    public static boolean bridgeAssist = false;
    public static boolean aimAssist = false;
    public static boolean silentAimPractice = false;
    public static boolean movementAssist = false;
    public static boolean edgeWarning = true;
    public static boolean showHud = true;
    public static boolean persistentStats = true;
    public static boolean learningEnabled = true;
    public static long learningAccepted;
    public static long learningRejected;
    public static double learnedDelayMultiplier = 1.0;
    public static String bridgeMode = "NORMAL";
    public static double aimStrength = 0.35;
    public static double bridgeDelayTicks = 2.0;
    public static double aimRange = 5.0;
    public static double aimAngle = 28.0;
    public static long totalEdgeWarnings;
    public static long totalPlacementAttempts;
    public static long totalPlacementAccepted;
    public static long totalPlacementRejected;

    private BridgeConfig() {}

    public static void load() {
        if (!Files.exists(FILE)) { save(); return; }
        try {
            Data data = GSON.fromJson(Files.readString(FILE), Data.class);
            if (data == null) return;
            bridgeAssist = data.bridgeAssist;
            aimAssist = data.aimAssist;
            silentAimPractice = data.silentAimPractice;
            movementAssist = data.movementAssist;
            edgeWarning = data.edgeWarning;
            showHud = data.showHud;
            persistentStats = data.persistentStats;
            learningEnabled = data.learningEnabled;
            learningAccepted = Math.max(0, data.learningAccepted);
            learningRejected = Math.max(0, data.learningRejected);
            learnedDelayMultiplier = clamp(data.learnedDelayMultiplier, 0.65, 1.8);
            bridgeMode = normalizeMode(data.bridgeMode);
            aimStrength = clamp(data.aimStrength, 0.0, 1.0);
            bridgeDelayTicks = clamp(data.bridgeDelayTicks, 1.0, 10.0);
            aimRange = clamp(data.aimRange, 2.0, 8.0);
            aimAngle = clamp(data.aimAngle, 10.0, 45.0);
            totalEdgeWarnings = Math.max(0, data.totalEdgeWarnings);
            totalPlacementAttempts = Math.max(0, data.totalPlacementAttempts);
            totalPlacementAccepted = Math.max(0, data.totalPlacementAccepted);
            totalPlacementRejected = Math.max(0, data.totalPlacementRejected);
        } catch (IOException | RuntimeException exception) {
            System.err.println("[Auto-Bridge] Could not read config: " + exception.getMessage());
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Data data = new Data();
            data.bridgeAssist = bridgeAssist;
            data.aimAssist = aimAssist;
            data.silentAimPractice = silentAimPractice;
            data.movementAssist = movementAssist;
            data.edgeWarning = edgeWarning;
            data.showHud = showHud;
            data.persistentStats = persistentStats;
            data.learningEnabled = learningEnabled;
            data.learningAccepted = learningAccepted;
            data.learningRejected = learningRejected;
            data.learnedDelayMultiplier = clamp(learnedDelayMultiplier, 0.65, 1.8);
            data.bridgeMode = normalizeMode(bridgeMode);
            data.aimStrength = clamp(aimStrength, 0.0, 1.0);
            data.bridgeDelayTicks = clamp(bridgeDelayTicks, 1.0, 10.0);
            data.aimRange = clamp(aimRange, 2.0, 8.0);
            data.aimAngle = clamp(aimAngle, 10.0, 45.0);
            data.totalEdgeWarnings = totalEdgeWarnings;
            data.totalPlacementAttempts = totalPlacementAttempts;
            data.totalPlacementAccepted = totalPlacementAccepted;
            data.totalPlacementRejected = totalPlacementRejected;
            Files.writeString(FILE, GSON.toJson(data));
        } catch (IOException exception) {
            System.err.println("[Auto-Bridge] Could not save config: " + exception.getMessage());
        }
    }

    public static String normalizeMode(String mode) {
        if (mode == null) return "NORMAL";
        return switch (mode.toUpperCase(java.util.Locale.ROOT)) {
            case "SAFE", "FAST", "PRECISE", "AGGRESSIVE" -> mode.toUpperCase(java.util.Locale.ROOT);
            default -> "NORMAL";
        };
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static final class Data {
        boolean bridgeAssist;
        boolean aimAssist;
        boolean silentAimPractice;
        boolean movementAssist;
        boolean edgeWarning = true;
        boolean showHud = true;
        boolean persistentStats = true;
        boolean learningEnabled = true;
        long learningAccepted;
        long learningRejected;
        double learnedDelayMultiplier = 1.0;
        String bridgeMode = "NORMAL";
        double aimStrength = 0.35;
        double bridgeDelayTicks = 2.0;
        double aimRange = 5.0;
        double aimAngle = 28.0;
        long totalEdgeWarnings;
        long totalPlacementAttempts;
        long totalPlacementAccepted;
        long totalPlacementRejected;
    }
}
