package com.cosmiclogic.deathbreakdown.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Saves each player's recent recaps in the world folder (deathbreakdown/&lt;uuid&gt;.txt), so the history survives
 * leaving, reloading the world and server restarts. Plain text, a few KB per player at most. Never throws: a broken
 * or missing file just means "no saved recaps".
 */
public final class RecapStore {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    /** Files bigger than this are ignored (someone pasted junk into the folder). */
    public static final int MAX_FILE_BYTES = 256 * 1024;

    private RecapStore() {}

    /** Joins recaps (oldest first) into one text. */
    public static String encodeAll(List<Recap> recaps) {
        StringBuilder b = new StringBuilder();
        for (Recap r : recaps) b.append(RecapCodec.encodeReplay(r));
        return b.toString();
    }

    /** Splits a text made by {@link #encodeAll} back into recaps; bad parts are skipped. */
    public static List<Recap> decodeAll(String s) {
        List<Recap> out = new ArrayList<Recap>();
        if (s == null || s.isEmpty()) return out;
        String[] lines = s.split("\n", -1);
        StringBuilder cur = null;
        for (String line : lines) {
            if (RecapCodec.HEADER.equals(line)) {
                add(out, cur);
                cur = new StringBuilder(line).append('\n');
            } else if (cur != null) {
                cur.append(line).append('\n');
            }
        }
        add(out, cur);
        return out;
    }

    private static void add(List<Recap> out, StringBuilder part) {
        if (part == null) return;
        Recap r = RecapCodec.decode(part.toString());
        if (r != null) out.add(r);
    }

    public static File fileFor(File dir, UUID player) {
        return new File(dir, player.toString() + ".txt");
    }

    public static List<Recap> load(File dir, UUID player) {
        File f = fileFor(dir, player);
        if (!f.isFile() || f.length() > MAX_FILE_BYTES) return new ArrayList<Recap>();
        InputStream in = null;
        try {
            in = new FileInputStream(f);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            return decodeAll(new String(buf.toByteArray(), UTF8));
        } catch (IOException e) {
            return new ArrayList<Recap>();
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
        }
    }

    /** Writes to a temp file first, then renames, so a crash mid-write can't leave a half file. */
    public static boolean save(File dir, UUID player, List<Recap> recaps) {
        OutputStream out = null;
        File f = fileFor(dir, player);
        File tmp = new File(dir, player.toString() + ".tmp");
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            out = new FileOutputStream(tmp);
            out.write(encodeAll(recaps).getBytes(UTF8));
            out.close();
            out = null;
            if (f.exists() && !f.delete()) return false;
            return tmp.renameTo(f);
        } catch (IOException e) {
            return false;
        } finally {
            if (out != null) try { out.close(); } catch (IOException ignored) {}
        }
    }
}
