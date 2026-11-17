package com.cosmiclogic.deathbreakdown.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Properties;

/**
 * deathbreakdown.properties: one small, commented file that works the same on every loader and version.
 * Missing or broken values fall back to the defaults, and new keys are written back so the file stays complete.
 */
public final class RecapConfig {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    // Tracking (server side)
    public int windowSeconds = 10;
    // What's shown
    public boolean showSource = true;
    public boolean showType = true;
    public boolean showDamage = true;
    public boolean showArmor = true;
    public boolean showEnchants = true;
    public boolean showOther = true;      // Resistance, absorption hearts and shields
    public boolean showHealthLeft = true;
    public boolean showTime = true;
    public boolean hearts = true;         // show damage in hearts (true) or health points (false)
    // When
    public boolean openOnDeath = true;    // open the recap screen automatically on the death screen
    public boolean chatSummary = true;    // one-line summary in chat after death
    // Creator's server (see CreatorServer): added once to the Multiplayer list across all CosmicLogic mods
    public boolean addCreatorServer = true;

    public long windowMs() { return windowSeconds * 1000L; }

    public static RecapConfig load(File file) {
        RecapConfig c = new RecapConfig();
        Properties p = new Properties();
        if (file.isFile()) {
            Reader r = null;
            try {
                r = new InputStreamReader(new FileInputStream(file), UTF8);
                p.load(r);
            } catch (IOException ignored) {
            } finally {
                if (r != null) try { r.close(); } catch (IOException ignored) {}
            }
        }
        c.windowSeconds = clampInt(p.getProperty("windowSeconds"), c.windowSeconds, 1, 60);
        c.showSource = bool(p.getProperty("showSource"), c.showSource);
        c.showType = bool(p.getProperty("showType"), c.showType);
        c.showDamage = bool(p.getProperty("showDamage"), c.showDamage);
        c.showArmor = bool(p.getProperty("showArmor"), c.showArmor);
        c.showEnchants = bool(p.getProperty("showEnchants"), c.showEnchants);
        c.showOther = bool(p.getProperty("showOther"), c.showOther);
        c.showHealthLeft = bool(p.getProperty("showHealthLeft"), c.showHealthLeft);
        c.showTime = bool(p.getProperty("showTime"), c.showTime);
        c.hearts = bool(p.getProperty("hearts"), c.hearts);
        c.openOnDeath = bool(p.getProperty("openOnDeath"), c.openOnDeath);
        c.chatSummary = bool(p.getProperty("chatSummary"), c.chatSummary);
        c.addCreatorServer = bool(p.getProperty("addCreatorServer"), c.addCreatorServer);
        try { c.save(file); } catch (IOException ignored) {}
        return c;
    }

    public void save(File file) throws IOException {
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Can't create " + dir);
        Writer w = new OutputStreamWriter(new FileOutputStream(file), UTF8);
        try {
            w.write("# Death Breakdown settings\n");
            w.write("# How many seconds before death to include (1-60)\n");
            w.write("windowSeconds=" + windowSeconds + "\n\n");
            w.write("# Columns in the recap (true/false)\n");
            w.write("showTime=" + showTime + "\n");
            w.write("showSource=" + showSource + "\n");
            w.write("showType=" + showType + "\n");
            w.write("showDamage=" + showDamage + "\n");
            w.write("showArmor=" + showArmor + "\n");
            w.write("showEnchants=" + showEnchants + "\n");
            w.write("# Resistance, absorption hearts and shields\n");
            w.write("showOther=" + showOther + "\n");
            w.write("showHealthLeft=" + showHealthLeft + "\n\n");
            w.write("# Show damage in hearts (true) or health points (false)\n");
            w.write("hearts=" + hearts + "\n\n");
            w.write("# Open the recap automatically when you die\n");
            w.write("openOnDeath=" + openOnDeath + "\n");
            w.write("# Post a one-line summary in chat when you die\n");
            w.write("chatSummary=" + chatSummary + "\n\n");
            w.write("# Add the creator's server (Logic Survival) to the end of your Multiplayer list. This happens only once\n");
            w.write("# across all CosmicLogic mods and never again after you delete it. Set to false to turn it off.\n");
            w.write("addCreatorServer=" + addCreatorServer + "\n");
        } finally {
            w.close();
        }
    }

    static boolean bool(String v, boolean def) {
        if (v == null) return def;
        v = v.trim();
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        return def;
    }

    static int clampInt(String v, int def, int lo, int hi) {
        if (v == null) return def;
        try {
            int i = Integer.parseInt(v.trim());
            return Math.max(lo, Math.min(hi, i));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
