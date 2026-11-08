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

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class App extends Application {

    public static final String CRASH_FILE = "crash.txt";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Config.init(this);
        } catch (Throwable ignored) {
        }
        try {
            VdMgr.hookPermResult();
        } catch (Throwable ignored) {
        }
        try {
            final Thread.UncaughtExceptionHandler prev =
                    Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable e) {
                    boolean main = thread != null && "main".equals(thread.getName());
                    try {
                        saveCrash(thread, e);
                    } catch (Throwable ignored) {
                    }
                    if (main) {
                        Process.killProcess(Process.myPid());
                        return;
                    }
                    if (prev != null) {
                        prev.uncaughtException(thread, e);
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void saveCrash(Thread thread, Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        pw.println("线程: " + (thread == null ? "?" : thread.getName()));
        pw.println("系统: Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        pw.println("机型: " + Build.MANUFACTURER + " " + Build.MODEL);
        pw.println();
        e.printStackTrace(pw);
        pw.flush();
        write(crashFile(this), sw.toString());
    }

    public static File crashFile(Context context) {
        return new File(context.getFilesDir(), CRASH_FILE);
    }

    public static void write(File file, String text) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(text.getBytes("UTF-8"));
            out.flush();
        } catch (Throwable ignored) {
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static String read(File file) {
        try {
            FileInputStream in = new FileInputStream(file);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try {
                byte[] tmp = new byte[4096];
                int n;
                while ((n = in.read(tmp)) != -1) {
                    buf.write(tmp, 0, n);
                }
            } finally {
                in.close();
            }
            return new String(buf.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
