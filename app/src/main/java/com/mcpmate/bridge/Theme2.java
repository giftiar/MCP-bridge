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

import android.app.Activity;
import android.os.Build;

public class Theme2 {

    private static final int[] STYLES = new int[]{
            R.style.Theme_McpBridge_Blue,
            R.style.Theme_McpBridge_Green,
            R.style.Theme_McpBridge_Purple,
            R.style.Theme_McpBridge_Orange,
            R.style.Theme_McpBridge_Pink,
            R.style.Theme_McpBridge_Teal
    };

    public static final String[] NAMES = new String[]{
            "晨雾蓝", "松针绿", "梦魇紫", "暖陶橙", "栀子粉", "青瓷青"
    };

    public static final int[] SWATCH = new int[]{
            0xFF3F5F8F, 0xFF3D6B4A, 0xFF63558C,
            0xFF8A5A2B, 0xFF8B4A5E, 0xFF2F6666
    };

    public static int count() {
        return STYLES.length;
    }

    public static boolean canDyn() {
        return Build.VERSION.SDK_INT >= 31;
    }

    public static boolean useDyn() {
        return canDyn() && Config.isFollowWallpaper();
    }

    public static void apply(Activity a) {
        if (a == null) {
            return;
        }
        try {
            if (useDyn()) {
                a.setTheme(R.style.Theme_McpBridge_Dyn);
                return;
            }
            int s = Config.getSeed();
            if (s < 0 || s >= STYLES.length) {
                s = 0;
            }
            a.setTheme(STYLES[s]);
        } catch (Throwable ignored) {
        }
    }
}
