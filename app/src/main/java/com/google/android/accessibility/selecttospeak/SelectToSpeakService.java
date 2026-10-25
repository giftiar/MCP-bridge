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

/*
 * NOTE ON THE PACKAGE NAME
 *
 * This class is NOT part of Google's SelectToSpeak. It is an independent,
 * minimal accessibility service written for this project, and it declares
 * no behaviour of its own.
 *
 * The package name deliberately mirrors a system accessibility service
 * because several applications, WeChat and Quark among them, refuse to
 * expose their accessibility tree once they detect a third-party
 * automation service. Registering under this name makes those
 * applications behave normally.
 *
 * This service reads nothing, sends nothing and performs no actions.
 */

package com.google.android.accessibility.selecttospeak;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import java.util.List;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.pm.ResolveInfo;

public class SelectToSpeakService extends AccessibilityService {

    public static final String UNLOCK_CLASS =
            "com.google.android.accessibility.selecttospeak.SelectToSpeakService";

    public static volatile boolean connected = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        try {
            com.mcpmate.bridge.Log.add("兼容模式服务已连接（用于微信等白名单应用）");
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        connected = false;
        return super.onUnbind(intent);
    }

    public static boolean isEnabled(Context ctx) {
        if (ctx == null) {
            return false;
        }
        try {
            AccessibilityManager am = (AccessibilityManager)
                    ctx.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null) {
                return false;
            }
            List<AccessibilityServiceInfo> list = am.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            if (list == null) {
                return false;
            }
            for (AccessibilityServiceInfo info : list) {
                try {
                    ResolveInfo ri = info.getResolveInfo();
                    if (ri == null || ri.serviceInfo == null) {
                        continue;
                    }
                    String name = ri.serviceInfo.name;
                    if (name != null && name.contains("selecttospeak")) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
