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
    public static boolean movementAssist = false;
    public static boolean edgeWarning = true;
    public static boolean showHud = true;
    public static double aimStrength = 0.35;
    public static double bridgeDelayTicks = 2.0;

    private BridgeConfig() {}

    public static void load() {
        if (!Files.exists(FILE)) { save(); return; }
        try {
            Data data = GSON.fromJson(Files.readString(FILE), Data.class);
            if (data == null) return;
            bridgeAssist = data.bridgeAssist;
            aimAssist = data.aimAssist;
            movementAssist = data.movementAssist;
            edgeWarning = data.edgeWarning;
            showHud = data.showHud;
            aimStrength = clamp(data.aimStrength, 0.0, 1.0);
            bridgeDelayTicks = clamp(data.bridgeDelayTicks, 1.0, 10.0);
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
            data.movementAssist = movementAssist;
            data.edgeWarning = edgeWarning;
            data.showHud = showHud;
            data.aimStrength = aimStrength;
            data.bridgeDelayTicks = bridgeDelayTicks;
            Files.writeString(FILE, GSON.toJson(data));
        } catch (IOException exception) {
            System.err.println("[Auto-Bridge] Could not save config: " + exception.getMessage());
        }
    }

    private static double clamp(double value, double min, double max) {
        // JSON can contain NaN or Infinity; never let invalid values leak into
        // sliders or placement timing.
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static final class Data {
        boolean bridgeAssist;
        boolean aimAssist;
        boolean movementAssist;
        boolean edgeWarning = true;
        boolean showHud = true;
        double aimStrength = 0.35;
        double bridgeDelayTicks = 2.0;
    }
}
