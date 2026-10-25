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

public class Mcp {

    public static final String PROTOCOL = "2024-11-05";
    public static final String VERSION = "0.1.0";
    public static final String VERSION_TEXT = VERSION;
    public static final String PROTOCOL_TEXT = PROTOCOL;

    private static final String REMINDER =
            "\n---\nreminder: call set_status when you start a new step or a new process.";

    private static String instructions() {
        if (Config.isVdMode()) {
            return VdTools.instructions();
        }
        return screenInstructions();
    }

    private static String screenInstructions() {
        StringBuilder b = new StringBuilder();

        b.append("MCP phone-control server. ")
                .append("If your task uses these tools, you MUST call set_status before each next step ")
                .append("so the user can see progress on a floating card, and you MUST call ")
                .append("set_status(text=\\\"TASK DONE!!!\\\") when the task is finished.\n\n");

        b.append("WHAT THE USER SEES\n")
                .append("The card sits at the top-right of the screen. It shows your step line and the last ")
                .append("few actions. If you run many tools silently the user thinks it is stuck.\n")
                .append("Good:  set_status(text=\"Open WeChat\", step=\"1/3\")\n")
                .append("Good:  set_status(text=\"Search File Transfer\", step=\"2/3\")\n")
                .append("Good:  set_status(text=\"UI unreadable, switching to OCR\")\n")
                .append("Good:  set_status(text=\"TASK DONE!!!\")\n")
                .append("Bad:   five clicks in a row with no set_status\n")
                .append("Bad:   reasoning paragraphs, or one call per tiny action\n\n");

        b.append("CAPABILITIES\n")
                .append("Read the accessibility tree, OCR screenshots, tap, long-press, set text, swipe, ")
                .append("drag, press system keys, open apps and URLs, download files to ")
                .append("Download/mcp-downloads/.\n\n");

        b.append("CHOOSING A LOCATING METHOD\n")
                .append("1. screen_snapshot + click(ref=N) is the fastest and most accurate. ")
                .append("It returns exact element bounds, so the centre is always correct. ")
                .append("Use this whenever the tree is readable.\n")
                .append("2. ocr is for anything the tree cannot see: WeChat lists, games, canvas, ")
                .append("video frames, or apps that refuse to expose nodes. It runs fully offline ")
                .append("and returns every text line with an index and an exact centre.\n")
                .append("3. ocr(grid=true) is the last resort for pure icons with no text: it draws ")
                .append("a grid over the same image so you can estimate a point.\n\n");

        b.append("OCR USAGE\n")
                .append("ocr() returns a numbered list T1..Tn, plus an annotated image when the ")
                .append("user has IMAGE on (the default).\n")
                .append("ocr(find=\"Send\") returns the centre of the matching line directly.\n")
                .append("Boxes are computed from real pixels, so pass the centre to click as-is. ")
                .append("Never re-estimate or convert coordinates.\n")
                .append("If a line you need is missing, the text may be cut off or covered; ")
                .append("scroll slightly and run ocr again.\n\n");

        b.append("GRID ESTIMATION (fallback)\n")
                .append("ocr(grid=true) adds thin cyan lines every 120px and thick yellow ")
                .append("lines every 600px, with pixel labels on the top and left edges. ")
                .append("The image is 1:1 with the screen.\n")
                .append("Estimate error is roughly +-30px, so only use it for large targets. ")
                .append("Never guess coordinates for small icons, list rows, or dense menus.\n\n");

        b.append("MANDATORY WORKFLOW\n")
                .append("1. set_status before you begin a stage.\n")
                .append("2. screen_snapshot to get refs and rev.\n")
                .append("3. click(ref=N, expect_rev=rev).\n")
                .append("4. Always read the returned effect:\n")
                .append("   effect=changed    the action took effect\n")
                .append("   effect=unchanged  nothing happened; do not repeat the same target, ")
                .append("switch to ocr or pick another element\n")
                .append("5. Refs are only valid inside the snapshot that produced them. ")
                .append("After the screen changes, snapshot again.\n")
                .append("6. stale means the screen changed (often the user touched it). ")
                .append("Snapshot again before retrying.\n\n");

        b.append("SWIPE DURATION\n")
                .append("MIUI treats short swipes as flings and they often do nothing. ")
                .append("Use duration=800 or more for scrolling. ")
                .append("Start the gesture on empty space, not on a button.\n\n");

        b.append("USER INTERFERENCE\n")
                .append("The user may touch the phone at any time. The change log in screen_snapshot ")
                .append("marks who did what: 'user' is the human, 'AI' is you. ")
                .append("When you see user actions, do not guess intent; re-plan from the current screen.\n\n");

        b.append("EMERGENCY STOP\n")
                .append("A red floating ball at the top-right is the user's safety button. ")
                .append("Red with a stop glyph means running; green with a resume glyph means paused. ")
                .append("Only the user's finger can press it. It is not in the element list and has no ref. ")
                .append("Never tap or drag that area, even if you know its coordinates.\n")
                .append("Read-only tools still work while paused. ")
                .append("When a tool returns paused, stop immediately: do not retry, do not work around it, ")
                .append("just tell the user you are paused and wait.\n\n");

        b.append("SYSTEM BARS\n")
                .append("The status bar strip at the very top and the navigation bar strip at the very ")
                .append("bottom must never be swiped or dragged by coordinates: on MIUI this crashes ")
                .append("SystemUI and can blank the screen or lock the phone. Taps on those strips are ")
                .append("allowed and are not blocked.\n")
                .append("For navigation, use press_key instead of gestures:\n")
                .append("  back / home / recents / notifications / settings / power / lock\n")
                .append("Do not nudge a coordinate to sneak past the guard.\n\n");

        b.append("SAFETY GUARD\n")
                .append("Actions whose text matches destructive words (uninstall, delete, pay, transfer, ")
                .append("reset, grant permission and similar) may return blocked. ")
                .append("Do not try click_text, raw coordinates, confirm_risk or an expanded menu to get ")
                .append("around it. Ask the user to do that step by hand.\n");

        return b.toString();
    }

    public static String handle(Context ctx, String request) {
        Object id = null;
        try {
            if (request == null || request.trim().length() == 0) {
                return null;
            }
            JSONObject req = new JSONObject(request);
            id = req.opt("id");
            String method = req.optString("method", "");

            if (id == null || JSONObject.NULL.equals(id)) {
                Log.add("通知 " + method);
                return null;
            }

            Log.add("调用 " + method);

            if ("initialize".equals(method)) {
                return wrap(id, initialize(req));
            }
            if ("tools/list".equals(method)) {
                return wrap(id, toolsList());
            }
            if ("tools/call".equals(method)) {
                return wrap(id, toolsCall(ctx, req));
            }
            if ("ping".equals(method)) {
                return wrap(id, new JSONObject());
            }
            return error(id, -32601, "method not found: " + method);
        } catch (Throwable t) {
            Log.add("请求异常: " + t);
            return error(id, -32603, String.valueOf(t));
        }
    }

    private static JSONObject initialize(JSONObject req) throws Exception {
        JSONObject params = req.optJSONObject("params");
        String pv = PROTOCOL;
        if (params != null) {
            String c = params.optString("protocolVersion", "");
            if (c.length() > 0) {
                pv = c;
            }
        }
        JSONObject r = new JSONObject();
        r.put("protocolVersion", pv);
        JSONObject caps = new JSONObject();
        caps.put("tools", new JSONObject());
        r.put("capabilities", caps);
        JSONObject info = new JSONObject();
        info.put("name", Config.getName());
        info.put("version", VERSION);
        r.put("serverInfo", info);
        r.put("instructions", instructions());
        return r;
    }

    private static String P_EXPECT = "Revision from the last screen_snapshot. "
            + "If the screen changed since then the action is refused and you must snapshot again.";
    private static String P_STALE = "Skip the revision check and force the action (default false). "
            + "Only when you are certain the target is still where you think it is.";
    private static String P_RISK = "Confirm a dangerous action (default false). "
            + "Required when the target text matches uninstall/delete/pay style keywords.";
    private static String P_FORCE = "Retry an element already marked unusable (default false). "
            + "force never bypasses the danger-word guard.";

    private static JSONObject toolsList() throws Exception {
        if (Config.isVdMode()) {
            return VdTools.list();
        }
        JSONArray arr = new JSONArray();

        arr.put(tool("set_status",
                "MUST CALL. Reports what you are doing right now to the floating status card the "
                        + "user is watching. Call it when you start a task, change stage, change goal, "
                        + "or begin the next step. One short human sentence, no reasoning. "
                        + "Report 'TASK DONE!!!' when the task is finished.",
                schema(props(
                        prop("text", "string", "What you are doing now, one short sentence"),
                        prop("step", "string", "Optional progress marker such as 2/5"),
                        prop("clear", "boolean", "true clears the progress line")), null)));

        arr.put(tool("screen_snapshot",
                "Reads the current screen. Returns the change log (what the user just did), "
                        + "the element list with @refs, and rev. Call this before acting. "
                        + "Pass the returned rev to expect_rev later.",
                schema(props(prop("include_offscreen", "boolean",
                        "Include elements outside the visible area (default false). "
                                + "Offscreen elements are usually not tappable.")), null)));

        arr.put(tool("ocr",
                "Preferred locator when the accessibility tree is unreadable. Screenshots the "
                        + "screen and runs offline Chinese OCR. Every text line comes back with "
                        + "an index T1..Tn and an exact centre coordinate. "
                        + "Pick the line you need and pass its centre to click. "
                        + "Accuracy is about +-5px because boxes come from real pixels. "
                        + "Works on WeChat, games, canvas, video frames; needs no network. "
                        + "Pass find to get the centre of a matching line directly.",
                schema(props(
                        prop("quality", "integer", "JPEG quality 40-100, default 85. Keep it at 70 or above"),
                        prop("find", "string", "Optional text to locate; returns that line's centre"),
                        prop("grid", "boolean", "Also draw the coordinate grid on the image "
                                + "(default false). The boxes are already exact, so usually unnecessary.")), null)));

        arr.put(tool("click",
                "Taps by ref (@number) or by x/y coordinates. "
                        + "The returned effect tells you whether it worked: changed or unchanged. "
                        + "Always read effect; when unchanged do not repeat the same target, "
                        + "switch to ocr or pick another element. "
                        + "Swiping or dragging the status bar crashes SystemUI; taps there are allowed.",
                schema(props(
                        prop("ref", "integer", "Element ref, for example 7 for @7"),
                        prop("x", "integer", "X coordinate"),
                        prop("y", "integer", "Y coordinate"),
                        prop("expect_rev", "integer", P_EXPECT),
                        prop("allow_stale", "boolean", P_STALE),
                        prop("force", "boolean", P_FORCE)), null)));

        arr.put(tool("long_click",
                "Long-presses by ref or x/y. Same parameters as click. "
                        + "Useful for context menus and selection drags.",
                schema(props(
                        prop("ref", "integer", "Element ref"),
                        prop("x", "integer", "X coordinate"),
                        prop("y", "integer", "Y coordinate"),
                        prop("expect_rev", "integer", P_EXPECT),
                        prop("allow_stale", "boolean", P_STALE),
                        prop("force", "boolean", P_FORCE)), null)));

        arr.put(tool("set_text",
                "Writes text into an editable field. Fails if the ref is not an input. "
                        + "Empty text clears the field.",
                schema(props(
                        prop("ref", "integer", "Input field ref"),
                        prop("text", "string", "Text to write; empty string clears it"),
                        prop("submit", "boolean", "Press enter after writing, default false"),
                        prop("expect_rev", "integer", P_EXPECT),
                        prop("allow_stale", "boolean", P_STALE)), required("ref", "text"))));

        arr.put(tool("swipe",
                "Swipes from (x1,y1) to (x2,y2). Coordinates are validated against the screen. "
                        + "MIUI treats short swipes as flings, so use duration=800 or more. "
                        + "Start on empty space, not on a button. "
                        + "Swipe on the status bar or navigation bar is refused; taps there are fine. "
                        + "To open the notification shade use press_key instead.",
                schema(props(
                        prop("x1", "integer", "Start x"),
                        prop("y1", "integer", "Start y"),
                        prop("x2", "integer", "End x"),
                        prop("y2", "integer", "End y"),
                        prop("duration", "integer", "Milliseconds, default 800. Use 600+ for slow drags"),
                        prop("expect_rev", "integer", P_EXPECT),
                        prop("allow_stale", "boolean", P_STALE)), required("x1", "y1", "x2", "y2"))));

        arr.put(tool("drag",
                "Long-press at (x1,y1) for hold_ms then drag to (x2,y2). "
                        + "For moving icons, sliders and long-press menus. "
                        + "Unlike swipe, it holds still at the start. "
                        + "HIGH RISK: starting or ending on the status bar or navigation bar is "
                        + "refused outright, because it crashes the MIUI system UI.",
                schema(props(
                        prop("x1", "integer", "Start x"),
                        prop("y1", "integer", "Start y"),
                        prop("x2", "integer", "End x"),
                        prop("y2", "integer", "End y"),
                        prop("hold_ms", "integer", "Hold duration at start, default 500"),
                        prop("move_ms", "integer", "Drag duration, default 600"),
                        prop("expect_rev", "integer", P_EXPECT),
                        prop("allow_stale", "boolean", P_STALE)),
                        required("x1", "y1", "x2", "y2"))));

        arr.put(tool("press_key",
                "Presses a system key. back, home, recents, notifications, settings, power, lock.",
                schema(props(
                        prop("key", "string", "Key to press"),
                        prop("expect_rev", "integer", P_EXPECT),
                        prop("allow_stale", "boolean", P_STALE)), required("key"))));

        arr.put(tool("click_text",
                "Taps by visible text. Resolved at execution time, so it still works after the "
                        + "page refreshed and refs went stale. Returns notfound when absent.",
                schema(props(
                        prop("text", "string", "Text to tap, substring match"),
                        prop("confirm_risk", "boolean", P_RISK)), required("text"))));

        arr.put(tool("find_text",
                "Searches the current screen for text without tapping. "
                        + "Use it to check whether something exists and where it is.",
                schema(props(prop("text", "string", "Text to find")), required("text"))));

        arr.put(tool("wait_for_text",
                "Waits until some text appears on screen, returning as soon as it does. "
                        + "More reliable than a fixed wait when loading or submitting.",
                schema(props(
                        prop("text", "string", "Text to wait for"),
                        prop("timeout", "integer", "Timeout in seconds, default 10, max 60")),
                        required("text"))));

        arr.put(tool("self_test",
                "Device capability self-check. Returns a matrix covering accessibility, screen, "
                        + "windows, events and screenshot support.",
                schema(null, null)));

        arr.put(tool("open_app",
                "Opens an app by package name or Chinese label. Verifies it really reached the "
                        + "foreground and tells you when the system blocked it.",
                schema(props(
                        prop("target", "string", "Package name or app label"),
                        prop("allow_stale", "boolean", P_STALE)), required("target"))));

        arr.put(tool("list_apps", "Lists installed apps as label -> package name.", schema(null, null)));

        arr.put(tool("open_url",
                "Opens a URL in the system browser. Read the page with screen_snapshot afterwards.",
                schema(props(
                        prop("url", "string", "Full URL"),
                        prop("allow_stale", "boolean", P_STALE)), required("url"))));

        arr.put(tool("current_app", "Shows which app is in the foreground and the current rev.",
                schema(null, null)));

        arr.put(tool("screen_info", "Shows screen size, current rev and last snapshot rev. For debugging.",
                schema(null, null)));

        arr.put(tool("wait", "Waits the given milliseconds, max 10000.",
                schema(props(prop("ms", "integer", "Milliseconds to wait")), required("ms"))));

        arr.put(tool("download",
                "Downloads a file to /storage/emulated/0/Download/mcp-downloads/ .",
                schema(props(
                        prop("url", "string", "File URL"),
                        prop("filename", "string", "Saved name; inferred when omitted")), required("url"))));

        arr.put(tool("list_downloads", "Lists files in the mcp-downloads directory.", schema(null, null)));
        arr.put(tool("server_status",
                "Shows server state, addresses, accessibility and interval.", schema(null, null)));

        JSONObject r = new JSONObject();
        r.put("tools", arr);
        return r;
    }

    private static JSONObject toolsCall(Context ctx, JSONObject req) throws Exception {
        JSONObject params = req.optJSONObject("params");
        if (params == null) {
            return result("ERROR: missing params", true);
        }
        String name = params.optString("name", "");
        JSONObject a = params.optJSONObject("arguments");
        if (a == null) {
            a = new JSONObject();
        }
        Log.add("工具 " + name);
        try {
            return call(ctx, name, a);
        } catch (Throwable t) {
            Log.add("工具异常 " + name + ": " + t);
            return result("ERROR: " + t, true);
        }
    }

    private static void applyRev(JSONObject a) {
        long er = a.optLong("expect_rev", -1L);
        if (er >= 0) {
            Ui.lastRev = er;
        }
    }

    private static boolean stale(JSONObject a) {
        return a.optBoolean("allow_stale", false);
    }

    private static final java.util.Set<String> READ_ONLY =
            new java.util.HashSet<String>(java.util.Arrays.asList(
                    "screen_snapshot", "ocr", "find_text", "current_app",
                    "server_status", "list_apps", "screen_info",
                    "list_downloads", "self_test", "wait",
                    "vd_info"));

    private static String pausedBlockMsg(String tool) {
        StringBuilder b = new StringBuilder();
        b.append("paused\neffect: none\naction: ").append(tool).append('\n');
        b.append("reason: the user pressed the emergency stop, all actions are paused\n");
        b.append("hint: stop immediately. do not retry, do not switch tools to work around ")
                .append("it. wait until the user resumes on the phone.\n");
        b.append("still_available: read-only tools still work.\n");
        b.append("resume: ask the user to tap the floating ball and continue once resumed.\n");
        return b.toString();
    }

    private static JSONObject call(Context ctx, String name, JSONObject a) throws Exception {
        if (!READ_ONLY.contains(name) && Config.isPaused()) {
            return result(pausedBlockMsg(name), true);
        }

        if (Config.isVdMode()) {
            return VdTools.call(ctx, name, a);
        }

        if ("screen_snapshot".equals(name)) {
            return result(Ui.snapshot(a.optBoolean("include_offscreen", false)), false);
        }

        if ("ocr".equals(name)) {
            java.util.List<Ocr.Line> lines = new java.util.ArrayList<Ocr.Line>();
            String[] note = new String[]{null};
            String[] img = new String[]{null};
            boolean withImage = Config.isOcrImageOn();
            boolean okRun = Ui.ocrRun(a.optInt("quality", 85), withImage,
                    a.optBoolean("grid", false), lines, note, img);
            if (!okRun) {
                return result("ERROR: " + (note[0] == null ? "OCR failed" : note[0])
                        + ". Requires Android 11+ with accessibility granted.", true);
            }
            StringBuilder cap = new StringBuilder();
            android.graphics.Rect r = Ui.screen();
            cap.append("OCR found ").append(lines.size()).append(" text lines.\n");
            if (withImage) {
                cap.append("Each line has a green box; the small green tag to its left ")
                        .append("holds the index.\n");
            } else {
                cap.append("No image attached: the server setting IMAGE is off. ")
                        .append("Use the list below; every centre is still exact.\n");
            }
            cap.append("Coordinates are based on ").append(r.width()).append('x').append(r.height());
            cap.append(", identical to the screenshot.\n\n");

            String kw = a.optString("find", "").trim();
            if (kw.length() > 0) {
                int idx = Ocr.find(lines, kw);
                if (idx >= 0) {
                    Ocr.Line ln = lines.get(idx);
                    cap.append("Found \"").append(kw).append("\": T").append(idx + 1)
                            .append(" \"").append(ln.text).append("\"\n");
                    cap.append("Tap it with: click(x=").append(ln.cx())
                            .append(", y=").append(ln.cy()).append(")\n\n");
                } else {
                    cap.append("No line contains \"").append(kw).append("\".\n");
                    cap.append("The text may be truncated, covered, or on another page. ")
                            .append("See the full list below.\n\n");
                }
            }

            if (lines.isEmpty()) {
                cap.append("No text recognised. The screen may be icon-only or blank.\n");
            } else {
                cap.append(Ocr.describe(lines, 60));
            }
            cap.append("\nPick the index from the list and tap its centre.\n");
            cap.append("Do not re-estimate; the centre is already exact.\n");
            cap.append("After tapping, read effect: when unchanged run ocr again instead of ")
                    .append("repeating the same point.\n");
            if (note[0] != null) {
                cap.append("\nWARN: ").append(note[0]);
            }
            if (withImage && img[0] != null) {
                return image(img[0], "image/jpeg", cap.toString());
            }
            return result(cap.toString(), false);
        }

        if ("click".equals(name)) {
            applyRev(a);
            int ref = a.optInt("ref", -1);
            boolean risk = a.optBoolean("confirm_risk", false);
            boolean force = a.optBoolean("force", false);
            if (ref > 0) {
                return result(Ui.click(ref, stale(a), risk, force), false);
            }
            int x = a.optInt("x", -1);
            int y = a.optInt("y", -1);
            if (x >= 0 && y >= 0) {
                return result(Ui.clickAt(x, y, stale(a)), false);
            }
            return result("ERROR: need ref or x/y", true);
        }

        if ("long_click".equals(name)) {
            applyRev(a);
            int ref = a.optInt("ref", -1);
            boolean risk = a.optBoolean("confirm_risk", false);
            if (ref > 0) {
                return result(Ui.longClick(ref, stale(a), risk,
                        a.optBoolean("force", false)), false);
            }
            int x = a.optInt("x", -1);
            int y = a.optInt("y", -1);
            if (x >= 0 && y >= 0) {
                return result(Ui.longClickAt(x, y, stale(a)), false);
            }
            return result("ERROR: need ref or x/y", true);
        }

        if ("set_text".equals(name)) {
            applyRev(a);
            int ref = a.optInt("ref", -1);
            if (ref <= 0) {
                return result("ERROR: need ref", true);
            }
            return result(Ui.setText(ref, a.optString("text", ""),
                    a.optBoolean("submit", false), stale(a)), false);
        }

        if ("swipe".equals(name)) {
            applyRev(a);
            return result(Ui.swipe(a.optInt("x1", 0), a.optInt("y1", 0),
                    a.optInt("x2", 0), a.optInt("y2", 0),
                    a.optInt("duration", 800), stale(a)), false);
        }

        if ("drag".equals(name)) {
            applyRev(a);
            return result(Ui.drag(a.optInt("x1", 0), a.optInt("y1", 0),
                    a.optInt("x2", 0), a.optInt("y2", 0),
                    a.optInt("hold_ms", 500), a.optInt("move_ms", 600),
                    stale(a)), false);
        }

        if ("press_key".equals(name)) {
            applyRev(a);
            return result(Ui.pressKey(a.optString("key", "back"), stale(a)), false);
        }

        if ("click_text".equals(name)) {
            return result(Ui.clickText(a.optString("text", ""),
                    a.optBoolean("confirm_risk", false)), false);
        }

        if ("find_text".equals(name)) {
            return result(Ui.findText(a.optString("text", "")), false);
        }

        if ("wait_for_text".equals(name)) {
            return result(Ui.waitForText(a.optString("text", ""),
                    a.optInt("timeout", 10)), false);
        }

        if ("self_test".equals(name)) {
            return result(Ui.selfTest(), false);
        }

        if ("open_app".equals(name)) {
            return result(Ui.openApp(a.optString("target", ""), stale(a)), false);
        }

        if ("list_apps".equals(name)) {
            return result(Ui.listApps(), false);
        }

        if ("open_url".equals(name)) {
            return result(Ui.openUrl(a.optString("url", ""), stale(a)), false);
        }

        if ("current_app".equals(name)) {
            return result(Ui.currentApp(), false);
        }

        if ("screen_info".equals(name)) {
            return result(Ui.screenInfo(), false);
        }

        if ("wait".equals(name)) {
            int ms = a.optInt("ms", 500);
            if (ms < 0) {
                ms = 0;
            }
            if (ms > 10000) {
                ms = 10000;
            }
            Ui.sleep(ms);
            return result("ok\neffect: none\nwaited_ms: " + ms
                    + "\nrev: " + A11ySvc.rev, false);
        }

        if ("download".equals(name)) {
            return result(Down.download(ctx, a.optString("url", ""),
                    a.optString("filename", "")), false);
        }

        if ("list_downloads".equals(name)) {
            return result(Down.list(ctx), false);
        }

        if ("set_status".equals(name)) {
            return result(Ui.setStatus(a.optString("text", ""),
                    a.optString("step", ""), a.optBoolean("clear", false)), false);
        }

        if ("server_status".equals(name)) {
            StringBuilder b = new StringBuilder();
            b.append("name: ").append(Config.getName()).append('\n');
            b.append("version: ").append(VERSION).append('\n');
            b.append("guard: ").append(Guard.ENABLED ? "on" : "off").append('\n');
            b.append("paused: ").append(Config.isPaused() ? "yes" : "no").append('\n');
            b.append("estop: ");
            if (Config.isBallOn() && StopBar.isShown()) {
                b.append("floating_ball (running)\n");
            } else if (Config.isBallOn()) {
                b.append("floating_ball (permission_ok, not_shown)\n");
            } else {
                b.append("floating_ball (NOT_ENABLED - writes are dangerous)\n");
            }
            b.append("running: ").append(HttpSrv.runningCount()).append(" ports\n");
            b.append("local: ").append(Net.urlLocal()).append('\n');
            b.append("lan: ").append(Net.urlLan()).append('\n');
            b.append("a11y: ").append(Ui.ready() ? "connected" : "disconnected").append('\n');
            b.append("interval: ").append(Config.getInterval()).append("s");
            b.append(Config.getInterval() == 0 ? " (unlimited)" : "").append('\n');
            b.append("rev: ").append(A11ySvc.rev).append('\n');
            b.append("screen: ").append(Ui.screenInfo()).append('\n');
            b.append("download_dir: ").append(Down.FULL_PATH).append('\n');
            b.append("ocr: bundled mlkit text-recognition-chinese 16.0.1, fully offline");
            return result(b.toString(), false);
        }

        return result("ERROR: unknown tool " + name, true);
    }

    private static JSONObject result(String text, boolean error) throws Exception {
        JSONObject o = new JSONObject();
        JSONArray arr = new JSONArray();
        JSONObject c = new JSONObject();
        c.put("type", "text");
        c.put("text", text + REMINDER);
        arr.put(c);
        o.put("content", arr);
        o.put("isError", error);
        return o;
    }

    private static JSONObject image(String base64, String mime, String extra) throws Exception {
        JSONObject o = new JSONObject();
        JSONArray arr = new JSONArray();
        JSONObject c = new JSONObject();
        c.put("type", "image");
        c.put("data", base64);
        c.put("mimeType", mime);
        arr.put(c);
        JSONObject t = new JSONObject();
        t.put("type", "text");
        String s = "Screenshot attached. Image coordinates can be passed straight to click as x/y.";
        if (extra != null && extra.length() > 0) {
            s = s + "\n\n" + extra;
        }
        t.put("text", s + REMINDER);
        arr.put(t);
        o.put("content", arr);
        o.put("isError", false);
        return o;
    }

    private static String wrap(Object id, JSONObject result) throws Exception {
        JSONObject o = new JSONObject();
        o.put("jsonrpc", "2.0");
        o.put("id", id);
        o.put("result", result);
        return o.toString();
    }

    private static String error(Object id, int code, String message) {
        try {
            JSONObject o = new JSONObject();
            o.put("jsonrpc", "2.0");
            o.put("id", id == null ? JSONObject.NULL : id);
            JSONObject e = new JSONObject();
            e.put("code", code);
            e.put("message", message);
            o.put("error", e);
            return o.toString();
        } catch (Throwable t) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"internal\"}}";
        }
    }

    private static JSONObject tool(String name, String desc, JSONObject inputSchema) throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("description", desc);
        o.put("inputSchema", inputSchema);
        return o;
    }

    private static JSONObject schema(JSONObject properties, JSONArray required) throws Exception {
        JSONObject o = new JSONObject();
        o.put("type", "object");
        o.put("properties", properties == null ? new JSONObject() : properties);
        if (required != null && required.length() > 0) {
            o.put("required", required);
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

    private static JSONObject props(JSONObject p1) throws Exception {
        JSONObject o = new JSONObject();
        putProp(o, p1);
        return o;
    }

    private static JSONObject props(JSONObject p1, JSONObject p2) throws Exception {
        JSONObject o = new JSONObject();
        putProp(o, p1);
        putProp(o, p2);
        return o;
    }

    private static JSONObject props(JSONObject p1, JSONObject p2, JSONObject p3) throws Exception {
        JSONObject o = new JSONObject();
        putProp(o, p1);
        putProp(o, p2);
        putProp(o, p3);
        return o;
    }

    private static JSONObject props(JSONObject p1, JSONObject p2, JSONObject p3,
                                    JSONObject p4) throws Exception {
        JSONObject o = new JSONObject();
        putProp(o, p1);
        putProp(o, p2);
        putProp(o, p3);
        putProp(o, p4);
        return o;
    }

    private static JSONObject props(JSONObject p1, JSONObject p2, JSONObject p3,
                                    JSONObject p4, JSONObject p5) throws Exception {
        JSONObject o = new JSONObject();
        putProp(o, p1);
        putProp(o, p2);
        putProp(o, p3);
        putProp(o, p4);
        putProp(o, p5);
        return o;
    }

    private static JSONObject props(JSONObject p1, JSONObject p2, JSONObject p3,
                                    JSONObject p4, JSONObject p5, JSONObject p6) throws Exception {
        JSONObject o = new JSONObject();
        putProp(o, p1);
        putProp(o, p2);
        putProp(o, p3);
        putProp(o, p4);
        putProp(o, p5);
        putProp(o, p6);
        return o;
    }

    private static JSONObject props(JSONObject p1, JSONObject p2, JSONObject p3,
                                    JSONObject p4, JSONObject p5, JSONObject p6,
                                    JSONObject p7) throws Exception {
        JSONObject o = new JSONObject();
        putProp(o, p1);
        putProp(o, p2);
        putProp(o, p3);
        putProp(o, p4);
        putProp(o, p5);
        putProp(o, p6);
        putProp(o, p7);
        return o;
    }

    private static JSONObject props(JSONObject p1, JSONObject p2, JSONObject p3,
                                    JSONObject p4, JSONObject p5, JSONObject p6,
                                    JSONObject p7, JSONObject p8) throws Exception {
        JSONObject o = new JSONObject();
        putProp(o, p1);
        putProp(o, p2);
        putProp(o, p3);
        putProp(o, p4);
        putProp(o, p5);
        putProp(o, p6);
        putProp(o, p7);
        putProp(o, p8);
        return o;
    }

    private static void putProp(JSONObject target, JSONObject p) throws Exception {
        if (p == null) {
            return;
        }
        String n = p.optString("__name", "");
        if (n.length() == 0) {
            return;
        }
        p.remove("__name");
        target.put(n, p);
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

    private static JSONArray required(String a, String b, String c) throws Exception {
        JSONArray r = new JSONArray();
        r.put(a);
        r.put(b);
        r.put(c);
        return r;
    }

    private static JSONArray required(String a, String b, String c, String d) throws Exception {
        JSONArray r = new JSONArray();
        r.put(a);
        r.put(b);
        r.put(c);
        r.put(d);
        return r;
    }
}
