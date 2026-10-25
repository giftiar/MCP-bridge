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
    public static final int DEF_W = 1080;
    public static final int DEF_H = 1920;
    public static final int DEF_DPI = 420;

    private static volatile String lastCreate = "";

    public static String lastCreate() {
        return lastCreate;
    }

    private static volatile IVdSvc svc;
    private static volatile String lastError = "";
    private static volatile boolean binding = false;

    private static Shizuku.UserServiceArgs args;
    private static android.os.Binder clientBinder;

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

    public static void requestPerm() {
        try {
            if (!Shizuku.pingBinder()) {
                lastError = "shizuku not running";
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQ_PERM);
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
        }
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
        int w = DEF_W;
        int h = DEF_H;
        int dpi = DEF_DPI;
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            Display d = wm.getDefaultDisplay();
            Point real = new Point();
            d.getRealSize(real);
            DisplayMetrics dm = new DisplayMetrics();
            d.getRealMetrics(dm);
            if (dm.densityDpi > 0) {
                dpi = dm.densityDpi;
            }
            int shortSide = Math.min(real.x, real.y);
            if (shortSide > 0) {
                w = shortSide;
                h = Math.round(shortSide * 16f / 9f);
            }
        } catch (Throwable ignored) {
        }
        if (w % 2 != 0) {
            w++;
        }
        if (h % 2 != 0) {
            h++;
        }
        return new int[]{w, h, dpi};
    }

    public static String create(Context ctx) {
        if (svc == null) {
            return "FAIL service not ready";
        }
        int[] s = autoSize(ctx);
        int w = Config.getVdW() > 0 ? Config.getVdW() : s[0];
        int h = Config.getVdH() > 0 ? Config.getVdH() : s[1];
        int dpi = Config.getVdDpi() > 0 ? Config.getVdDpi() : s[2];
        try {
            String r = svc.create(w, h, dpi);
            lastCreate = r;
            if (r.startsWith("OK")) {
                Config.setVdW(w);
                Config.setVdH(h);
                Config.setVdDpi(dpi);
            }
            return r;
        } catch (Throwable t) {
            lastCreate = "FAIL " + t;
            return lastCreate;
        }
    }

    public static String rotate(boolean landscape) {
        if (svc == null) {
            return "FAIL no service";
        }
        try {
            String r = svc.rotate(landscape ? 1 : 0);
            if (r.startsWith("OK")) {
                String info = svc.info();
                int w = pickInt(info, "width=");
                int h = pickInt(info, "height=");
                int d = pickInt(info, "dpi=");
                if (w > 0 && h > 0) {
                    Config.setVdW(w);
                    Config.setVdH(h);
                }
                if (d > 0) {
                    Config.setVdDpi(d);
                }
            }
            return r;
        } catch (Throwable t) {
            return "FAIL " + t;
        }
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
            final byte[][] box = new byte[1][];
            Thread t = new Thread(new Runnable() {
                public void run() {
                    box[0] = readAll(rr);
                }
            }, "vd-cap");
            t.setDaemon(true);
            t.start();

            s.capture(w);
            try {
                w.close();
            } catch (Throwable ignored) {
            }
            w = null;
            t.join(8000L);
            return box[0];
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
            final byte[][] box = new byte[1][];
            Thread t = new Thread(new Runnable() {
                public void run() {
                    box[0] = readAll(rr);
                }
            }, "vd-prev");
            t.setDaemon(true);
            t.start();

            s.preview(maxWidth, quality, w);
            try {
                w.close();
            } catch (Throwable ignored) {
            }
            w = null;
            t.join(5000L);
            return box[0];
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
