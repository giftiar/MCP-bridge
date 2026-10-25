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
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;

public class BridgeService extends Service {

    public static final String CHANNEL = "mcp_bridge";
    public static final String ACTION_STOP = "com.mcpmate.bridge.STOP";
    public static final String ACTION_TOGGLE_PAUSE = "com.mcpmate.bridge.TOGGLE_PAUSE";
    public static final int NID = 1001;

    public static volatile boolean running = false;

    private static volatile Context INST;

    public static Context ctx() {
        return INST;
    }

    public static void start(Context c) {
        try {
            c.startService(new Intent(c, BridgeService.class));
        } catch (Throwable t) {
            Log.add("服务启动失败: " + t);
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, BridgeService.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        INST = getApplicationContext();
        Config.init(this);
        try {
            makeChannel();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Config.init(this);

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Config.setEnabled(false);
            HttpSrv.stopAll();
            running = false;
            Log.add("通知栏手动停止");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_TOGGLE_PAUSE.equals(intent.getAction())) {
            boolean next = !Config.isPaused();
            Config.setPaused(next);
            String msg = next ? "已急停：AI 的所有操作已被阻止" : "已恢复：允许 AI 操作";
            Log.add(msg);
            try {
                android.widget.Toast.makeText(this, msg,
                        android.widget.Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {
            }
            show(build(running ? "已急停" : "已停止"));
            return START_STICKY;
        }

        try {
            startForeground(NID, build("启动中"));
        } catch (Throwable ignored) {
        }

        try {
            if (!Config.isEnabled()) {
                HttpSrv.stopAll();
                running = false;
                Log.add("已停用");
                stopSelf();
                return START_NOT_STICKY;
            }
            HttpSrv.startAll(this);
            running = HttpSrv.isRunning();
            Log.add(running ? "服务器已就绪" : "服务器未能启动");
            show(build(Config.isPaused() ? "已急停" : (running ? "运行中" : "未启动")));

            if (Config.isVdMode()) {
                startVd();
            } else {
                stopVd();
                StopBar.setSmall(false);
                if (Config.isBallOn()) {
                    StopBar.show(this);
                } else {
                    StopBar.hide();
                }
                if (Config.isInfoOn()) {
                    InfoBar.show(this);
                } else {
                    InfoBar.hide();
                }
            }
            return START_STICKY;
        } catch (Throwable t) {
            Log.add("启动异常: " + t);
        }
        return START_STICKY;
    }

    private void startVd() {
        InfoBar.hide();
        StopBar.setSmall(true);
        if (Config.isBallOn()) {
            StopBar.show(this);
        } else {
            StopBar.hide();
        }
        boolean canOverlay = true;
        try {
            canOverlay = android.provider.Settings.canDrawOverlays(this);
        } catch (Throwable ignored) {
        }
        if (canOverlay) {
            VdWin.show(this);
        } else {
            Log.add("缺少悬浮窗权限，请到设置页授予");
        }

        VdMgr.setListener(new VdMgr.Listener() {
            public void onBound() {
                Log.add("虚拟屏服务已连接");
                new Thread(new Runnable() {
                    public void run() {
                        String sweep = VdMgr.killAllDisplays();
                        Log.add("冷启动清理 " + sweep.replace("\n", " | "));
                        String r = VdMgr.create(BridgeService.this);
                        Log.add("建屏 " + r);
                    }
                }, "vd-create").start();
            }

            public void onLost() {
                Log.add("虚拟屏服务已断开");
            }
        });

        if (!VdMgr.shizukuAlive()) {
            Log.add("Shizuku 未运行，虚拟屏不可用");
            return;
        }
        if (!VdMgr.hasPerm()) {
            Log.add("Shizuku 未授权，请到设置页授权");
            return;
        }
        VdMgr.bind(this);
    }

    private void stopVd() {
        try {
            if (VdMgr.ready()) {
                VdMgr.deactivateIme();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (VdMgr.ready()) {
                VdMgr.destroy();
            }
        } catch (Throwable ignored) {
        }
        VdWin.hide();
        VdMgr.setListener(null);
        VdMgr.unbind();
    }

    public static void applyMode(Context c) {
        if (!running) {
            VdWin.hide();
            return;
        }
        if (Config.isVdMode()) {
            InfoBar.hide();
            StopBar.setSmall(true);
            if (Config.isBallOn()) {
                StopBar.refresh(c);
            }
            if (VdWin.isShowing()) {
                VdWin.refresh(c);
            } else if (Settings0.canOverlay(c)) {
                VdWin.show(c);
            }
        } else {
            VdWin.hide();
            StopBar.setSmall(false);
            if (Config.isBallOn()) {
                StopBar.refresh(c);
            }
            if (Config.isInfoOn()) {
                InfoBar.show(c);
            }
        }
    }

    public static class Settings0 {
        public static boolean canOverlay(Context c) {
            try {
                return android.provider.Settings.canDrawOverlays(c);
            } catch (Throwable t) {
                return false;
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.add("服务已停止");
        stopVd();
        StopBar.setSmall(false);
        StopBar.hide();
        InfoBar.hide();
        HttpSrv.stopAll();
        running = false;
        try {
            stopForeground(true);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void makeChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(CHANNEL, "MCP桥服务",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private Notification build(String status) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);

        b.setSmallIcon(android.R.drawable.stat_sys_upload_done);
        b.setContentTitle(Config.getName());
        b.setContentText(status + "  " + Net.addrText());
        b.setOngoing(true);
        b.setOnlyAlertOnce(true);
        b.setContentIntent(pi);

        try {
            Intent ti = new Intent(this, BridgeService.class);
            ti.setAction(ACTION_TOGGLE_PAUSE);
            PendingIntent tpi = PendingIntent.getService(this, 2, ti,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Icon ticon = Icon.createWithResource(this,
                    android.R.drawable.ic_media_pause);
            b.addAction(new Notification.Action.Builder(ticon,
                    Config.isPaused() ? "恢复" : "急停", tpi).build());
        } catch (Throwable ignored) {
        }

        try {
            Intent si = new Intent(this, BridgeService.class);
            si.setAction(ACTION_STOP);
            PendingIntent spi = PendingIntent.getService(this, 1, si,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Icon icon = Icon.createWithResource(this,
                    android.R.drawable.ic_menu_close_clear_cancel);
            b.addAction(new Notification.Action.Builder(icon, "停止", spi).build());
        } catch (Throwable ignored) {
        }

        return b.build();
    }

    private void show(Notification n) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NID, n);
            }
        } catch (Throwable ignored) {
        }
    }
}
