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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.Os;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class VdSvc extends IVdSvc.Stub {

    private static final int FLAG_PRIVATE = 0;
    private static final int MODE_ASYNC = 0;
    private static final int MODE_WAIT_FOR_RESULT = 1;
    private static final int MODE_WAIT_FOR_FINISH = 2;
    private static volatile int injectMode = MODE_WAIT_FOR_FINISH;
    private static volatile int injectUsable = -1;
    private static final int LONG_PRESS_MS = 800;

    private static Context appCtx;
    private static Object inputMgr;
    private static Method mSetDisplayId;
    private static Method mInjectPlain;
    private static Method mInjectTarget;
    private static String ctxNote = "not built";
    private static volatile int startUid = -1;
    private static volatile String dropNote = "not tried";

    private static HandlerThread readerThread;

    private volatile VirtualDisplay vd;
    private volatile ImageReader reader;
    private volatile Bitmap latest;
    private volatile long lastFrameAt;
    private volatile String name = "";
    private int w;
    private int h;
    private int dpi;
    private int displayId = -1;

    public VdSvc() {
        startUid = android.os.Process.myUid();
        try {
            HiddenApiBypass.addHiddenApiExemptions("");
        } catch (Throwable ignored) {
        }
        dropNote = dropToShell();
    }

    private static String dropToShell() {
        StringBuilder note = new StringBuilder();
        int beforeUid = android.os.Process.myUid();

        if (beforeUid == 0) {
            try {
                Os.setgid(2000);
                Os.setuid(2000);
                note.append("uid 0->2000 ");
            } catch (Throwable t) {
                note.append("setuid_fail ");
            }
        } else if (beforeUid == 2000) {
            note.append("uid ok ");
        } else {
            note.append("uid=").append(beforeUid).append(' ');
        }

        String ctx = selinux();
        if (ctx == null || !ctx.startsWith("u:r:shell:")) {
            setcon("u:r:shell:s0");
            ctx = selinux();
        }
        note.append("se=").append(ctx == null ? "?" : ctx);
        return note.toString();
    }

    private static boolean setcon(String context) {
        java.io.FileOutputStream fos = null;
        try {
            fos = new java.io.FileOutputStream("/proc/self/attr/current");
            fos.write(context.getBytes("UTF-8"));
            fos.flush();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    static String selinux() {
        java.io.FileInputStream in = null;
        try {
            in = new java.io.FileInputStream("/proc/self/attr/current");
            byte[] buf = new byte[128];
            int n = in.read(buf);
            if (n <= 0) {
                return null;
            }
            return new String(buf, 0, n, "UTF-8").trim();
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

    private static synchronized Handler readerHandler() {
        if (readerThread == null) {
            readerThread = new HandlerThread("vd-reader");
            readerThread.start();
        }
        return new Handler(readerThread.getLooper());
    }

    private static synchronized Context app() {
        if (appCtx != null) {
            return appCtx;
        }
        Context sys = null;
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method curr = at.getDeclaredMethod("currentActivityThread");
            curr.setAccessible(true);
            Object thread = curr.invoke(null);
            if (thread == null) {
                Method sysMain = at.getDeclaredMethod("systemMain");
                sysMain.setAccessible(true);
                thread = sysMain.invoke(null);
            }
            if (thread != null) {
                Method gsc = at.getDeclaredMethod("getSystemContext");
                gsc.setAccessible(true);
                sys = (Context) gsc.invoke(thread);
            }
        } catch (Throwable ignored) {
        }
        if (sys == null) {
            ctxNote = "no system context";
            return null;
        }

        int uid = android.os.Process.myUid();
        List<String> tries = new ArrayList<String>();
        try {
            String[] names = sys.getPackageManager().getPackagesForUid(uid);
            if (names != null) {
                for (String n : names) {
                    if (n != null && n.length() > 0) {
                        tries.add(n);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (!tries.contains("com.android.shell")) {
            tries.add("com.android.shell");
        }
        if (!tries.contains("android")) {
            tries.add("android");
        }

        StringBuilder note = new StringBuilder();
        for (String pkg : tries) {
            try {
                Context pc = sys.createPackageContext(pkg,
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                if (pc != null) {
                    appCtx = pc;
                    ctxNote = "ok via " + pkg;
                    return appCtx;
                }
            } catch (Throwable t) {
                if (note.length() > 0) {
                    note.append(" | ");
                }
                note.append(pkg).append(':').append(t.getClass().getSimpleName());
            }
        }
        ctxNote = "failed: " + note;
        appCtx = sys;
        return appCtx;
    }

    private static synchronized Object input() {
        if (inputMgr != null) {
            return inputMgr;
        }
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method get = sm.getDeclaredMethod("getService", String.class);
            get.setAccessible(true);
            IBinder binder = (IBinder) get.invoke(null, Context.INPUT_SERVICE);
            if (binder == null) {
                return null;
            }
            Class<?> stub = Class.forName("android.hardware.input.IInputManager$Stub");
            Method asI = stub.getDeclaredMethod("asInterface", IBinder.class);
            asI.setAccessible(true);
            inputMgr = asI.invoke(null, binder);
        } catch (Throwable t) {
            return null;
        }
        mSetDisplayId = findMethod(InputEvent.class, "setDisplayId", int.class);

        mInjectPlain = findInject("injectInputEvent",
                new Class[]{InputEvent.class, int.class});
        mInjectTarget = findInject("injectInputEventToTarget",
                new Class[]{InputEvent.class, int.class, int.class});
        return inputMgr;
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... types) {
        try {
            Method m = owner.getMethod(name, types);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
        }
        try {
            Method m = owner.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
        }
        Class<?> sup = owner.getSuperclass();
        while (sup != null) {
            try {
                Method m = sup.getDeclaredMethod(name, types);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
            }
            sup = sup.getSuperclass();
        }
        return null;
    }

    private static Method findInject(String name, Class[] types) {
        Class<?>[] holders = new Class<?>[]{
                null,
                safeClass("android.hardware.input.IInputManager"),
                safeClass("android.hardware.input.IInputManager$Stub$Proxy")
        };
        Object mgr = inputMgr;
        if (mgr != null) {
            holders[0] = mgr.getClass();
        }
        for (int i = 0; i < holders.length; i++) {
            Class<?> c = holders[i];
            if (c == null) {
                continue;
            }
            try {
                Method m = c.getMethod(name, types);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
            }
            try {
                Method m = c.getDeclaredMethod(name, types);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Class<?> safeClass(String n) {
        try {
            return Class.forName(n);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public String ping() {
        StringBuilder b = new StringBuilder();
        b.append("uid_start=").append(startUid).append('\n');
        b.append("uid=").append(android.os.Process.myUid()).append('\n');
        b.append("drop=").append(dropNote).append('\n');
        b.append("pid=").append(android.os.Process.myPid()).append('\n');
        b.append("sdk=").append(Build.VERSION.SDK_INT).append('\n');
        b.append("ctx=").append(ctxNote).append('\n');
        b.append("input=").append(input() == null ? "null" : "ok").append('\n');
        b.append("client=").append(clientNote).append('\n');
        b.append("selinux=").append(selinux()).append('\n');
        b.append("drop=").append(dropNote).append('\n');
        b.append("inject=").append(injectUsable == 1 ? "on" : "off").append('\n');
        return b.toString();
    }

    @Override
    public String create(int width, int height, int density) {
        Context c = app();
        if (c == null) {
            return "FAIL no context";
        }
        DisplayManager dm = (DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) {
            return "FAIL no DisplayManager";
        }

        destroy();

        if (width < 320) {
            width = 320;
        }
        if (height < 320) {
            height = 320;
        }
        if (density < 120) {
            density = 160;
        }

        ImageReader r = null;
        try {
            r = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
            String nm = "mcp-vd-" + System.currentTimeMillis();
            VirtualDisplay v = dm.createVirtualDisplay(nm, width, height, density,
                    r.getSurface(), FLAG_PRIVATE);
            if (v == null) {
                r.close();
                return "FAIL createVirtualDisplay null (ctx=" + ctxNote + ")";
            }
            Display d = v.getDisplay();
            if (d == null) {
                v.release();
                r.close();
                return "FAIL no Display";
            }

            vd = v;
            reader = r;
            name = nm;
            w = width;
            h = height;
            dpi = density;
            displayId = d.getDisplayId();
            latest = null;
            lastFrameAt = 0L;

            final int cw = width;
            final int ch = height;
            r.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                public void onImageAvailable(ImageReader ir) {
                    Image img = null;
                    try {
                        img = ir.acquireLatestImage();
                        if (img != null) {
                            Bitmap bmp = toBitmap(img, cw, ch);
                            if (bmp != null) {
                                Bitmap old = latest;
                                latest = bmp;
                                lastFrameAt = SystemClock.uptimeMillis();
                                if (old != null) {
                                    try {
                                        old.recycle();
                                    } catch (Throwable ignored) {
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        if (img != null) {
                            try {
                                img.close();
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            }, readerHandler());

            startOrientationWatch();
            injectUsable = -1;
            probeInject();
            return "OK id=" + displayId + " " + width + "x" + height + "@" + density
                    + " inject=" + (injectUsable == 1 ? "on" : "off") + "(" + injectMode + ")";
        } catch (Throwable t) {
            if (r != null) {
                try {
                    r.close();
                } catch (Throwable ignored) {
                }
            }
            return "FAIL " + t;
        }
    }

    @Override
    public String destroy() {
        int old = displayId;
        ImageReader r = reader;
        VirtualDisplay v = vd;
        reader = null;
        vd = null;
        displayId = -1;
        latest = null;

        if (r != null) {
            try {
                r.setOnImageAvailableListener(null, null);
            } catch (Throwable ignored) {
            }
            try {
                r.close();
            } catch (Throwable ignored) {
            }
        }
        if (v != null) {
            try {
                v.release();
            } catch (Throwable ignored) {
            }
        }
        return old > 0 ? "OK released " + old : "OK nothing";
    }

    @Override
    public String startApp(String pkg) {
        if (displayId <= 0) {
            return "FAIL no display";
        }
        Context c = app();
        if (c == null) {
            return "FAIL no context";
        }
        String comp = null;
        try {
            Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) {
                return "FAIL no launcher for " + pkg;
            }
            android.content.ComponentName cn = launch.getComponent();
            if (cn == null) {
                cn = launch.resolveActivity(c.getPackageManager());
            }
            if (cn == null) {
                return "FAIL no component for " + pkg;
            }
            comp = cn.flattenToShortString();
        } catch (Throwable t) {
            return "FAIL resolve " + t;
        }

        String out = exec("am start --display " + displayId
                + " --user current -n " + comp);
        sleep(1200);
        return "OK " + comp + (out.length() == 0 ? "" : " | " + out);
    }

    private boolean send(InputEvent ev) {
        if (injectUsable == 0) {
            return false;
        }
        Object im = input();
        if (im == null || mInjectPlain == null) {
            injectUsable = 0;
            return false;
        }

        if (displayId > 0 && mSetDisplayId != null) {
            try {
                mSetDisplayId.invoke(ev, displayId);
            } catch (Throwable ignored) {
            }
        }

        try {
            Object r = mInjectPlain.invoke(im, ev, injectMode);
            boolean ok = Boolean.TRUE.equals(r);
            if (!ok) {
                injectUsable = 0;
            }
            return ok;
        } catch (Throwable t) {
            injectUsable = 0;
            return false;
        }
    }

    private void probeInject() {
        if (injectUsable == 0 || displayId <= 0) {
            return;
        }
        if (injectUsable == 1) {
            return;
        }
        int cx = w / 2;
        int cy = h / 2;
        long d = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(d, d, MotionEvent.ACTION_DOWN,
                cx, cy, 1f, 1f, 0, 1f, 1f, 0, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            Object im = input();
            if (im == null || mInjectPlain == null) {
                injectUsable = 0;
                return;
            }
            if (displayId > 0 && mSetDisplayId != null) {
                try {
                    mSetDisplayId.invoke(ev, displayId);
                } catch (Throwable ignored) {
                }
            }
            Object r = mInjectPlain.invoke(im, ev, injectMode);
            injectUsable = Boolean.TRUE.equals(r) ? 1 : 0;
        } catch (Throwable t) {
            injectUsable = 0;
        } finally {
            ev.recycle();
        }
        MotionEvent up = MotionEvent.obtain(d, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_CANCEL, cx, cy, 0f, 1f, 0, 1f, 1f, 0, 0);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            send(up);
        } catch (Throwable ignored) {
        } finally {
            up.recycle();
        }
    }

    private boolean touch(int x, int y, int action, long downTime) {
        float pressure = action == MotionEvent.ACTION_UP ? 0f : 1f;
        MotionEvent ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                action, x, y, pressure, 1f, 0, 1f, 1f, 0, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            return send(ev);
        } finally {
            ev.recycle();
        }
    }

    @Override
    public String click(int x, int y, int holdMs) {
        if (displayId <= 0) {
            return "FAIL no display";
        }
        if (holdMs < 30) {
            holdMs = 30;
        }
        if (holdMs > 400) {
            holdMs = 400;
        }
        long down = SystemClock.uptimeMillis();
        boolean a = touch(x, y, MotionEvent.ACTION_DOWN, down);
        sleep(holdMs);
        boolean b = touch(x, y, MotionEvent.ACTION_UP, down);
        if (a || b) {
            return "OK tap " + x + "," + y;
        }
        String out = exec("input -d " + displayId + " tap " + x + " " + y);
        if (out.length() > 0) {
            return "FAIL tap " + x + "," + y + " | " + out;
        }
        return "OK tap " + x + "," + y;
    }

    @Override
    public String longClick(int x, int y) {
        if (displayId <= 0) {
            return "FAIL no display";
        }
        long down = SystemClock.uptimeMillis();
        boolean a = touch(x, y, MotionEvent.ACTION_DOWN, down);
        sleep(LONG_PRESS_MS);
        boolean b = touch(x, y, MotionEvent.ACTION_UP, down);
        if (a || b) {
            return "OK long " + x + "," + y;
        }
        String out = exec("input -d " + displayId + " swipe " + x + " " + y
                + " " + x + " " + y + " " + LONG_PRESS_MS);
        if (out.length() > 0) {
            return "FAIL long " + x + "," + y + " | " + out;
        }
        return "OK long " + x + "," + y;
    }

    @Override
    public String drag(int x1, int y1, int x2, int y2, int holdMs, int durationMs) {
        if (displayId <= 0) {
            return "FAIL no display";
        }
        if (holdMs < LONG_PRESS_MS) {
            holdMs = LONG_PRESS_MS;
        }
        if (durationMs < 200) {
            durationMs = 200;
        }

        long down = SystemClock.uptimeMillis();
        boolean a = touch(x1, y1, MotionEvent.ACTION_DOWN, down);
        sleep(holdMs);

        int steps = Math.max(10, durationMs / 16);
        long startAt = SystemClock.uptimeMillis();
        for (int i = 1; i <= steps; i++) {
            float f = (float) i / steps;
            float e = f * f * (3f - 2f * f);
            int cx = Math.round(x1 + (x2 - x1) * e);
            int cy = Math.round(y1 + (y2 - y1) * e);
            touch(cx, cy, MotionEvent.ACTION_MOVE, down);
            long target = startAt + (long) durationMs * i / steps;
            long wait = target - SystemClock.uptimeMillis();
            if (wait > 0) {
                sleep(wait);
            }
        }
        touch(x2, y2, MotionEvent.ACTION_UP, down);

        if (a) {
            return "OK drag " + x1 + "," + y1 + " -> " + x2 + "," + y2;
        }
        String out = exec("input -d " + displayId + " swipe " + x1 + " " + y1
                + " " + x2 + " " + y2 + " " + (holdMs + durationMs));
        if (out.length() > 0) {
            return "FAIL drag | " + out;
        }
        return "OK drag " + x1 + "," + y1 + " -> " + x2 + "," + y2;
    }

    @Override
    public String swipe(int x1, int y1, int x2, int y2, int durationMs) {
        if (displayId <= 0) {
            return "FAIL no display";
        }
        if (durationMs < 100) {
            durationMs = 100;
        }
        long down = SystemClock.uptimeMillis();
        boolean a = touch(x1, y1, MotionEvent.ACTION_DOWN, down);

        int steps = Math.max(8, durationMs / 16);
        long startAt = SystemClock.uptimeMillis();
        for (int i = 1; i <= steps; i++) {
            float f = (float) i / steps;
            float e = f * f * (3f - 2f * f);
            int cx = Math.round(x1 + (x2 - x1) * e);
            int cy = Math.round(y1 + (y2 - y1) * e);
            touch(cx, cy, MotionEvent.ACTION_MOVE, down);
            long target = startAt + (long) durationMs * i / steps;
            long wait = target - SystemClock.uptimeMillis();
            if (wait > 0) {
                sleep(wait);
            }
        }
        touch(x2, y2, MotionEvent.ACTION_UP, down);

        if (a) {
            return "OK swipe " + x1 + "," + y1 + " -> " + x2 + "," + y2;
        }
        String out = exec("input -d " + displayId + " swipe " + x1 + " " + y1
                + " " + x2 + " " + y2 + " " + durationMs);
        if (out.length() > 0) {
            return "FAIL swipe | " + out;
        }
        return "OK swipe " + x1 + "," + y1 + " -> " + x2 + "," + y2;
    }

    @Override
    public String key(int keycode) {
        if (displayId <= 0) {
            return "FAIL no display";
        }
        long down = SystemClock.uptimeMillis();
        KeyEvent kd = new KeyEvent(down, SystemClock.uptimeMillis(),
                KeyEvent.ACTION_DOWN, keycode, 0);
        kd.setSource(InputDevice.SOURCE_KEYBOARD);
        boolean a = send(kd);
        sleep(30);
        KeyEvent ku = new KeyEvent(down, SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP, keycode, 0);
        ku.setSource(InputDevice.SOURCE_KEYBOARD);
        boolean b = send(ku);
        if (a || b) {
            return "OK key " + keycode;
        }
        String out = exec("input -d " + displayId + " keyevent " + keycode);
        if (out.length() > 0) {
            return "FAIL key | " + out;
        }
        return "OK key " + keycode;
    }

    @Override
    public void capture(ParcelFileDescriptor out) {
        Bitmap b = grab(3000L);
        if (b == null) {
            closeQuietly(out);
            return;
        }
        try {
            OutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(out);
            b.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.flush();
            os.close();
        } catch (Throwable ignored) {
            closeQuietly(out);
        } finally {
            try {
                b.recycle();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void preview(int maxWidth, int quality, ParcelFileDescriptor out) {
        Bitmap src = grab(900L);
        if (src == null) {
            closeQuietly(out);
            return;
        }
        Bitmap small = src;
        try {
            int sw = src.getWidth();
            int sh = src.getHeight();
            if (maxWidth > 0 && sw > maxWidth) {
                int nw = maxWidth;
                int nh = Math.max(1, Math.round((float) sh * nw / sw));
                small = Bitmap.createScaledBitmap(src, nw, nh, true);
            }
            if (quality < 30) {
                quality = 30;
            }
            if (quality > 95) {
                quality = 95;
            }
            OutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(out);
            small.compress(Bitmap.CompressFormat.JPEG, quality, os);
            os.flush();
            os.close();
        } catch (Throwable ignored) {
            closeQuietly(out);
        } finally {
            if (small != null && small != src) {
                try {
                    small.recycle();
                } catch (Throwable ignored) {
                }
            }
            try {
                src.recycle();
            } catch (Throwable ignored) {
            }
        }
    }

    private void closeQuietly(ParcelFileDescriptor p) {
        try {
            p.close();
        } catch (Throwable ignored) {
        }
    }

    private Bitmap grab(long timeoutMs) {
        long end = SystemClock.uptimeMillis() + timeoutMs;
        Bitmap b = latest;
        while (b == null && SystemClock.uptimeMillis() < end) {
            sleep(25);
            b = latest;
        }
        if (b == null) {
            return null;
        }
        try {
            return b.copy(Bitmap.Config.ARGB_8888, false);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap toBitmap(Image img, int w, int h) {
        try {
            Image.Plane p = img.getPlanes()[0];
            ByteBuffer buf = p.getBuffer();
            int rowStride = p.getRowStride();
            int pixStride = p.getPixelStride();
            int iw = img.getWidth();
            int ih = img.getHeight();
            if (iw <= 0) {
                iw = w;
            }
            if (ih <= 0) {
                ih = h;
            }

            Bitmap bmp = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888);
            if (pixStride == 4 && rowStride == iw * 4) {
                buf.rewind();
                bmp.copyPixelsFromBuffer(buf);
            } else {
                byte[] row = new byte[rowStride];
                int[] pix = new int[iw * ih];
                for (int y = 0; y < ih; y++) {
                    buf.position(y * rowStride);
                    int room = Math.min(rowStride, buf.remaining());
                    if (room <= 0) {
                        break;
                    }
                    buf.get(row, 0, room);
                    for (int x = 0; x < iw; x++) {
                        int o = x * pixStride;
                        if (o + 3 >= room) {
                            break;
                        }
                        int r = row[o] & 0xFF;
                        int g = row[o + 1] & 0xFF;
                        int b = row[o + 2] & 0xFF;
                        int a = row[o + 3] & 0xFF;
                        pix[y * iw + x] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }
                bmp.setPixels(pix, 0, iw, 0, 0, iw, ih);
            }
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public String info() {
        StringBuilder b = new StringBuilder();
        b.append("ready=").append(displayId > 0).append('\n');
        b.append("displayId=").append(displayId).append('\n');
        b.append("width=").append(w).append('\n');
        b.append("height=").append(h).append('\n');
        b.append("dpi=").append(dpi).append('\n');
        b.append("name=").append(name).append('\n');
        b.append("frame_at=").append(lastFrameAt).append('\n');
        b.append("has_frame=").append(latest != null).append('\n');
        b.append("ctx=").append(ctxNote).append('\n');

        Context c = app();
        if (c != null && displayId > 0) {
            try {
                DisplayManager dm = (DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE);
                Display d = dm.getDisplay(displayId);
                if (d != null) {
                    Point real = new Point();
                    d.getRealSize(real);
                    Point logical = new Point();
                    d.getSize(logical);
                    b.append("real=").append(real.x).append('x').append(real.y).append('\n');
                    b.append("logical=").append(logical.x).append('x').append(logical.y).append('\n');
                    b.append("rotation=").append(d.getRotation()).append('\n');
                    b.append("state=").append(d.getState()).append('\n');
                }
            } catch (Throwable t) {
                b.append("display_err=").append(t).append('\n');
            }
        }
        return b.toString();
    }

    @Override
    public String shell(String cmd) {
        return exec(cmd);
    }

    private static final String IME_ID = "com.mcpmate.bridge/.McpIme";
    private static final String IME_BACKUP = "/data/local/tmp/mcp_ime_backup";

    private static String currentIme() {
        return exec("settings get secure default_input_method").trim();
    }

    @Override
    public String activateIme() {
        try {
            String cur = currentIme();
            if (IME_ID.equals(cur)) {
                return "OK ime already active";
            }
            if (cur.length() > 0 && !"null".equals(cur)) {
                exec("echo '" + cur + "' > " + IME_BACKUP);
            }
            exec("ime enable " + IME_ID);
            exec("ime set " + IME_ID);
            String now = currentIme();
            if (IME_ID.equals(now)) {
                return "OK ime active";
            }
            return "FAIL cannot switch, now=" + now;
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    @Override
    public String deactivateIme() {
        try {
            String cur = currentIme();
            if (!IME_ID.equals(cur)) {
                return "OK already restored";
            }
            String back = exec("cat " + IME_BACKUP + " 2>/dev/null").trim();
            if (back.length() == 0 || "null".equals(back) || IME_ID.equals(back)) {
                return "FAIL no backup to restore";
            }
            exec("ime set " + back);
            String now = currentIme();
            if (back.equals(now)) {
                return "OK restored";
            }
            return "FAIL restore, now=" + now;
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }

    @Override
    public void registerClient(IBinder client) {
        if (client == null) {
            clientNote = "null";
            return;
        }
        clientToken = client;
        try {
            client.linkToDeath(new IBinder.DeathRecipient() {
                public void binderDied() {
                    shutdownByClientDeath("linkToDeath");
                }
            }, 0);
            clientNote = "linked";
        } catch (Throwable t) {
            clientNote = "link fail: " + t.getClass().getSimpleName();
        }
        startClientWatch();
    }

    private static volatile IBinder clientToken;
    private static volatile String clientNote = "not registered";
    private static volatile boolean watchStarted = false;

    private void startClientWatch() {
        synchronized (VdSvc.class) {
            if (watchStarted) {
                return;
            }
            watchStarted = true;
        }
        Thread t = new Thread(new Runnable() {
            public void run() {
                sleep(8000L);
                while (true) {
                    sleep(3000L);
                    IBinder tok = clientToken;
                    if (tok == null) {
                        continue;
                    }
                    try {
                        if (tok.isBinderAlive()) {
                            continue;
                        }
                    } catch (Throwable ignored) {
                        continue;
                    }
                    shutdownByClientDeath("isBinderAlive");
                    return;
                }
            }
        }, "vd-client-watch");
        t.setDaemon(true);
        t.start();
    }

    private void shutdownByClientDeath(String why) {
        clientNote = "client gone via " + why;
        try {
            destroy();
        } catch (Throwable ignored) {
        }
        sleep(300L);
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    public String listDisplays() {
        StringBuilder b = new StringBuilder();
        StringBuilder mine = new StringBuilder();
        StringBuilder other = new StringBuilder();
        int mineCount = 0;
        int otherCount = 0;

        String ids = exec("dumpsys display | grep -o 'mDisplayId=[0-9]*'"
                + " | sed 's/.*=//' | sort -un");
        for (String sid : ids.split("\n")) {
            String id = sid.trim();
            if (id.length() == 0 || "0".equals(id)) {
                continue;
            }
            String head = exec("dumpsys display | grep -A3 'mDisplayId="
                    + id + " ' | tr '\\n' ' ' | head -c 300");
            String state = field(head, "state=");
            String name = exec("dumpsys display | grep -B2 -A20 'mDisplayId="
                    + id + " ' | grep -o 'mcp-vd-[0-9]*' | head -1");
            String tasks = exec("dumpsys window displays | grep 'Display: mDisplayId="
                    + id + " ' | grep -o 'rootTasks=[0-9]*' | head -1");

            boolean isMine = name.length() > 0 || displayId == parseIntSafe(id);

            StringBuilder line = new StringBuilder();
            line.append("id=").append(id);
            if (name.length() > 0) {
                line.append("  ").append(name);
            } else {
                line.append("  (其他来源)");
            }
            if (state.length() > 0) {
                line.append("  ").append(state);
            }
            if (tasks.length() > 0) {
                line.append("  ").append(tasks);
            }
            line.append('\n');

            if (isMine) {
                mine.append(line);
                mineCount++;
            } else {
                other.append(line);
                otherCount++;
            }
        }

        String procs = exec("ps -A -o PID,USER,ARGS 2>/dev/null"
                + " | grep 'com.mcpmate.bridge:vd' | grep -v grep");
        int procCount = 0;
        for (String ln : procs.split("\n")) {
            if (ln.trim().length() > 0) {
                procCount++;
            }
        }

        b.append("本应用虚拟屏: ").append(mineCount).append('\n');
        if (mineCount > 0) {
            b.append(mine);
        }
        b.append('\n');
        b.append("本应用服务进程: ").append(procCount).append('\n');
        if (procCount > 0) {
            b.append(procs).append('\n');
        }
        if (otherCount > 0) {
            b.append('\n');
            b.append("其他应用虚拟屏（不可操作）: ").append(otherCount).append('\n');
            b.append(other);
        }
        return b.toString();
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String field(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) {
            return "-";
        }
        int j = i + key.length();
        int k = j;
        while (k < s.length()) {
            char c = s.charAt(k);
            if (c == ' ' || c == '\n' || c == ',') {
                break;
            }
            k++;
        }
        return s.substring(i, k);
    }

    @Override
    public String killAllDisplays() {
        StringBuilder b = new StringBuilder();

        if (displayId > 0) {
            b.append("当前屏: ").append(destroy()).append('\n');
        }

        String pids = exec("ps -A -o PID,ARGS 2>/dev/null"
                + " | grep 'com.mcpmate.bridge:vd' | grep -v grep | awk '{print $1}'");
        int me = android.os.Process.myPid();
        int killed = 0;
        StringBuilder detail = new StringBuilder();
        for (String sp : pids.split("\n")) {
            String t = sp.trim();
            if (t.length() == 0) {
                continue;
            }
            int p = parseIntSafe(t);
            if (p <= 0) {
                continue;
            }
            if (p == me) {
                continue;
            }
            exec("kill -9 " + p);
            detail.append("  killed pid ").append(p).append('\n');
            killed++;
        }

        b.append("清理孤儿进程: ").append(killed).append('\n');
        if (detail.length() > 0) {
            b.append(detail);
        }

        sleep(1200);

        String left = exec("dumpsys display | grep -o 'mcp-vd-[0-9]*' | sort -u");
        if (left.length() == 0) {
            b.append("剩余本应用虚拟屏: 0");
        } else {
            b.append("剩余标记: ").append(left.replace("\n", ", "));
        }
        return b.toString();
    }

    @Override
    public String rotate(int orientation) {
        if (displayId <= 0) {
            return "FAIL no display";
        }
        int nw = orientation == 1 ? Math.max(w, h) : Math.min(w, h);
        int nh = orientation == 1 ? Math.min(w, h) : Math.max(w, h);
        if (nw == w && nh == h) {
            return "OK already " + w + "x" + h;
        }
        return resizeTo(nw, nh, dpi);
    }

    private synchronized String resizeTo(int nw, int nh, int ndpi) {
        VirtualDisplay v = vd;
        if (v == null) {
            return "FAIL no display";
        }
        ImageReader old = reader;
        ImageReader fresh = null;
        try {
            fresh = ImageReader.newInstance(nw, nh, PixelFormat.RGBA_8888, 3);
            final int cw = nw;
            final int ch = nh;
            fresh.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                public void onImageAvailable(ImageReader ir) {
                    Image img = null;
                    try {
                        img = ir.acquireLatestImage();
                        if (img != null) {
                            Bitmap bmp = toBitmap(img, cw, ch);
                            if (bmp != null) {
                                Bitmap prev = latest;
                                latest = bmp;
                                lastFrameAt = SystemClock.uptimeMillis();
                                if (prev != null) {
                                    try {
                                        prev.recycle();
                                    } catch (Throwable ignored) {
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        if (img != null) {
                            try {
                                img.close();
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            }, readerHandler());

            v.setSurface(fresh.getSurface());
            v.resize(nw, nh, ndpi);

            reader = fresh;
            w = nw;
            h = nh;
            dpi = ndpi;
            latest = null;

            if (old != null) {
                try {
                    old.setOnImageAvailableListener(null, null);
                } catch (Throwable ignored) {
                }
                try {
                    old.close();
                } catch (Throwable ignored) {
                }
            }
            return "OK resized " + nw + "x" + nh;
        } catch (Throwable t) {
            if (fresh != null) {
                try {
                    fresh.close();
                } catch (Throwable ignored) {
                }
            }
            return "FAIL resize " + t;
        }
    }

    private void startOrientationWatch() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (displayId > 0) {
                    sleep(1500);
                    if (displayId <= 0) {
                        return;
                    }
                    try {
                        int[] cur = currentSize();
                        if (cur == null) {
                            continue;
                        }
                        if (cur[0] == w && cur[1] == h) {
                            continue;
                        }
                        if ((cur[0] == h && cur[1] == w)) {
                            resizeTo(cur[0], cur[1], dpi);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, "vd-orient");
        t.setDaemon(true);
        t.start();
    }

    private int[] currentSize() {
        try {
            String out = exec("dumpsys window displays 2>/dev/null | grep 'Display: mDisplayId="
                    + displayId + " '");
            if (out.length() == 0) {
                return null;
            }
            String[] parts = out.split("\\s+");
            for (String p : parts) {
                if (p.startsWith("cur=")) {
                    String v = p.substring(4);
                    int x = v.indexOf('x');
                    if (x <= 0) {
                        return null;
                    }
                    int ww = Integer.parseInt(v.substring(0, x));
                    int hh = Integer.parseInt(v.substring(x + 1));
                    return new int[]{ww, hh};
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String exec(String cmd) {
        StringBuilder b = new StringBuilder();
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
            pb.redirectErrorStream(true);
            p = pb.start();
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()));
            String line;
            int n = 0;
            while ((line = r.readLine()) != null && n < 20) {
                b.append(line).append('\n');
                n++;
            }
            p.waitFor();
        } catch (Throwable t) {
            b.append("ERR ").append(t);
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
        return b.toString().trim();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Throwable ignored) {
        }
    }
}
