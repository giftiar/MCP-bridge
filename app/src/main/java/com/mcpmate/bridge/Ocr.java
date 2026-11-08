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
import android.graphics.Rect;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Ocr {

    public static final float UPSCALE = 1.6f;

    public static class Line {
        public final String text;
        public final Rect r;

        Line(String text, Rect r) {
            this.text = text;
            this.r = r;
        }

        public int cx() {
            return (r.left + r.right) / 2;
        }

        public int cy() {
            return (r.top + r.bottom) / 2;
        }
    }

    private static volatile TextRecognizer REC;

    private static TextRecognizer rec() {
        if (REC == null) {
            synchronized (Ocr.class) {
                if (REC == null) {
                    REC = TextRecognition.getClient(
                            new ChineseTextRecognizerOptions.Builder().build());
                }
            }
        }
        return REC;
    }

    public static List<Line> run(Bitmap bmp, float coordScale, String[] err) {
        List<Line> out = new ArrayList<Line>();
        if (bmp == null) {
            err[0] = "位图为空";
            return out;
        }
        if (coordScale <= 0f) {
            coordScale = 1f;
        }

        final float inv = 1f / coordScale;
        final CountDownLatch latch = new CountDownLatch(1);
        final List<Line> got = new ArrayList<Line>();
        final String[] fail = new String[]{null};

        try {
            InputImage img = InputImage.fromBitmap(bmp, 0);
            rec().process(img)
                    .addOnSuccessListener(new OnSuccessListener<Text>() {
                        public void onSuccess(Text result) {
                            try {
                                for (Text.TextBlock blk : result.getTextBlocks()) {
                                    for (Text.Line ln : blk.getLines()) {
                                        Rect r = ln.getBoundingBox();
                                        if (r == null || r.width() <= 0 || r.height() <= 0) {
                                            continue;
                                        }
                                        String t = ln.getText();
                                        if (t == null) {
                                            continue;
                                        }
                                        t = clean(t);
                                        if (t.length() == 0) {
                                            continue;
                                        }
                                        Rect sc = new Rect(
                                                Math.round(r.left * inv),
                                                Math.round(r.top * inv),
                                                Math.round(r.right * inv),
                                                Math.round(r.bottom * inv));
                                        got.add(new Line(t, sc));
                                    }
                                }
                            } catch (Throwable t) {
                                fail[0] = String.valueOf(t);
                            }
                            latch.countDown();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        public void onFailure(Exception e) {
                            fail[0] = String.valueOf(e);
                            latch.countDown();
                        }
                    });
            if (!latch.await(25, TimeUnit.SECONDS)) {
                err[0] = "识别超时（25 秒）";
                return out;
            }
        } catch (Throwable t) {
            err[0] = "识别异常 " + t;
            return out;
        }

        if (fail[0] != null) {
            err[0] = fail[0];
        }
        out.addAll(filter(got));
        return out;
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\n' || ch == '\r' || ch == '\t') {
                b.append(' ');
            } else {
                b.append(ch);
            }
        }
        return b.toString().trim();
    }

    private static List<Line> filter(List<Line> src) {
        List<Line> keep = new ArrayList<Line>();
        for (Line ln : src) {
            Rect r = ln.r;
            int w = r.width();
            int h = r.height();
            if (h < 16 || w < 12) {
                continue;
            }
            if (h > 400) {
                continue;
            }
            String t = ln.text;
            int vis = 0;
            for (int i = 0; i < t.length(); i++) {
                if (t.charAt(i) > ' ') {
                    vis++;
                }
            }
            if (vis < 1) {
                continue;
            }
            if (vis <= 2 && h < 26) {
                continue;
            }
            keep.add(ln);
        }

        List<Line> sorted = new ArrayList<Line>(keep);
        Collections.sort(sorted, new Comparator<Line>() {
            public int compare(Line a, Line b) {
                return area(b) - area(a);
            }
        });

        List<Line> finalList = new ArrayList<Line>();
        for (Line ln : sorted) {
            boolean dup = false;
            for (Line k : finalList) {
                if (overlap(ln.r, k.r) > 0.70f) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                finalList.add(ln);
            }
        }

        Collections.sort(finalList, new Comparator<Line>() {
            public int compare(Line a, Line b) {
                int dy = a.r.top - b.r.top;
                if (Math.abs(dy) > 12) {
                    return dy;
                }
                return a.r.left - b.r.left;
            }
        });
        return finalList;
    }

    private static int area(Line l) {
        return l.r.width() * l.r.height();
    }

    private static float overlap(Rect a, Rect b) {
        int l = Math.max(a.left, b.left);
        int t = Math.max(a.top, b.top);
        int r = Math.min(a.right, b.right);
        int bo = Math.min(a.bottom, b.bottom);
        if (r <= l || bo <= t) {
            return 0f;
        }
        long inter = (long) (r - l) * (bo - t);
        long small = Math.min((long) a.width() * a.height(),
                (long) b.width() * b.height());
        if (small <= 0) {
            return 0f;
        }
        return (float) inter / (float) small;
    }

    public static String describe(List<Line> list, int limit) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (Line x : list) {
            if (n >= limit) {
                b.append("... 另有 ").append(list.size() - n).append(" 行未列出\n");
                break;
            }
            n++;
            b.append("T").append(n).append(" (").append(x.cx()).append(',').append(x.cy())
                    .append(") \"").append(cut(x.text, 40)).append("\"")
                    .append('\n');
        }
        return b.toString();
    }

    public static int find(List<Line> list, String keyword) {
        if (keyword == null || keyword.length() == 0) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).text.contains(keyword)) {
                return i;
            }
        }
        return -1;
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }
}
