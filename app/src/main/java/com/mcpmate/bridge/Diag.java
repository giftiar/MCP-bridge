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
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class Diag {

    private Diag() {
    }

    public static File outDir(Context ctx) {
        File d = null;
        try {
            File dl = android.os.Environment
                    .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            d = new File(dl, "mcp-diagnostics");
            if (!d.exists() && !d.mkdirs()) {
                d = null;
            }
            if (d != null) {
                File probe = new File(d, ".w");
                if (!probe.createNewFile() && !probe.exists()) {
                    d = null;
                } else {
                    probe.delete();
                }
            }
        } catch (Throwable ignored) {
            d = null;
        }
        if (d == null) {
            try {
                File base = ctx.getExternalFilesDir(null);
                if (base == null) {
                    base = ctx.getFilesDir();
                }
                d = new File(base, "mcp-diagnostics");
                if (!d.exists()) {
                    d.mkdirs();
                }
            } catch (Throwable t) {
                d = new File(ctx.getFilesDir(), "mcp-diagnostics");
                d.mkdirs();
            }
        }
        return d;
    }

    public static String build(Context ctx) {
        File dir = outDir(ctx);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date());
        File out = new File(dir, "diag_" + stamp + ".zip");
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new FileOutputStream(out));
            put(zos, "device.txt", device(ctx));
            put(zos, "log.txt", Log.dump());
            put(zos, "crash.txt", crash(ctx));
            put(zos, "self_test.txt", safeSelfTest());
            put(zos, "vd.txt", safeVd());
            put(zos, "settings.txt", settings());
            zos.finish();
            return "OK " + out.getAbsolutePath();
        } catch (Throwable t) {
            return "FAIL " + t;
        } finally {
            if (zos != null) {
                try {
                    zos.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void put(ZipOutputStream zos, String name, String body) {
        try {
            zos.putNextEntry(new ZipEntry(name));
            zos.write((body == null ? "" : body).getBytes("UTF-8"));
            zos.closeEntry();
        } catch (Throwable ignored) {
        }
    }

    private static String device(Context ctx) {
        StringBuilder b = new StringBuilder();
        b.append("app_version: ").append(Mcp.VERSION).append('\n');
        b.append("package: ").append(ctx.getPackageName()).append('\n');
        b.append("sdk: ").append(Build.VERSION.SDK_INT).append('\n');
        b.append("release: ").append(Build.VERSION.RELEASE).append('\n');
        b.append("device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append('\n');
        b.append("brand: ").append(Build.BRAND).append('\n');
        b.append("fingerprint: ").append(Build.FINGERPRINT).append('\n');
        b.append("abi: ");
        for (String s : Build.SUPPORTED_ABIS) {
            b.append(s).append(' ');
        }
        b.append('\n');
        b.append("mode: ").append(Config.isVdMode() ? "virtual_display" : "screen").append('\n');
        b.append("enabled: ").append(Config.isEnabled()).append('\n');
        b.append("paused: ").append(Config.isPaused()).append('\n');
        b.append("service_running: ").append(BridgeService.running).append('\n');
        b.append("a11y: ").append(Ui.ready()).append('\n');
        b.append("shizuku_alive: ").append(VdMgr.shizukuAlive()).append('\n');
        b.append("shizuku_perm: ").append(VdMgr.hasPerm()).append('\n');
        return b.toString();
    }

    private static String settings() {
        StringBuilder b = new StringBuilder();
        b.append("local_port: ").append(Config.getPortLocal()).append('\n');
        b.append("lan_port: ").append(Config.getPortLan()).append('\n');
        b.append("lan_on: ").append(Config.isLan()).append('\n');
        b.append("interval_ms: ").append(Config.getInterval()).append('\n');
        b.append("token_set: ").append(Config.getToken().length() > 0).append('\n');
        b.append("vd_w: ").append(Config.getVdW()).append('\n');
        b.append("vd_h: ").append(Config.getVdH()).append('\n');
        b.append("vd_orient_land: ").append(Config.getVdOrient() == 1).append('\n');
        b.append("ball_shown: ").append(StopBar.isShown()).append('\n');
        b.append("info_on: ").append(Config.isInfoOn()).append('\n');
        return b.toString();
    }

    private static String crash(Context ctx) {
        try {
            File f = App.crashFile(ctx);
            if (!f.exists()) {
                return "(no crash recorded)";
            }
            return App.read(f);
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    private static String safeSelfTest() {
        try {
            return Ui.selfTest();
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    private static String safeVd() {
        try {
            StringBuilder b = new StringBuilder();
            b.append("ready: ").append(VdMgr.ready()).append('\n');
            b.append("last_create: ").append(VdMgr.lastCreate()).append('\n');
            b.append("error: ").append(VdMgr.error()).append('\n');
            if (VdMgr.ready()) {
                b.append("--- info ---\n").append(VdMgr.info()).append('\n');
                b.append("--- ping ---\n").append(VdMgr.ping()).append('\n');
                b.append("--- displays ---\n").append(VdMgr.listDisplays()).append('\n');
            }
            return b.toString();
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }
}
