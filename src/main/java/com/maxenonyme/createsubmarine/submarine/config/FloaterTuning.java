package com.maxenonyme.createsubmarine.submarine.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FloaterTuning {
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("create_submarine_floater.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile double liftPerBlock = 0.2;
    private static volatile double verticalDrag = 0.1;
    private static volatile double horizontalDrag = 0.05;
    private static volatile double surfaceTaperDistance = 1.5;

    private static long lastMtime = -1;
    private static long lastCheckNanos = 0;
    private static final long CHECK_INTERVAL_NS = 5_000_000_000L;

    public static double liftPerBlock() { tryReload(); return liftPerBlock; }
    public static double verticalDrag() { tryReload(); return verticalDrag; }
    public static double horizontalDrag() { tryReload(); return horizontalDrag; }
    public static double surfaceTaperDistance() { tryReload(); return surfaceTaperDistance; }

    private static void tryReload() {
        long now = System.nanoTime();
        if (lastCheckNanos != 0 && now - lastCheckNanos < CHECK_INTERVAL_NS) return;
        lastCheckNanos = now;
        try {
            if (!Files.exists(PATH)) {
                writeDefaults();
                lastMtime = Files.getLastModifiedTime(PATH).toMillis();
                return;
            }
            long mtime = Files.getLastModifiedTime(PATH).toMillis();
            if (mtime == lastMtime) return;

            JsonObject json = JsonParser.parseString(Files.readString(PATH)).getAsJsonObject();
            if (json.has("lift_per_block")) liftPerBlock = json.get("lift_per_block").getAsDouble();
            if (json.has("vertical_drag")) verticalDrag = json.get("vertical_drag").getAsDouble();
            if (json.has("horizontal_drag")) horizontalDrag = json.get("horizontal_drag").getAsDouble();
            if (json.has("surface_taper_distance")) surfaceTaperDistance = json.get("surface_taper_distance").getAsDouble();
            lastMtime = mtime;
        } catch (Exception ignored) {}
    }

    private static void writeDefaults() throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("_comment", "Hot-reloaded on save. Per-floater per-tick contribution. lift_per_block = upward velocity delta contributed when fully submerged (m/s per tick). vertical_drag = vy resistance coef. horizontal_drag = horizontal resistance coef. surface_taper_distance = how many blocks above/below sea level the lift fades (half lift exactly at sea level).");
        json.addProperty("lift_per_block", liftPerBlock);
        json.addProperty("vertical_drag", verticalDrag);
        json.addProperty("horizontal_drag", horizontalDrag);
        json.addProperty("surface_taper_distance", surfaceTaperDistance);
        Files.createDirectories(PATH.getParent());
        Files.writeString(PATH, GSON.toJson(json));
    }
}
