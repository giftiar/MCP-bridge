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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class InfoBar {

    public static final String MARK = "MCP状态卡片";

    private static final int BALL_DP = 52;
    private static final int CARD_W = 214;
    private static final int CARD_H = 214;
    private static final int ROW_MAX = 4;
    private static final int R = 16;

    private static final int C_BG = 0xFFFAF7FF;
    private static final int C_HEAD = 0xFFEDE8F9;
    private static final int C_LINE = 0xFFDDD6EC;
    private static final int C_MAIN = 0xFF2C2A33;
    private static final int C_SUB = 0xFF6E6A7A;
    private static final int C_DIM = 0xFF9E99AA;
    private static final int C_STEP = 0xFF9A5B00;
    private static final int C_OK = 0xFF2E7D32;
    private static final int C_FAIL = 0xFFC62828;
    private static final int C_RUN = 0xFF34A853;
    private static final int C_PAUSE = 0xFFF57C00;

    private static WindowManager wm;
    private static FrameLayout root;
    private static LinearLayout card;
    private static LinearLayout list;
    private static TextView step;
    private static TextView status;
    private static TextView icon;
    private static View dot;

    private static volatile boolean shown;
    private static volatile boolean expanded;

    private static WindowManager.LayoutParams lp;
    private static int touchX;
    private static int touchY;
    private static boolean moved;

    public static boolean isShown() {
        return shown;
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static GradientDrawable oval(Context c, int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        g.setStroke(dp(c, 1), C_LINE);
        return g;
    }

    private static GradientDrawable cardBg(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, R));
        g.setColor(C_BG);
        g.setStroke(dp(c, 1), C_LINE);
        return g;
    }

    private static GradientDrawable headBg(Context c) {
        GradientDrawable g = new GradientDrawable();
        float r = dp(c, R);
        g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        g.setColor(C_HEAD);
        return g;
    }

    public static synchronized void show(Context ctx) {
        if (shown) {
            return;
        }
        try {
            if (!Settings.canDrawOverlays(ctx)) {
                Log.add("状态卡片未显示：缺少悬浮窗权限");
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            final Context app = ctx.getApplicationContext();
            wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                return;
            }

            FrameLayout box = new FrameLayout(app);
            box.setElevation(dp(app, 6));

            LinearLayout panel = new LinearLayout(app);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackground(cardBg(app));
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            box.addView(panel, pp);
            card = panel;

            LinearLayout head = new LinearLayout(app);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setBackground(headBg(app));
            head.setPadding(dp(app, 12), 0, dp(app, 4), 0);

            View d = new View(app);
            d.setBackground(oval(app, C_RUN));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    dp(app, 8), dp(app, 8));
            dlp.rightMargin = dp(app, 7);
            head.addView(d, dlp);
            dot = d;

            TextView name = new TextView(app);
            name.setText("MCP交互器");
            name.setTextSize(11.5f);
            name.setTextColor(C_MAIN);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            head.addView(name);

            TextView st = new TextView(app);
            st.setTextSize(10.5f);
            st.setTextColor(C_SUB);
            st.setPadding(dp(app, 6), 0, 0, 0);
            head.addView(st);
            status = st;

            TextView close = new TextView(app);
            close.setText("\u00D7");
            close.setTextSize(18);
            close.setTextColor(C_SUB);
            close.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    dp(app, 34), dp(app, 34));
            head.addView(close, clp);
            close.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    collapse(app);
                }
            });

            panel.addView(head, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(app, 36)));

            TextView sp = new TextView(app);
            sp.setTextSize(11.5f);
            sp.setTextColor(C_STEP);
            sp.setMaxLines(2);
            sp.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sp.setPadding(dp(app, 12), 0, dp(app, 12), 0);
            sp.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams spl = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(app, 40));
            panel.addView(sp, spl);
            step = sp;

            View divider = new View(app);
            divider.setBackgroundColor(C_LINE);
            LinearLayout.LayoutParams dvp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(app, 1));
            dvp.leftMargin = dp(app, 10);
            dvp.rightMargin = dp(app, 10);
            panel.addView(divider, dvp);

            LinearLayout body = new LinearLayout(app);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(app, 12), dp(app, 2), dp(app, 12), dp(app, 8));
            panel.addView(body, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            list = body;

            TextView ic = new TextView(app);
            ic.setText("\u2261");
            ic.setTextSize(24);
            ic.setTextColor(C_MAIN);
            ic.setGravity(Gravity.CENTER);
            ic.setTypeface(Typeface.DEFAULT_BOLD);
            ic.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(0x1A000000), oval(app, 0xFFFFFFFF), null));
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            box.addView(ic, ilp);
            icon = ic;

            attachTouch(app, box);

            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            final WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    dp(app, BALL_DP), dp(app, BALL_DP),
                    type, flags, PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.END;
            p.x = dp(app, 12);
            p.y = dp(app, 200);

            wm.addView(box, p);
            root = box;
            lp = p;
            shown = true;
            expand(app);
        } catch (Throwable t) {
            Log.add("状态卡片创建失败: " + t);
        }
    }

    private static void attachTouch(final Context app, View v) {
        if (v == null) {
            return;
        }
        v.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View view, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    touchX = (int) e.getRawX();
                    touchY = (int) e.getRawY();
                    moved = false;
                    return false;
                }
                if (a == MotionEvent.ACTION_MOVE) {
                    int dx = (int) e.getRawX() - touchX;
                    int dy = (int) e.getRawY() - touchY;
                    if (!moved
                            && (Math.abs(dx) > dp(app, 8) || Math.abs(dy) > dp(app, 8))) {
                        moved = true;
                    }
                    if (moved && lp != null && wm != null && root != null) {
                        lp.x -= dx;
                        lp.y += dy;
                        if (lp.x < 0) {
                            lp.x = 0;
                        }
                        if (lp.y < 0) {
                            lp.y = 0;
                        }
                        touchX = (int) e.getRawX();
                        touchY = (int) e.getRawY();
                        try {
                            wm.updateViewLayout(root, lp);
                        } catch (Throwable t) {
                        }
                        return true;
                    }
                    return false;
                }
                if (a == MotionEvent.ACTION_UP) {
                    if (!moved && !expanded) {
                        expand(app);
                    }
                    return false;
                }
                return false;
            }
        });
        v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (!expanded) {
                    expand(app);
                }
            }
        });
    }

    private static void expand(Context app) {
        expanded = true;
        try {
            if (card != null) {
                card.setVisibility(View.VISIBLE);
            }
            if (icon != null) {
                icon.setVisibility(View.GONE);
            }
            if (lp != null && wm != null && root != null) {
                lp.width = dp(app, CARD_W);
                lp.height = dp(app, CARD_H);
                try {
                    wm.updateViewLayout(root, lp);
                } catch (Throwable ignored) {
                }
            }
            render(app);
        } catch (Throwable ignored) {
        }
    }

    private static void collapse(Context app) {
        expanded = false;
        try {
            if (card != null) {
                card.setVisibility(View.GONE);
            }
            if (icon != null) {
                icon.setVisibility(View.VISIBLE);
            }
            if (lp != null && wm != null && root != null) {
                lp.width = dp(app, BALL_DP);
                lp.height = dp(app, BALL_DP);
                try {
                    wm.updateViewLayout(root, lp);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void render(final Context ctx) {
        if (!shown || list == null) {
            return;
        }
        final Context app = ctx == null ? list.getContext() : ctx;
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(
                    new Runnable() {
                        public void run() {
                            paint(app);
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private static void paint(Context app) {
        try {
            if (list == null || !expanded) {
                return;
            }

            boolean pz = Config.isPaused();
            if (status != null) {
                status.setText(pz ? " 已暂停" : " 运行中");
                status.setTextColor(pz ? C_PAUSE : C_SUB);
            }
            if (dot != null) {
                dot.setBackground(oval(app, pz ? C_PAUSE : C_RUN));
            }

            if (step != null) {
                String pt = Log.progressText();
                String ps = Log.progressStep();
                if (pt != null && pt.length() > 0) {
                    String line = ps != null && ps.length() > 0
                            ? ps + "  " + pt : pt;
                    step.setText("\u25B8 " + line);
                    step.setTextColor(C_STEP);
                } else {
                    step.setText("\u25B8 等待 AI 报告步骤");
                    step.setTextColor(C_DIM);
                }
            }

            list.removeAllViews();
            List<Log.Card> cs = Log.cardList(ROW_MAX);
            if (cs.isEmpty()) {
                TextView t = new TextView(app);
                t.setText("暂无操作记录");
                t.setTextSize(11);
                t.setTextColor(C_DIM);
                t.setPadding(0, dp(app, 4), 0, 0);
                list.addView(t);
                return;
            }
            for (Log.Card c : cs) {
                TextView row = new TextView(app);
                row.setText((c.ok ? "\u2713 " : "\u2717 ") + c.title);
                row.setTextSize(11);
                row.setTextColor(c.ok ? C_OK : C_FAIL);
                row.setSingleLine(true);
                row.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.setPadding(0, dp(app, 4), 0, dp(app, 2));
                list.addView(row);
            }
        } catch (Throwable ignored) {
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
        list = null;
        card = null;
        icon = null;
        step = null;
        status = null;
        dot = null;
        wm = null;
        shown = false;
        expanded = false;
    }
}
