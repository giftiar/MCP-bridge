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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public class Notif {

    public static void alert(Context c, boolean paused) {
        try {
            NotificationManager nm = (NotificationManager)
                    c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(c, BridgeService.CHANNEL)
                    : new Notification.Builder(c);
            b.setSmallIcon(android.R.drawable.stat_sys_warning);
            b.setContentTitle(Config.getName());
            b.setContentText(paused
                    ? "已暂停：AI 的所有操作都被阻止。点击“续”恢复。"
                    : "运行中  " + Net.addrText());
            b.setOngoing(true);
            b.setOnlyAlertOnce(true);
            if (paused) {
                b.setPriority(Notification.PRIORITY_HIGH);
            }
            nm.notify(BridgeService.NID, b.build());
        } catch (Throwable ignored) {
        }
    }
}
