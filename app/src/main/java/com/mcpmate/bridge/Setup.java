/*
 * Copyright (C) 2026 giftiar
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.mcpmate.bridge;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;

public class Setup {

    private static final String FILE = "setup.done";
    private static final int VERSION = 2;

    private static File file(Context c) {
        return new File(c.getFilesDir(), FILE);
    }

    public static boolean done(Context c) {
        if (c == null) {
            return false;
        }
        try {
            File f = file(c);
            if (!f.exists() || f.length() == 0) {
                return false;
            }
            String body = read(c);
            int v = pick(body, "version=");
            if (v != VERSION) {
                return false;
            }
            String mode = pickStr(body, "mode=");
            if (mode.length() == 0) {
                return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void mark(Context c) {
        if (c == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        b.append("version=").append(VERSION).append('\n');
        b.append("mode=").append(Config.isVdMode() ? "vd" : "screen").append('\n');
        b.append("time=").append(System.currentTimeMillis()).append('\n');
        write(c, b.toString());
    }

    public static void reset(Context c) {
        try {
            File f = file(c);
            if (f.exists()) {
                f.delete();
            }
        } catch (Throwable ignored) {
        }
    }

    public static String snapshot(Context c) {
        try {
            return read(c).trim();
        } catch (Throwable t) {
            return "";
        }
    }

    private static void write(Context c, String body) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file(c));
            fos.write(body.getBytes("UTF-8"));
            fos.flush();
            fos.getFD().sync();
        } catch (Throwable ignored) {
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String read(Context c) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file(c));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return "";
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static int pick(String s, String key) {
        String v = pickStr(s, key);
        if (v.length() == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(v);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String pickStr(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) {
            return "";
        }
        int j = i + key.length();
        int k = s.indexOf('\n', j);
        if (k < 0) {
            k = s.length();
        }
        return s.substring(j, k).trim();
    }
}
