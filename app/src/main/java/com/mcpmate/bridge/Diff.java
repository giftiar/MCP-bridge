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

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.List;

public final class Diff {

    public static final class Item {
        public final String text;
        public final Rect rect;

        public Item(String text, Rect rect) {
            this.text = text;
            this.rect = rect == null ? new Rect() : new Rect(rect);
        }
    }

    private static final int MAX_ITEMS = 600;
    private static final int MAX_SHOW = 5;
    private static final int MAX_TEXT = 26;

    private static final List<String> OLD_TEXT = new ArrayList<String>();
    private static final List<Rect> OLD_RECT = new ArrayList<Rect>();
    private static volatile boolean hasBaseline = false;

    private Diff() {
    }

    public static synchronized void clear() {
        OLD_TEXT.clear();
        OLD_RECT.clear();
        hasBaseline = false;
    }

    public static synchronized boolean ready() {
        return hasBaseline;
    }

    public static synchronized void remember(List<Item> items) {
        fill(items);
        hasBaseline = true;
    }

    public static synchronized String report(List<Item> items, int screenW, int screenH) {
        if (!hasBaseline || items == null) {
            return "";
        }
        List<String> oldText = new ArrayList<String>(OLD_TEXT);
        List<Rect> oldRect = new ArrayList<Rect>(OLD_RECT);

        fill(items);

        List<String> added = new ArrayList<String>();
        List<String> removed = new ArrayList<String>();
        Rect dirty = null;
        boolean[] usedPrev = new boolean[oldText.size()];

        for (int i = 0; i < OLD_TEXT.size(); i++) {
            String t = OLD_TEXT.get(i);
            boolean found = false;
            for (int j = 0; j < oldText.size(); j++) {
                if (!usedPrev[j] && oldText.get(j).equals(t)) {
                    usedPrev[j] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                Rect r = OLD_RECT.get(i);
                added.add(t + "  (" + r.centerX() + "," + r.centerY() + ")");
                dirty = union(dirty, r);
            }
        }
        for (int j = 0; j < oldText.size(); j++) {
            if (!usedPrev[j]) {
                removed.add(oldText.get(j));
                dirty = union(dirty, oldRect.get(j));
            }
        }

        if (added.isEmpty() && removed.isEmpty()) {
            return "screen: same\n";
        }
        StringBuilder b = new StringBuilder();
        b.append("screen: changed\n");
        if (!added.isEmpty()) {
            b.append("added: ").append(join(added)).append('\n');
        }
        if (!removed.isEmpty()) {
            b.append("removed: ").append(join(removed)).append('\n');
        }
        if (dirty != null) {
            b.append("area: ").append(area(dirty, screenW, screenH)).append('\n');
        }
        return b.toString();
    }

    private static void fill(List<Item> items) {
        OLD_TEXT.clear();
        OLD_RECT.clear();
        if (items == null) {
            hasBaseline = false;
            return;
        }
        int n = 0;
        for (int i = 0; i < items.size(); i++) {
            if (n >= MAX_ITEMS) {
                break;
            }
            Item it = items.get(i);
            if (it == null) {
                continue;
            }
            String t = norm(it.text);
            if (t.length() == 0) {
                continue;
            }
            OLD_TEXT.add(t);
            OLD_RECT.add(new Rect(it.rect));
            n++;
        }
    }

    private static String norm(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').replace('\r', ' ').trim();
        while (t.indexOf("  ") >= 0) {
            t = t.replace("  ", " ");
        }
        return t;
    }

    private static Rect union(Rect acc, Rect r) {
        if (r == null || r.isEmpty()) {
            return acc;
        }
        if (acc == null) {
            return new Rect(r);
        }
        acc.union(r);
        return acc;
    }

    private static String join(List<String> list) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String s : list) {
            if (n >= MAX_SHOW) {
                b.append(" + ").append(list.size() - n).append(" more");
                break;
            }
            if (n > 0) {
                b.append("  ");
            }
            b.append('"').append(cut(s)).append('"');
            n++;
        }
        return b.toString();
    }

    private static String cut(String s) {
        if (s.length() <= MAX_TEXT) {
            return s;
        }
        return s.substring(0, MAX_TEXT) + "...";
    }

    private static String area(Rect r, int w, int h) {
        if (w <= 0 || h <= 0) {
            return r.left + "," + r.top + "-" + r.right + "," + r.bottom;
        }
        int cy = (r.top + r.bottom) / 2;
        int yPct = cy * 100 / h;
        String band;
        if (yPct < 25) {
            band = "top";
        } else if (yPct < 45) {
            band = "upper-middle";
        } else if (yPct < 60) {
            band = "middle";
        } else if (yPct < 80) {
            band = "lower-middle";
        } else {
            band = "bottom";
        }
        int wPct = Math.max(1, r.width() * 100 / w);
        int hPct = Math.max(1, r.height() * 100 / h);
        return band + " (y " + Math.max(0, r.top) + "-" + Math.min(h, r.bottom)
                + ", about " + wPct + "% x " + hPct + "% of the screen)";
    }
}
