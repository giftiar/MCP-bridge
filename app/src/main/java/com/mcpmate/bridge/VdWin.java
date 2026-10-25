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
    private static final int OPEN_DP = 200;
    private static final int PREVIEW_W = 420;
    private static final int PREVIEW_Q = 62;
    private static final long FRAME_GAP = 120L;

    private final Context ctx;
    private final WindowManager wm;
    private final float aspect;

    private FrameLayout root;
    private FrameLayout body;
    private ImageView image;
    private TextView badge;
    private TextView btn;
    private WindowManager.LayoutParams lp;

    private volatile boolean open;
    private volatile boolean loop;
    private Thread poll;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private float downRawX;
    private float downRawY;
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
        int w = Config.getVdW() > 0 ? Config.getVdW() : VdMgr.DEF_W;
        int h = Config.getVdH() > 0 ? Config.getVdH() : VdMgr.DEF_H;
        aspect = h / (float) w;
    }

    public static void refresh(Context c) {
        VdWin w = inst;
        if (w != null) {
            int[] sz = VdMgr.autoSize(c);
            Config.setVdW(sz[0]);
            Config.setVdH(sz[1]);
            w.rebuild();
        } else {
            show(c);
        }
    }

    private void rebuild() {
        boolean wasOpen = open;
        int x = lp.x;
        int y = lp.y;
        int ww = lp.width;
        detach();
        open = wasOpen;
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
            android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            scrW = dm.widthPixels;
            scrH = dm.heightPixels;

            lp = new WindowManager.LayoutParams();
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            lp.format = PixelFormat.TRANSLUCENT;
            lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            lp.gravity = Gravity.TOP | Gravity.START;

            int savedX = Config.getVdBallX();
            int savedY = Config.getVdBallY();
            open = Config.isVdBallOpen();

            int w = open ? dp(OPEN_DP) : dp(BALL_DP);
            int h = open ? Math.round(w * aspect) : dp(BALL_DP);
            lp.width = w;
            lp.height = h;
            lp.x = savedX >= 0 ? savedX : Math.max(0, scrW - w - dp(16));
            lp.y = savedY >= 0 ? savedY : Math.max(dp(60), Math.round(scrH * 0.62f));

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
            ilp.setMargins(dp(2), dp(2), dp(2), dp(2));
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
        loop = false;
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
        int w = dp(OPEN_DP);
        int h = Math.round(w * aspect);
        int cx = lp.x + lp.width / 2;
        int cy = lp.y + lp.height / 2;
        lp.width = w;
        lp.height = h;
        lp.x = clamp(cx - w / 2, 0, Math.max(0, scrW - w));
        lp.y = clamp(cy - h / 2, 0, Math.max(0, scrH - h));
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
        loop = false;
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
                moved = false;
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (open && e.getPointerCount() >= 2) {
                    pinching = true;
                    startDist = spacing(e);
                    startW = lp.width;
                    startH = lp.height;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (pinching && e.getPointerCount() >= 2) {
                    float d = spacing(e);
                    if (startDist > 10f) {
                        resize(startW, d / startDist);
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
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                startX = lp.x;
                startY = lp.y;
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pinching = false;
                Config.setVdBallPos(lp.x, lp.y);
                if (!moved && !open) {
                    expand();
                }
                return true;

            default:
                return false;
        }
    }

    private void resize(int baseW, float k) {
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
        if (loop) {
            return;
        }
        loop = true;
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (loop) {
                    long t0 = System.currentTimeMillis();
                    byte[] jpg = null;
                    try {
                        jpg = VdMgr.preview(PREVIEW_W, PREVIEW_Q);
                    } catch (Throwable ignored) {
                    }
                    if (jpg != null && jpg.length > 4) {
                        final Bitmap bmp = BitmapFactory.decodeByteArray(jpg, 0, jpg.length);
                        if (bmp != null) {
                            ui.post(new Runnable() {
                                public void run() {
                                    draw(bmp);
                                }
                            });
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
