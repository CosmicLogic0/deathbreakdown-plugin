package com.cosmiclogic.deathbreakdown.core;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Logic Survival in the Multiplayer list, exactly as the hub's Server Promotion Policy says:
 * added ONCE across all CosmicLogic mods (shared marker config/cosmiclogic/creator_server.json), at the END of the
 * list, never touching the player's own servers, off with addCreatorServer=false, client only, and never crashes.
 * Plain Java (servers.dat is edited by {@link ServersDat}), so it works the same on every Minecraft version.
 */
public final class CreatorServer {
    private CreatorServer() {}

    public static final String NAME = "Logic Survival";
    public static final String IP = "play.logicsurvival.fyi";
    public static final String MARKER = "config/cosmiclogic/creator_server.json";

    /** @return what happened, for the log: "disabled", "marker", "added", "already-there" or "skipped: ..." */
    public static String run(Path gameDir, boolean enabled, String modId) {
        try {
            if (!enabled) return "disabled";
            Path marker = gameDir.resolve(MARKER);
            if (Files.exists(marker)) return "marker";
            Path list = gameDir.resolve("servers.dat");
            byte[] old = Files.exists(list) ? Files.readAllBytes(list) : null;
            String result;
            if (old != null && ServersDat.has(old, IP)) {
                result = "already-there";
            } else {
                byte[] now = ServersDat.withServer(old, NAME, IP);
                Path tmp = list.resolveSibling("servers.dat.dbtmp");
                Files.write(tmp, now);
                if (old != null) Files.write(list.resolveSibling("servers.dat_old"), old);   // the game's own backup name
                Files.move(tmp, list, StandardCopyOption.REPLACE_EXISTING);
                result = "added";
            }
            File dir = marker.getParent().toFile();
            if (!dir.isDirectory() && !dir.mkdirs()) return result + " (marker folder failed)";
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            String json = "{\"added\": true, \"by\": \"" + modId + "\", \"date\": \"" + iso.format(new Date()) + "\"}\n";
            Files.write(marker, json.getBytes(Charset.forName("UTF-8")));
            return result;
        } catch (Throwable t) {
            return "skipped: " + t;
        }
    }
}
