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
import android.content.SharedPreferences;

public class Config {

    private static SharedPreferences sp;

    public static void init(Context context) {
        if (sp != null) {
            return;
        }
        sp = context.getApplicationContext().getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    private static String getString(String key, String def) {
        if (sp == null) {
            return def;
        }
        String v = sp.getString(key, def);
        return v == null ? def : v;
    }

    private static void putString(String key, String value) {
        if (sp != null) {
            sp.edit().putString(key, value).apply();
        }
    }

    private static int getInt(String key, int def) {
        return sp == null ? def : sp.getInt(key, def);
    }

    private static void putInt(String key, int value) {
        if (sp != null) {
            sp.edit().putInt(key, value).apply();
        }
    }

    private static boolean getBool(String key, boolean def) {
        return sp == null ? def : sp.getBoolean(key, def);
    }

    private static void putBool(String key, boolean value) {
        if (sp != null) {
            sp.edit().putBoolean(key, value).apply();
        }
    }

    public static String randomToken() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        java.security.SecureRandom r = new java.security.SecureRandom();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            b.append(chars.charAt(r.nextInt(chars.length())));
        }
        return b.toString();
    }

    public static boolean isInfoOn() {
        return getBool("info", true);
    }

    public static void setInfoOn(boolean v) {
        putBool("info", v);
    }

    public static boolean isAgreed() {
        return getBool("agreed", false);
    }

    public static void setAgreed(boolean v) {
        putBool("agreed", v);
    }

    public static boolean isEnabled() {
        return getBool("en", false);
    }

    public static void setEnabled(boolean v) {
        putBool("en", v);
    }

    public static String getName() {
        return "MCP交互器";
    }

    public static boolean isFollowWallpaper() {
        return getBool("dyn", true);
    }

    public static void setFollowWallpaper(boolean v) {
        putBool("dyn", v);
    }

    public static String getToken() {
        return getString("tok", "");
    }

    public static void setToken(String v) {
        putString("tok", v);
    }

    public static int getPortLocal() {
        return getInt("pl", 18800);
    }

    public static void setPortLocal(int v) {
        putInt("pl", v);
    }

    public static int getPortLan() {
        return getInt("pw", 18801);
    }

    public static void setPortLan(int v) {
        putInt("pw", v);
    }

    public static boolean isLan() {
        return getBool("lan", false);
    }

    public static void setLan(boolean v) {
        putBool("lan", v);
    }

    public static boolean isPaused() {
        return getBool("paused", false);
    }

    public static void setPaused(boolean v) {
        putBool("paused", v);
    }

    public static int getSeed() {
        return getInt("seed", 0);
    }


    public static void setSeed(int v) {
        putInt("seed", v);
    }

    public static int getInterval() {
        return getInt("ivl", 300);
    }

    public static void setInterval(int v) {
        putInt("ivl", v);
    }

    public static boolean isOcrImageOn() {
        return getBool("ocrimg", true);
    }

    public static boolean isDiffOn() {
        return getBool("diff", true);
    }

    public static void setDiffOn(boolean v) {
        putBool("diff", v);
    }

    public static void setOcrImageOn(boolean v) {
        putBool("ocrimg", v);
    }

    public static boolean isVdMode() {
        return getBool("vdmode", false);
    }

    public static void setVdMode(boolean v) {
        putBool("vdpicked", true);
        putBool("vdmode", v);
    }

    public static boolean isModePicked() {
        return getBool("vdpicked", false);
    }

    public static int getVdW() {
        return getInt("vdw", 0);
    }

    public static void setVdW(int v) {
        putInt("vdw", v);
    }

    public static int getVdH() {
        return getInt("vdh", 0);
    }

    public static void setVdH(int v) {
        putInt("vdh", v);
    }

    public static int getVdOrient() {
        return getInt("vdorient", 0);
    }

    public static void setVdOrient(int v) {
        putInt("vdorient", v > 0 ? 1 : 0);
    }

    public static int getVdDpi() {
        return getInt("vddpi", 0);
    }

    public static void setVdDpi(int v) {
        putInt("vddpi", v);
    }

    public static boolean isVdBallOpen() {
        return getBool("vdopen", false);
    }

    public static void setVdBallOpen(boolean v) {
        putBool("vdopen", v);
    }

    public static int getVdBallX() {
        return getInt("vdballx", -1);
    }

    public static int getVdBallY() {
        return getInt("vdbally", -1);
    }

    public static void setVdBallPos(int x, int y) {
        putInt("vdballx", x);
        putInt("vdbally", y);
    }

    public static int getStopX() {
        return getInt("stopx", -1);
    }

    public static int getStopY() {
        return getInt("stopy", -1);
    }

    public static void setStopPos(int x, int y) {
        putInt("stopx", x);
        putInt("stopy", y);
    }
}
