package dev.autobridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Stores a bounded history of completed singleplayer/server play sessions. */
public final class SessionHistory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("auto-bridge-sessions.json");
    private static final Type LIST_TYPE = new TypeToken<List<Entry>>() {}.getType();
    private static final int MAX_ENTRIES = 20;
    private static boolean connected;
    private static long startedAt;
    private static long startAttempts, startAccepted, startRejected, startEdges;

    private SessionHistory() {}

    public static void tick(MinecraftClient client) {
        boolean nowConnected = client.player != null && client.world != null;
        if (nowConnected && !connected) {
            connected = true;
            startedAt = System.currentTimeMillis();
            startAttempts = BridgeController.getSessionPlacementAttempts();
            startAccepted = BridgeController.getSessionPlacementAccepted();
            startRejected = BridgeController.getSessionPlacementRejected();
            startEdges = BridgeController.getSessionEdgeWarnings();
        } else if (!nowConnected && connected) {
            saveCurrentSession();
            connected = false;
        }
    }

    public static void saveCurrentSession() {
        if (!connected) return;
        long attempts = Math.max(0, BridgeController.getSessionPlacementAttempts() - startAttempts);
        long accepted = Math.max(0, BridgeController.getSessionPlacementAccepted() - startAccepted);
        long rejected = Math.max(0, BridgeController.getSessionPlacementRejected() - startRejected);
        long edges = Math.max(0, BridgeController.getSessionEdgeWarnings() - startEdges);
        // Ignore sessions where the mod recorded no activity.
        if (attempts == 0 && rejected == 0 && edges == 0) {
            connected = false;
            return;
        }
        List<Entry> entries = load();
        entries.add(0, new Entry(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(startedAt)),
                Math.max(0, (System.currentTimeMillis() - startedAt) / 1000),
                attempts, accepted, rejected, edges));
        if (entries.size() > MAX_ENTRIES) entries = new ArrayList<>(entries.subList(0, MAX_ENTRIES));
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(entries));
        } catch (IOException exception) {
            System.err.println("[Auto-Bridge] Could not save session history: " + exception.getMessage());
        }
        connected = false;
    }

    public static List<Entry> getEntries() {
        return List.copyOf(load());
    }

    private static List<Entry> load() {
        try {
            if (!Files.exists(FILE)) return new ArrayList<>();
            List<Entry> entries = GSON.fromJson(Files.readString(FILE), LIST_TYPE);
            return entries == null ? new ArrayList<>() : new ArrayList<>(entries);
        } catch (IOException | RuntimeException exception) {
            System.err.println("[Auto-Bridge] Could not read session history: " + exception.getMessage());
            return new ArrayList<>();
        }
    }

    public static final class Entry {
        final String date;
        final long durationSeconds;
        final long attempts;
        final long accepted;
        final long rejected;
        final long edgeWarnings;

        Entry(String date, long durationSeconds, long attempts, long accepted, long rejected, long edgeWarnings) {
            this.date = date;
            this.durationSeconds = durationSeconds;
            this.attempts = attempts;
            this.accepted = accepted;
            this.rejected = rejected;
            this.edgeWarnings = edgeWarnings;
        }

        public String getDate() { return date; }
        public long getDurationSeconds() { return durationSeconds; }
        public long getAttempts() { return attempts; }
        public long getAccepted() { return accepted; }
        public long getRejected() { return rejected; }
        public long getEdgeWarnings() { return edgeWarnings; }
        public long getSuccessPercent() { return attempts == 0 ? 0 : Math.round(accepted * 100.0 / attempts); }
    }
}
