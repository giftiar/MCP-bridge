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
import android.graphics.Color;
import android.graphics.Paint;

public class Grid {

    private static final int STEP = 120;
    private static final int MAJOR = 5;

    public static Bitmap draw(Bitmap src) {
        if (src == null) {
            return null;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap bmp = src;
        if (!bmp.isMutable()) {
            try {
                bmp = src.copy(Bitmap.Config.ARGB_8888, true);
            } catch (Throwable t) {
                return src;
            }
            if (bmp == null) {
                return src;
            }
        }
        try {
            Canvas c = new Canvas(bmp);

            Paint thin = new Paint();
            thin.setStyle(Paint.Style.STROKE);
            thin.setStrokeWidth(1f);
            thin.setColor(Color.argb(90, 0, 255, 255));
            thin.setAntiAlias(false);

            Paint thick = new Paint();
            thick.setStyle(Paint.Style.STROKE);
            thick.setStrokeWidth(2f);
            thick.setColor(Color.argb(150, 255, 210, 0));
            thick.setAntiAlias(false);

            Paint label = new Paint();
            label.setTextSize(Math.max(18f, w / 45f));
            label.setAntiAlias(true);
            label.setColor(Color.argb(230, 255, 255, 255));
            label.setShadowLayer(3f, 0f, 0f, Color.BLACK);

            Paint box = new Paint();
            box.setStyle(Paint.Style.FILL);
            box.setColor(Color.argb(150, 0, 0, 0));

            float th = label.getTextSize();

            {
                int i = 0;
                for (int x = 0; x <= w; x += STEP, i++) {
                    c.drawLine(x, 0, x, h, (i % MAJOR == 0) ? thick : thin);
                }
                i = 0;
                for (int y = 0; y <= h; y += STEP, i++) {
                    c.drawLine(0, y, w, y, (i % MAJOR == 0) ? thick : thin);
                }

                for (int x = 0; x <= w; x += STEP * MAJOR) {
                    String s = String.valueOf(x);
                    float tw = label.measureText(s);
                    c.drawRect(x + 2, 2, x + tw + 10, th + 12, box);
                    c.drawText(s, x + 6, th + 4, label);
                }
                for (int y = STEP * MAJOR; y <= h; y += STEP * MAJOR) {
                    String s = String.valueOf(y);
                    float tw = label.measureText(s);
                    c.drawRect(2, y + 2, tw + 10, y + th + 12, box);
                    c.drawText(s, 6, y + th + 4, label);
                }
            }

            String tag = w + "x" + h;
            float tw = label.measureText(tag);
            c.drawRect(w - tw - 14, h - th - 14, w - 2, h - 2, box);
            c.drawText(tag, w - tw - 8, h - 8, label);
        } catch (Throwable ignored) {
        }
        return bmp;
    }

    public static Bitmap drawOcr(Bitmap src, java.util.List<Ocr.Line> lines,
                                 boolean drawGrid) {
        if (src == null) {
            return null;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap bmp = src;
        if (!bmp.isMutable()) {
            try {
                bmp = src.copy(Bitmap.Config.ARGB_8888, true);
            } catch (Throwable t) {
                return src;
            }
            if (bmp == null) {
                return src;
            }
        }
        try {
            Canvas c = new Canvas(bmp);

            Paint edge = new Paint();
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(3f);
            edge.setColor(Color.argb(235, 0, 230, 118));
            edge.setAntiAlias(true);

            Paint tagBg = new Paint();
            tagBg.setStyle(Paint.Style.FILL);
            tagBg.setColor(Color.argb(235, 0, 150, 80));
            tagBg.setAntiAlias(true);

            Paint txt = new Paint();
            txt.setTextSize(Math.max(22f, w / 42f));
            txt.setAntiAlias(true);
            txt.setColor(Color.WHITE);

            float fs = txt.getTextSize();
            float pad = Math.max(3f, fs * 0.14f);

            if (drawGrid) {
                Paint thin = new Paint();
                thin.setStyle(Paint.Style.STROKE);
                thin.setStrokeWidth(1f);
                thin.setColor(Color.argb(70, 0, 255, 255));
                thin.setAntiAlias(false);

                Paint thick = new Paint();
                thick.setStyle(Paint.Style.STROKE);
                thick.setStrokeWidth(2f);
                thick.setColor(Color.argb(120, 255, 210, 0));
                thick.setAntiAlias(false);

                int i = 0;
                for (int x = 0; x <= w; x += STEP, i++) {
                    c.drawLine(x, 0, x, h, (i % MAJOR == 0) ? thick : thin);
                }
                i = 0;
                for (int y = 0; y <= h; y += STEP, i++) {
                    c.drawLine(0, y, w, y, (i % MAJOR == 0) ? thick : thin);
                }
            }

            if (lines != null) {
                int n = 0;
                for (Ocr.Line ln : lines) {
                    n++;
                    if (n > 80) {
                        break;
                    }
                    int left = ln.r.left;
                    int top = ln.r.top;
                    int right = ln.r.right;
                    int bottom = ln.r.bottom;

                    c.drawRect(left, top, right, bottom, edge);

                    String s = String.valueOf(n);
                    float tw = txt.measureText(s);
                    float bw = tw + pad * 2;
                    float bh = fs + pad * 2;
                    float bx;
                    if (left - bw - 3 >= 0) {
                        bx = left - bw - 3;
                    } else if (right + bw + 3 <= w) {
                        bx = right + 3;
                    } else {
                        bx = Math.max(0, left);
                    }
                    float by = (top + bottom) / 2f - bh / 2f;
                    if (by < 0) {
                        by = 0;
                    }
                    if (by + bh > h) {
                        by = h - bh;
                    }
                    c.drawRect(bx, by, bx + bw, by + bh, tagBg);
                    c.drawText(s, bx + pad, by + fs + pad * 0.6f, txt);
                }
            }

            Paint info = new Paint();
            info.setTextSize(Math.max(18f, w / 55f));
            info.setAntiAlias(true);
            info.setColor(Color.argb(230, 255, 255, 255));
            info.setShadowLayer(3f, 0f, 0f, Color.BLACK);

            Paint infoBg = new Paint();
            infoBg.setStyle(Paint.Style.FILL);
            infoBg.setColor(Color.argb(160, 0, 0, 0));

            String tag = w + "x" + h;
            float tw2 = info.measureText(tag);
            float fh = info.getTextSize();
            c.drawRect(w - tw2 - 14, h - fh - 14, w - 2, h - 2, infoBg);
            c.drawText(tag, w - tw2 - 8, h - 8, info);
        } catch (Throwable ignored) {
        }
        return bmp;
    }

    private static String shortText(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "\u2026";
    }

    public static String hint(int w, int h) {
        StringBuilder b = new StringBuilder();
        b.append("坐标网格已叠在图上：\n");
        b.append("  - 细青色线每 ").append(STEP).append("px 一条\n");
        b.append("  - 粗黄色线每 ").append(STEP * MAJOR).append("px 一条\n");
        b.append("  - 上边缘数字是 x 坐标，左边缘数字是 y 坐标，都是像素值\n");
        b.append("  - 图片尺寸 ").append(w).append('x').append(h);
        b.append("，和屏幕像素 1:1，不用换算\n");
        b.append("用法：先看目标夹在哪两条线之间，再按比例估出具体像素，");
        b.append("直接当 click 的 x/y 传。\n");
        b.append("例：目标左侧最近粗黄线标着 360，右边一条是 960，");
        b.append("目标大约在两者中间偏左，则 x≈420。\n");
        return b.toString();
    }
}
