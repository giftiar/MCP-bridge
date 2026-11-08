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

import org.json.JSONArray;
import org.json.JSONObject;

public class VdTools {

    private static final String STATE
            = "state: ready / service_missing / shizuku_missing / shizuku_denied / "
            + "display_missing / overlay_denied";

    static String instructions() {
        StringBuilder b = new StringBuilder();

        b.append("VIRTUAL DISPLAY MODE\n\n")
                .append("You operate an isolated virtual display. The user's own screen is ")
                .append("untouched and your work is invisible to them.\n\n");

        b.append("READING\n")
                .append("ocr returns every recognised text line with an index and an exact ")
                .append("centre in virtual display pixels. Pass those centres to gestures ")
                .append("unchanged. No accessibility tree exists here; ocr is the only way ")
                .append("to inspect the display and the only way to verify an action.\n\n");

        b.append("ACTING\n")
                .append("click taps. long_click holds in place. swipe moves between two ")
                .append("points. long_drag holds before moving and is the only reliable way ")
                .append("to reorder lists, drag handles and select text.\n")
                .append("All gestures take absolute pixel coordinates within the bounds ")
                .append("reported by vd_info.\n\n");

        b.append("TYPING\n")
                .append("type_text writes into the currently focused input field. Tap the ")
                .append("field first, then call type_text. It uses this app's own input ")
                .append("method, so the user's keyboard is never disturbed.\n")
                .append("type_text returns no_connection when nothing in the virtual display ")
                .append("holds input focus. In that case tap the field again and retry once; ")
                .append("if it still fails, the app cannot accept typed text on this display ")
                .append("and you must report it as unsupported.\n\n");

        b.append("KEYS\n")
                .append("Use press_key for back, home, recents, enter, backspace, menu and ")
                .append("volume. power is accepted but locks the real phone, so avoid it.\n")
                .append("There is no clipboard on this display.\n\n")
                .append("SAFETY\n")
                .append("Clicks landing on a danger word (delete, pay, uninstall, allow, ")
                .append("install and similar) are refused. ocr first so the guard can see the ")
                .append("text, and pass confirm_risk only when the user explicitly asked for ")
                .append("that exact action.\n\n");

        b.append("WORKFLOW\n")
                .append("Call vd_info first to confirm the display is ready and to learn its ")
                .append("bounds. Then ocr once and act.\n")
                .append("Every action returns the current text with centres, so you normally ")
                .append("do not need to call ocr again between steps.\n")
                .append("Actions re-read the screen before acting. If the content at the point ")
                .append("changed since you last looked, the action is refused and you get the ")
                .append("current text list; pick a new centre instead of retrying the old one.\n")
                .append("Call wait only when content is clearly still loading.\n");

        return b.toString();
    }

    static JSONObject list() throws Exception {
        JSONArray arr = new JSONArray();

        JSONObject ocrProps = props(
                prop("find", "string", "Optional text to locate; returns that line's centre"),
                prop("grid", "boolean", "Also draw a coordinate grid on the image (default false)"));
        arr.put(tool("ocr",
                "Reads the virtual display and returns every text line with an index and its "
                        + "exact centre coordinate. This is the only way to inspect the display.",
                schema(ocrProps, null)));

        JSONObject pointProps = props(
                prop("x", "integer", "X coordinate"),
                prop("y", "integer", "Y coordinate"),
                prop("expect", "string",
                        "Text you believe sits at this point. The tap is refused when the "
                                + "screen moved and something else is there now."),
                prop("allow_stale", "boolean",
                        "Skip the stale check for this one tap (default false)."),
                prop("confirm_risk", "boolean",
                        "Skip the danger-word guard for this one tap. Only use it when the "
                                + "user explicitly asked for this exact action."));
        JSONArray pointReq = required("x", "y");

        arr.put(tool("click",
                "Taps a point on the virtual display. The screen is re-read first: if the "
                        + "content at that point no longer matches what you saw, nothing is "
                        + "tapped and the current text list is returned. Danger words are "
                        + "refused unless confirm_risk is true.",
                schema(pointProps, pointReq)));

        arr.put(tool("long_click",
                "Long-presses a point on the virtual display. Same checks as click.",
                schema(pointProps, pointReq)));

        JSONArray lineReq = required("x1", "y1", "x2", "y2");

        JSONObject swipeProps = props(
                prop("x1", "integer", "Start X"),
                prop("y1", "integer", "Start Y"),
                prop("x2", "integer", "End X"),
                prop("y2", "integer", "End Y"),
                prop("duration", "integer", "Duration in ms, default 600"),
                prop("allow_stale", "boolean",
                        "Skip the stale check on the start point (default false)."));
        arr.put(tool("swipe",
                "Swipes between two points on the virtual display. The start point is "
                        + "verified against the current screen first.",
                schema(swipeProps, lineReq)));

        JSONObject dragProps = props(
                prop("x1", "integer", "Start X"),
                prop("y1", "integer", "Start Y"),
                prop("x2", "integer", "End X"),
                prop("y2", "integer", "End Y"),
                prop("hold", "integer", "Hold before moving in ms, default 800, min 500"),
                prop("duration", "integer", "Move duration in ms, default 800"));
        arr.put(tool("long_drag",
                "Holds a point, then moves to another. Required for reordering, selection "
                        + "and drag handles.",
                schema(dragProps, lineReq)));

        arr.put(tool("type_text",
                "Writes text into the input field that currently holds focus on the virtual "
                        + "display. Tap the field first. Uses this app's own input method, so "
                        + "the user's keyboard is untouched. Returns no_connection when nothing "
                        + "is focused.",
                schema(props(
                        prop("text", "string", "Text to write; replaces the field content"),
                        prop("submit", "boolean", "Press enter after writing, default false")),
                        required("text"))));

        arr.put(tool("open_app",
                "Opens an app on the virtual display by package name or label. "
                        + "The display is resized automatically when the app declares a fixed "
                        + "portrait or landscape orientation, so no manual rotation is needed.",
                schema(props(prop("target", "string", "Package name or app label")),
                        required("target"))));

        arr.put(tool("list_apps", "Lists installed apps as label -> package name.",
                schema(props(), null)));

        arr.put(tool("read_focus_text",
                "Reads the text currently inside the focused input field, plus the field hint. "
                        + "Use it to see what is already typed before replacing it.",
                schema(props(), null)));

        arr.put(tool("press_key",
                "Sends a system key to the virtual display. Use it for back, home, recents "
                        + "and enter instead of hunting for an on-screen button.",
                schema(props(prop("key", "string",
                        "back, home, recents, enter, backspace, menu, power, "
                                + "volume_up or volume_down")),
                        required("key"))));

        arr.put(tool("vd_rotate",
                "Swaps the virtual display size between portrait and landscape. "
                        + "Use it when an app is letterboxed into a narrow strip with large "
                        + "black bars above and below: those apps declare a fixed landscape "
                        + "orientation, such as TV builds and games. After rotating, call ocr "
                        + "again because every coordinate changed.",
                schema(props(prop("landscape", "boolean",
                        "true for landscape, false for portrait")))));

        arr.put(tool("vd_info",
                "Reports the virtual display state, size and any blocker. " + STATE,
                schema(props(), null)));

        arr.put(tool("wait",
                "Pauses before the next observation.",
                schema(props(prop("ms", "integer", "Milliseconds, default 500, max 10000")),
                        null)));

        JSONObject out = new JSONObject();
        out.put("tools", arr);
        return out;
    }

    static JSONObject call(Context ctx, String name, JSONObject a) throws Exception {
        if ("vd_info".equals(name)) {
            return result(state(ctx), false);
        }

        if ("vd_rotate".equals(name)) {
            if (!state(ctx).startsWith("ready")) {
                return result("ERROR: " + state(ctx), true);
            }
            String rr = VdMgr.rotate(a.optBoolean("landscape", true));
            cachedSize = null;
            Overlay.clearAction();
            Diff.clear();
            return result(rr + "\nCall ocr again; coordinates changed.", false);
        }

        if ("wait".equals(name)) {
            int ms = a.optInt("ms", 500);
            if (ms < 0) {
                ms = 0;
            }
            if (ms > 10000) {
                ms = 10000;
            }
            try {
                Thread.sleep(ms);
            } catch (Throwable ignored) {
            }
            return result("ok " + ms + "ms", false);
        }

        String blocker = state(ctx);
        if (!blocker.startsWith("ready")) {
            return result("ERROR: " + blocker + "\nCall vd_info for details.", true);
        }

        if ("ocr".equals(name)) {
            return ocr(a);
        }

        if ("click".equals(name)) {
            int cx = a.optInt("x", -1);
            int cy = a.optInt("y", -1);
            String pre = preAction(cx, cy, a.optString("expect", ""),
                    a.optBoolean("allow_stale", false),
                    a.optBoolean("confirm_risk", false));
            if (pre != null) {
                return result(pre, true);
            }
            String r = VdMgr.click(cx, cy);
            if (r.startsWith("OK")) {
                int[] sz = vdSize();
                Overlay.markClick(cx, cy, sz[0], sz[1]);
            }
            return result(r + diffSuffix(ctx), !r.startsWith("OK"));
        }

        if ("long_click".equals(name)) {
            int cx = a.optInt("x", -1);
            int cy = a.optInt("y", -1);
            String pre = preAction(cx, cy, a.optString("expect", ""),
                    a.optBoolean("allow_stale", false),
                    a.optBoolean("confirm_risk", false));
            if (pre != null) {
                return result(pre, true);
            }
            String r = VdMgr.longClick(cx, cy);
            if (r.startsWith("OK")) {
                int[] sz = vdSize();
                Overlay.markClick(cx, cy, sz[0], sz[1]);
            }
            return result(r + diffSuffix(ctx), !r.startsWith("OK"));
        }

        if ("swipe".equals(name)) {
            int dur = a.optInt("duration", 600);
            if (dur < 100) {
                dur = 100;
            }
            int x1 = a.optInt("x1", -1);
            int y1 = a.optInt("y1", -1);
            int x2 = a.optInt("x2", -1);
            int y2 = a.optInt("y2", -1);
            String pre = preAction(x1, y1, "",
                    a.optBoolean("allow_stale", false), true);
            if (pre != null) {
                return result(pre, true);
            }
            String r = VdMgr.swipe(x1, y1, x2, y2, dur);
            if (r.startsWith("OK")) {
                int[] sz = vdSize();
                Overlay.markSwipe(x1, y1, x2, y2, sz[0], sz[1]);
            }
            return result(r + diffSuffix(ctx), !r.startsWith("OK"));
        }

        if ("long_drag".equals(name)) {
            int hold = a.optInt("hold", 800);
            if (hold < 500) {
                hold = 500;
            }
            int dur = a.optInt("duration", 800);
            if (dur < 200) {
                dur = 200;
            }
            int x1 = a.optInt("x1", -1);
            int y1 = a.optInt("y1", -1);
            int x2 = a.optInt("x2", -1);
            int y2 = a.optInt("y2", -1);
            String pre = preAction(x1, y1, "",
                    a.optBoolean("allow_stale", false), true);
            if (pre != null) {
                return result(pre, true);
            }
            String r = VdMgr.drag(x1, y1, x2, y2, hold, dur);
            if (r.startsWith("OK")) {
                int[] sz = vdSize();
                Overlay.markSwipe(x1, y1, x2, y2, sz[0], sz[1]);
            }
            return result(r + diffSuffix(ctx), !r.startsWith("OK"));
        }

        if ("type_text".equals(name)) {
            return typeText(a, ctx);
        }

        if ("open_app".equals(name)) {
            String target = a.optString("target", "");
            if (target.length() == 0) {
                return result("ERROR: need target", true);
            }
            String pkg = target;
            if (target.indexOf('.') < 0) {
                pkg = resolveLabel(ctx, target);
                if (pkg.length() == 0) {
                    return result("ERROR: no app matches " + target, true);
                }
            }
            String sr = VdMgr.startApp(pkg);
            Overlay.clearAction();
            Diff.clear();
            sleepQuiet(600L);
            return result(sr + "\nOpened." + diffSuffix(ctx), !sr.startsWith("OK"));
        }

        if ("list_apps".equals(name)) {
            return result(listApps(ctx), false);
        }

        if ("read_focus_text".equals(name)) {
            return readFocusText();
        }

        if ("press_key".equals(name)) {
            String k = a.optString("key", "").trim().toLowerCase(java.util.Locale.US);
            int code = keyCode(k);
            if (code <= 0) {
                return result("ERROR: unknown key " + k
                        + ". use back, home, recents, enter, backspace, menu, power, "
                        + "volume_up or volume_down.", true);
            }
            String r = VdMgr.key(code);
            if (r.startsWith("OK")) {
                Overlay.clearAction();
            }
            return result(r + diffSuffix(ctx), !r.startsWith("OK"));
        }

        return result("ERROR: unknown tool " + name, true);
    }

    private static JSONObject typeText(JSONObject a, Context ctx) throws Exception {
        String text = a.optString("text", "");
        if (text.length() == 0) {
            return result("ERROR: empty text", true);
        }

        McpIme ime = McpIme.inst;
        boolean switched = false;

        if (ime == null || !ime.hasRealField()) {
            String act = VdMgr.activateIme();
            if (act.startsWith("FAIL")) {
                return result("ERROR: " + act, true);
            }
            switched = true;

            long deadline = System.currentTimeMillis() + 3500L;
            while (System.currentTimeMillis() < deadline) {
                sleepQuiet(150L);
                McpIme m = McpIme.inst;
                if (m != null && m.hasRealField()) {
                    break;
                }
            }
            ime = McpIme.inst;
        }

        try {
            if (ime == null || !ime.hasRealField()) {
                return result("no_connection: nothing on the virtual display holds input "
                        + "focus. Tap the field and retry once. If it still fails, this app "
                        + "cannot accept typed text here.\nime_event: "
                        + McpIme.lastEvent, true);
            }

            if (!ime.commit(text)) {
                return result("ERROR: commit failed\nime_event: "
                        + McpIme.lastEvent, true);
            }

            StringBuilder b = new StringBuilder();
            b.append("typed ").append(text.length()).append(" char(s)\n");
            b.append("ime_event: ").append(McpIme.lastEvent).append('\n');
            if (a.optBoolean("submit", false)) {
                ime.sendEnter();
                b.append("enter sent\n");
            }
            sleepQuiet(250L);
            return result(b.toString() + diffSuffix(ctx), false);
        } finally {
            if (switched) {
                sleepQuiet(120L);
                VdMgr.deactivateIme();
            }
        }
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Throwable ignored) {
        }
    }

    private static JSONObject ocr(JSONObject a) throws Exception {
        byte[] png = VdMgr.capture();
        if (png == null || png.length == 0) {
            return result("ERROR: capture failed, the display produced no frame", true);
        }

        java.util.List<Ocr.Line> lines = new java.util.ArrayList<Ocr.Line>();
        String[] note = new String[]{null};
        String[] img = new String[]{null};
        boolean withImage = Config.isOcrImageOn();
        boolean ok = Ui.ocrPng(png, withImage, a.optBoolean("grid", false),
                lines, note, img);
        if (!ok) {
            return result("ERROR: " + (note[0] == null ? "OCR failed" : note[0]), true);
        }

        StringBuilder cap = new StringBuilder();
        cap.append("OCR found ").append(lines.size()).append(" text lines.\n");
        if (withImage) {
            cap.append("Each line has a box; the tag at its left edge holds the index.\n");
        } else {
            cap.append("No image attached: the image setting is off.\n");
        }
        cap.append("Coordinates are virtual display pixels and are accepted by every ")
                .append("gesture unchanged.\n\n");

        String kw = a.optString("find", "").trim();
        if (kw.length() > 0) {
            int idx = Ocr.find(lines, kw);
            if (idx >= 0) {
                Ocr.Line ln = lines.get(idx);
                cap.append("Found \"").append(kw).append("\": T").append(idx + 1)
                        .append(" \"").append(ln.text).append("\"\n");
                cap.append("Centre: x=").append(ln.cx())
                        .append(" y=").append(ln.cy()).append("\n\n");
            } else {
                cap.append("No line contains \"").append(kw).append("\".\n")
                        .append("The text may be truncated, covered, or on another page.\n\n");
            }
        }

        rememberLines(lines);
        Diff.remember(items(lines));
        int[] osz = vdSize();
        Overlay.boxes(lines, osz[0], osz[1]);

        if (lines.isEmpty()) {
            cap.append("No text recognised. The display may be blank or icon only.\n");
        } else {
            cap.append(Ocr.describe(lines, 60));
        }
        if (note[0] != null) {
            cap.append("\nWARN: ").append(note[0]);
        }

        if (withImage && img[0] != null) {
            return image(img[0], "image/jpeg", cap.toString());
        }
        return result(cap.toString(), false);
    }

    private static JSONObject readFocusText() throws Exception {
        McpIme ime = McpIme.inst;
        boolean switched = false;
        if (ime == null || !ime.hasConnection()) {
            String act = VdMgr.activateIme();
            if (act.startsWith("FAIL")) {
                return result("ERROR: " + act, true);
            }
            switched = true;
            long deadline = System.currentTimeMillis() + 3500L;
            while (System.currentTimeMillis() < deadline) {
                sleepQuiet(150L);
                McpIme m = McpIme.inst;
                if (m != null && m.hasConnection()) {
                    break;
                }
            }
            ime = McpIme.inst;
        }
        try {
            if (ime == null || !ime.hasConnection()) {
                return result("no_connection: nothing on the virtual display holds input "
                        + "focus. Tap the field first.\nime_event: " + McpIme.lastEvent, true);
            }
            String[] r = ime.readAround();
            if (r == null) {
                return result("ERROR: the field refused to return its text\nime_event: "
                        + McpIme.lastEvent, true);
            }
            StringBuilder b = new StringBuilder();
            b.append("inputType: ").append(ime.inputType()).append('\n');
            if (r[2].length() > 0) {
                b.append("hint: ").append(r[2]).append('\n');
            }
            b.append("before_cursor: ").append(r[0]).append('\n');
            b.append("after_cursor: ").append(r[1]).append('\n');
            if (r[0].length() == 0 && r[1].length() == 0) {
                b.append("note: the field is empty\n");
            }
            return result(b.toString(), false);
        } finally {
            if (switched) {
                sleepQuiet(120L);
                VdMgr.deactivateIme();
            }
        }
    }

    private static volatile int[] cachedSize;

    private static int[] vdSize() {
        int w = Config.getVdW();
        int h = Config.getVdH();
        if (w > 0 && h > 0) {
            return new int[]{w, h};
        }
        int[] c = cachedSize;
        if (c != null && c[0] > 0 && c[1] > 0) {
            return c;
        }
        int[] p = parseSize(VdMgr.info());
        if (p != null) {
            cachedSize = p;
            return p;
        }
        return new int[]{0, 0};
    }

    private static int[] parseSize(String s) {
        if (s == null) {
            return null;
        }
        int w = field(s, "width=");
        int h = field(s, "height=");
        if (w > 100 && h > 100) {
            return new int[]{w, h};
        }
        return parseWxH(s);
    }

    private static int field(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) {
            return -1;
        }
        i += key.length();
        int j = i;
        while (j < s.length() && Character.isDigit(s.charAt(j))) {
            j++;
        }
        if (j == i) {
            return -1;
        }
        try {
            return Integer.parseInt(s.substring(i, j));
        } catch (Throwable t) {
            return -1;
        }
    }

    private static int[] parseWxH(String s) {
        if (s == null) {
            return null;
        }
        try {
            int i = 0;
            while (i < s.length()) {
                if (!Character.isDigit(s.charAt(i))) {
                    i++;
                    continue;
                }
                int start = i;
                while (i < s.length() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
                if (i >= s.length() || s.charAt(i) != 'x') {
                    continue;
                }
                int wv = Integer.parseInt(s.substring(start, i));
                int j = i + 1;
                int ds = j;
                while (j < s.length() && Character.isDigit(s.charAt(j))) {
                    j++;
                }
                if (j == ds) {
                    continue;
                }
                int hv = Integer.parseInt(s.substring(ds, j));
                if (wv > 100 && hv > 100) {
                    return new int[]{wv, hv};
                }
                i = j;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int keyCode(String k) {
        if ("back".equals(k)) {
            return android.view.KeyEvent.KEYCODE_BACK;
        }
        if ("home".equals(k)) {
            return android.view.KeyEvent.KEYCODE_HOME;
        }
        if ("recents".equals(k)) {
            return android.view.KeyEvent.KEYCODE_APP_SWITCH;
        }
        if ("enter".equals(k)) {
            return android.view.KeyEvent.KEYCODE_ENTER;
        }
        if ("backspace".equals(k)) {
            return android.view.KeyEvent.KEYCODE_DEL;
        }
        if ("menu".equals(k)) {
            return android.view.KeyEvent.KEYCODE_MENU;
        }
        if ("power".equals(k)) {
            return android.view.KeyEvent.KEYCODE_POWER;
        }
        if ("volume_up".equals(k)) {
            return android.view.KeyEvent.KEYCODE_VOLUME_UP;
        }
        if ("volume_down".equals(k)) {
            return android.view.KeyEvent.KEYCODE_VOLUME_DOWN;
        }
        return -1;
    }

    private static java.util.List<Diff.Item> items(java.util.List<Ocr.Line> lines) {
        java.util.List<Diff.Item> out = new java.util.ArrayList<Diff.Item>();
        if (lines == null) {
            return out;
        }
        for (int i = 0; i < lines.size(); i++) {
            Ocr.Line ln = lines.get(i);
            out.add(new Diff.Item(ln.text, ln.r));
        }
        return out;
    }

    private static volatile java.util.List<Ocr.Line> lastLines =
            new java.util.ArrayList<Ocr.Line>();

    private static void rememberLines(java.util.List<Ocr.Line> lines) {
        lastLines = lines == null ? new java.util.ArrayList<Ocr.Line>() : lines;
    }

    private static String textAt(java.util.List<Ocr.Line> lines, int x, int y) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        String best = null;
        int bestArea = Integer.MAX_VALUE;
        for (int i = 0; i < lines.size(); i++) {
            Ocr.Line ln = lines.get(i);
            if (ln.r.contains(x, y)) {
                int area = ln.r.width() * ln.r.height();
                if (area < bestArea) {
                    bestArea = area;
                    best = ln.text;
                }
            }
        }
        return best;
    }

    private static boolean similar(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String x = a.replaceAll("\\s+", "");
        String y = b.replaceAll("\\s+", "");
        if (x.equals(y)) {
            return true;
        }
        if (x.length() == 0 || y.length() == 0) {
            return false;
        }
        if (x.contains(y) || y.contains(x)) {
            return true;
        }
        boolean[] used = new boolean[y.length()];
        int common = 0;
        for (int i = 0; i < x.length(); i++) {
            char c = x.charAt(i);
            for (int j = 0; j < y.length(); j++) {
                if (!used[j] && y.charAt(j) == c) {
                    used[j] = true;
                    common++;
                    break;
                }
            }
        }
        return common * 2 >= Math.min(x.length(), y.length());
    }

    private static String textList(java.util.List<Ocr.Line> lines) {
        StringBuilder b = new StringBuilder();
        b.append("--- current text, tap by these centres ---\n");
        b.append(Ocr.describe(lines, 40));
        return b.toString();
    }

    private static String preAction(int x, int y, String expect,
                                    boolean allowStale, boolean skipGuard) {
        java.util.List<Ocr.Line> before = lastLines;
        java.util.List<Ocr.Line> fresh = null;
        try {
            fresh = snapshot(null);
        } catch (Throwable ignored) {
        }
        if (fresh == null || fresh.isEmpty()) {
            return null;
        }

        int[] sz = vdSize();
        rememberLines(fresh);
        Overlay.boxes(fresh, sz[0], sz[1]);
        if (Config.isDiffOn()) {
            Diff.remember(items(fresh));
        }

        String now = textAt(fresh, x, y);

        if (!skipGuard && Guard.ENABLED) {
            String w = Guard.hitHard(now);
            if (w != null) {
                return Guard.blocked(w, "\"" + now + "\" at " + x + "," + y);
            }
        }

        if (expect != null && expect.trim().length() > 0) {
            if (now == null || !similar(expect.trim(), now)) {
                StringBuilder b = new StringBuilder();
                b.append("stale\neffect: none, nothing was tapped\n");
                b.append("reason: you asked to tap \"").append(expect)
                        .append("\" but that is not at ").append(x).append(',').append(y)
                        .append(" any more\n");
                b.append("found there: ")
                        .append(now == null ? "no text" : "\"" + now + "\"").append('\n');
                b.append("hint: do not retry the same coordinates. pick a centre from ")
                        .append("the list below.\n");
                b.append(textList(fresh));
                return b.toString();
            }
            return null;
        }

        if (allowStale || before == null || before.isEmpty()) {
            return null;
        }
        String was = textAt(before, x, y);
        if (was == null || was.length() == 0) {
            return null;
        }
        if (now != null && similar(was, now)) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        b.append("stale\neffect: none, nothing was tapped\n");
        b.append("reason: the content at ").append(x).append(',').append(y)
                .append(" changed after you last read the screen\n");
        b.append("you were about to tap: \"").append(was).append("\"\n");
        b.append("now at that point: ")
                .append(now == null ? "no text" : "\"" + now + "\"").append('\n');
        b.append("hint: the screen moved or loaded. do not guess coordinates again; ")
                .append("use a centre from the list below, or pass allow_stale=true ")
                .append("if you are certain.\n");
        b.append(textList(fresh));
        return b.toString();
    }

    private static java.util.List<Ocr.Line> snapshot(Context ctx) {
        byte[] png = VdMgr.capture();
        if (png == null || png.length == 0) {
            return null;
        }
        java.util.List<Ocr.Line> lines = new java.util.ArrayList<Ocr.Line>();
        String[] note = new String[]{null};
        String[] img = new String[]{null};
        if (!Ui.ocrPng(png, false, false, lines, note, img)) {
            return null;
        }
        return lines;
    }

    private static String diffSuffix(Context ctx) {
        if (!Config.isDiffOn()) {
            return "";
        }
        java.util.List<Ocr.Line> lines = null;
        String fail = null;
        try {
            lines = snapshot(ctx);
        } catch (Throwable t) {
            fail = t.getClass().getSimpleName();
        }
        if (lines == null) {
            return "\ndiff_unavailable: " + (fail == null ? "no frame" : fail)
                    + "  (capture or OCR failed; call ocr to verify before acting)";
        }

        int[] sz = vdSize();
        boolean fresh = !Diff.ready();
        String d;
        if (fresh) {
            Diff.remember(items(lines));
            d = "screen: baseline_set (first observation, no delta yet)";
        } else {
            d = Diff.report(items(lines), sz[0], sz[1]);
        }

        rememberLines(lines);
        Overlay.boxes(lines, sz[0], sz[1]);

        StringBuilder b = new StringBuilder();
        b.append('\n').append(d);
        if (!d.endsWith("\n")) {
            b.append('\n');
        }
        if (!lines.isEmpty()) {
            b.append("--- current text, tap by these centres ---\n");
            b.append(Ocr.describe(lines, 40));
        }
        return b.toString();
    }

    static String state(Context ctx) {
        if (!VdMgr.shizukuAlive()) {
            return "shizuku_missing: Shizuku is not running";
        }
        if (!VdMgr.hasPerm()) {
            return "shizuku_denied: Shizuku permission not granted";
        }
        if (!VdMgr.ready()) {
            return "service_missing: virtual display service not connected";
        }
        String info = VdMgr.info();
        if (info.contains("ready=false")) {
            String last = VdMgr.lastCreate();
            return "display_missing: no virtual display is active"
                    + (last.length() > 0 ? "\nlast_create: " + last : "");
        }
        int w = pick(info, "width=");
        int h = pick(info, "height=");
        int dpi = pick(info, "dpi=");
        int id = pick(info, "displayId=");
        StringBuilder b = new StringBuilder();
        b.append("ready  displayId=").append(id)
                .append("  size=").append(w).append('x').append(h)
                .append("  dpi=").append(dpi)
                .append("  ").append(w > h ? "landscape" : "portrait")
                .append('\n');
        b.append("Use these pixel bounds for every coordinate.\n");
        b.append("No accessibility tree and no focus. Use type_text to enter text;")
                .append(" it drives a bundled input method.");
        return b.toString();
    }

    private static int pick(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) {
            return -1;
        }
        int j = i + key.length();
        int k = j;
        while (k < s.length() && Character.isDigit(s.charAt(k))) {
            k++;
        }
        if (k == j) {
            return -1;
        }
        try {
            return Integer.parseInt(s.substring(j, k));
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String resolveLabel(Context ctx, String label) {
        try {
            android.content.pm.PackageManager pm = ctx.getPackageManager();
            java.util.List<android.content.pm.ApplicationInfo> all
                    = pm.getInstalledApplications(0);
            for (android.content.pm.ApplicationInfo ai : all) {
                CharSequence l = ai.loadLabel(pm);
                if (l == null) {
                    continue;
                }
                if (l.toString().equalsIgnoreCase(label)) {
                    return ai.packageName;
                }
            }
            for (android.content.pm.ApplicationInfo ai : all) {
                CharSequence l = ai.loadLabel(pm);
                if (l == null) {
                    continue;
                }
                if (l.toString().toLowerCase().contains(label.toLowerCase())) {
                    return ai.packageName;
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String listApps(Context ctx) {
        StringBuilder b = new StringBuilder();
        try {
            android.content.pm.PackageManager pm = ctx.getPackageManager();
            java.util.List<android.content.pm.ApplicationInfo> all
                    = pm.getInstalledApplications(0);
            int n = 0;
            for (android.content.pm.ApplicationInfo ai : all) {
                if (pm.getLaunchIntentForPackage(ai.packageName) == null) {
                    continue;
                }
                CharSequence l = ai.loadLabel(pm);
                b.append(l == null ? ai.packageName : l.toString())
                        .append(" -> ").append(ai.packageName).append('\n');
                n++;
            }
            if (n == 0) {
                return "no launchable apps";
            }
        } catch (Throwable t) {
            return "ERROR: " + t;
        }
        return b.toString();
    }

    private static JSONObject tool(String name, String desc, JSONObject schema)
            throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("description", desc);
        o.put("inputSchema", schema);
        return o;
    }

    private static JSONObject schema(JSONObject properties) throws Exception {
        JSONObject o = new JSONObject();
        o.put("type", "object");
        o.put("properties", properties == null ? new JSONObject() : properties);
        return o;
    }

    private static JSONObject schema(JSONObject properties, JSONArray required)
            throws Exception {
        JSONObject o = schema(properties);
        if (required != null && required.length() > 0) {
            o.put("required", required);
        }
        return o;
    }

    private static JSONObject props(JSONObject... items) throws Exception {
        JSONObject o = new JSONObject();
        for (int i = 0; i < items.length; i++) {
            JSONObject one = items[i];
            if (one == null) {
                continue;
            }
            String n = one.optString("__name", "");
            if (n.length() == 0) {
                continue;
            }
            one.remove("__name");
            o.put(n, one);
        }
        return o;
    }

    private static JSONObject prop(String name, String type, String desc) throws Exception {
        JSONObject o = new JSONObject();
        o.put("__name", name);
        o.put("type", type);
        o.put("description", desc);
        return o;
    }

    private static JSONArray required(String a) throws Exception {
        JSONArray r = new JSONArray();
        r.put(a);
        return r;
    }

    private static JSONArray required(String a, String b) throws Exception {
        JSONArray r = new JSONArray();
        r.put(a);
        r.put(b);
        return r;
    }

    private static JSONArray required(String a, String b, String c, String d)
            throws Exception {
        JSONArray r = new JSONArray();
        r.put(a);
        r.put(b);
        r.put(c);
        r.put(d);
        return r;
    }

    private static JSONObject result(String text, boolean error) throws Exception {
        JSONObject o = new JSONObject();
        JSONArray arr = new JSONArray();
        JSONObject c = new JSONObject();
        c.put("type", "text");
        c.put("text", (error ? "" : "") + text);
        arr.put(c);
        o.put("content", arr);
        if (error) {
            o.put("isError", true);
        }
        return o;
    }

    private static JSONObject image(String base64, String mime, String extra)
            throws Exception {
        JSONObject o = new JSONObject();
        JSONArray arr = new JSONArray();
        JSONObject img = new JSONObject();
        img.put("type", "image");
        img.put("data", base64);
        img.put("mimeType", mime);
        arr.put(img);
        if (extra != null && extra.length() > 0) {
            JSONObject c = new JSONObject();
            c.put("type", "text");
            c.put("text", extra);
            arr.put(c);
        }
        o.put("content", arr);
        return o;
    }
}
