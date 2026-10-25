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

        b.append("NOT AVAILABLE\n")
                .append("There are no back or home keys. There is no clipboard. ")
                .append("To leave an app, use open_app for the next one.\n\n");

        b.append("WORKFLOW\n")
                .append("Call vd_info first to confirm the display is ready and to learn its ")
                .append("bounds. Then ocr, act once, and ocr again.\n")
                .append("If the display is unchanged after an action, do not repeat the same ")
                .append("coordinates; re-read first.\n")
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
                prop("y", "integer", "Y coordinate"));
        JSONArray pointReq = required("x", "y");

        arr.put(tool("click",
                "Taps a point on the virtual display.",
                schema(pointProps, pointReq)));

        arr.put(tool("long_click",
                "Long-presses a point on the virtual display.",
                schema(pointProps, pointReq)));

        JSONArray lineReq = required("x1", "y1", "x2", "y2");

        JSONObject swipeProps = props(
                prop("x1", "integer", "Start X"),
                prop("y1", "integer", "Start Y"),
                prop("x2", "integer", "End X"),
                prop("y2", "integer", "End Y"),
                prop("duration", "integer", "Duration in ms, default 600"));
        arr.put(tool("swipe",
                "Swipes between two points on the virtual display.",
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
                "Opens an app on the virtual display by package name or label.",
                schema(props(prop("target", "string", "Package name or app label")),
                        required("target"))));

        arr.put(tool("list_apps", "Lists installed apps as label -> package name.",
                schema(props(), null)));

        arr.put(tool("vd_rotate",
                "Changes the virtual display orientation and recreates its surface. "
                        + "The display also follows the foreground app automatically.",
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

    private static final java.util.Set<String> READ_ONLY =
            new java.util.HashSet<String>(java.util.Arrays.asList(
                    "vd_info", "ocr", "list_apps", "wait"));

    private static String pausedMsg(String action) {
        StringBuilder b = new StringBuilder();
        b.append("paused\neffect: none\naction: ").append(action).append('\n');
        b.append("reason: the user pressed the emergency stop, all actions are paused\n");
        b.append("hint: stop immediately. do not retry, do not switch tools to work around ")
                .append("it. wait until the user resumes on the phone.\n");
        b.append("still_available: vd_info, ocr, list_apps, wait. read-only tools still ")
                .append("work.\n");
        b.append("resume: ask the user to tap the floating ball and continue once resumed.\n");
        return b.toString();
    }

    static JSONObject call(Context ctx, String name, JSONObject a) throws Exception {
        if (!READ_ONLY.contains(name) && Config.isPaused()) {
            return result(pausedMsg(name), true);
        }

        if ("vd_info".equals(name)) {
            return result(state(ctx), false);
        }

        if ("vd_rotate".equals(name)) {
            if (!state(ctx).startsWith("ready")) {
                return result("ERROR: " + state(ctx), true);
            }
            return result(VdMgr.rotate(a.optBoolean("landscape", true))
                    + "\nCall ocr again; coordinates changed.", false);
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
            return result(VdMgr.click(a.optInt("x", -1), a.optInt("y", -1)), false);
        }

        if ("long_click".equals(name)) {
            return result(VdMgr.longClick(a.optInt("x", -1), a.optInt("y", -1)), false);
        }

        if ("swipe".equals(name)) {
            int dur = a.optInt("duration", 600);
            if (dur < 100) {
                dur = 100;
            }
            return result(VdMgr.swipe(a.optInt("x1", -1), a.optInt("y1", -1),
                    a.optInt("x2", -1), a.optInt("y2", -1), dur), false);
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
            return result(VdMgr.drag(a.optInt("x1", -1), a.optInt("y1", -1),
                    a.optInt("x2", -1), a.optInt("y2", -1), hold, dur), false);
        }

        if ("type_text".equals(name)) {
            return typeText(a);
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
            return result(VdMgr.startApp(pkg) + "\nOpened. Call ocr to read it.", false);
        }

        if ("list_apps".equals(name)) {
            return result(listApps(ctx), false);
        }

        return result("ERROR: unknown tool " + name, true);
    }

    private static JSONObject typeText(JSONObject a) throws Exception {
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
            b.append("Call ocr to confirm the field content.");
            return result(b.toString(), false);
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
                .append("  dpi=").append(dpi).append('\n');
        b.append("Use these pixel bounds for every coordinate.\n");
        b.append("No accessibility tree, no focus, no keyboard, no text input.");
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
