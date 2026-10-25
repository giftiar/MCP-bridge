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

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public class A11ySvc extends AccessibilityService {

    public static volatile A11ySvc inst;

    public static volatile int rev = 0;

    public static volatile long lastUserTouch = 0L;

    public static volatile long aiUntil = 0L;

    public static volatile int eventTotal = 0;

    private static final ConcurrentLinkedDeque<Ev> EVENTS =
            new ConcurrentLinkedDeque<Ev>();

    private static volatile long lastContentBump = 0L;

    private static final int MAX_EVENTS = 80;

    public static class Ev {
        public final long t;
        public final String kind;
        public final String who;
        public final String detail;

        Ev(long t, String kind, String who, String detail) {
            this.t = t;
            this.kind = kind;
            this.who = who;
            this.detail = detail;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        inst = this;
        rev++;
        Log.add("无障碍已连接");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null) {
            return;
        }
        try {
            handle(e);
        } catch (Throwable t) {
            Log.add("事件异常: " + t);
        }
    }

    private void handle(AccessibilityEvent e) {
        long now = System.currentTimeMillis();
        int type = e.getEventType();
        eventTotal++;
        boolean byAi = now < aiUntil;
        String who = byAi ? "ai" : "user";

        if (type == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
            if (!byAi) {
                lastUserTouch = now;
                add(now, "touch", "user", "手指触碰屏幕");
            }
            return;
        }

        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            add(now, "click", who, describe(e));
            bump(now);
            return;
        }

        if (type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            add(now, "longclick", who, describe(e));
            bump(now);
            return;
        }

        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            add(now, "input", who, describe(e));
            bump(now);
            return;
        }

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            add(now, "window", who, describe(e));
            bump(now);
            return;
        }

        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            add(now, "scroll", who, describe(e));
            bump(now);
            return;
        }

        if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            add(now, "focus", who, describe(e));
            return;
        }

        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (now - lastContentBump > 400) {
                lastContentBump = now;
                add(now, "content", who, describe(e));
            }
        }
    }

    private void bump(long now) {
        rev++;
    }

    private void add(long t, String kind, String who, String detail) {
        EVENTS.addFirst(new Ev(t, kind, who, detail));
        while (EVENTS.size() > MAX_EVENTS) {
            EVENTS.pollLast();
        }
    }

    private String describe(AccessibilityEvent e) {
        StringBuilder b = new StringBuilder();
        CharSequence pkg = e.getPackageName();
        String p = pkg == null ? "" : pkg.toString();

        CharSequence cls = e.getClassName();
        String c = cls == null ? "" : cls.toString();
        int dot = c.lastIndexOf('.');
        if (dot >= 0) {
            c = c.substring(dot + 1);
        }

        List<CharSequence> texts = null;
        try {
            texts = e.getText();
        } catch (Throwable ignored) {
        }

        StringBuilder txt = new StringBuilder();
        if (texts != null) {
            for (int i = 0; i < texts.size(); i++) {
                CharSequence cs = texts.get(i);
                if (cs != null && cs.length() > 0) {
                    if (txt.length() > 0) {
                        txt.append(' ');
                    }
                    txt.append(cs);
                }
            }
        }

        if (txt.length() > 0) {
            b.append('\"').append(cut(txt.toString(), 60)).append('\"');
        }
        if (c.length() > 0) {
            if (b.length() > 0) {
                b.append(' ');
            }
            b.append(c);
        }
        if (p.length() > 0) {
            if (b.length() > 0) {
                b.append(' ');
            }
            b.append('(').append(p).append(')');
        }
        if (b.length() == 0) {
            b.append("(无详情)");
        }
        return b.toString();
    }

    private String cut(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

    public static String eventsSince(long sinceMillis) {
        long now = System.currentTimeMillis();
        long from = sinceMillis;
        if (now - from > 60000L) {
            from = now - 60000L;
        }

        StringBuilder b = new StringBuilder();
        Iterator<Ev> it = EVENTS.iterator();
        int shown = 0;
        int refreshUser = 0;
        int refreshAi = 0;

        while (it.hasNext()) {
            Ev ev = it.next();
            if (ev.t < from) {
                break;
            }
            if ("content".equals(ev.kind)) {
                if ("user".equals(ev.who)) {
                    refreshUser++;
                } else {
                    refreshAi++;
                }
                continue;
            }
            if (shown >= 15) {
                break;
            }
            shown++;
            b.append("  [+").append(ev.t - from).append("ms] ");
            if ("user".equals(ev.who)) {
                b.append("用户 ");
            } else {
                b.append("AI ");
            }
            b.append(kindText(ev.kind)).append(' ').append(ev.detail).append('\n');
        }

        int refreshTotal = refreshUser + refreshAi;
        if (refreshTotal > 0) {
            b.append("  界面刷新 ×").append(refreshTotal);
            if (refreshUser > 0 && refreshAi > 0) {
                b.append("（用户=").append(refreshUser)
                        .append(" AI=").append(refreshAi).append("）");
            }
            b.append('\n');
        }
        return b.toString();
    }

    public static boolean userBusy() {
        return System.currentTimeMillis() - lastUserTouch < 1500;
    }

    public static void markAiAction() {
        aiUntil = System.currentTimeMillis() + 900;
    }

    private static String kindText(String kind) {
        if ("click".equals(kind)) {
            return "点击";
        }
        if ("longclick".equals(kind)) {
            return "长按";
        }
        if ("input".equals(kind)) {
            return "输入";
        }
        if ("window".equals(kind)) {
            return "切换界面 ->";
        }
        if ("scroll".equals(kind)) {
            return "滚动";
        }
        if ("touch".equals(kind)) {
            return "触屏";
        }
        if ("focus".equals(kind)) {
            return "聚焦";
        }
        if ("content".equals(kind)) {
            return "界面刷新";
        }
        return kind;
    }

    public static String dumpDisplays() {
        StringBuilder b = new StringBuilder();
        A11ySvc s = inst;
        if (s == null) {
            return "P6 all_displays: FAIL(a11y off)\n";
        }
        if (Build.VERSION.SDK_INT < 30) {
            return "P6 all_displays: unsupported(sdk " + Build.VERSION.SDK_INT + ")\n";
        }
        SparseArray<List<AccessibilityWindowInfo>> map;
        try {
            map = s.getWindowsOnAllDisplays();
        } catch (Throwable t) {
            return "P6 all_displays: THROW " + t + "\n";
        }
        if (map == null || map.size() == 0) {
            return "P6 all_displays: FAIL(empty)\n";
        }
        b.append("P6 all_displays: ").append(map.size()).append(" display(s)\n");
        for (int i = 0; i < map.size(); i++) {
            int did = map.keyAt(i);
            List<AccessibilityWindowInfo> ws = map.valueAt(i);
            int n = ws == null ? 0 : ws.size();
            b.append("  display ").append(did).append(": ").append(n).append(" window(s)\n");
            if (ws == null) {
                continue;
            }
            for (int j = 0; j < ws.size() && j < 6; j++) {
                AccessibilityWindowInfo w = ws.get(j);
                if (w == null) {
                    continue;
                }
                Rect r = new Rect();
                try {
                    w.getBoundsInScreen(r);
                } catch (Throwable ignored) {
                }
                AccessibilityNodeInfo root = null;
                try {
                    root = w.getRoot();
                } catch (Throwable ignored) {
                }
                String pkg = "";
                String title = "";
                int nodes = 0;
                StringBuilder txt = new StringBuilder();
                if (root != null) {
                    try {
                        CharSequence p = root.getPackageName();
                        if (p != null) {
                            pkg = p.toString();
                        }
                    } catch (Throwable ignored) {
                    }
                    try {
                        CharSequence t2 = w.getTitle();
                        if (t2 != null) {
                            title = t2.toString();
                        }
                    } catch (Throwable ignored) {
                    }
                    nodes = countNodes(root, 0);
                    collectText(root, txt, 0);
                }
                b.append("    W").append(j)
                        .append(" type=").append(typeName(w.getType()))
                        .append(" root=").append(root != null ? "yes" : "no")
                        .append(" nodes=").append(nodes)
                        .append(" bounds=").append(r.width()).append('x').append(r.height())
                        .append(" pkg=").append(pkg)
                        .append('\n');
                if (title.length() > 0) {
                    b.append("       title=").append(title).append('\n');
                }
                if (txt.length() > 0) {
                    b.append("       text=").append(txt).append('\n');
                }
            }
        }
        return b.toString();
    }

    private static int countNodes(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 30) {
            return 0;
        }
        int c = 1;
        int num = 0;
        try {
            num = n.getChildCount();
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < num; i++) {
            AccessibilityNodeInfo ch = null;
            try {
                ch = n.getChild(i);
            } catch (Throwable ignored) {
            }
            if (ch != null) {
                c += countNodes(ch, depth + 1);
            }
        }
        return c;
    }

    private static void collectText(AccessibilityNodeInfo n, StringBuilder out, int depth) {
        if (n == null || depth > 30 || out.length() > 400) {
            return;
        }
        try {
            CharSequence t = n.getText();
            if (t == null) {
                t = n.getContentDescription();
            }
            if (t != null && t.length() > 0) {
                if (out.length() > 0) {
                    out.append(" | ");
                }
                String one = t.toString();
                if (one.length() > 30) {
                    one = one.substring(0, 30) + "...";
                }
                out.append(one);
            }
        } catch (Throwable ignored) {
        }
        int num = 0;
        try {
            num = n.getChildCount();
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < num; i++) {
            AccessibilityNodeInfo ch = null;
            try {
                ch = n.getChild(i);
            } catch (Throwable ignored) {
            }
            if (ch != null) {
                collectText(ch, out, depth + 1);
            }
        }
    }

    private static String typeName(int t) {
        if (t == AccessibilityWindowInfo.TYPE_APPLICATION) {
            return "APP";
        }
        if (t == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
            return "IME";
        }
        if (t == AccessibilityWindowInfo.TYPE_SYSTEM) {
            return "SYS";
        }
        if (t == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
            return "A11Y";
        }
        if (t == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
            return "DIV";
        }
        return "T" + t;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        inst = null;
        rev++;
        Log.add("无障碍已断开");
        return super.onUnbind(intent);
    }
}
