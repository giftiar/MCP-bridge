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

import java.util.LinkedList;

public class Log {

    private static final LinkedList<String> LIST = new LinkedList<String>();

    private static String now() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        StringBuilder b = new StringBuilder(8);
        int h = c.get(java.util.Calendar.HOUR_OF_DAY);
        int m = c.get(java.util.Calendar.MINUTE);
        int s = c.get(java.util.Calendar.SECOND);
        if (h < 10) {
            b.append('0');
        }
        b.append(h).append(':');
        if (m < 10) {
            b.append('0');
        }
        b.append(m).append(':');
        if (s < 10) {
            b.append('0');
        }
        b.append(s);
        return b.toString();
    }

    private static final LinkedList<Card> CARDS = new LinkedList<Card>();

    public static class Card {
        public final String time;
        public final String title;
        public final String detail;
        public final boolean ok;

        Card(String time, String title, String detail, boolean ok) {
            this.time = time;
            this.title = title;
            this.detail = detail;
            this.ok = ok;
        }
    }

    public static void add(String text) {
        String line = "[" + now() + "] " + text;
        synchronized (LIST) {
            LIST.addFirst(line);
            while (LIST.size() > 300) {
                LIST.removeLast();
            }
        }
    }

    public static void card(String title, String detail, boolean ok) {
        Card c = new Card(now(), title, detail, ok);
        synchronized (CARDS) {
            CARDS.addFirst(c);
            while (CARDS.size() > 20) {
                CARDS.removeLast();
            }
        }
    }

    private static volatile String progText = "";
    private static volatile String progStep = "";
    private static volatile long progAt = 0L;

    public static void progress(String text, String step) {
        progText = text == null ? "" : text;
        progStep = step == null ? "" : step;
        progAt = System.currentTimeMillis();
    }

    public static String progressText() {
        return progText;
    }

    public static String progressStep() {
        return progStep;
    }

    public static long progressAt() {
        return progAt;
    }

    public static void clearProgress() {
        progText = "";
        progStep = "";
        progAt = 0L;
    }

    public static Card cardTop() {
        synchronized (CARDS) {
            return CARDS.isEmpty() ? null : CARDS.getFirst();
        }
    }

    public static java.util.List<Card> cardList(int max) {
        java.util.List<Card> out = new java.util.ArrayList<Card>();
        synchronized (CARDS) {
            int n = 0;
            for (Card c : CARDS) {
                if (n++ >= max) {
                    break;
                }
                out.add(c);
            }
        }
        return out;
    }

    public static String dump() {
        StringBuilder b = new StringBuilder();
        synchronized (LIST) {
            int n = 0;
            for (String s : LIST) {
                if (n++ >= 80) {
                    break;
                }
                b.append(s).append('\n');
            }
        }
        if (b.length() == 0) {
            return "(暂无日志)";
        }
        return b.toString();
    }
}
