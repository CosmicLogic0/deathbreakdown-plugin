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
import java.util.ArrayList;
import java.util.List;
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
    // Added in 1.1.0 (old files get these defaults written in)
    public boolean showTip = true;        // one short tip under the summary ("Lava did 89% of the damage.")
    public int historySize = 5;           // recaps kept per player (1-20), browse with < > in the screen
    public boolean saveHistory = true;    // keep them in the world folder so they survive restarts

    /** What was wrong with the file when it was loaded (empty if nothing). Shown in the log and by /deathbreakdown reload. */
    public final List<String> problems = new ArrayList<String>();

    public long windowMs() { return windowSeconds * 1000L; }

    public static RecapConfig load(File file) {
        RecapConfig c = new RecapConfig();
        Properties p = new Properties();
        if (file.isDirectory()) {
            c.problems.add(file.getName() + " is a folder, not a file: using the defaults");
            return c;
        }
        if (file.isFile()) {
            Reader r = null;
            try {
                r = new InputStreamReader(new FileInputStream(file), UTF8);
                p.load(r);
            } catch (IOException e) {
                c.problems.add("couldn't read " + file.getName() + " (" + e.getMessage() + "): using the defaults");
            } catch (IllegalArgumentException e) {
                c.problems.add(file.getName() + " has a broken line (" + e.getMessage() + "): using the defaults for it");
            } finally {
                if (r != null) try { r.close(); } catch (IOException ignored) {}
            }
        }
        c.windowSeconds = c.clampInt(p, "windowSeconds", c.windowSeconds, 1, 60);
        c.showSource = c.bool(p, "showSource", c.showSource);
        c.showType = c.bool(p, "showType", c.showType);
        c.showDamage = c.bool(p, "showDamage", c.showDamage);
        c.showArmor = c.bool(p, "showArmor", c.showArmor);
        c.showEnchants = c.bool(p, "showEnchants", c.showEnchants);
        c.showOther = c.bool(p, "showOther", c.showOther);
        c.showHealthLeft = c.bool(p, "showHealthLeft", c.showHealthLeft);
        c.showTime = c.bool(p, "showTime", c.showTime);
        c.hearts = c.bool(p, "hearts", c.hearts);
        c.openOnDeath = c.bool(p, "openOnDeath", c.openOnDeath);
        c.chatSummary = c.bool(p, "chatSummary", c.chatSummary);
        c.showTip = c.bool(p, "showTip", c.showTip);
        c.historySize = c.clampInt(p, "historySize", c.historySize, 1, 20);
        c.saveHistory = c.bool(p, "saveHistory", c.saveHistory);
        if (!c.showTime && !c.showSource && !c.showType && !c.showDamage && !c.showArmor && !c.showEnchants
                && !c.showOther && !c.showHealthLeft) {
            c.problems.add("every column was turned off: showing \"Hit by\" so the recap isn't empty");
            c.showSource = true;
        }
        try { c.save(file); } catch (IOException e) { c.problems.add("couldn't save " + file.getName() + ": " + e.getMessage()); }
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
            w.write("chatSummary=" + chatSummary + "\n");
            w.write("# Show one short tip under the summary, e.g. \"Lava did 89% of the damage.\"\n");
            w.write("showTip=" + showTip + "\n\n");
            w.write("# How many of your last deaths to keep (1-20). Browse them with < and > in the recap screen.\n");
            w.write("historySize=" + historySize + "\n");
            w.write("# Keep them in the world folder so they survive leaving and restarting\n");
            w.write("saveHistory=" + saveHistory + "\n");
        } finally {
            w.close();
        }
    }

    static boolean bool(String v, boolean def) {
        if (v == null) return def;
        v = v.trim();
        if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || v.equals("1") || v.equalsIgnoreCase("on")) return true;
        if (v.equalsIgnoreCase("false") || v.equalsIgnoreCase("no") || v.equals("0") || v.equalsIgnoreCase("off")) return false;
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

    private boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        boolean out = bool(v, def);
        if (v != null && !isBoolWord(v)) {
            problems.add(key + "=" + v.trim() + " isn't true or false: using " + def);
        }
        return out;
    }

    private static boolean isBoolWord(String v) {
        v = v.trim().toLowerCase(java.util.Locale.ROOT);
        return v.equals("true") || v.equals("false") || v.equals("yes") || v.equals("no") || v.equals("1") || v.equals("0")
                || v.equals("on") || v.equals("off");
    }

    private int clampInt(Properties p, String key, int def, int lo, int hi) {
        String v = p.getProperty(key);
        int out = clampInt(v, def, lo, hi);
        if (v != null) {
            try {
                int i = Integer.parseInt(v.trim());
                if (i != out) problems.add(key + "=" + i + " is outside " + lo + "-" + hi + ": using " + out);
            } catch (NumberFormatException e) {
                problems.add(key + "=" + v.trim() + " isn't a number: using " + def);
            }
        }
        return out;
    }
}
