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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import java.util.List;

public final class Overlay {

    private static final int MAX_BOX = 60;

    private static final int KIND_NONE = 0;
    private static final int KIND_CLICK = 1;
    private static final int KIND_SWIPE = 2;

    private static volatile boolean enabled = true;
    private static volatile int kind = KIND_NONE;

    private static volatile float aU = 0f;
    private static volatile float aV = 0f;
    private static volatile float bU = 0f;
    private static volatile float bV = 0f;

    private static volatile String[] boxText = new String[0];
    private static volatile float[] boxU = new float[0];
    private static volatile float[] boxV = new float[0];
    private static volatile float[] boxW = new float[0];
    private static volatile float[] boxH = new float[0];

    private static final Paint BOX = new Paint();
    private static final Paint EDGE = new Paint();
    private static final Paint CLICK = new Paint();
    private static final Paint SWIPE = new Paint();

    static {
        BOX.setStyle(Paint.Style.STROKE);
        BOX.setStrokeWidth(1.5f);
        BOX.setColor(0x403DDC84);
        BOX.setAntiAlias(true);

        EDGE.setStyle(Paint.Style.STROKE);
        EDGE.setStrokeWidth(6f);
        EDGE.setColor(0xCC000000);
        EDGE.setAntiAlias(true);
        EDGE.setStrokeCap(Paint.Cap.ROUND);

        CLICK.setStyle(Paint.Style.STROKE);
        CLICK.setStrokeWidth(2.5f);
        CLICK.setColor(0xFFFF3B30);
        CLICK.setAntiAlias(true);
        CLICK.setStrokeCap(Paint.Cap.ROUND);

        SWIPE.setStyle(Paint.Style.STROKE);
        SWIPE.setStrokeWidth(3f);
        SWIPE.setColor(0xFF00E676);
        SWIPE.setAntiAlias(true);
        SWIPE.setStrokeCap(Paint.Cap.ROUND);
    }

    private Overlay() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean v) {
        enabled = v;
        if (!v) {
            clearAction();
            clearBoxes();
        }
    }

    public static synchronized void markClick(int x, int y, int devW, int devH) {
        if (devW <= 0 || devH <= 0) {
            Log.add("标记失败：显示尺寸未知 " + devW + "x" + devH);
            return;
        }
        aU = x / (float) devW;
        aV = y / (float) devH;
        bU = 0f;
        bV = 0f;
        kind = KIND_CLICK;
    }

    public static synchronized void markSwipe(int x1, int y1, int x2, int y2,
                                              int devW, int devH) {
        if (devW <= 0 || devH <= 0) {
            Log.add("标记失败：显示尺寸未知 " + devW + "x" + devH);
            return;
        }
        aU = x1 / (float) devW;
        aV = y1 / (float) devH;
        bU = x2 / (float) devW;
        bV = y2 / (float) devH;
        kind = KIND_SWIPE;
    }

    public static synchronized void clearAction() {
        kind = KIND_NONE;
        aU = 0f;
        aV = 0f;
        bU = 0f;
        bV = 0f;
    }

    public static synchronized void boxes(List<Ocr.Line> lines, int devW, int devH) {
        if (lines == null || devW <= 0 || devH <= 0) {
            clearBoxes();
            return;
        }
        int n = Math.min(lines.size(), MAX_BOX);
        String[] t = new String[n];
        float[] u = new float[n];
        float[] v = new float[n];
        float[] w = new float[n];
        float[] h = new float[n];
        for (int i = 0; i < n; i++) {
            Ocr.Line ln = lines.get(i);
            Rect r = ln.r;
            t[i] = ln.text;
            u[i] = r.left / (float) devW;
            v[i] = r.top / (float) devH;
            w[i] = r.width() / (float) devW;
            h[i] = r.height() / (float) devH;
        }
        boxText = t;
        boxU = u;
        boxV = v;
        boxW = w;
        boxH = h;
    }

    public static synchronized void clearBoxes() {
        boxText = new String[0];
        boxU = new float[0];
        boxV = new float[0];
        boxW = new float[0];
        boxH = new float[0];
    }

    public static void paint(Bitmap bmp) {
        if (!enabled || bmp == null || bmp.isRecycled()) {
            return;
        }
        try {
            Canvas c = new Canvas(bmp);
            int w = bmp.getWidth();
            int h = bmp.getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            String[] t = boxText;
            float[] u = boxU;
            float[] v = boxV;
            float[] bw = boxW;
            float[] bh = boxH;
            for (int i = 0; i < t.length; i++) {
                c.drawRect(u[i] * w, v[i] * h,
                        (u[i] + bw[i]) * w, (v[i] + bh[i]) * h, BOX);
            }

            if (kind == KIND_CLICK) {
                drawClick(c, aU * w, aV * h, w);
            } else if (kind == KIND_SWIPE) {
                drawSwipe(c, aU * w, aV * h, bU * w, bV * h, w);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void drawClick(Canvas c, float cx, float cy, int w) {
        float rad = Math.max(10f, w * 0.016f);
        float arm = rad * 1.7f;

        c.drawCircle(cx, cy, rad, EDGE);
        c.drawLine(cx - arm, cy, cx + arm, cy, EDGE);
        c.drawLine(cx, cy - arm, cx, cy + arm, EDGE);

        c.drawCircle(cx, cy, rad, CLICK);
        c.drawLine(cx - arm, cy, cx + arm, cy, CLICK);
        c.drawLine(cx, cy - arm, cx, cy + arm, CLICK);
    }

    private static void drawSwipe(Canvas c, float ax, float ay, float bx, float by, int w) {
        c.drawLine(ax, ay, bx, by, EDGE);
        c.drawLine(ax, ay, bx, by, SWIPE);

        float dot = Math.max(3f, w * 0.005f);
        c.drawCircle(ax, ay, dot + 1.5f, EDGE);
        c.drawCircle(ax, ay, dot, SWIPE);

        double ang = Math.atan2(by - ay, bx - ax);
        float len = Math.max(16f, w * 0.022f);
        float a1 = (float) (ang + Math.PI * 0.80);
        float a2 = (float) (ang - Math.PI * 0.80);
        float h1x = bx + len * (float) Math.cos(a1);
        float h1y = by + len * (float) Math.sin(a1);
        float h2x = bx + len * (float) Math.cos(a2);
        float h2y = by + len * (float) Math.sin(a2);

        c.drawLine(bx, by, h1x, h1y, EDGE);
        c.drawLine(bx, by, h2x, h2y, EDGE);
        c.drawLine(bx, by, h1x, h1y, SWIPE);
        c.drawLine(bx, by, h2x, h2y, SWIPE);
    }
}
