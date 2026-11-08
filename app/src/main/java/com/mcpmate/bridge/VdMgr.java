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

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import rikka.shizuku.Shizuku;

public class VdMgr {

    public static final int REQ_PERM = 8801;

    private static volatile String lastCreate = "";

    public static String lastCreate() {
        return lastCreate;
    }

    private static volatile IVdSvc svc;
    private static volatile String lastError = "";
    private static volatile boolean binding = false;
    private static final java.util.concurrent.atomic.AtomicLong bindToken =
            new java.util.concurrent.atomic.AtomicLong(0L);

    private static Shizuku.UserServiceArgs args;
    private static android.os.Binder clientBinder;

    private static final java.util.concurrent.ExecutorService PIPE_READER =
            java.util.concurrent.Executors.newCachedThreadPool(
                    new java.util.concurrent.ThreadFactory() {
                        public Thread newThread(Runnable r) {
                            Thread t = new Thread(r, "vd-pipe");
                            t.setDaemon(true);
                            return t;
                        }
                    });

    public interface Listener {
        void onBound();

        void onLost();
    }

    private static volatile Listener listener;

    public static void setListener(Listener l) {
        listener = l;
    }

    private static final ServiceConnection CONN = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName n, IBinder b) {
            svc = IVdSvc.Stub.asInterface(b);
            binding = false;
            lastError = "";
            try {
                if (clientBinder == null) {
                    clientBinder = new android.os.Binder();
                }
                svc.registerClient(clientBinder);
            } catch (Throwable t) {
                lastError = "register client: " + t;
            }
            Listener l = listener;
            if (l != null) {
                l.onBound();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName n) {
            svc = null;
            binding = false;
            lastError = "service disconnected";
            Listener l = listener;
            if (l != null) {
                l.onLost();
            }
        }
    };

    public static String error() {
        return lastError;
    }

    public static boolean ready() {
        return svc != null;
    }

    public static boolean shizukuAlive() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPerm() {
        try {
            if (!Shizuku.pingBinder()) {
                return false;
            }
            if (Shizuku.isPreV11()) {
                return false;
            }
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    private static volatile boolean permHooked = false;

    private static final Shizuku.OnRequestPermissionResultListener PERM_LISTENER =
            new Shizuku.OnRequestPermissionResultListener() {
                public void onRequestPermissionResult(int requestCode, int grantResult) {
                    if (requestCode != REQ_PERM) {
                        return;
                    }
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        lastError = "permission denied by user";
                        Log.add("Shizuku 授权被拒");
                        return;
                    }
                    Log.add("Shizuku 已授权，开始连接");
                    Context c = BridgeService.ctx();
                    if (c != null) {
                        bind(c);
                    }
                }
            };

    public static void hookPermResult() {
        if (permHooked) {
            return;
        }
        permHooked = true;
        try {
            Shizuku.addRequestPermissionResultListener(PERM_LISTENER);
        } catch (Throwable t) {
            lastError = "hook perm: " + t;
        }
    }

    public static void requestPerm() {
        try {
            hookPermResult();
            if (!Shizuku.pingBinder()) {
                lastError = "shizuku not running";
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQ_PERM);
            } else {
                Context c = BridgeService.ctx();
                if (c != null) {
                    bind(c);
                }
            }
        } catch (Throwable t) {
            lastError = "request perm: " + t;
        }
    }

    public static synchronized void bind(Context ctx) {
        if (svc != null || binding) {
            return;
        }
        if (!hasPerm()) {
            lastError = "permission denied";
            return;
        }
        binding = true;
        final long token = bindToken.incrementAndGet();
        try {
            ComponentName cn = new ComponentName(ctx.getPackageName(), VdSvc.class.getName());
            args = new Shizuku.UserServiceArgs(cn)
                    .daemon(false)
                    .processNameSuffix("vd")
                    .debuggable(false)
                    .version(1);
            Shizuku.bindUserService(args, CONN);
        } catch (Throwable t) {
            binding = false;
            lastError = "bind: " + t;
            return;
        }
        Thread guard = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(12000L);
                } catch (Throwable ignored) {
                }
                if (bindToken.get() == token && binding) {
                    binding = false;
                    if (svc == null) {
                        lastError = "bind timeout: Shizuku did not answer";
                    }
                }
            }
        }, "vd-bind-guard");
        guard.setDaemon(true);
        guard.start();
    }

    public static void unbind() {
        try {
            if (args != null) {
                Shizuku.unbindUserService(args, CONN, true);
            }
        } catch (Throwable ignored) {
        }
        svc = null;
        binding = false;
    }

    public static int[] autoSize(Context ctx) {
        int w = 0;
        int h = 0;
        int dpi = 0;

        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            Display d = wm.getDefaultDisplay();
            Point real = new Point();
            d.getRealSize(real);
            DisplayMetrics dm = new DisplayMetrics();
            d.getRealMetrics(dm);
            w = real.x;
            h = real.y;
            dpi = dm.densityDpi;
            if ((d.getRotation() % 2) != 0) {
                int t = w;
                w = h;
                h = t;
            }
        } catch (Throwable ignored) {
        }

        if (w <= 0 || h <= 0 || dpi <= 0) {
            try {
                DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                if (w <= 0) {
                    w = dm.widthPixels;
                }
                if (h <= 0) {
                    h = dm.heightPixels;
                }
                if (dpi <= 0) {
                    dpi = dm.densityDpi;
                }
            } catch (Throwable ignored) {
            }
        }

        if (w <= 0) {
            w = 720;
        }
        if (h <= 0) {
            h = 1280;
        }
        if (dpi <= 0) {
            dpi = 320;
        }

        if (w % 2 != 0) {
            w++;
        }
        if (h % 2 != 0) {
            h++;
        }
        return new int[]{w, h, dpi};
    }

    public static int[] applyOrient(Context ctx) {
        int[] s = autoSize(ctx);
        boolean land = Config.getVdOrient() == 1;
        int w = land ? s[1] : s[0];
        int h = land ? s[0] : s[1];
        Config.setVdW(w);
        Config.setVdH(h);
        Config.setVdDpi(s[2]);
        return new int[]{w, h, s[2]};
    }

    public static String create(Context ctx) {
        if (svc == null) {
            return "FAIL service not ready";
        }
        int[] s = applyOrient(ctx);
        int w = s[0];
        int h = s[1];
        int dpi = s[2];
        try {
            String r = svc.create(w, h, dpi);
            lastCreate = r;
            if (r.startsWith("OK")) {
                Config.setVdW(w);
                Config.setVdH(h);
                Config.setVdDpi(dpi);
                Config.setVdOrient(w > h ? 1 : 0);
            }
            return r + " land=" + (w > h) + " want=" + Config.getVdOrient();
        } catch (Throwable t) {
            lastCreate = "FAIL " + t;
            return lastCreate;
        }
    }

    public static boolean isLandscape() {
        IVdSvc s = svc;
        if (s == null) {
            return Config.getVdW() > Config.getVdH();
        }
        try {
            String info = s.info();
            int w = pickInt(info, "width=");
            int h = pickInt(info, "height=");
            if (w > 0 && h > 0) {
                return w > h;
            }
        } catch (Throwable ignored) {
        }
        return Config.getVdW() > Config.getVdH();
    }

    public static String rotate(boolean landscape) {
        IVdSvc s = svc;
        if (s == null) {
            return "FAIL no service";
        }
        try {
            String r = s.rotate(landscape ? 1 : 0);
            if (!r.startsWith("OK")) {
                return r;
            }
            int w = -1;
            int h = -1;
            int[] sz = parseSize(r);
            if (sz != null) {
                w = sz[0];
                h = sz[1];
            }
            if (w <= 0 || h <= 0) {
                String info = s.info();
                w = pickInt(info, "width=");
                h = pickInt(info, "height=");
            }
            if (w <= 0 || h <= 0) {
                return r + " but size unknown, config not updated";
            }
            Config.setVdW(w);
            Config.setVdH(h);
            Config.setVdOrient(w > h ? 1 : 0);
            return r + " conf=" + w + "x" + h;
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    private static int[] parseSize(String s) {
        try {
            for (int i = 0; i < s.length(); i++) {
                if (!Character.isDigit(s.charAt(i))) {
                    continue;
                }
                int j = i;
                while (j < s.length() && Character.isDigit(s.charAt(j))) {
                    j++;
                }
                if (j >= s.length() || s.charAt(j) != 'x') {
                    continue;
                }
                int k = j + 1;
                int m = k;
                while (m < s.length() && Character.isDigit(s.charAt(m))) {
                    m++;
                }
                if (m == k) {
                    continue;
                }
                int w = Integer.parseInt(s.substring(i, j));
                int h = Integer.parseInt(s.substring(k, m));
                if (w > 0 && h > 0) {
                    return new int[]{w, h};
                }
                i = m;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static int pickInt(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) {
            return -1;
        }
        int j = i + key.length();
        int k = j;
        while (k < s.length() && Character.isDigit(s.charAt(k))) {
            k++;
        }
        if (k == j) {
            return -1;
        }
        try {
            return Integer.parseInt(s.substring(j, k));
        } catch (Throwable t) {
            return -1;
        }
    }

    public static String destroy() {
        if (svc == null) {
            return "FAIL no service";
        }
        try {
            return svc.destroy();
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    public static String info() {
        if (svc == null) {
            return "FAIL no service";
        }
        try {
            return svc.info();
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    public static String listDisplays() {
        if (svc == null) {
            return "FAIL service not ready";
        }
        try {
            return svc.listDisplays();
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    public static String activateIme() {
        if (svc == null) {
            return "FAIL service not ready";
        }
        try {
            return svc.activateIme();
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    public static String deactivateIme() {
        if (svc == null) {
            return "FAIL service not ready";
        }
        try {
            return svc.deactivateIme();
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    public static String killAllDisplays() {
        if (svc == null) {
            return "FAIL service not ready";
        }
        try {
            return svc.killAllDisplays();
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    public static String ping() {
        if (svc == null) {
            return "FAIL no service";
        }
        try {
            return svc.ping();
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    public static String startApp(String pkg) {
        if (svc == null) {
            return "FAIL no service";
        }
        try {
            return svc.startApp(pkg);
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    public static String click(int x, int y) {
        if (svc == null) {
            return "FAIL no service";
        }
        try {
            return svc.click(x, y, 60);
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    public static String touchDown(int x, int y) {
        IVdSvc s = svc;
        if (s == null) {
            return "FAIL no service";
        }
        try {
            return s.touchDown(x, y);
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    public static String touchMove(int x, int y) {
        IVdSvc s = svc;
        if (s == null) {
            return "FAIL no service";
        }
        try {
            return s.touchMove(x, y);
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    public static String touchUp(int x, int y) {
        IVdSvc s = svc;
        if (s == null) {
            return "FAIL no service";
        }
        try {
            return s.touchUp(x, y);
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    public static String longClick(int x, int y) {
        if (svc == null) {
            return "FAIL no service";
        }
        try {
            return svc.longClick(x, y);
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    public static String drag(int x1, int y1, int x2, int y2, int holdMs, int durMs) {
        if (svc == null) {
            return "FAIL no service";
        }
        try {
            return svc.drag(x1, y1, x2, y2, holdMs, durMs);
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    public static String swipe(int x1, int y1, int x2, int y2, int durMs) {
        if (svc == null) {
            return "FAIL no service";
        }
        try {
            return svc.swipe(x1, y1, x2, y2, durMs);
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    public static String key(int keycode) {
        if (svc == null) {
            return "FAIL no service";
        }
        try {
            return svc.key(keycode);
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    public static byte[] capture() {
        IVdSvc s = svc;
        if (s == null) {
            return null;
        }
        ParcelFileDescriptor r = null;
        ParcelFileDescriptor w = null;
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            r = pipe[0];
            w = pipe[1];

            final ParcelFileDescriptor rr = r;
            java.util.concurrent.Future<byte[]> fr =
                    PIPE_READER.submit(new java.util.concurrent.Callable<byte[]>() {
                        public byte[] call() {
                            return readAll(rr);
                        }
                    });

            s.capture(w);
            try {
                w.close();
            } catch (Throwable ignored) {
            }
            w = null;
            try {
                return fr.get(8, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Throwable ignored) {
                fr.cancel(true);
                lastError = "capture timeout";
                return null;
            }
        } catch (Throwable t) {
            lastError = "capture: " + t;
            return null;
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (Throwable ignored) {
                }
            }
            if (r != null) {
                try {
                    r.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static byte[] preview(int maxWidth, int quality) {
        IVdSvc s = svc;
        if (s == null) {
            return null;
        }
        ParcelFileDescriptor r = null;
        ParcelFileDescriptor w = null;
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            r = pipe[0];
            w = pipe[1];

            final ParcelFileDescriptor rr = r;
            java.util.concurrent.Future<byte[]> fr =
                    PIPE_READER.submit(new java.util.concurrent.Callable<byte[]>() {
                        public byte[] call() {
                            return readAll(rr);
                        }
                    });

            s.preview(maxWidth, quality, w);
            try {
                w.close();
            } catch (Throwable ignored) {
            }
            w = null;
            try {
                return fr.get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Throwable ignored) {
                fr.cancel(true);
                lastError = "preview timeout";
                return null;
            }
        } catch (Throwable t) {
            return null;
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (Throwable ignored) {
                }
            }
            if (r != null) {
                try {
                    r.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static byte[] readAll(ParcelFileDescriptor pfd) {
        InputStream in = null;
        try {
            in = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
