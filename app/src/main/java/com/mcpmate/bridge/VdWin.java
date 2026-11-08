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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class VdWin {

    private static VdWin inst;

    private static final int BALL_DP = 38;
    private static final int OPEN_DP = 220;
    private static final int OPEN_DP_LAND = 340;
    private static final int PREVIEW_LONG = 1100;
    private static final int PREVIEW_Q = 72;
    private static final long FRAME_GAP = 120L;
    private static final int TAP_SLOP_DP = 8;

    private final Context ctx;
    private final WindowManager wm;
    private float aspect = 1f;

    private FrameLayout root;
    private FrameLayout body;
    private ImageView image;
    private TextView badge;
    private TextView btn;
    private TextView rot;
    private volatile boolean rotBusy;
    private volatile float lastPreviewAspect;
    private WindowManager.LayoutParams lp;

    private volatile boolean open;
    private volatile int pollGen;
    private Thread poll;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private float downRawX;
    private float downRawY;
    private float lastContentX;
    private float lastContentY;
    private static final java.util.concurrent.ExecutorService TOUCH =
            java.util.concurrent.Executors.newSingleThreadExecutor(
                    new java.util.concurrent.ThreadFactory() {
                        public Thread newThread(Runnable r) {
                            Thread t = new Thread(r, "vd-utouch");
                            t.setDaemon(true);
                            return t;
                        }
                    });
    private boolean twoFinger;
    private boolean contentTouch;
    private boolean customSize;
    private boolean pendingDown;
    private long lastMoveAt;
    private static final long MOVE_GAP = 16L;
    private int startX;
    private int startY;
    private int startW;
    private int startH;
    private float startDist;
    private boolean pinching;
    private boolean moved;

    private int scrW;
    private int scrH;

    public static void show(Context c) {
        if (inst != null) {
            return;
        }
        VdWin w = new VdWin(c);
        if (w.attach()) {
            inst = w;
            Log.add("虚拟屏小窗已就绪");
        } else {
            Log.add("虚拟屏小窗创建失败");
        }
    }

    public static void hide() {
        VdWin w = inst;
        inst = null;
        if (w != null) {
            w.detach();
        }
    }

    public static boolean isShowing() {
        return inst != null;
    }

    public static void collapseIfOpen() {
        VdWin w = inst;
        if (w != null && w.open) {
            w.collapse();
        }
    }

    private VdWin(Context c) {
        ctx = c.getApplicationContext();
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        recomputeAspect();
    }

    private int[] realSize() {
        int w = 0;
        int h = 0;
        try {
            if (wm != null) {
                android.graphics.Point p = new android.graphics.Point();
                wm.getDefaultDisplay().getRealSize(p);
                w = p.x;
                h = p.y;
            }
        } catch (Throwable ignored) {
        }
        if (w <= 0 || h <= 0) {
            android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            w = dm.widthPixels;
            h = dm.heightPixels;
        }
        return new int[]{w, h};
    }

    private void recomputeAspect() {
        int w = Config.getVdW();
        int h = Config.getVdH();
        boolean land = Config.getVdOrient() == 1;
        if (w <= 0 || h <= 0) {
            int[] s = VdMgr.autoSize(ctx);
            w = land ? s[1] : s[0];
            h = land ? s[0] : s[1];
        } else if (land != (w > h)) {
            int t = w;
            w = h;
            h = t;
        }
        aspect = (w > 0 && h > 0) ? h / (float) w : 1f;
    }

    public static void refresh(Context c) {
        VdWin w = inst;
        if (w != null) {
            w.rebuild();
        } else {
            show(c);
        }
    }

    private void rebuild() {
        int x = lp.x;
        int y = lp.y;
        int ww = lp.width;
        recomputeAspect();
        detach();
        attach();
        if (root != null) {
            lp.x = x;
            lp.y = y;
            if (open) {
                lp.width = ww;
                lp.height = Math.round(ww * aspect);
            }
            push();
            Config.setVdBallPos(lp.x, lp.y);
        }
    }

    private boolean attach() {
        try {
            int[] real = realSize();
            scrW = real[0];
            scrH = real[1];

            lp = new WindowManager.LayoutParams();
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            lp.format = PixelFormat.TRANSLUCENT;
            lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            lp.gravity = Gravity.TOP | Gravity.START;

            int savedX = Config.getVdBallX();
            int savedY = Config.getVdBallY();
            open = Config.isVdBallOpen();

            if (open) {
                customSize = false;
                fitOpenSize();
            } else {
                lp.width = dp(BALL_DP);
                lp.height = dp(BALL_DP);
            }
            lp.x = savedX >= 0 ? savedX : Math.max(0, scrW - lp.width - dp(16));
            lp.y = savedY >= 0 ? savedY : Math.max(dp(60), Math.round(scrH * 0.62f));
            lp.x = clamp(lp.x, 0, Math.max(0, scrW - lp.width));
            lp.y = clamp(lp.y, 0, Math.max(0, scrH - lp.height));

            root = new FrameLayout(ctx);

            body = new FrameLayout(ctx);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xF01A1A1A);
            bg.setCornerRadius(dp(10));
            bg.setStroke(dp(2), 0xFF3DDC84);
            body.setBackground(bg);

            image = new ImageView(ctx);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(Color.BLACK);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(-1, -1);
            int grip = dp(10);
            ilp.setMargins(grip, grip, grip, grip);
            body.addView(image, ilp);

            badge = new TextView(ctx);
            badge.setTextColor(0xFF3DDC84);
            badge.setTextSize(8f);
            badge.setText("VD");
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2);
            blp.gravity = Gravity.BOTTOM | Gravity.START;
            blp.setMargins(dp(7), 0, 0, dp(4));
            body.addView(badge, blp);

            btn = new TextView(ctx);
            btn.setText("\u25BE");
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(11f);
            btn.setGravity(Gravity.CENTER);
            GradientDrawable cbg = new GradientDrawable();
            cbg.setColor(0xCC000000);
            cbg.setShape(GradientDrawable.OVAL);
            btn.setBackground(cbg);
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(22), dp(22));
            clp.gravity = Gravity.TOP | Gravity.END;
            clp.setMargins(0, dp(3), dp(3), 0);
            body.addView(btn, clp);
            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    collapse();
                }
            });

            rot = new TextView(ctx);
            rot.setText("\u21BB");
            rot.setTextColor(0xDDFFFFFF);
            rot.setTextSize(13f);
            rot.setGravity(Gravity.CENTER);
            GradientDrawable rbg = new GradientDrawable();
            rbg.setColor(0x55000000);
            rbg.setShape(GradientDrawable.OVAL);
            rot.setBackground(rbg);
            FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(dp(24), dp(24));
            rlp.gravity = Gravity.TOP | Gravity.START;
            rlp.setMargins(dp(3), dp(3), 0, 0);
            body.addView(rot, rlp);
            rot.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    doRotate();
                }
            });

            root.addView(body, new FrameLayout.LayoutParams(-1, -1));

            root.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent e) {
                    return handle(e);
                }
            });

            wm.addView(root, lp);
            applyMode();
            if (open) {
                startPoll();
            }
            return true;
        } catch (Throwable t) {
            Log.add("VdWin attach: " + t);
            root = null;
            return false;
        }
    }

    private void detach() {
        pollGen++;
        Thread t = poll;
        poll = null;
        if (t != null) {
            try {
                t.join(400L);
            } catch (Throwable ignored) {
            }
        }
        FrameLayout r = root;
        root = null;
        if (r != null) {
            try {
                if (r.getParent() != null) {
                    wm.removeViewImmediate(r);
                }
            } catch (Throwable ignored) {
                try {
                    wm.removeView(r);
                } catch (Throwable ignored2) {
                }
            }
        }
        if (image != null) {
            Bitmap old = (Bitmap) image.getTag();
            image.setTag(null);
            if (old != null) {
                try {
                    old.recycle();
                } catch (Throwable ignored) {
                }
            }
        }
        image = null;
        body = null;
        badge = null;
        btn = null;
        rot = null;
    }

    private void doRotate() {
        if (rotBusy) {
            return;
        }
        rotBusy = true;
        new Thread(new Runnable() {
            public void run() {
                boolean toLand = !VdMgr.isLandscape();
                String r;
                try {
                    r = VdMgr.rotate(toLand);
                } catch (Throwable t) {
                    r = "FAIL " + t;
                }
                Log.add("旋转屏幕 " + r);
                ui.post(new Runnable() {
                    public void run() {
                        rotBusy = false;
                        afterRotate();
                    }
                });
            }
        }, "vd-rot").start();
    }

    private void syncAspect(float pa) {
        if (pa <= 0.05f || root == null) {
            return;
        }
        if (Math.abs(pa - lastPreviewAspect) < 0.02f) {
            return;
        }
        lastPreviewAspect = pa;
        if (Math.abs(pa - aspect) < 0.02f) {
            return;
        }
        aspect = pa;
        syncConfigSize();
        if (open) {
            customSize = false;
            applyOpenSize();
            push();
            Config.setVdBallPos(lp.x, lp.y);
        }
        if (badge != null) {
            badge.setText(aspect >= 1f ? "竖" : "横");
        }
        Log.add("屏幕方向已同步 " + (aspect >= 1f ? "竖屏" : "横屏"));
    }

    private int openWidth() {
        if (aspect >= 1f) {
            return dp(OPEN_DP);
        }
        return Math.min(dp(OPEN_DP_LAND), Math.round(scrW * 0.88f));
    }

    private void fitOpenSize() {
        if (aspect <= 0.05f) {
            lp.width = dp(OPEN_DP);
            lp.height = dp(OPEN_DP);
            return;
        }
        int w = openWidth();
        int h = Math.round(w * aspect);
        int capH = Math.round(scrH * 0.62f);
        if (h > capH) {
            h = capH;
            w = Math.round(h / aspect);
        }
        if (w > scrW - dp(16)) {
            w = scrW - dp(16);
            h = Math.round(w * aspect);
        }
        lp.width = Math.max(dp(120), w);
        lp.height = Math.max(dp(72), h);
    }

    private void applyOpenSize() {
        int cx = lp.x + lp.width / 2;
        int cy = lp.y + lp.height / 2;
        if (customSize) {
            int nh = Math.round(lp.width * aspect);
            int capH = scrH - dp(40);
            if (nh > capH) {
                nh = capH;
                lp.width = Math.round(nh / aspect);
            }
            lp.height = nh;
        } else {
            fitOpenSize();
        }
        lp.x = clamp(cx - lp.width / 2, 0, Math.max(0, scrW - lp.width));
        lp.y = clamp(cy - lp.height / 2, 0, Math.max(0, scrH - lp.height));
    }

    private void syncConfigSize() {
        int w = Config.getVdW();
        int h = Config.getVdH();
        if (w <= 0 || h <= 0) {
            return;
        }
        int longSide = Math.max(w, h);
        int shortSide = Math.min(w, h);
        if (aspect >= 1f) {
            Config.setVdW(shortSide);
            Config.setVdH(longSide);
            Config.setVdOrient(0);
        } else {
            Config.setVdW(longSide);
            Config.setVdH(shortSide);
            Config.setVdOrient(1);
        }
    }

    private void afterRotate() {
        recomputeAspect();
        lastPreviewAspect = aspect;
        if (root == null) {
            return;
        }
        if (open) {
            customSize = false;
            applyOpenSize();
        } else {
            int d = dp(BALL_DP);
            lp.width = d;
            lp.height = d;
        }
        applyMode();
        push();
        Config.setVdBallPos(lp.x, lp.y);
    }

    private void applyMode() {
        if (body == null) {
            return;
        }
        if (open) {
            body.setBackground(bgOpen());
            if (image != null) {
                image.setVisibility(View.VISIBLE);
            }
            if (badge != null) {
                badge.setVisibility(View.VISIBLE);
            }
            if (btn != null) {
                btn.setVisibility(View.VISIBLE);
            }
            if (rot != null) {
                rot.setVisibility(View.VISIBLE);
            }
            if (badge != null) {
                badge.setText(aspect >= 1f ? "竖" : "横");
            }
        } else {
            body.setBackground(bgBall());
            if (image != null) {
                image.setVisibility(View.GONE);
            }
            if (badge != null) {
                badge.setVisibility(View.GONE);
            }
            if (btn != null) {
                btn.setVisibility(View.GONE);
            }
            if (rot != null) {
                rot.setVisibility(View.GONE);
            }
        }
    }

    private GradientDrawable bgOpen() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xF01A1A1A);
        g.setCornerRadius(dp(10));
        g.setStroke(dp(2), 0xFF3DDC84);
        return g;
    }

    private GradientDrawable bgBall() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xE62E7D32);
        g.setStroke(dp(2), 0xFF3DDC84);
        return g;
    }

    private void expand() {
        if (open || root == null) {
            return;
        }
        open = true;
        Config.setVdBallOpen(true);
        customSize = false;
        applyOpenSize();
        push();
        applyMode();
        startPoll();
    }

    private void collapse() {
        if (!open || root == null) {
            return;
        }
        open = false;
        Config.setVdBallOpen(false);
        int d = dp(BALL_DP);
        int cx = lp.x + lp.width / 2;
        int cy = lp.y + lp.height / 2;
        lp.width = d;
        lp.height = d;
        lp.x = clamp(cx - d / 2, 0, Math.max(0, scrW - d));
        lp.y = clamp(cy - d / 2, 0, Math.max(0, scrH - d));
        push();
        applyMode();
        pollGen++;
    }

    private float[] contentToDevice(float vx, float vy) {
        ImageView iv = image;
        if (iv == null) {
            return null;
        }
        android.graphics.drawable.Drawable d = iv.getDrawable();
        if (!(d instanceof android.graphics.drawable.BitmapDrawable)) {
            return null;
        }
        Bitmap bmp = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
        if (bmp == null || bmp.isRecycled()) {
            return null;
        }
        int iw = iv.getWidth();
        int ih = iv.getHeight();
        int bw = bmp.getWidth();
        int bh = bmp.getHeight();
        if (iw <= 0 || ih <= 0 || bw <= 0 || bh <= 0) {
            return null;
        }
        float sc = Math.min(iw / (float) bw, ih / (float) bh);
        float dw = bw * sc;
        float dh = bh * sc;
        float ox = (iw - dw) / 2f;
        float oy = (ih - dh) / 2f;
        if (vx < ox || vx > ox + dw || vy < oy || vy > oy + dh) {
            return null;
        }
        int devW = Config.getVdW();
        int devH = Config.getVdH();
        if (devW <= 0 || devH <= 0) {
            return null;
        }
        float px = (vx - ox) / sc;
        float py = (vy - oy) / sc;
        return new float[]{px * devW / bw, py * devH / bh};
    }

    private void post(Runnable a) {
        try {
            TOUCH.execute(a);
        } catch (Throwable ignored) {
        }
    }

    private void injectDown(float dx, float dy) {
        final int x = Math.round(dx);
        final int y = Math.round(dy);
        post(new Runnable() {
            public void run() {
                try {
                    String r = VdMgr.touchDown(x, y);
                    if (!r.startsWith("OK")) {
                        Log.add("手触 down 失败 " + r);
                    }
                } catch (Throwable t) {
                    Log.add("手触异常 " + t);
                }
            }
        });
    }

    private boolean moveWarned;

    private void injectMove(float dx, float dy) {
        final int x = Math.round(dx);
        final int y = Math.round(dy);
        post(new Runnable() {
            public void run() {
                try {
                    String r = VdMgr.touchMove(x, y);
                    if (!r.startsWith("OK") && !moveWarned) {
                        moveWarned = true;
                        Log.add("手触 move 失败 " + r);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private void injectUp(float dx, float dy) {
        final int x = Math.round(dx);
        final int y = Math.round(dy);
        post(new Runnable() {
            public void run() {
                try {
                    String r = VdMgr.touchUp(x, y);
                    if (!r.startsWith("OK")) {
                        Log.add("手触 up 失败 " + r);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private boolean handle(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                startX = lp.x;
                startY = lp.y;
                startW = lp.width;
                startH = lp.height;
                pinching = false;
                twoFinger = false;
                moved = false;
                contentTouch = false;
                if (open && image != null) {
                    float[] p = contentToDevice(e.getX() - image.getLeft(),
                            e.getY() - image.getTop());
                    if (p != null) {
                        contentTouch = true;
                        lastContentX = p[0];
                        lastContentY = p[1];
                        pendingDown = true;
                        lastMoveAt = 0L;
                        injectDown(p[0], p[1]);
                    }
                }
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (open && e.getPointerCount() >= 2) {
                    if (pendingDown) {
                        pendingDown = false;
                        injectUp(lastContentX, lastContentY);
                    }
                    twoFinger = true;
                    contentTouch = false;
                    pinching = false;
                    startDist = spacing(e);
                    startW = lp.width;
                    startH = lp.height;
                    downRawX = e.getRawX();
                    downRawY = e.getRawY();
                    startX = lp.x;
                    startY = lp.y;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (twoFinger && e.getPointerCount() >= 2) {
                    float d = spacing(e);
                    if (!pinching && Math.abs(d - startDist) > dp(14)) {
                        pinching = true;
                    }
                    if (pinching && startDist > 10f) {
                        resize(startW, d / startDist);
                    } else {
                        lp.x = clamp(startX + (int) (e.getRawX() - downRawX), 0,
                                Math.max(0, scrW - lp.width));
                        lp.y = clamp(startY + (int) (e.getRawY() - downRawY), 0,
                                Math.max(0, scrH - lp.height));
                        push();
                    }
                } else if (contentTouch && image != null) {
                    float dx = e.getRawX() - downRawX;
                    float dy = e.getRawY() - downRawY;
                    if (Math.abs(dx) > dp(TAP_SLOP_DP) || Math.abs(dy) > dp(TAP_SLOP_DP)) {
                        moved = true;
                    }
                    float[] p = contentToDevice(e.getX() - image.getLeft(),
                            e.getY() - image.getTop());
                    if (p != null) {
                        lastContentX = p[0];
                        lastContentY = p[1];
                        long now = SystemClock.uptimeMillis();
                        if (now - lastMoveAt >= MOVE_GAP) {
                            lastMoveAt = now;
                            injectMove(p[0], p[1]);
                        }
                    }
                } else {
                    float dx = e.getRawX() - downRawX;
                    float dy = e.getRawY() - downRawY;
                    if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) {
                        moved = true;
                    }
                    lp.x = clamp(startX + (int) dx, 0,
                            Math.max(0, scrW - lp.width));
                    lp.y = clamp(startY + (int) dy, 0,
                            Math.max(0, scrH - lp.height));
                    push();
                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                pinching = false;
                twoFinger = false;
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                startX = lp.x;
                startY = lp.y;
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean wasPinch = pinching || twoFinger;
                pinching = false;
                twoFinger = false;
                Config.setVdBallPos(lp.x, lp.y);
                if (!open) {
                    if (!moved) {
                        expand();
                    }
                    return true;
                }
                if (contentTouch && !wasPinch && pendingDown) {
                    float[] p = null;
                    if (image != null) {
                        p = contentToDevice(e.getX() - image.getLeft(),
                                e.getY() - image.getTop());
                    }
                    if (p == null) {
                        p = new float[]{lastContentX, lastContentY};
                    }
                    lastContentX = p[0];
                    lastContentY = p[1];
                    pendingDown = false;
                    injectUp(p[0], p[1]);
                }
                contentTouch = false;
                return true;

            default:
                return false;
        }
    }

    private void resize(int baseW, float k) {
        customSize = true;
        int nw = Math.round(baseW * k);
        nw = clamp(nw, dp(140), Math.min(dp(520), scrW));
        int nh = Math.round(nw * aspect);
        if (nh > scrH - dp(40)) {
            nh = scrH - dp(40);
            nw = Math.round(nh / aspect);
        }
        int cx = lp.x + lp.width / 2;
        int cy = lp.y + lp.height / 2;
        lp.width = nw;
        lp.height = nh;
        lp.x = clamp(cx - nw / 2, 0, Math.max(0, scrW - nw));
        lp.y = clamp(cy - nh / 2, 0, Math.max(0, scrH - nh));
        push();
    }

    private void push() {
        try {
            if (root != null && root.getParent() != null) {
                wm.updateViewLayout(root, lp);
            }
        } catch (Throwable ignored) {
        }
    }

    private static float spacing(MotionEvent e) {
        if (e.getPointerCount() < 2) {
            return 0f;
        }
        float dx = e.getX(0) - e.getX(1);
        float dy = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void startPoll() {
        final int gen = ++pollGen;
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (pollGen == gen) {
                    long t0 = System.currentTimeMillis();
                    byte[] jpg = null;
                    try {
                        jpg = VdMgr.preview(PREVIEW_LONG, PREVIEW_Q);
                    } catch (Throwable ignored) {
                    }
                    if (jpg != null && jpg.length > 4) {
                        BitmapFactory.Options opt = new BitmapFactory.Options();
                        opt.inMutable = true;
                        Bitmap decoded = BitmapFactory.decodeByteArray(
                                jpg, 0, jpg.length, opt);
                        if (decoded != null) {
                            Bitmap use = decoded;
                            if (!decoded.isMutable()) {
                                Bitmap cp = null;
                                try {
                                    cp = decoded.copy(Bitmap.Config.ARGB_8888, true);
                                } catch (Throwable ignored) {
                                }
                                decoded.recycle();
                                use = cp;
                            }
                            if (use != null) {
                                final Bitmap bmp = use;
                                Overlay.paint(bmp);
                                final int pw = bmp.getWidth();
                                final int ph = bmp.getHeight();
                                ui.post(new Runnable() {
                                    public void run() {
                                        if (pw > 0 && ph > 0) {
                                            syncAspect(ph / (float) pw);
                                        }
                                        draw(bmp);
                                    }
                                });
                            }
                        }
                    }
                    long cost = System.currentTimeMillis() - t0;
                    long wait = FRAME_GAP - cost;
                    if (wait > 0) {
                        try {
                            Thread.sleep(wait);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }, "vd-win");
        t.setDaemon(true);
        poll = t;
        t.start();
    }

    private void draw(Bitmap bmp) {
        ImageView iv = image;
        if (iv == null || !open) {
            try {
                bmp.recycle();
            } catch (Throwable ignored) {
            }
            return;
        }
        final Bitmap old = (Bitmap) iv.getTag();
        iv.setImageBitmap(bmp);
        iv.setTag(bmp);
        if (old != null && old != bmp) {
            ui.postDelayed(new Runnable() {
                public void run() {
                    try {
                        old.recycle();
                    } catch (Throwable ignored) {
                    }
                }
            }, 250L);
        }
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }
}
