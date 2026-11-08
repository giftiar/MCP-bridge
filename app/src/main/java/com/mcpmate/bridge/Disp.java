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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class Disp {

    public static final class Info {
        public final int displayId;
        public final int width;
        public final int height;
        public final int rotation;
        public final int dpi;
        public final int layerStack;
        public final int flags;

        Info(int displayId, int width, int height, int rotation,
                int dpi, int layerStack, int flags) {
            this.displayId = displayId;
            this.width = width;
            this.height = height;
            this.rotation = rotation;
            this.dpi = dpi;
            this.layerStack = layerStack;
            this.flags = flags;
        }

        public boolean landscape() {
            return width > height;
        }

        public String sizeText() {
            return width + "x" + height;
        }

        public String rotationText() {
            return Disp.rotationText(rotation);
        }
    }

    private static final String GLOBAL = "android.hardware.display.DisplayManagerGlobal";
    private static final String INFO = "android.view.DisplayInfo";

    private static Object global;
    private static Method mGetInfo;
    private static Field fW;
    private static Field fH;
    private static Field fRot;
    private static Field fDpi;
    private static Field fLayer;
    private static Field fFlags;
    private static volatile boolean tried;
    private static volatile String lastError = "not initialized";

    private Disp() {
    }

    private static synchronized boolean init() {
        if (tried) {
            return global != null;
        }
        tried = true;
        try {
            Class<?> gc = Class.forName(GLOBAL);
            Method g = gc.getDeclaredMethod("getInstance");
            g.setAccessible(true);
            Object o = g.invoke(null);
            if (o == null) {
                lastError = "getInstance null";
                return false;
            }
            Method mi = gc.getDeclaredMethod("getDisplayInfo", int.class);
            mi.setAccessible(true);
            Class<?> ic = Class.forName(INFO);
            global = o;
            mGetInfo = mi;
            fW = field(ic, "logicalWidth");
            fH = field(ic, "logicalHeight");
            fRot = field(ic, "rotation");
            fDpi = field(ic, "logicalDensityDpi");
            fLayer = field(ic, "layerStack");
            fFlags = field(ic, "flags");
            if (fW == null || fH == null) {
                lastError = "DisplayInfo width fields missing";
                global = null;
                return false;
            }
            lastError = "";
            return true;
        } catch (Throwable t) {
            lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
            global = null;
            return false;
        }
    }

    private static Field field(Class<?> c, String n) {
        try {
            Field f = c.getDeclaredField(n);
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int read(Field f, Object o, int def) {
        if (f == null || o == null) {
            return def;
        }
        try {
            return f.getInt(o);
        } catch (Throwable t) {
            return def;
        }
    }

    public static String error() {
        init();
        return lastError;
    }

    public static Info get(int displayId) {
        if (!init()) {
            return null;
        }
        try {
            Object o = mGetInfo.invoke(global, displayId);
            if (o == null) {
                return null;
            }
            return new Info(displayId,
                    read(fW, o, 0),
                    read(fH, o, 0),
                    read(fRot, o, 0),
                    read(fDpi, o, 0),
                    read(fLayer, o, 0),
                    read(fFlags, o, 0));
        } catch (Throwable t) {
            lastError = "getDisplayInfo: " + t.getClass().getSimpleName();
            return null;
        }
    }

    public static String rotationText(int r) {
        switch (r) {
            case 0:
                return "0";
            case 1:
                return "90";
            case 2:
                return "180";
            case 3:
                return "270";
            default:
                return String.valueOf(r);
        }
    }

    public static boolean isLandscapeOrientation(int screenOrientation) {
        return screenOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                || screenOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                || screenOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                || screenOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE;
    }

    public static boolean isPortraitOrientation(int screenOrientation) {
        return screenOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                || screenOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                || screenOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                || screenOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
    }

    public static String orientationText(int screenOrientation) {
        if (isLandscapeOrientation(screenOrientation)) {
            return "landscape";
        }
        if (isPortraitOrientation(screenOrientation)) {
            return "portrait";
        }
        return "unspecified";
    }
}
