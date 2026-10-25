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
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public class StopBar {

    public static final String MARK = "MCP急停开关";

    private static final int SIZE_DP = 52;
    private static final int SIZE_SMALL_DP = 38;

    private static volatile boolean small;

    public static void setSmall(boolean v) {
        if (small == v) {
            return;
        }
        small = v;
        Context c = BridgeService.ctx();
        if (c != null && shown) {
            hide();
            show(c);
        }
    }

    public static boolean isSmall() {
        return small;
    }

    public static void refresh(Context c) {
        if (!shown || c == null) {
            return;
        }
        hide();
        show(c);
    }

    private static WindowManager wm;
    private static FrameLayout root;
    private static TextView label;
    private static volatile boolean shown;
    private static final Rect RECT = new Rect();

    public static boolean hitBall(int x, int y) {
        if (!shown) {
            return false;
        }
        synchronized (RECT) {
            return RECT.width() > 0 && RECT.contains(x, y);
        }
    }

    public static String boundsText() {
        synchronized (RECT) {
            if (RECT.width() <= 0) {
                return "";
            }
            return RECT.left + "," + RECT.top + "-"
                    + RECT.right + "," + RECT.bottom;
        }
    }

    private static void syncRect(Context c, WindowManager.LayoutParams lp, int sizePx) {
        int sw = 0;
        try {
            Display d = ((WindowManager) c.getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
            Point p = new Point();
            d.getRealSize(p);
            sw = p.x;
        } catch (Throwable ignored) {
        }
        if (sw <= 0) {
            try {
                sw = c.getResources().getDisplayMetrics().widthPixels;
            } catch (Throwable ignored) {
            }
        }
        if (sw <= 0) {
            return;
        }
        int margin = dp(c, 8);
        int left = sw - sizePx - lp.x;
        int top = lp.y;
        synchronized (RECT) {
            RECT.set(left - margin, top - margin,
                    left + sizePx + margin, top + sizePx + margin);
        }
    }

    public static boolean canDraw(Context c) {
        try {
            if (Build.VERSION.SDK_INT < 23) {
                return true;
            }
            return Settings.canDrawOverlays(c);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isShown() {
        return shown;
    }

    private static int dp(Context c, int v) {
        try {
            return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
        } catch (Throwable t) {
            return v * 3;
        }
    }

    private static int bgColor(boolean paused) {
        return paused ? 0xFF2E7D32 : 0xFFD32F2F;
    }

    private static GradientDrawable circle(Context c, boolean paused) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(bgColor(paused));
        g.setStroke(dp(c, 2), 0x40FFFFFF);
        return g;
    }

    public static synchronized void show(Context ctx) {
        if (shown) {
            return;
        }
        if (!canDraw(ctx)) {
            Log.add("悬浮球未显示：缺少悬浮窗权限");
            return;
        }
        try {
            final Context app = ctx.getApplicationContext();
            wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                return;
            }

            final int sizePx = dp(app, small ? SIZE_SMALL_DP : SIZE_DP);
            final boolean paused = Config.isPaused();

            FrameLayout box = new FrameLayout(app);
            GradientDrawable base = circle(app, paused);
            box.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(0x40FFFFFF), base, null));
            box.setElevation(dp(app, 7));

            TextView t = new TextView(app);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(15);
            t.setTextColor(Color.WHITE);
            t.setText(paused ? "续" : "停");
            t.setContentDescription(desc());
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            box.addView(t, tp);

            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    sizePx, sizePx, type, flags, PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = dp(app, 12);
            lp.y = dp(app, 132);

            box.setOnTouchListener(new View.OnTouchListener() {
                float downX;
                float downY;
                int startX;
                int startY;
                boolean dragging;

                @Override
                public boolean onTouch(View host, MotionEvent e) {
                    int act = e.getActionMasked();
                    if (act == MotionEvent.ACTION_DOWN) {
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        dragging = false;
                        return true;
                    }
                    if (act == MotionEvent.ACTION_MOVE) {
                        int dx = (int) (e.getRawX() - downX);
                        int dy = (int) (e.getRawY() - downY);
                        if (!dragging
                                && (Math.abs(dx) > dp(app, 6) || Math.abs(dy) > dp(app, 6))) {
                            dragging = true;
                        }
                        if (dragging) {
                            lp.x = startX - dx;
                            lp.y = startY + dy;
                            if (lp.x < 0) {
                                lp.x = 0;
                            }
                            if (lp.y < 0) {
                                lp.y = 0;
                            }
                            try {
                                wm.updateViewLayout(host, lp);
                            } catch (Throwable ignored) {
                            }
                            syncRect(app, lp, sizePx);
                        }
                        return true;
                    }
                    if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                        if (!dragging) {
                            toggle(app);
                        }
                        return true;
                    }
                    return true;
                }
            });

            wm.addView(box, lp);
            root = box;
            label = t;
            shown = true;
            syncRect(app, lp, sizePx);
            Log.add("悬浮球已就位（点击急停，长按拖动）");
        } catch (Throwable t) {
            Log.add("悬浮球创建失败: " + t);
        }
    }

    public static synchronized void hide() {
        try {
            if (wm != null && root != null) {
                wm.removeView(root);
            }
        } catch (Throwable ignored) {
        }
        root = null;
        label = null;
        wm = null;
        shown = false;
        synchronized (RECT) {
            RECT.setEmpty();
        }
    }

    private static String desc() {
        return MARK + "：用户专用安全按钮。点一下立即暂停 AI 操作，再点恢复，长按可拖动。"
                + "当前状态=" + (Config.isPaused() ? "已暂停" : "运行中");
    }

    private static void toggle(Context app) {
        boolean nowPaused = !Config.isPaused();
        Config.setPaused(nowPaused);
        String msg = nowPaused ? "已急停：AI 的所有操作已被阻止" : "已恢复：允许 AI 操作";
        Log.add(msg);
        try {
            if (root != null) {
                root.setBackground(new RippleDrawable(
                        ColorStateList.valueOf(0x40FFFFFF),
                        circle(app, nowPaused), null));
            }
            if (label != null) {
                label.setText(nowPaused ? "续" : "停");
                label.setContentDescription(desc());
            }
        } catch (Throwable ignored) {
        }
        try {
            Notif.alert(app, nowPaused);
        } catch (Throwable ignored) {
        }
        try {
            android.widget.Toast.makeText(app, msg,
                    android.widget.Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
        }
    }
}
