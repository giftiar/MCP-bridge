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
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Ui {

    public static final int FAIL_LIMIT = 5;

    private static final List<AccessibilityNodeInfo> CACHE =
            new ArrayList<AccessibilityNodeInfo>();
    private static final Map<String, Integer> FAILS = new HashMap<String, Integer>();

    private static final int MAX_ITEMS = 220;
    private static final int MAX_DEPTH = 40;
    private static final long SETTLE_MS = 420;

    private static final long SNAP_GAP_MS = 100L;

    private static long lastAct = 0L;
    private static long lastSnapEnd = 0L;
    public static volatile long lastRev = -1L;
    public static volatile long expectRev = -1L;
    public static volatile long lastSnapAt = 0L;

    public static void setExpect(long rev) {
        expectRev = rev;
    }

    public static boolean ready() {
        return A11ySvc.inst != null;
    }

    public static void gate() {
        long gap = Math.max(0, Config.getInterval());
        if (gap <= 0) {
            return;
        }
        synchronized (Ui.class) {
            long at = lastAct + gap;
            while (true) {
                long wait = at - System.currentTimeMillis();
                if (wait <= 0) {
                    break;
                }
                sleep(wait);
            }
            lastAct = System.currentTimeMillis();
        }
    }

    public static void awaitSnap() {
        synchronized (Ui.class) {
            long at = lastSnapEnd + SNAP_GAP_MS;
            while (true) {
                long wait = at - System.currentTimeMillis();
                if (wait <= 0) {
                    break;
                }
                sleep(wait);
            }
        }
    }

    private static void markSnap() {
        synchronized (Ui.class) {
            lastSnapEnd = System.currentTimeMillis();
        }
    }


    public static Rect screen() {
        Rect r = new Rect();
        AccessibilityService s = A11ySvc.inst;
        if (s == null) {
            return r;
        }
        try {
            Object wm = s.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics dm = new DisplayMetrics();
                Display d = ((android.view.WindowManager) wm).getDefaultDisplay();
                d.getRealMetrics(dm);
                r.set(0, 0, dm.widthPixels, dm.heightPixels);
            }
        } catch (Throwable ignored) {
        }
        if (r.width() <= 0 || r.height() <= 0) {
            try {
                DisplayMetrics dm = s.getResources().getDisplayMetrics();
                r.set(0, 0, dm.widthPixels, dm.heightPixels);
            } catch (Throwable ignored) {
            }
        }
        if (r.width() <= 0 || r.height() <= 0) {
            try {
                AccessibilityNodeInfo root = s.getRootInActiveWindow();
                if (root != null) {
                    root.getBoundsInScreen(r);
                }
            } catch (Throwable ignored) {
            }
        }
        return r;
    }

    private static boolean onScreen(Rect node, Rect scr) {
        if (node == null || scr == null) {
            return false;
        }
        if (node.width() <= 0 || node.height() <= 0) {
            return false;
        }
        if (scr.width() <= 0 || scr.height() <= 0) {
            return true;
        }
        int cx = node.centerX();
        int cy = node.centerY();
        return cx >= scr.left && cx < scr.right && cy >= scr.top && cy < scr.bottom;
    }


    public static String fingerprint() {
        AccessibilityService s = A11ySvc.inst;
        if (s == null) {
            return "none";
        }
        AccessibilityNodeInfo root = null;
        try {
            root = s.getRootInActiveWindow();
        } catch (Throwable ignored) {
        }
        if (root == null) {
            return "null";
        }
        Rect scr = screen();
        StringBuilder b = new StringBuilder();
        try {
            b.append(root.getPackageName());
        } catch (Throwable ignored) {
        }
        try {
            List<AccessibilityWindowInfo> ws = s.getWindows();
            b.append("|w").append(ws == null ? 0 : ws.size());
        } catch (Throwable ignored) {
        }
        fpWalk(root, b, scr, 0, new int[]{0});
        return String.valueOf(b.toString().hashCode());
    }

    private static java.util.List<Diff.Item> treeItems(AccessibilityService s) {
        java.util.List<Diff.Item> out = new java.util.ArrayList<Diff.Item>();
        try {
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            if (root != null) {
                collectItems(root, out, screen(), 0, new int[]{0});
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void collectItems(AccessibilityNodeInfo n,
                                     java.util.List<Diff.Item> out, Rect scr,
                                     int depth, int[] cnt) {
        if (n == null || depth > 25 || cnt[0] > 300) {
            return;
        }
        Rect r = new Rect();
        try {
            n.getBoundsInScreen(r);
            if (onScreen(r, scr)) {
                CharSequence t = n.getText();
                String txt = t == null ? "" : t.toString().trim();
                if (txt.length() == 0) {
                    CharSequence d = n.getContentDescription();
                    txt = d == null ? "" : d.toString().trim();
                }
                if (txt.length() > 0) {
                    out.add(new Diff.Item(txt, r));
                    cnt[0]++;
                }
            }
        } catch (Throwable ignored) {
        }
        int cc = 0;
        try {
            cc = n.getChildCount();
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < cc; i++) {
            AccessibilityNodeInfo c = null;
            try {
                c = n.getChild(i);
            } catch (Throwable ignored) {
            }
            collectItems(c, out, scr, depth + 1, cnt);
        }
    }

    private static void fpWalk(AccessibilityNodeInfo n, StringBuilder b, Rect scr,
                               int depth, int[] cnt) {
        if (n == null || depth > 30 || cnt[0] > 400) {
            return;
        }
        Rect r = new Rect();
        try {
            n.getBoundsInScreen(r);
            if (onScreen(r, scr)) {
                CharSequence t = n.getText();
                if (t != null && t.length() > 0) {
                    b.append(t).append(';');
                    cnt[0]++;
                }
                CharSequence d = n.getContentDescription();
                if (d != null && d.length() > 0) {
                    b.append(d).append(';');
                    cnt[0]++;
                }
            }
        } catch (Throwable ignored) {
        }
        int cc = 0;
        try {
            cc = n.getChildCount();
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < cc; i++) {
            AccessibilityNodeInfo c = null;
            try {
                c = n.getChild(i);
            } catch (Throwable ignored) {
            }
            fpWalk(c, b, scr, depth + 1, cnt);
        }
    }


    public static String snapshot(boolean includeOffscreen) {
        AccessibilityService s = A11ySvc.inst;
        if (s == null) {
            return "ERROR: 无障碍服务未开启，请先在 App 内开启「MCP交互器」";
        }
        AccessibilityNodeInfo root;
        try {
            root = s.getRootInActiveWindow();
        } catch (Throwable t) {
            return "ERROR: 读取窗口失败 " + t;
        }
        if (root == null) {
            return "ERROR: 当前窗口不可读（可能在系统安全界面或锁屏）";
        }

        Rect scr = screen();
        StringBuilder info = new StringBuilder();

        try {
            info.append("pkg: ").append(root.getPackageName()).append('\n');
        } catch (Throwable ignored) {
        }
        info.append("rev: ").append(A11ySvc.rev).append('\n');
        info.append("paused: ").append(Config.isPaused() ? "YES" : "no").append('\n');
        info.append("screen: ").append(scr.width()).append('x').append(scr.height()).append('\n');
        if (Config.isPaused()) {
            info.append("WARNING: 用户已急停，所有操作被阻止。请勿尝试任何操作。\n");
        }

        Rect rb = new Rect();
        boolean hasRect = false;
        try {
            root.getBoundsInScreen(rb);
            hasRect = true;
            info.append("window_bounds: ").append(rb.flattenToString()).append('\n');
        } catch (Throwable ignored) {
        }
        if (hasRect && rb.width() > 0 && rb.height() > 0) {
            appendFocus(info, rb, scr);
        }

        appendWindows(info, s);

        try {
            Diff.remember(treeItems(s));
        } catch (Throwable ignored) {
        }

        String evts = A11ySvc.eventsSince(lastSnapAt);
        lastSnapAt = System.currentTimeMillis();
        lastRev = A11ySvc.rev;

        synchronized (CACHE) {
            for (AccessibilityNodeInfo old : CACHE) {
                try {
                    old.recycle();
                } catch (Throwable ignored) {
                }
            }
            CACHE.clear();
        }

        StringBuilder body = new StringBuilder();
        int[] counter = new int[]{0};
        int[] hidden = new int[]{0};
        try {
            walk(root, body, scr, 0, counter, hidden, includeOffscreen);
        } catch (Throwable t) {
            body.append("ERROR: 遍历异常 ").append(t).append('\n');
        }

        info.append("elements: ").append(counter[0]);
        if (hidden[0] > 0) {
            info.append("（另有 ").append(hidden[0])
                    .append(" 个屏外元素未列出，需要查看请传 include_offscreen=true）");
        }
        info.append('\n');
        info.append(a11yBlockedNote(counter[0]));

        if (StopBar.isShown()) {
            boolean pz = Config.isPaused();
            info.append("overlay: ").append(StopBar.MARK)
                    .append("  [用户专用·AI 禁止点击]").append('\n');
            info.append("  状态: ").append(pz ? "已暂停（AI 写操作全部被拒）" : "运行中")
                    .append('\n');
            info.append("  位置: ").append(StopBar.boundsText())
                    .append("（屏幕右上角悬浮圆球现显示\"")
                    .append(pz ? "续" : "停").append("\"）\n");
            info.append("  用途: 手机用户的安全按钮。用户点一下 = 立即急停，")
                    .append("再点一下 = 恢复。只有用户的手指能点它。\n");
            info.append("  对你的要求: 不要点击、不要用坐标点它、不要尝试拖动它。")
                    .append("它不在元素列表里，也无法用 @编号 选中。\n");
            info.append("  遇到 paused: 说明用户已急停。立刻停下，告诉用户你被暂停了，")
                    .append("等用户自己恢复，不要重试、不要绕过。\n");
        }

        info.append("\n--- 界面变化 ---\n");
        if (evts.length() == 0) {
            info.append("  (无变化)\n");
        } else {
            info.append(evts);
        }

        info.append("\n--- 元素 ---\n");
        if (body.length() == 0) {
            info.append("(无交互元素，可尝试 ocr 看画面)\n");
        } else {
            info.append(body);
        }
        return info.toString();
    }

    private static String a11yBlockedNote(int count) {
        if (count > 0) {
            return "";
        }
        boolean unlock = false;
        String pkgNow = "";
        try {
            unlock = com.google.android.accessibility.selecttospeak
                    .SelectToSpeakService.isEnabled(A11ySvc.inst);
        } catch (Throwable ignored) {
        }
        try {
            pkgNow = String.valueOf(A11ySvc.inst.getPackageName());
        } catch (Throwable ignored) {
        }
        StringBuilder b = new StringBuilder();
        b.append("\nWARN: 本次快照读到 0 个元素，当前界面内容不可读。\n");
        if (unlock) {
            b.append("  兼容模式服务已开启，但这个应用仍然没有返回无障碍树。\n");
            b.append("  可能原因：该应用有更深层的防护，或页面正在加载。\n");
            b.append("  建议：等 2 秒重新快照一次；仍为 0 就只能 ocr 看图，");
            b.append("然后用坐标操作，但必须先用截图核对位置，不要盲点。\n");
        } else {
            if (pkgNow.contains("systemui") || pkgNow.contains("settings")) {
                b.append("  当前是系统界面，通常不应该为 0，可能是页面还在加载。等 1 秒再试。\n");
            } else {
                b.append("  兼容模式服务【未开启】。微信、夸克等应用会检测这项：");
                b.append("未开启时它们故意不返回任何界面内容。\n");
                b.append("  处理方式：告知用户去系统设置 → 无障碍 → 开启「MCP交互器 兼容模式」，");
                b.append("开启后再重新快照。\n");
                b.append("  在用户开启之前，不要反复重试快照，也不要靠猜坐标点击。\n");
            }
        }
        return b.toString();
    }

    private static String focusNote() {
        AccessibilityService s = A11ySvc.inst;
        if (s == null) {
            return "";
        }
        AccessibilityNodeInfo root = null;
        try {
            root = s.getRootInActiveWindow();
        } catch (Throwable ignored) {
        }
        if (root == null) {
            return "";
        }
        Rect rb = new Rect();
        try {
            root.getBoundsInScreen(rb);
        } catch (Throwable t) {
            return "";
        }
        Rect scr = screen();
        if (Math.abs(rb.width() - scr.width()) <= 2
                && Math.abs(rb.height() - scr.height()) <= 2) {
            return "";
        }
        String pkg = "";
        try {
            pkg = String.valueOf(root.getPackageName());
        } catch (Throwable ignored) {
        }
        StringBuilder b = new StringBuilder();
        b.append("focus_note: 当前焦点窗口是小窗/分屏（").append(pkg)
                .append("），不是全屏应用。你要找的内容可能在全屏应用里。\n");
        b.append(focusAdvice(rb, scr, "           "));
        return b.toString();
    }

    private static int systemBarHeight(String name, int fallback) {
        try {
            AccessibilityService s = A11ySvc.inst;
            if (s == null) {
                return fallback;
            }
            int id = s.getResources().getIdentifier(
                    name, "dimen", "android");
            if (id <= 0) {
                return fallback;
            }
            return s.getResources().getDimensionPixelSize(id);
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static int safeTop() {
        return systemBarHeight("status_bar_height", 90) + 24;
    }

    private static int safeBottom(Rect scr) {
        int nav = systemBarHeight("navigation_bar_height", 0);
        int gesture = systemBarHeight("navigation_bar_height_landscape", 0);
        if (gesture > 0 && gesture < nav) {
            nav = gesture;
        }
        if (nav <= 0) {
            nav = Math.max(scr.height() / 22, 48);
        }
        return scr.height() - nav - 24;
    }

    private static int[] findBlankTapPoint(Rect rb, Rect scr) {
        List<AccessibilityWindowInfo> ws = null;
        try {
            ws = A11ySvc.inst.getWindows();
        } catch (Throwable ignored) {
        }
        if (ws == null) {
            return null;
        }

        int top = safeTop();
        int bottom = safeBottom(scr);
        if (bottom - top < 80) {
            return null;
        }

        int minBand = Math.max(scr.height() / 24, 48);
        int[] ys = new int[3];
        int yn = 0;

        int upLo = top;
        int upHi = Math.min(rb.top, bottom);
        if (upHi - upLo >= minBand) {
            ys[yn++] = (upLo + upHi) / 2;
        }
        int dnLo = Math.max(rb.bottom, top);
        int dnHi = bottom;
        if (dnHi - dnLo >= minBand) {
            ys[yn++] = (dnLo + dnHi) / 2;
        }
        if (yn == 0) {
            return null;
        }

        int w = scr.width();
        int[] xs = new int[]{w / 2, w / 4, w * 3 / 4, w / 8, w * 7 / 8};
        for (int i = 0; i < yn; i++) {
            for (int j = 0; j < xs.length; j++) {
                if (isBlankAt(ws, xs[j], ys[i])) {
                    return new int[]{xs[j], ys[i]};
                }
            }
        }
        return null;
    }

    private static boolean isBlankAt(List<AccessibilityWindowInfo> ws, int x, int y) {
        boolean probed = false;
        for (AccessibilityWindowInfo win : ws) {
            try {
                if (win.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) {
                    continue;
                }
                if (win.isActive()) {
                    continue;
                }
                Rect wb = new Rect();
                win.getBoundsInScreen(wb);
                if (!wb.contains(x, y)) {
                    continue;
                }
                AccessibilityNodeInfo r = win.getRoot();
                if (r == null) {
                    continue;
                }
                probed = true;
                AccessibilityNodeInfo hit = hitAt(r, x, y, 0);
                if (hit == null) {
                    continue;
                }
                if (hit.isClickable() || hit.isLongClickable() || hit.isEditable()) {
                    return false;
                }
                if (hit.isScrollable()) {
                    return false;
                }
                if (safeText(hit).length() > 0) {
                    return false;
                }
            } catch (Throwable ignored) {
            }
        }
        return probed;
    }

    private static String focusAdvice(Rect rb, Rect scr, String pad) {
        int upBand = rb.top;
        int downBand = scr.height() - rb.bottom;
        int minBand = Math.max(scr.height() / 24, 48);
        boolean canUp = upBand >= minBand;
        boolean canDown = downBand >= minBand;
        StringBuilder b = new StringBuilder();
        if (!canUp && !canDown) {
            b.append(pad).append("小窗几乎占满屏幕，无法用点击切换焦点，")
                    .append("请让用户手动关闭小窗。\n");
            return b.toString();
        }

        b.append(pad).append("小窗之外的区域属于下层窗口。点一下可以让焦点切到下层，")
                .append("但可能误触下层的按钮、图标或其它内容。\n");

        int[] pt = findBlankTapPoint(rb, scr);
        if (pt != null) {
            b.append(pad).append("已在 ").append(pt[0]).append(",").append(pt[1])
                    .append(" 探测到空白处（下层该位置没有可点内容），可试：click(")
                    .append(pt[0]).append(", ").append(pt[1]).append(")\n");
        } else {
            b.append(pad).append("未找到确认空白的位置，盲点有误触风险。\n");
        }
        b.append(pad).append("更稳妥的做法：请用户在手机上关掉小窗，")
                .append("或用 list_apps 查包名后 open_app 重新打开目标应用。\n");
        return b.toString();
    }

    private static void appendFocus(StringBuilder info, Rect rb, Rect scr) {
        boolean full = Math.abs(rb.width() - scr.width()) <= 2
                && Math.abs(rb.height() - scr.height()) <= 2;
        if (full) {
            info.append("focus: 全屏应用（占据整个屏幕）\n");
            return;
        }
        info.append("focus: 小窗或分屏（未铺满屏幕）\n");
        info.append("note: 下面的元素来自这个小窗，不是它后面的全屏应用。\n");
        info.append(focusAdvice(rb, scr, "      "));
    }

    private static void appendWindows(StringBuilder sb, AccessibilityService s) {
        List<AccessibilityWindowInfo> ws = null;
        try {
            ws = s.getWindows();
        } catch (Throwable ignored) {
        }
        if (ws == null || ws.size() <= 1) {
            return;
        }
        sb.append("windows: ").append(ws.size()).append('\n');
        String self = null;
        try {
            self = s.getPackageName();
        } catch (Throwable ignored) {
        }
        for (AccessibilityWindowInfo w : ws) {
            try {
                AccessibilityNodeInfo r = w.getRoot();
                String pkg = null;
                if (r != null && r.getPackageName() != null) {
                    pkg = r.getPackageName().toString();
                }
                if (self != null && self.equals(pkg)) {
                    continue;
                }
                sb.append("  W").append(w.getId()).append(' ');
                if (pkg != null) {
                    sb.append(pkg).append(' ');
                }
                sb.append(typeName(w.getType()))
                        .append("  layer=").append(w.getLayer());
                if (w.isActive()) {
                    sb.append("  ← 当前读取的窗口");
                }
                sb.append('\n');
            } catch (Throwable ignored) {
            }
        }
    }

    private static String typeName(int t) {
        if (t == AccessibilityWindowInfo.TYPE_APPLICATION) {
            return "APPLICATION";
        }
        if (t == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
            return "IME";
        }
        if (t == AccessibilityWindowInfo.TYPE_SYSTEM) {
            return "SYSTEM";
        }
        if (t == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
            return "A11Y_OVERLAY";
        }
        if (t == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
            return "SPLIT_DIVIDER";
        }
        return "TYPE_" + t;
    }

    private static void walk(AccessibilityNodeInfo n, StringBuilder sb, Rect scr,
                             int depth, int[] counter, int[] hidden, boolean all) {
        if (n == null || depth > MAX_DEPTH || counter[0] >= MAX_ITEMS) {
            return;
        }

        Rect r = new Rect();
        boolean hasRect = false;
        try {
            n.getBoundsInScreen(r);
            hasRect = true;
        } catch (Throwable ignored) {
        }
        boolean visible = hasRect && onScreen(r, scr);

        String cls = "";
        try {
            cls = n.getClassName() == null ? "" : n.getClassName().toString();
        } catch (Throwable ignored) {
        }
        String shortCls = cls;
        int dot = cls.lastIndexOf('.');
        if (dot >= 0) {
            shortCls = cls.substring(dot + 1);
        }

        String text = "";
        try {
            text = n.getText() == null ? "" : n.getText().toString().trim();
        } catch (Throwable ignored) {
        }
        String hint = "";
        try {
            hint = n.getHintText() == null ? "" : n.getHintText().toString().trim();
        } catch (Throwable ignored) {
        }
        String desc = "";
        try {
            desc = n.getContentDescription() == null ? ""
                    : n.getContentDescription().toString().trim();
        } catch (Throwable ignored) {
        }
        String viewId = "";
        try {
            viewId = n.getViewIdResourceName() == null ? "" : n.getViewIdResourceName();
        } catch (Throwable ignored) {
        }

        boolean isEdit = cls.contains("EditText");
        try {
            isEdit = isEdit || n.isEditable();
        } catch (Throwable ignored) {
        }

        boolean clickable = false;
        boolean longClickable = false;
        boolean scrollable = false;
        boolean checkable = false;
        boolean checked = false;
        try {
            clickable = n.isClickable();
            longClickable = n.isLongClickable();
            scrollable = n.isScrollable();
            checkable = n.isCheckable();
            checked = n.isChecked();
        } catch (Throwable ignored) {
        }

        boolean interesting = text.length() > 0 || desc.length() > 0 || hint.length() > 0
                || clickable || checkable || isEdit || scrollable;

        if (interesting) {
            if (!visible && !all) {
                hidden[0]++;
            } else if (counter[0] < MAX_ITEMS) {
                counter[0]++;
                int ref = counter[0];

                sb.append('@').append(ref).append(' ').append(shortCls);
                if (!visible) {
                    sb.append("[屏外]");
                }
                if (isEdit) {
                    sb.append("[输入框]");
                }
                if (text.length() > 0) {
                    sb.append(" \"").append(cut(text, 100)).append('\"');
                }
                if (desc.length() > 0) {
                    sb.append(" desc=\"").append(cut(desc, 60)).append('\"');
                }
                if (hint.length() > 0) {
                    sb.append(" hint=\"").append(cut(hint, 40)).append('\"');
                }
                if (viewId.length() > 0) {
                    sb.append(" id=").append(viewId);
                }
                if (hasRect) {
                    sb.append(" xy=(").append(r.centerX()).append(',')
                            .append(r.centerY()).append(')');
                }

                StringBuilder acts = new StringBuilder();
                if (clickable) {
                    acts.append("click ");
                }
                if (longClickable) {
                    acts.append("long ");
                }
                if (isEdit) {
                    acts.append("settext ");
                }
                if (scrollable) {
                    acts.append("scroll ");
                }
                if (checkable) {
                    acts.append(checked ? "checked " : "unchecked ");
                }
                if (acts.length() > 0) {
                    sb.append(" [").append(acts.toString().trim()).append(']');
                }

                String fk = keyOf(n, text);
                Integer f = FAILS.get(fk);
                if (f != null && f.intValue() >= FAIL_LIMIT) {
                    sb.append(" [已标记无效]");
                } else if (f != null && f.intValue() > 0) {
                    sb.append(" [无效").append(f.intValue()).append("/")
                            .append(FAIL_LIMIT).append("]");
                }
                sb.append('\n');

                synchronized (CACHE) {
                    CACHE.add(n);
                }
            }
        }

        int cc = 0;
        try {
            cc = n.getChildCount();
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < cc; i++) {
            if (counter[0] >= MAX_ITEMS) {
                break;
            }
            AccessibilityNodeInfo c = null;
            try {
                c = n.getChild(i);
            } catch (Throwable ignored) {
            }
            walk(c, sb, scr, depth + 1, counter, hidden, all);
        }
    }

    private static String cut(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

    private static String keyOf(AccessibilityNodeInfo n, String text) {
        String cls = "";
        String pkg = "";
        try {
            cls = String.valueOf(n.getClassName());
        } catch (Throwable ignored) {
        }
        try {
            pkg = String.valueOf(n.getPackageName());
        } catch (Throwable ignored) {
        }
        return pkg + "|" + cls + "|" + (text == null ? 0 : text.hashCode());
    }

    private static String systemBarAt(int x, int y, Rect scr, boolean allowClick) {
        int top = safeTop();
        if (y < top) {
            if (allowClick) {
                return null;
            }
            return "blocked\neffect: none\n"
                    + "reason: point (" + x + "," + y + ") is inside the status bar; swipe/drag refused, nothing executed\n"
                    + "why: on MIUI, injecting long-press or drag gestures into the status bar crashes SystemUI\n"
                    + "danger: this is a real crash that happened before, not a theoretical risk\n"
                    + "hint: use press_key instead of coordinates:\n"
                    + "  open notification shade -> press_key(key=\"notifications\")\n"
                    + "  go home                 -> press_key(key=\"home\")\n"
                    + "  quick settings          -> press_key(key=\"settings\")\n"
                    + "  recents                 -> press_key(key=\"recents\")\n"
                    + "do not lower the start point, nudge the y value, or retry with another x. "
                    + "the whole status bar strip is off limits for swipe and drag.\n";
        }
        int bottom = safeBottom(scr);
        if (y > bottom) {
            if (allowClick) {
                return null;
            }
            return "blocked\neffect: none\n"
                    + "reason: point (" + x + "," + y + ") is inside the navigation bar; swipe/drag refused, nothing executed\n"
                    + "why: the navigation gesture area is owned by the system; injected swipe or drag there "
                    + "is unreliable and can disturb SystemUI\n"
                    + "hint: taps are allowed here, only swipe and drag are blocked. "
                    + "for navigation use press_key:\n"
                    + "  back    -> press_key(key=\"back\")\n"
                    + "  home    -> press_key(key=\"home\")\n"
                    + "  recents -> press_key(key=\"recents\")\n"
                    + "do not move the point upward and retry, the whole navigation bar is barred for swipe and drag.\n";
        }
        return null;
    }

    private static String stopBarAt(int x, int y) {
        if (!StopBar.isShown() || !StopBar.hitBall(x, y)) {
            return null;
        }
        return "blocked\neffect: none\n"
                + "reason: point (" + x + "," + y + ") is on the user's emergency stop button, AI must not touch it\n"
                + "hint: it is a safety control for the human (pause/resume AI). "
                + "do not retry with another coordinate and do not try to drag it.\n";
    }

    private static String stopBarHit(String text) {
        if (text == null || text.length() == 0) {
            return null;
        }
        if (!text.contains(StopBar.MARK)) {
            return null;
        }
        return "blocked\neffect: none\n"
                + "reason: this element is the user's emergency stop button, AI must not touch it\n"
                + "hint: it is a safety control for the human. do not tap it and do not work around it "
                + "with coordinates. if you got paused, wait for the user to resume.\n";
    }

    private static boolean isDead(String key) {
        Integer f = FAILS.get(key);
        return f != null && f.intValue() >= FAIL_LIMIT;
    }

    private static String dead(String key) {
        return "blocked\neffect: none\n"
                + "reason: this element failed " + FAIL_LIMIT + " times in a row and is marked unusable\n"
                + "key: " + key + "\n"
                + "hint: check what is at that position now with ocr. "
                + "if you switch to coordinates, look at the screen first to confirm the target is really there. "
                + "do not reuse the old centre of a ref, it may be something else now. "
                + "you can also pick another element or wait for the user.\n";
    }

    private static String pausedMsg(String action) {
        StringBuilder b = new StringBuilder();
        b.append("paused\neffect: none\naction: ").append(action).append('\n');
        b.append("reason: the user pressed the emergency stop, all actions are paused\n");
        b.append("hint: stop immediately. do not retry, do not switch tools to work around it. ")
                .append("wait until the user resumes on the phone.\n");
        b.append("still_available: screen_snapshot, ocr, find_text, ")
                .append("current_app, server_status. read-only tools still work.\n");
        b.append("resume: ask the user to tap the floating ball at the top-right ")
                .append("(it shows a resume glyph) and continue once resumed.\n");
        return b.toString();
    }

    private static AccessibilityNodeInfo node(int ref) {
        synchronized (CACHE) {
            if (ref < 1 || ref > CACHE.size()) {
                return null;
            }
            return CACHE.get(ref - 1);
        }
    }


    private static String revCheck(boolean allowStale) {
        long er = expectRev;
        expectRev = -1L;
        long base = er >= 0 ? er : lastRev;
        if (allowStale || base < 0) {
            return null;
        }
        if (A11ySvc.rev == base) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        b.append("stale\n");
        b.append("effect: none\n");
        b.append("rev: ").append(base).append(" -> ").append(A11ySvc.rev).append('\n');
        b.append("reason: the screen changed after your last snapshot\n");
        b.append("hint: the action was refused to avoid hitting the wrong target. ")
                .append("call screen_snapshot again and retry with the new refs.\n");
        String evts = A11ySvc.eventsSince(lastSnapAt);
        if (evts.length() > 0) {
            b.append("changes:\n").append(evts);
        }
        return b.toString();
    }


    private static String finish(String action, String key, String detail,
                                 int revBefore, String fpBefore, String inline) {
        return finish(action, key, detail, revBefore, fpBefore, inline, null, null);
    }

    private static String finish(String action, String key, String detail,
                                 int revBefore, String fpBefore, String inline,
                                 AccessibilityNodeInfo watch, String watchText) {
        if (inline != null && inline.startsWith("ERROR")) {
            return inline;
        }

        long evMark = System.currentTimeMillis();
        sleep(SETTLE_MS);

        String fpAfter = fingerprint();
        int revAfter = A11ySvc.rev;

        boolean byTree = !fpAfter.equals(fpBefore);
        boolean byRev = revAfter != revBefore;
        boolean byEvent = false;
        try {
            String ev = A11ySvc.eventsSince(evMark - 1);
            byEvent = ev.length() > 0;
        } catch (Throwable ignored) {
        }

        boolean byText = false;
        if (watch != null) {
            try {
                CharSequence now = watch.getText();
                String n = now == null ? "" : now.toString().trim();
                String was = watchText == null ? "" : watchText;
                byText = !n.equals(was);
            } catch (Throwable ignored) {
            }
        }

        boolean changed = byTree || byRev || byEvent || byText;

        lastRev = revAfter;

        int fails = 0;
        if (changed) {
            FAILS.remove(key);
        } else {
            Integer old = FAILS.get(key);
            fails = (old == null ? 0 : old.intValue()) + 1;
            FAILS.put(key, Integer.valueOf(fails));
        }

        StringBuilder b = new StringBuilder();
        b.append(changed ? "ok" : "nochange").append('\n');
        b.append("action: ").append(action).append('\n');
        if (detail != null && detail.length() > 0) {
            b.append("target: ").append(detail).append('\n');
        }
        b.append("effect: ").append(changed ? "changed" : "unchanged").append('\n');
        if (changed) {
            try {
                AccessibilityService s = A11ySvc.inst;
                if (s != null) {
                    Rect scr2 = screen();
                    String d = Diff.report(treeItems(s), scr2.width(), scr2.height());
                    if (d.length() > 0) {
                        b.append(d);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        String method = methodText(inline);
        if (method != null) {
            b.append("method: ").append(method).append('\n');
        }
        try {
            String ct = (detail != null && detail.length() > 0) ? detail : action;
            StringBuilder cd = new StringBuilder();
            cd.append(changed ? "已生效" : "无变化");
            if (method != null) {
                cd.append(" · ").append(method);
            }
            cd.append(" · rev ").append(revBefore).append("\u2192").append(revAfter);
            Log.card(action + " " + cut(ct, 28), cd.toString(), changed);
            InfoBar.render(null);
        } catch (Throwable ignored) {
        }
        if (changed) {
            StringBuilder why = new StringBuilder();
            if (byTree) {
                why.append("tree ");
            }
            if (byRev) {
                why.append("rev ");
            }
            if (byEvent) {
                why.append("event ");
            }
            if (byText) {
                why.append("text ");
            }
            b.append("detected_by: ").append(why.toString().trim()).append('\n');
        }
        b.append("rev: ").append(revBefore).append(" -> ").append(revAfter).append('\n');

        boolean fellBack = "fallback:tap".equals(inline);
        if (changed) {
            if (fellBack) {
                b.append("warning: 无障碍动作不可用，本次已降级为坐标点击，精度较低。");
                b.append("请 screen_snapshot 确认是否落在预期页面。\n");
            }
        } else {
            b.append("failed_times: ").append(fails).append('\n');
            if (fails >= FAIL_LIMIT) {
                b.append("marked_invalid: true\n");
                b.append("hint: 该元素已连续 ").append(fails)
                        .append(" 次无效，后续不再自动重试。");
                b.append("请换其它元素，或 ocr 看画面，或等用户处理。\n");
            } else if (inline != null && inline.startsWith("performAction")) {
                b.append("hint: 无障碍动作报告成功，但界面没有任何变化。");
                b.append("常见原因：节点未真正绘制（幽灵节点）、节点属于非焦点窗口、");
                b.append("或该控件需要长按/滑动而不是点击。");
                b.append("先 ocr 确认画面是否真的变了，不要重复点同一个 @编号。\n");
            } else if (fellBack) {
                b.append("hint: 降级坐标点击后界面无变化。");
                b.append("可能是控件不支持点击、坐标已错位，或该处不可交互。");
                b.append("建议重新 screen_snapshot 后再判断。\n");
            } else {
                b.append("hint: 手势已下发，但界面无变化。");
                b.append("可能该处不可滚动或不可交互，或已到边界。");
                b.append("建议 ocr 确认画面。\n");
            }
        }
        return b.toString();
    }

    private static String methodText(String inline) {
        if (inline == null || inline.length() == 0) {
            return null;
        }
        if (inline.startsWith("performAction")) {
            return "无障碍动作";
        }
        if ("fallback:tap".equals(inline)) {
            return "坐标点击·已降级";
        }
        if ("gesture:tap".equals(inline)) {
            return "坐标点击";
        }
        if ("gesture:swipe".equals(inline)) {
            return "手势滑动";
        }
        if ("gesture:drag".equals(inline)) {
            return "手势拖拽";
        }
        if ("gesture:longpress".equals(inline)) {
            return "手势长按";
        }
        return inline;
    }

    private static String prepare(String action, boolean allowStale) {
        if (!ready()) {
            return "ERROR: accessibility service is not enabled, turn on MCP交互器 in system settings";
        }
        if (Config.isPaused()) {
            return pausedMsg(action);
        }
        String stale = revCheck(allowStale);
        if (stale != null) {
            return stale;
        }
        return null;
    }


    public static String click(int ref, boolean allowStale, boolean confirmRisk,
                               boolean force) {
        String pre = prepare("click", allowStale);
        if (pre != null) {
            return pre;
        }
        AccessibilityNodeInfo n = node(ref);
        if (n == null) {
            return "ERROR: ref @" + ref + " is invalid, call screen_snapshot again";
        }

        String text = safeText(n);
        String key = keyOf(n, text);
        String stop = stopBarHit(text);
        if (stop != null) {
            return stop;
        }
        if (!force && isDead(key)) {
            return dead(key);
        }
        String word = Guard.hit(text);
        if (word != null && !confirmRisk) {
            return Guard.blocked(word, "@" + ref + " " + cut(text, 40));
        }

        int revBefore = A11ySvc.rev;
        String fpBefore = fingerprint();
        A11ySvc.markAiAction();
        gate();

        String inline = null;
        try {
            AccessibilityNodeInfo t = n;
            boolean done = false;
            for (int i = 0; i < 6 && t != null; i++) {
                if (t.isClickable()) {
                    done = t.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    if (done) {
                        inline = "performAction(CLICK)=true";
                    }
                    break;
                }
                t = t.getParent();
            }
            if (!done) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                Rect scr = screen();
                if (!onScreen(r, scr)) {
                    return "ERROR: @" + ref + " 不在屏幕可见范围内（坐标 "
                            + r.centerX() + "," + r.centerY()
                            + "），无法点击。请先滑动让它出现在屏幕上。";
                }
                int tx = r.centerX();
                int ty = r.centerY();
                AccessibilityNodeInfo root = null;
                try {
                    root = A11ySvc.inst.getRootInActiveWindow();
                } catch (Throwable ignored) {
                }
                if (root == null) {
                    return "ERROR: performAction(CLICK) 返回 false，当前窗口不可读，未执行";
                }
                AccessibilityNodeInfo fresh = hitAt(root, tx, ty, 0);
                if (fresh == null) {
                    return "ERROR: 坐标 (" + tx + "," + ty + ") 处取不到控件，未执行\n"
                            + "hint: 界面可能已刷新，请重新 screen_snapshot。\n";
                }
                String freshText = safeText(fresh);
                boolean sameNode = isSameOrChild(n, fresh);
                boolean sameText = freshText.length() > 0 && freshText.equals(text);
                if (!sameNode && !sameText) {
                    return "ERROR: 坐标处控件已变化，未执行\n"
                            + "原目标: " + (text.length() == 0 ? "(无文字)" : cut(text, 40)) + "\n"
                            + "此处实际: " + (freshText.length() == 0
                            ? "(无文字)" : cut(freshText, 40)) + "\n"
                            + "hint: performAction(CLICK) 返回 false，且原位置已不是同一个控件。"
                            + "请重新 screen_snapshot 后再操作。\n";
                }
                return tapRaw(tx, ty) == null
                        ? finish("click", key, "@" + ref,
                        revBefore, fpBefore, "fallback:tap")
                        : "ERROR: 坐标点击失败";
            }
        } catch (Throwable t) {
            return "ERROR: 点击异常 " + t;
        }

        return finish("click", key, "@" + ref,
                revBefore, fpBefore, inline, n, text);
    }

    public static String clickAt(int x, int y, boolean allowStale) {
        String pre = prepare("click_at", allowStale);
        if (pre != null) {
            return pre;
        }
        Rect scr = screen();
        if (!onScreen(new Rect(x, y, x + 1, y + 1), scr)) {
            return "ERROR: 坐标 (" + x + "," + y + ") 超出屏幕范围 "
                    + scr.width() + "x" + scr.height() + "，未执行";
        }
        String st = stopBarAt(x, y);
        if (st != null) {
            return st;
        }
        String bar = systemBarAt(x, y, scr, true);
        if (bar != null) {
            return bar;
        }
        String g = guardAt(x, y);
        if (g != null) {
            return g;
        }
        int revBefore = A11ySvc.rev;
        String fpBefore = fingerprint();
        A11ySvc.markAiAction();
        gate();
        String err = tapRaw(x, y);
        if (err != null) {
            return err;
        }
        return finish("click_at", "xy:" + x + "," + y, "(" + x + "," + y + ")",
                revBefore, fpBefore, "gesture:tap");
    }

    public static String longClick(int ref, boolean allowStale, boolean confirmRisk,
                                  boolean force) {
        String pre = prepare("long_click", allowStale);
        if (pre != null) {
            return pre;
        }
        AccessibilityNodeInfo n = node(ref);
        if (n == null) {
            return "ERROR: 编号 @" + ref + " 无效，请先重新 screen_snapshot";
        }
        String text = safeText(n);
        String lkey = keyOf(n, text);
        String stop2 = stopBarHit(text);
        if (stop2 != null) {
            return stop2;
        }
        if (!force && isDead(lkey)) {
            return dead(lkey);
        }
        String word = Guard.hit(text);
        if (word != null && !confirmRisk) {
            return Guard.blocked(word, "@" + ref + " " + cut(text, 40));
        }

        int revBefore = A11ySvc.rev;
        String fpBefore = fingerprint();
        A11ySvc.markAiAction();
        gate();

        String inline = null;
        try {
            AccessibilityNodeInfo t = n;
            boolean done = false;
            for (int i = 0; i < 6 && t != null; i++) {
                if (t.isLongClickable()) {
                    done = t.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
                    if (done) {
                        inline = "performAction(LONG_CLICK)=true";
                    }
                    break;
                }
                t = t.getParent();
            }
            if (!done) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                if (!onScreen(r, screen())) {
                    return "ERROR: @" + ref + " 不在屏幕可见范围内，无法长按";
                }
                int tx = r.centerX();
                int ty = r.centerY();
                AccessibilityNodeInfo root = null;
                try {
                    root = A11ySvc.inst.getRootInActiveWindow();
                } catch (Throwable ignored) {
                }
                if (root == null) {
                    return "ERROR: performAction(LONG_CLICK) 返回 false，当前窗口不可读，未执行";
                }
                AccessibilityNodeInfo fresh = hitAt(root, tx, ty, 0);
                if (fresh == null) {
                    return "ERROR: 坐标 (" + tx + "," + ty + ") 处取不到控件，未执行\n"
                            + "hint: 界面可能已刷新，请重新 screen_snapshot。\n";
                }
                String freshText = safeText(fresh);
                boolean sameNode = isSameOrChild(n, fresh);
                boolean sameText = freshText.length() > 0 && freshText.equals(text);
                if (!sameNode && !sameText) {
                    return "ERROR: 坐标处控件已变化，未执行\n"
                            + "原目标: " + (text.length() == 0 ? "(无文字)" : cut(text, 40)) + "\n"
                            + "此处实际: " + (freshText.length() == 0
                            ? "(无文字)" : cut(freshText, 40)) + "\n"
                            + "hint: performAction(LONG_CLICK) 返回 false，且原位置已不是同一个控件。"
                            + "请重新 screen_snapshot 后再操作。\n";
                }
                String err = longTapRaw(tx, ty);
                if (err != null) {
                    return err;
                }
                inline = "gesture:longpress";
            }
        } catch (Throwable t) {
            return "ERROR: 长按异常 " + t;
        }
        return finish("long_click", lkey, "@" + ref,
                revBefore, fpBefore, inline);
    }

    public static String longClickAt(int x, int y, boolean allowStale) {
        String pre = prepare("long_click_at", allowStale);
        if (pre != null) {
            return pre;
        }
        Rect scr = screen();
        if (!onScreen(new Rect(x, y, x + 1, y + 1), scr)) {
            return "ERROR: 坐标 (" + x + "," + y + ") 超出屏幕 "
                    + scr.width() + "x" + scr.height() + "，未执行";
        }
        String st = stopBarAt(x, y);
        if (st != null) {
            return st;
        }
        String bar = systemBarAt(x, y, scr, true);
        if (bar != null) {
            return bar;
        }
        String g = guardAt(x, y);
        if (g != null) {
            return g;
        }
        int revBefore = A11ySvc.rev;
        String fpBefore = fingerprint();
        A11ySvc.markAiAction();
        gate();
        String err = longTapRaw(x, y);
        if (err != null) {
            return err;
        }
        return finish("long_click_at", "xy:" + x + "," + y, "(" + x + "," + y + ")",
                revBefore, fpBefore, "gesture:longpress");
    }


    public static String setText(int ref, String text, boolean submit, boolean allowStale) {
        String pre = prepare("set_text", allowStale);
        if (pre != null) {
            return pre;
        }
        AccessibilityNodeInfo n = node(ref);
        if (n == null) {
            return "ERROR: 编号 @" + ref + " 无效，请先重新 screen_snapshot";
        }

        int revBefore = A11ySvc.rev;
        String fpBefore = fingerprint();
        A11ySvc.markAiAction();
        gate();

        boolean done = false;
        String inline = null;
        String before = safeText(n);
        AccessibilityNodeInfo watch = null;
        try {
            AccessibilityNodeInfo target = n;
            for (int i = 0; i < 5 && target != null; i++) {
                CharSequence cls = target.getClassName();
                boolean edit = (cls != null && cls.toString().contains("EditText"))
                        || target.isEditable();
                if (edit) {
                    try {
                        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    } catch (Throwable ignored) {
                    }
                    Bundle args = new Bundle();
                    args.putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                    done = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                    if (done) {
                        inline = "performAction(SET_TEXT)=true";
                        watch = target;
                        break;
                    }
                }
                target = target.getParent();
            }
        } catch (Throwable ignored) {
        }

        if (!done) {
            return "ERROR: @" + ref + " 该控件不是输入框\n"
                    + "hint: 如确定其应当是输入框，可能需先点击展开/聚焦，"
                    + "再重新 screen_snapshot 后对新的输入框编号调用 set_text\n";
        }

        String r = finish("set_text", keyOf(n, text),
                "@" + ref + " len=" + text.length(), revBefore, fpBefore, inline,
                watch, before);

        if (submit) {
            sleep(200);
            String e = enter();
            return r + "\nsubmit: " + e + "\n";
        }
        return r;
    }

    public static String enter() {
        AccessibilityService s = A11ySvc.inst;
        if (s == null) {
            return "ERROR: 无障碍服务未开启";
        }
        try {
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            AccessibilityNodeInfo f = findFocus(root, 0);
            if (f != null) {
                try {
                    if (f.performAction(0x01000010)) {
                        return "ok(IME_ENTER)";
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return "ERROR: 没有聚焦的输入框，无法回车";
    }

    private static AccessibilityNodeInfo findFocus(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 25) {
            return null;
        }
        try {
            if (n.isFocused()) {
                return n;
            }
            int cc = n.getChildCount();
            for (int i = 0; i < cc; i++) {
                AccessibilityNodeInfo r = findFocus(n.getChild(i), depth + 1);
                if (r != null) {
                    return r;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }


    private static String tapRaw(int x, int y) {
        AccessibilityService s = A11ySvc.inst;
        if (s == null) {
            return "ERROR: 无障碍服务未开启";
        }
        try {
            Path p = new Path();
            p.moveTo(x, y);
            GestureDescription.Builder b = new GestureDescription.Builder();
            b.addStroke(new GestureDescription.StrokeDescription(p, 0, 60));
            boolean ok = s.dispatchGesture(b.build(), null, null);
            return ok ? null : "ERROR: 手势被系统拒绝";
        } catch (Throwable t) {
            return "ERROR: 手势异常 " + t;
        }
    }

    private static String longTapRaw(int x, int y) {
        AccessibilityService s = A11ySvc.inst;
        if (s == null) {
            return "ERROR: 无障碍服务未开启";
        }
        try {
            Path p = new Path();
            p.moveTo(x, y);
            GestureDescription.Builder b = new GestureDescription.Builder();
            b.addStroke(new GestureDescription.StrokeDescription(p, 0, 800));
            boolean ok = s.dispatchGesture(b.build(), null, null);
            return ok ? null : "ERROR: 手势被系统拒绝";
        } catch (Throwable t) {
            return "ERROR: 手势异常 " + t;
        }
    }

    public static String swipe(int x1, int y1, int x2, int y2, int duration,
                               boolean allowStale) {
        String pre = prepare("swipe", allowStale);
        if (pre != null) {
            return pre;
        }
        Rect scr = screen();
        if (scr.width() > 0) {
            if (x1 < 0 || x1 >= scr.width() || x2 < 0 || x2 >= scr.width()
                    || y1 < 0 || y1 >= scr.height() || y2 < 0 || y2 >= scr.height()) {
                return "ERROR: 滑动坐标超出屏幕 " + scr.width() + "x" + scr.height()
                        + "，未执行";
            }
        }
        String st1 = stopBarAt(x1, y1);
        if (st1 == null) {
            st1 = stopBarAt(x2, y2);
        }
        if (st1 != null) {
            return st1;
        }
        String bar1 = systemBarAt(x1, y1, scr, false);
        if (bar1 != null) {
            return bar1;
        }
        String bar2 = systemBarAt(x2, y2, scr, false);
        if (bar2 != null) {
            return bar2;
        }
        if (duration < 50) {
            duration = 50;
        }
        if (duration > 3000) {
            duration = 3000;
        }

        int revBefore = A11ySvc.rev;
        String fpBefore = fingerprint();
        A11ySvc.markAiAction();
        gate();

        try {
            Path p = new Path();
            p.moveTo(x1, y1);
            p.lineTo(x2, y2);
            GestureDescription.Builder b = new GestureDescription.Builder();
            b.addStroke(new GestureDescription.StrokeDescription(p, 0, duration));
            boolean ok = A11ySvc.inst.dispatchGesture(b.build(), null, null);
            if (!ok) {
                return "ERROR: 滑动手势被系统拒绝";
            }
        } catch (Throwable t) {
            return "ERROR: 滑动异常 " + t;
        }
        return finish("swipe", "swipe:" + x1 + "," + y1 + "," + x2 + "," + y2,
                "(" + x1 + "," + y1 + ")->(" + x2 + "," + y2 + ")",
                revBefore, fpBefore, "gesture:swipe");
    }

    public static String drag(int x1, int y1, int x2, int y2,
                              int holdMs, int moveMs, boolean allowStale) {
        String pre = prepare("drag", allowStale);
        if (pre != null) {
            return pre;
        }
        Rect scr = screen();
        if (scr.width() > 0) {
            if (x1 < 0 || x1 >= scr.width() || y1 < 0 || y1 >= scr.height()
                    || x2 < 0 || x2 >= scr.width() || y2 < 0 || y2 >= scr.height()) {
                return "ERROR: 拖动坐标超出屏幕 " + scr.width() + "x" + scr.height()
                        + "，未执行";
            }
        }
        String st1 = stopBarAt(x1, y1);
        if (st1 == null) {
            st1 = stopBarAt(x2, y2);
        }
        if (st1 != null) {
            return st1;
        }
        String bar1 = systemBarAt(x1, y1, scr, false);
        if (bar1 != null) {
            return bar1;
        }
        String bar2 = systemBarAt(x2, y2, scr, false);
        if (bar2 != null) {
            return bar2;
        }
        if (holdMs < 200) {
            holdMs = 200;
        }
        if (holdMs > 3000) {
            holdMs = 3000;
        }
        if (moveMs < 100) {
            moveMs = 100;
        }
        if (moveMs > 5000) {
            moveMs = 5000;
        }

        int revBefore = A11ySvc.rev;
        String fpBefore = fingerprint();
        A11ySvc.markAiAction();
        gate();

        try {
            GestureDescription.Builder b = new GestureDescription.Builder();

            Path hold = new Path();
            hold.moveTo(x1, y1);
            b.addStroke(new GestureDescription.StrokeDescription(hold, 0, holdMs));

            Path move = new Path();
            move.moveTo(x1, y1);
            move.lineTo(x2, y2);
            b.addStroke(new GestureDescription.StrokeDescription(move, holdMs, moveMs));

            boolean ok = A11ySvc.inst.dispatchGesture(b.build(), null, null);
            if (!ok) {
                return "ERROR: 拖动手势被系统拒绝";
            }
        } catch (Throwable t) {
            return "ERROR: 拖动异常 " + t;
        }
        return finish("drag", "drag:" + x1 + "," + y1 + "," + x2 + "," + y2,
                "(" + x1 + "," + y1 + ")->(" + x2 + "," + y2 + ")",
                revBefore, fpBefore, "gesture:drag");
    }

    public static String pressKey(String key, boolean allowStale) {
        String pre = prepare("press_key", allowStale);
        if (pre != null) {
            return pre;
        }
        if (key == null) {
            key = "back";
        }
        key = key.toLowerCase();

        int action = -1;
        if (key.equals("back")) {
            action = AccessibilityService.GLOBAL_ACTION_BACK;
        } else if (key.equals("home")) {
            action = AccessibilityService.GLOBAL_ACTION_HOME;
        } else if (key.equals("recents") || key.equals("recent")) {
            action = AccessibilityService.GLOBAL_ACTION_RECENTS;
        } else if (key.equals("notifications")) {
            action = AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS;
        } else if (key.equals("settings")) {
            action = AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS;
        } else if (key.equals("power")) {
            action = AccessibilityService.GLOBAL_ACTION_POWER_DIALOG;
        } else if (key.equals("lock")) {
            if (Build.VERSION.SDK_INT >= 28) {
                action = AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN;
            }
        }
        if (action < 0) {
            return "ERROR: 不支持 " + key
                    + "（可用: back/home/recents/notifications/settings/power/lock）";
        }

        int revBefore = A11ySvc.rev;
        String fpBefore = fingerprint();
        A11ySvc.markAiAction();
        gate();
        try {
            boolean ok = A11ySvc.inst.performGlobalAction(action);
            if (!ok) {
                return "ERROR: " + key + " 执行被拒绝";
            }
        } catch (Throwable t) {
            return "ERROR: " + t;
        }
        return finish("press_key", "key:" + key, key, revBefore, fpBefore,
                "performGlobalAction=true");
    }


    public static String clickText(String keyword, boolean confirmRisk,
                                   boolean allowStale) {
        String pre = prepare("click_text", allowStale);
        if (pre != null) {
            return pre;
        }
        AccessibilityService s = A11ySvc.inst;
        AccessibilityNodeInfo root = null;
        try {
            root = s.getRootInActiveWindow();
        } catch (Throwable ignored) {
        }
        if (root == null) {
            return "ERROR: 当前窗口不可读";
        }

        Rect scr = screen();
        AccessibilityNodeInfo hit = null;
        try {
            hit = search(root, keyword, scr, 0, true);
        } catch (Throwable ignored) {
        }
        if (hit == null) {
            return "notfound\neffect: none\nkeyword: " + keyword
                    + "\nhint: 当前屏幕内未找到包含该文字的可点击元素。"
                    + "请先 screen_snapshot 确认文字，或先滑动加载。"
                    + "若不确定屏幕状态，可先用 find_text 查文字是否存在。\n"
                    + focusNote();
        }

        String text = safeText(hit);
        String word = Guard.hit(text.length() > 0 ? text : keyword);
        if (word != null && !confirmRisk) {
            return Guard.blocked(word, cut(text.length() > 0 ? text : keyword, 40));
        }

        int revBefore = A11ySvc.rev;
        String fpBefore = fingerprint();
        A11ySvc.markAiAction();
        gate();

        boolean done = false;
        try {
            AccessibilityNodeInfo t = hit;
            for (int i = 0; i < 6 && t != null; i++) {
                if (t.isClickable()) {
                    done = t.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    break;
                }
                t = t.getParent();
            }
            if (!done) {
                Rect r = new Rect();
                hit.getBoundsInScreen(r);
                if (!onScreen(r, scr)) {
                    return "ERROR: 找到「" + keyword + "」但不在可见区域";
                }
                String err = tapRaw(r.centerX(), r.centerY());
                if (err != null) {
                    return err;
                }
            }
        } catch (Throwable t) {
            return "ERROR: 点击异常 " + t;
        }

        return finish("click_text", "text:" + keyword, "\"" + keyword + "\"",
                revBefore, fpBefore, "matched");
    }

    public static String findText(String keyword) {
        if (!ready()) {
            return "ERROR: 无障碍服务未开启";
        }
        AccessibilityService s = A11ySvc.inst;
        AccessibilityNodeInfo root = null;
        try {
            root = s.getRootInActiveWindow();
        } catch (Throwable ignored) {
        }
        if (root == null) {
            return "ERROR: 当前窗口不可读";
        }
        Rect scr = screen();
        StringBuilder b = new StringBuilder();
        int[] cnt = new int[]{0};
        collect(root, keyword, scr, 0, b, cnt);
        if (cnt[0] == 0) {
            return "notfound\nkeyword: " + keyword + "\n"
                    + "hint: 当前屏幕没有这段文字。"
                    + "若内容在滚动区域下方，先 swipe 向上滑动再查；"
                    + "若画面与无障碍树不一致，用 ocr 看实际画面。\n"
                    + focusNote();
        }
        b.insert(0, "found: " + cnt[0] + "\nkeyword: " + keyword + "\n");
        return b.toString();
    }

    private static void collect(AccessibilityNodeInfo n, String kw, Rect scr,
                                int depth, StringBuilder b, int[] cnt) {
        if (n == null || depth > 30 || cnt[0] > 60) {
            return;
        }
        Rect r = new Rect();
        try {
            n.getBoundsInScreen(r);
            if (onScreen(r, scr)) {
                String t = safeText(n);
                if (t.length() > 0 && t.contains(kw)) {
                    boolean ck = false;
                    try {
                        ck = n.isClickable();
                    } catch (Throwable ignored) {
                    }
                    cnt[0]++;
                    b.append("  \"").append(cut(t, 80)).append("\" xy=(")
                            .append(r.centerX()).append(',').append(r.centerY()).append(')')
                            .append(ck ? " [click]" : "")
                            .append('\n');
                }
            }
        } catch (Throwable ignored) {
        }
        int cc = 0;
        try {
            cc = n.getChildCount();
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < cc; i++) {
            AccessibilityNodeInfo c = null;
            try {
                c = n.getChild(i);
            } catch (Throwable ignored) {
            }
            collect(c, kw, scr, depth + 1, b, cnt);
        }
    }

    private static AccessibilityNodeInfo search(AccessibilityNodeInfo n, String kw, Rect scr,
                                                int depth, boolean clickOnly) {
        if (n == null || depth > 30) {
            return null;
        }
        try {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (onScreen(r, scr)) {
                String t = safeText(n);
                if (t.length() > 0 && t.contains(kw)) {
                    boolean ck = n.isClickable();
                    if (ck) {
                        return n;
                    }
                    if (!clickOnly) {
                        return n;
                    }
                    AccessibilityNodeInfo p = n.getParent();
                    for (int i = 0; i < 5 && p != null; i++) {
                        if (p.isClickable()) {
                            return n;
                        }
                        p = p.getParent();
                    }
                    return n;
                }
            }
            int cc = n.getChildCount();
            for (int i = 0; i < cc; i++) {
                AccessibilityNodeInfo c = search(n.getChild(i), kw, scr, depth + 1, clickOnly);
                if (c != null) {
                    return c;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String safeText(AccessibilityNodeInfo n) {
        try {
            CharSequence t = n.getText();
            if (t != null && t.length() > 0) {
                return t.toString().trim();
            }
            CharSequence d = n.getContentDescription();
            if (d != null && d.length() > 0) {
                return d.toString().trim();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }


    public static boolean ocrRun(int quality, boolean wantImage, boolean grid,
                                 java.util.List<Ocr.Line> out, String[] note,
                                 String[] imgOut) {
        if (imgOut != null) {
            imgOut[0] = null;
        }
        if (quality < 40) {
            quality = 40;
        }
        if (quality > 100) {
            quality = 100;
        }
        String b64 = screenshotBase64(quality, false, note);
        if (b64 == null) {
            return false;
        }
        Bitmap src = null;
        Bitmap big = null;
        Bitmap drawn = null;
        try {
            byte[] raw = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP);
            src = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
            if (src == null) {
                note[0] = "截图解码失败";
                return false;
            }

            int sw = src.getWidth();
            int sh = src.getHeight();
            int bw = Math.round(sw * Ocr.UPSCALE);
            int bh = Math.round(sh * Ocr.UPSCALE);
            big = Bitmap.createScaledBitmap(src, bw, bh, true);
            if (big == null) {
                big = src;
            }

            String[] oe = new String[]{null};
            java.util.List<Ocr.Line> lines = Ocr.run(big, Ocr.UPSCALE, oe);
            if (oe[0] != null) {
                note[0] = (note[0] == null ? "" : note[0] + " / ") + "OCR: " + oe[0];
            }
            if (out != null) {
                out.addAll(lines);
            }
            lastRev = A11ySvc.rev;
            if (!wantImage) {
                return true;
            }
            drawn = Grid.drawOcr(src, lines, grid);
            if (drawn == null) {
                return true;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            drawn.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            if (imgOut != null) {
                imgOut[0] = android.util.Base64.encodeToString(
                        bos.toByteArray(), android.util.Base64.NO_WRAP);
            }
            return true;
        } catch (Throwable t) {
            note[0] = "OCR 异常 " + t;
            return false;
        } finally {
            try {
                if (drawn != null && drawn != src) {
                    drawn.recycle();
                }
                if (big != null && big != src) {
                    big.recycle();
                }
                if (src != null) {
                    src.recycle();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean ocrPng(byte[] png, boolean wantImage, boolean grid,
                                 java.util.List<Ocr.Line> out, String[] note,
                                 String[] imgOut) {
        if (imgOut != null) {
            imgOut[0] = null;
        }
        if (png == null || png.length == 0) {
            note[0] = "no image";
            return false;
        }

        Bitmap src = null;
        Bitmap big = null;
        Bitmap drawn = null;
        try {
            src = android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length);
            if (src == null) {
                note[0] = "decode failed";
                return false;
            }

            int bw = Math.round(src.getWidth() * Ocr.UPSCALE);
            int bh = Math.round(src.getHeight() * Ocr.UPSCALE);
            big = Bitmap.createScaledBitmap(src, bw, bh, true);
            if (big == null) {
                big = src;
            }

            String[] oe = new String[]{null};
            java.util.List<Ocr.Line> lines = Ocr.run(big, Ocr.UPSCALE, oe);
            if (oe[0] != null) {
                note[0] = (note[0] == null ? "" : note[0] + " / ") + "OCR: " + oe[0];
            }
            if (out != null) {
                out.addAll(lines);
            }
            if (!wantImage) {
                return true;
            }
            drawn = Grid.drawOcr(src, lines, grid);
            if (drawn == null) {
                return true;
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            drawn.compress(Bitmap.CompressFormat.JPEG, 85, bos);
            if (imgOut != null) {
                imgOut[0] = android.util.Base64.encodeToString(
                        bos.toByteArray(), android.util.Base64.NO_WRAP);
            }
            return true;
        } catch (Throwable t) {
            note[0] = "OCR error " + t;
            return false;
        } finally {
            try {
                if (drawn != null && drawn != src) {
                    drawn.recycle();
                }
                if (big != null && big != src) {
                    big.recycle();
                }
                if (src != null) {
                    src.recycle();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static String setStatus(String text, String step, boolean clear) {
        if (clear) {
            Log.clearProgress();
            InfoBar.render(null);
            return "ok\nprogress: cleared\n";
        }
        if (text == null || text.trim().length() == 0) {
            return "ERROR: text 不能为空（要清除请传 clear=true）";
        }
        String t = text.trim();
        if (t.length() > 120) {
            t = t.substring(0, 120);
        }
        String st = step == null ? "" : step.trim();
        if (st.length() > 20) {
            st = st.substring(0, 20);
        }
        Log.progress(t, st);
        InfoBar.render(null);
        StringBuilder b = new StringBuilder();
        b.append("ok\n");
        b.append("progress: ").append(t).append('\n');
        if (st.length() > 0) {
            b.append("step: ").append(st).append('\n');
        }
        b.append("note: 已显示在手机右上角的状态卡片上，用户能看到你当前在做什么。\n");
        b.append("hint: 在阶段变化、换目标、或开始下一步前再调一次；");
        b.append("不要每个小动作都调，也不要写很长的句子。\n");
        return b.toString();
    }

    public static String currentApp() {
        AccessibilityService s = A11ySvc.inst;
        if (s == null) {
            return "ERROR: 无障碍服务未开启";
        }
        try {
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            if (root == null) {
                return "ERROR: 无法获取当前窗口";
            }
            String pkg = String.valueOf(root.getPackageName());
            String label = pkg;
            try {
                PackageManager pm = s.getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                label = String.valueOf(pm.getApplicationLabel(ai));
            } catch (Throwable ignored) {
            }
            return "app: " + label + "\npackage: " + pkg + "\nrev: " + A11ySvc.rev;
        } catch (Throwable t) {
            return "ERROR: " + t;
        }
    }

    private static String fgPkg(AccessibilityService s) {
        try {
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            if (root != null) {
                return String.valueOf(root.getPackageName());
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String waitFgChange(AccessibilityService s, String before,
                                       long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        String last = before;
        while (System.currentTimeMillis() < end) {
            sleep(150);
            String p = fgPkg(s);
            if (p.length() == 0) {
                continue;
            }
            last = p;
            if (!p.equals(before)) {
                return p;
            }
        }
        return last;
    }

    public static String openApp(String key, boolean allowStale) {
        String pre = prepare("open_app", allowStale);
        if (pre != null) {
            return pre;
        }
        AccessibilityService s = A11ySvc.inst;
        Context ctx = s.getApplicationContext();
        PackageManager pm = ctx.getPackageManager();

        Intent intent = null;
        String resolved = null;
        try {
            intent = pm.getLaunchIntentForPackage(key);
            if (intent != null) {
                resolved = key;
            }
        } catch (Throwable ignored) {
        }

        if (intent == null) {
            try {
                List<ApplicationInfo> list = pm.getInstalledApplications(0);
                String exact = null;
                String part = null;
                for (ApplicationInfo ai : list) {
                    String label = String.valueOf(pm.getApplicationLabel(ai));
                    if (label.equalsIgnoreCase(key)) {
                        exact = ai.packageName;
                        break;
                    }
                    if (part == null && label.contains(key)) {
                        part = ai.packageName;
                    }
                }
                String found = exact != null ? exact : part;
                if (found != null) {
                    intent = pm.getLaunchIntentForPackage(found);
                    resolved = found;
                }
            } catch (Throwable ignored) {
            }
        }

        if (intent == null) {
            return "ERROR: 找不到应用「" + key + "」，可先 list_apps 查包名";
        }

        A11ySvc.markAiAction();
        gate();
        String before = fgPkg(s);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean started = false;
        try {
            ctx.startActivity(intent);
            started = true;
        } catch (Throwable t) {
            Log.add("openApp 失败: " + t);
        }

        String now = started ? waitFgChange(s, before, 2500L) : before;
        if (now.length() == 0) {
            now = "?";
        }

        boolean ok = resolved != null && resolved.equals(now);
        StringBuilder b = new StringBuilder();
        b.append(ok ? "ok" : "nochange").append('\n');
        b.append("action: open_app\n");
        b.append("target: ").append(key).append(" -> ").append(resolved).append('\n');
        b.append("effect: ").append(ok ? "changed" : "unchanged").append('\n');
        b.append("foreground: ").append(now).append('\n');
        if (!started) {
            b.append("reason: startActivity 调用失败\n");
        }
        if (!ok) {
            b.append("hint: 应用未切到前台。常见原因：系统「后台弹出界面」权限未授予（MIUI/HyperOS 需单独开启）。");
            b.append("可改用 open_url，或要求用户在桌面手动打开。\n");
        }
        return b.toString();
    }

    public static String openUrl(String url, boolean allowStale) {
        String pre = prepare("open_url", allowStale);
        if (pre != null) {
            return pre;
        }
        AccessibilityService s = A11ySvc.inst;
        if (url == null || url.length() == 0) {
            return "ERROR: url 不能为空";
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        A11ySvc.markAiAction();
        gate();
        String before = fgPkg(s);
        boolean started = false;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            s.getApplicationContext().startActivity(i);
            started = true;
        } catch (Throwable ignored) {
        }

        String now = started ? waitFgChange(s, before, 2500L) : before;
        boolean changed = started && now.length() > 0 && !now.equals(before);

        StringBuilder b = new StringBuilder();
        b.append(started ? "ok" : "nochange").append('\n');
        b.append("action: open_url\n");
        b.append("url: ").append(url).append('\n');
        b.append("foreground: ").append(now).append('\n');
        b.append("effect: ").append(changed ? "changed" : "unchanged").append('\n');
        if (!started) {
            b.append("reason: startActivity 调用失败\n");
        } else if (!changed) {
            b.append("hint: 前台应用没有变化，可能被系统拦截或浏览器未接管\n");
        } else {
            b.append("note: 已切换到 " ).append(now)
                    .append("，请用 screen_snapshot 读取页面\n");
        }
        return b.toString();
    }

    public static String listApps() {
        AccessibilityService s = A11ySvc.inst;
        if (s == null) {
            return "ERROR: 无障碍服务未开启";
        }
        PackageManager pm = s.getPackageManager();
        List<String> lines = new ArrayList<String>();
        try {
            List<ApplicationInfo> list = pm.getInstalledApplications(0);
            for (ApplicationInfo ai : list) {
                String label = String.valueOf(pm.getApplicationLabel(ai));
                lines.add(label + "  ->  " + ai.packageName);
            }
        } catch (Throwable t) {
            return "ERROR: " + t;
        }
        Collections.sort(lines, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return a.compareToIgnoreCase(b);
            }
        });
        StringBuilder sb = new StringBuilder();
        sb.append("count: ").append(lines.size()).append('\n');
        for (String l : lines) {
            sb.append(l).append('\n');
        }
        return sb.toString();
    }


    public static String screenshotBase64(int quality, boolean grid, String[] note) {
        awaitSnap();
        try {
            return screenshotInner(quality, grid, note);
        } finally {
            markSnap();
        }
    }

    private static String screenshotInner(int quality, boolean grid, String[] note) {
        final AccessibilityService s = A11ySvc.inst;
        if (s == null) {
            note[0] = "无障碍服务未开启";
            return null;
        }
        if (Build.VERSION.SDK_INT < 30) {
            note[0] = "系统版本低于 Android 11，不支持无障碍截图";
            return null;
        }
        if (quality < 20) {
            quality = 20;
        }
        if (quality > 100) {
            quality = 100;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final Bitmap[] out = new Bitmap[1];
        final int[] failCode = new int[]{-1};
        try {
            s.takeScreenshot(Display.DEFAULT_DISPLAY, s.getMainExecutor(),
                    new AccessibilityService.TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(AccessibilityService.ScreenshotResult result) {
                            try {
                                Bitmap bmp = Bitmap.wrapHardwareBuffer(
                                        result.getHardwareBuffer(), result.getColorSpace());
                                if (bmp != null) {
                                    out[0] = bmp.copy(Bitmap.Config.ARGB_8888, false);
                                }
                            } catch (Throwable ignored) {
                            } finally {
                                try {
                                    result.getHardwareBuffer().close();
                                } catch (Throwable ignored) {
                                }
                            }
                            latch.countDown();
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            failCode[0] = errorCode;
                            latch.countDown();
                        }
                    });
            latch.await(6, TimeUnit.SECONDS);
        } catch (Throwable t) {
            note[0] = "截图异常 " + t;
            return null;
        }

        if (out[0] == null) {
            note[0] = "截图失败(errorCode=" + failCode[0] + ")";
            return null;
        }

        String warn = blankCheck(out[0]);
        if (warn != null) {
            note[0] = warn;
        }

        Bitmap out2 = out[0];
        if (grid) {
            try {
                out2 = Grid.draw(out[0]);
            } catch (Throwable t) {
                out2 = out[0];
            }
        }

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            out2.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            return android.util.Base64.encodeToString(
                    bos.toByteArray(), android.util.Base64.NO_WRAP);
        } catch (Throwable t) {
            note[0] = "编码失败 " + t;
            return null;
        } finally {
            try {
                if (out2 != out[0]) {
                    out2.recycle();
                }
                out[0].recycle();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String blankCheck(Bitmap bmp) {
        try {
            int w = bmp.getWidth();
            int h = bmp.getHeight();
            int step = Math.max(1, Math.min(w, h) / 40);
            int first = bmp.getPixel(w / 2, h / 2);
            int same = 0;
            int total = 0;
            for (int y = step; y < h; y += step) {
                for (int x = step; x < w; x += step) {
                    total++;
                    if (bmp.getPixel(x, y) == first) {
                        same++;
                    }
                }
            }
            if (total > 20 && same * 100 / total > 97) {
                return "截图几乎为纯色，可能被安全窗口(FLAG_SECURE)遮挡，本帧内容不可信"
                        + "（统一色占比 " + (same * 100 / total) + "%）";
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static String screenInfo() {
        Rect r = screen();
        return "screen: " + r.width() + "x" + r.height()
                + "  rev=" + A11ySvc.rev
                + "  lastRev=" + lastRev;
    }


    public static String waitForText(String keyword, int timeoutSec) {
        if (!ready()) {
            return "ERROR: 无障碍服务未开启";
        }
        if (keyword == null || keyword.length() == 0) {
            return "ERROR: text 不能为空";
        }
        if (timeoutSec <= 0) {
            timeoutSec = 10;
        }
        if (timeoutSec > 60) {
            timeoutSec = 60;
        }

        long start = System.currentTimeMillis();
        long deadline = start + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (Config.isPaused()) {
                return pausedMsg("wait_for_text");
            }
            if (existsNow(keyword)) {
                long cost = System.currentTimeMillis() - start;
                lastRev = A11ySvc.rev;
                return "found\neffect: changed\ntext: " + keyword
                        + "\nwaited_ms: " + cost + "\nrev: " + A11ySvc.rev + "\n";
            }
            sleep(250);
        }
        long cost = System.currentTimeMillis() - start;
        return "timeout\neffect: none\ntext: " + keyword
                + "\nwaited_ms: " + cost
                + "\nhint: 在 " + timeoutSec + " 秒内当前屏幕未出现该文字。"
                + "可能是加载慢（可加大 timeout 重试一次），也可能根本没有这个文字。"
                + "先 ocr 看画面确认，不要无条件反复等。\n"
                + focusNote();
    }

    private static boolean existsNow(String keyword) {
        AccessibilityService s = A11ySvc.inst;
        if (s == null) {
            return false;
        }
        AccessibilityNodeInfo root = null;
        try {
            root = s.getRootInActiveWindow();
        } catch (Throwable ignored) {
        }
        if (root == null) {
            return false;
        }
        Rect scr = screen();
        int[] cnt = new int[]{0};
        countText(root, keyword, scr, 0, cnt);
        return cnt[0] > 0;
    }

    private static void countText(AccessibilityNodeInfo n, String kw, Rect scr,
                                  int depth, int[] cnt) {
        if (n == null || depth > 30 || cnt[0] > 0) {
            return;
        }
        Rect r = new Rect();
        try {
            n.getBoundsInScreen(r);
            if (onScreen(r, scr)) {
                String t = safeText(n);
                if (t.length() > 0 && t.contains(kw)) {
                    cnt[0]++;
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
        int cc = 0;
        try {
            cc = n.getChildCount();
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < cc; i++) {
            AccessibilityNodeInfo c = null;
            try {
                c = n.getChild(i);
            } catch (Throwable ignored) {
            }
            countText(c, kw, scr, depth + 1, cnt);
            if (cnt[0] > 0) {
                return;
            }
        }
    }


    public static String selfTest() {
        StringBuilder b = new StringBuilder();
        b.append("self_test\n");

        AccessibilityService s = A11ySvc.inst;
        b.append("P0 a11y: ").append(s != null ? "ok" : "FAIL").append('\n');
        if (s == null) {
            b.append("hint: 无障碍未开启，其余检测均无法进行\n");
            return b.toString();
        }

        Rect scr = screen();
        boolean p2 = scr.width() > 0 && scr.height() > 0;
        b.append("P2 screen: ").append(p2 ? "ok" : "FAIL")
                .append("  ").append(scr.width()).append('x').append(scr.height()).append('\n');

        AccessibilityNodeInfo root = null;
        try {
            root = s.getRootInActiveWindow();
        } catch (Throwable ignored) {
        }
        b.append("P1 active_window: ").append(root != null ? "ok" : "FAIL").append('\n');

        if (root != null && p2) {
            Rect rb = new Rect();
            try {
                root.getBoundsInScreen(rb);
            } catch (Throwable ignored) {
            }
            boolean same = Math.abs(rb.width() - scr.width()) <= 2
                    && Math.abs(rb.height() - scr.height()) <= 2;
            if (same) {
                b.append("P2 coord_base: ok 坐标基准与屏幕一致(")
                        .append(rb.width()).append('x').append(rb.height()).append(")\n");
            } else {
                b.append("P2 coord_base: WARN 窗口 ")
                        .append(rb.width()).append('x').append(rb.height())
                        .append(" 小于屏幕 ")
                        .append(scr.width()).append('x').append(scr.height())
                        .append("，坐标可能不是全局坐标\n");
            }
        }

        int wc = 0;
        boolean hasNonApp = false;
        try {
            List<AccessibilityWindowInfo> ws = s.getWindows();
            if (ws != null) {
                wc = ws.size();
                for (AccessibilityWindowInfo w : ws) {
                    if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) {
                        hasNonApp = true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        b.append("P5 windows: ").append(wc > 0 ? "ok(" + wc + ")" : "FAIL").append('\n');
        b.append("P5 multi_window: ").append(wc > 1 ? "可用" : "当前仅 1 窗口")
                .append(hasNonApp ? "（已含系统窗口）" : "").append('\n');
        b.append(A11ySvc.dumpDisplays());

        int evCount = 0;
        try {
            String ev = A11ySvc.eventsSince(System.currentTimeMillis() - 3600000L);
            evCount = ev.length() > 0 ? 1 : 0;
        } catch (Throwable ignored) {
        }
        int total = 0;
        try {
            total = A11ySvc.eventTotal;
        } catch (Throwable ignored) {
        }
        b.append("P3 events: ").append(total > 0 ? "ok(" + total + ")" : "WARN(尚无事件)")
                .append('\n');
        if (total == 0) {
            b.append("  hint: 若持续为 0，说明本机未上报无障碍事件，"
                    + "「界面变化记录」将不可用，AI 无法感知用户操作。\n");
        }

        boolean shot = Build.VERSION.SDK_INT >= 30;
        b.append("P4 screenshot: ").append(shot ? "api_ok" : "unsupported")
                .append(Build.VERSION.SDK_INT >= 30 ? "" : "(需 Android 11+)").append('\n');

        b.append("guard: ").append(Guard.ENABLED ? "on" : "off").append('\n');
        b.append("paused: ").append(Config.isPaused() ? "YES" : "no").append('\n');
        b.append("fail_limit: ").append(FAIL_LIMIT).append('\n');
        b.append("settle_ms: ").append(SETTLE_MS).append('\n');
        return b.toString();
    }


    public static String guardAt(int x, int y) {
        if (!Guard.ENABLED) {
            return null;
        }
        AccessibilityService s = A11ySvc.inst;
        if (s == null) {
            return null;
        }
        AccessibilityNodeInfo root = null;
        try {
            root = s.getRootInActiveWindow();
        } catch (Throwable ignored) {
        }
        if (root == null) {
            return null;
        }
        AccessibilityNodeInfo n = hitAt(root, x, y, 0);
        if (n == null) {
            return null;
        }
        StringBuilder all = new StringBuilder();
        try {
            CharSequence t = n.getText();
            if (t != null) {
                all.append(t);
            }
        } catch (Throwable ignored) {
        }
        try {
            CharSequence d = n.getContentDescription();
            if (d != null) {
                all.append(' ').append(d);
            }
        } catch (Throwable ignored) {
        }
        AccessibilityNodeInfo p = null;
        try {
            p = n.getParent();
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < 3 && p != null; i++) {
            try {
                CharSequence t = p.getText();
                if (t != null) {
                    all.append(' ').append(t);
                }
            } catch (Throwable ignored) {
            }
            try {
                p = p.getParent();
            } catch (Throwable ignored) {
                p = null;
            }
        }
        String word = Guard.hit(all.toString());
        if (word == null) {
            return null;
        }
        return Guard.blocked(word, "(" + x + "," + y + ") " + cut(all.toString(), 40));
    }

    private static boolean isSameOrChild(AccessibilityNodeInfo original,
                                         AccessibilityNodeInfo hit) {
        if (original == null || hit == null) {
            return false;
        }
        AccessibilityNodeInfo p = hit;
        for (int i = 0; i < 15 && p != null; i++) {
            try {
                if (p.equals(original)) {
                    return true;
                }
                p = p.getParent();
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    private static AccessibilityNodeInfo hitAt(AccessibilityNodeInfo n, int x, int y, int depth) {
        if (n == null || depth > 30) {
            return null;
        }
        Rect r = new Rect();
        try {
            n.getBoundsInScreen(r);
            if (!r.contains(x, y)) {
                return null;
            }
        } catch (Throwable t) {
            return null;
        }
        int cc = 0;
        try {
            cc = n.getChildCount();
        } catch (Throwable ignored) {
        }
        for (int i = cc - 1; i >= 0; i--) {
            AccessibilityNodeInfo c = null;
            try {
                c = n.getChild(i);
            } catch (Throwable ignored) {
            }
            AccessibilityNodeInfo got = hitAt(c, x, y, depth + 1);
            if (got != null) {
                return got;
            }
        }
        return n;
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
