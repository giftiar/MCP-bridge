# MCP Bridge

<p align="center">
  <strong>Turn an Android phone into a device an AI can operate.</strong><br/>
  Any MCP-capable assistant can read the screen, tap buttons, type text and scroll lists.
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPLv3-blue" alt="License"></a>
  <img src="https://img.shields.io/badge/Android-11%2B-green" alt="Android 11+">
  <img src="https://img.shields.io/badge/MCP-2024--11--05-orange" alt="MCP">
</p>

<p align="center">
  <a href="README.md">中文文档</a>
</p>

---

## 🤔 What is this

A local MCP server that turns Android accessibility, virtual displays and input
injection into tools an AI can call directly.

**Two modes, fully independent:**

*   **Screen mode** — drives the current screen through the accessibility service.
    No root, no Shizuku.
*   **Virtual display mode** — runs apps on an isolated virtual display. Your own
    screen stays completely untouched.

---

## 🏆 How it differs

*   **🚀 Real background operation** — in virtual display mode the AI works on a
    fully isolated display while you keep using the main screen
*   **👁️ Visible live** — a floating window previews the display; drag it, pinch
    it, collapse it into a ball
*   **👆 Direct touch** — one finger on the preview injects into the display in real time
*   **🧠 Mode isolation** — each mode has its own tool set and prompt, so the model
    never sees the other mode
*   **📊 Actions report back** — every action returns what changed on screen: what
    appeared, what disappeared and where
*   **🛡️ Guard rails** — danger-word blocking in both modes, plus node-failure
    counting, screen-change checks and an emergency stop
*   **🔌 Offline recognition** — OCR runs entirely on device, screenshots never leave the phone
*   **🎨 Material 3** — six muted palettes, wallpaper colours from Android 12

---

## 📱 Mode comparison

| Capability | Screen mode | Virtual display mode |
|---|---|---|
| Read UI | Node tree + OCR | OCR only |
| Tap / long press / swipe | ✅ | ✅ |
| Long drag | ✅ | ✅ |
| Live finger tracking | ❌ | ✅ |
| Text input | ✅ `set_text` | ✅ `type_text` |
| Read focused field | ❌ | ✅ `read_focus_text` |
| Keys | ✅ | ✅ `press_key` |
| Change summary after action | ✅ | ✅ |
| Floating preview | ❌ | ✅ |
| Status card | ✅ | ❌ |
| Danger-word guard | ✅ incl. permission/install | ✅ irreversible only |
| Tool count | 22 | 13 |
| Accessibility | **required** | not needed |
| Shizuku | not needed | **required** |

---

## 💻 Requirements

| Item | Requirement |
|---|---|
| Android | 11 or newer (accessibility screenshot needs API 30) |
| Overlay permission | **Required in both modes**, for the emergency stop |
| Storage permission | All-files access is needed for downloads |
| Shizuku | Required only in virtual display mode |

### Shizuku deployment

| Deployment | SELinux domain | Display | Input injection |
|---|---|---|---|
| Official Shizuku (ADB start) | `u:r:shell:s0` | ✅ | ✅ native injection |
| Official Shizuku (root start) | `u:r:shell:s0` | ✅ | ✅ native injection |
| SUI / KernelSU module | `u:r:magisk:s0` | ✅ | ⚠️ falls back to the `input` command |

In fallback a tap takes about 71 ms. Everything still works but is slower.
`vd_info` reports the active injection path.

---

## 🚀 Quick start

### 1. Install

```
adb install MCPBridge_1.0.0.apk
```

### 2. First launch

```
Accept the terms → Choose a mode → Setup list → Main screen
```

The mode cannot be skipped. It can be changed later in settings.

### 3. Setup list

**Screen mode:**

| # | Item | Needed |
|---|---|---|
| 1 | Accessibility service | **yes** |
| 2 | Compatibility mode (WeChat and similar) | recommended |
| 3 | Notification permission | recommended |
| 4 | Battery optimisation whitelist | recommended |
| 5 | Background pop-up | recommended |
| 6 | Emergency stop ball | **yes** |

**Virtual display mode:**

| # | Item | Needed |
|---|---|---|
| 1 | Notification permission | recommended |
| 2 | Battery optimisation whitelist | recommended |
| 3 | Background pop-up | recommended |
| 4 | Emergency stop ball | **yes** |
| — | Shizuku permission | **yes** |

---

## 🔗 Connecting

```
Local  http://127.0.0.1:18800/mcp
LAN    http://<phone-ip>:18801/mcp  (enable in settings)
```

**An empty token disables authentication.** Set a token when LAN access is on.

Use `server_status` to confirm the connection:

```json
{
  "name": "MCP Bridge",
  "version": "1.0.0",
  "guard": "on",
  "paused": "no",
  "estop": "floating_ball (running)"
}
```

Settings → Test local connection sends a real `ping` request and checks the
HTTP status code.

---

## 🖥️ Screen mode

Drives the current screen through the accessibility service. **The AI and your
hands will interfere with each other**, so it suits situations where:

*   you are not using the phone for anything else
*   you need precise UI structure
*   you need text input and keys

### Capabilities

| Capability | Notes |
|---|---|
| Read node tree | Pixel-accurate bounds, text, clickability |
| Tap by ref | `@7` reference, invalidated when the screen changes |
| Tap by coordinate | Centre computed from node bounds, most accurate |
| Text input | `set_text` writes directly |
| Keys | Back, home, recents, notifications and others |
| OCR | Screenshot recognition when the tree is unavailable |

### UI elements

*   **Status card** — top right, shows what the AI is doing
*   **Emergency stop ball** — top right, 52dp, one tap to pause, tap again to
    resume, draggable

The stop ball is the only emergency exit and is always on. The status card can
be turned off in settings.

### 🛡️ Safety

| Mechanism | Behaviour |
|---|---|
| Danger-word guard | Refuses uninstall, pay, delete and similar |
| Node failure counting | An element failing five times in a row is marked unusable |
| Screen change check | Actions based on a stale ref are refused |
| Emergency stop | All write actions are blocked immediately |
| System bar guard | Swiping or dragging the status and navigation bars is refused (it crashes `SystemUI`) |

---

## 📺 Virtual display mode

Runs apps on an isolated virtual display. **Your own screen is completely unaffected.**

### Stale-tap protection

Every tap, long press and swipe re-reads the screen first and confirms that the
content at the target point has not changed. If the list scrolled, a dialog
appeared or the app is still loading, the action is refused and the current text
with centre coordinates is returned, so the AI can aim again without a second
recognition pass.

This addresses taps landing on outdated content caused by latency, loading or
slow recognition.

### How it works

```
Your phone (main screen)
  │
  └─ MCP Bridge app (floating preview, control panel)
        │ Shizuku bind
        ▼
     Shizuku user service (shell identity)
        │ createVirtualDisplay
        ▼
     Virtual display (private, invisible on the physical screen)
        └─ Runs any app
```

### 🪟 Floating window

| State | Appearance | Interaction |
|---|---|---|
| Collapsed | 38dp green ball | Tap to expand, drag to move |
| Expanded | Aspect-correct live preview | One finger maps to the display, two fingers pinch, ▾ collapses |

Position is saved and restored on the next launch.

### Capabilities

| Capability | Notes |
|---|---|
| Display creation | Follows the real main screen size, normalised to natural orientation |
| Capture | Full resolution from `ImageReader`, no process fork |
| OCR | Offline Chinese recognition, returns precise centres |
| Gestures | Tap, long press, swipe, long drag |
| Live touch | One finger on the preview injects immediately, throttled to about 60 fps |
| Keys | `press_key` sends back, home, recents, enter, backspace, menu and volume |
| Launch app | `am start --display`, landscape apps rotate first |
| Rotation | Chosen at creation, toggled from the preview at any time |
| Text input | Bundled IME using `commitText`, Chinese supported |
| Read field | `read_focus_text` returns current content and hint |

### 🧹 Cleanup

```
1. Cold sweep    — clear previous instances before creating a display
2. Binder death  — clean up immediately when the client process exits
3. Polling       — periodic check that the client is still alive
```

Cleanup is strictly limited to `com.mcpmate.bridge:vd`; **no third-party virtual
display is ever touched.** Settings → Virtual display management clears them manually.

---

## 🧰 Tools

### Shared

| Tool | Arguments | Notes |
|---|---|---|
| `list_apps` | — | Lists launchable apps |
| `wait` | `ms` | Sleeps, maximum 10000 |

### Screen mode only

| Tool | Arguments | Notes |
|---|---|---|
| `screen_snapshot` | `include_offscreen` | Node tree, element list and rev |
| `click` | `ref` or `x`,`y` | Tap |
| `long_click` | `ref` or `x`,`y` | Long press |
| `set_text` | `ref`, `text`, `submit` | Write into a field |
| `swipe` | `x1`,`y1`,`x2`,`y2`,`duration` | Swipe |
| `drag` | `x1`,`y1`,`x2`,`y2`,`hold`,`move` | Long drag |
| `press_key` | `key` | back / home / recents / notifications / settings |
| `click_text` | `text` | Tap by visible text |
| `find_text` | `text` | Locate text on screen |
| `wait_for_text` | `text`, `timeout` | Wait for text to appear |
| `download` | `url`, `filename` | Download to `Download/mcp-downloads/` |
| `list_downloads` | — | List downloaded files |
| `ocr` | `find`, `grid`, `quality` | Screenshot recognition |
| `set_status` | `text`, `step`, `clear` | Update the status card |
| `self_test` | — | Capability self-test |
| `open_app` | `target` | Launch an app |
| `open_url` | `url` | Open a link in the browser |
| `current_app` | — | Foreground app and rev |
| `screen_info` | — | Screen size and rev |
| `server_status` | — | Server state |

Most action tools accept `expect_rev` and `allow_stale` for screen-change checks.

### Virtual display mode only

| Tool | Arguments | Notes |
|---|---|---|
| `ocr` | `find`, `grid` | Recognise the display, returns text and centres |
| `click` | `x`, `y`, `expect`, `allow_stale`, `confirm_risk` | Tap, target verified first |
| `long_click` | `x`, `y`, `expect`, `allow_stale`, `confirm_risk` | Long press, same check |
| `swipe` | `x1`,`y1`,`x2`,`y2`,`duration`,`allow_stale` | Swipe, start point verified |
| `long_drag` | `x1`,`y1`,`x2`,`y2`,`hold`,`duration`,`allow_stale` | Long drag, start point verified |
| `type_text` | `text`, `submit` | Write into the focused field |
| `read_focus_text` | — | Read the focused field and its hint |
| `press_key` | `key` | back / home / recents / enter / backspace / menu / volume |
| `open_app` | `target` | Launch on the display, rotates by app declaration |
| `vd_info` | — | Display state and size |
| `vd_rotate` | `landscape` | Manual rotation, usually unnecessary |

---

## ⚙️ Settings

### Work mode

Switching changes the tool set and prompt immediately; the MCP client must
reconnect.

| Group | Item | Default | Notes |
|---|---|---|---|
| Basic | Access token | empty | Empty means no authentication |
| Network | Local port | 18800 | 0 disables |
| Network | LAN port | 18801 | 0 disables |
| Network | Allow LAN access | off | Set a token when on |
| Network | Action interval (ms) | 300 | Minimum gap between actions |
| Recognition | Report screen delta after actions | on | Off skips the delta, stale-tap checks still run |
| Display | Status card | on | Screen mode only |
| Theme | Follow wallpaper | on | Android 12+, Material You |
| Theme | Manual theme colour | — | Six palettes: Mist Blue, Pine Green, Nightmare Purple, Warm Clay, Gardenia Pink, Celadon |

Changing a port or the LAN switch restarts the service.

### Diagnostics

*   **Export diagnostic package** — bundles device info, logs, crash record,
    self-test result and display state
*   **Clear crash record** — deletes the local `crash.txt`

---

## ⚠️ Known issues

### IME conflict with the main screen

**Symptom:** while the AI types on the virtual display, the IME in use on the
main screen is temporarily taken over and your typing is interrupted.

**Cause:** Android IMEs are a global singleton with no per-display instance.
`commitText` is the only path that can enter Chinese text, and it requires this
app's IME to become the default.

**Current mitigation:** the previous IME is restored immediately after each
`type_text`, so the conflict window is about 120 ms.

**Still unsolved:**

*   typing on both screens at the same time makes both fail
*   the first `type_text` may fail (`ime_event` shows `type=0`) and needs a retry

### Some apps refuse external input connections

A virtual display has no window focus, so some apps never create an
`InputConnection`. `type_text` and `read_focus_text` are unavailable there.

### Heavy apps may cause the service to be reclaimed

**Symptom:** running a memory-heavy app on the virtual display can cause the
service process to be reclaimed and the display to disappear.

**Current behaviour:** the service restarts through `START_STICKY` and recreates
the display, but the app inside it closes.

**Mitigation:** collapse the floating window, which stops preview frame production.

### MCP protocol scope

*   Only `Content-Length` bodies are parsed; **`Transfer-Encoding: chunked` is unsupported**
*   No session isolation, so concurrent clients share the same screen revision
*   The `GET /mcp` SSE stream sends one event and closes

### CORS and tokens

Responses always carry `Access-Control-Allow-Origin: *`. **With an empty token,
any page on the same network can call the local endpoint.** Always set a token.

### Other

*   Some apps detect accessibility services and refuse to return a node tree;
    enable compatibility mode for those
*   Swiping or dragging the status and navigation bars crashes `SystemUI`; taps are fine
*   MIUI treats swipes under 400 ms as a fling, so the default swipe duration is 800 ms

---

## ❓ Troubleshooting

### The service runs but the AI cannot connect

```
1. Confirm the main screen shows "running" and a listening port
2. Confirm the address is http://127.0.0.1:18800/mcp
3. If a token is set, send Authorization: Bearer <token>
4. Check whether a VPN is intercepting 127.0.0.1
5. Settings → Test local connection verifies the real HTTP response
```

### The virtual display shows "not connected"

```
1. Check that Shizuku is running
2. Check that this app is authorised
3. Settings → Authorise Shizuku
4. If it keeps failing: Settings → Virtual display management → clear this app's display
```

### The AI cannot see the UI

```
Screen mode:
  1. Confirm the accessibility service is enabled
  2. For WeChat and similar, confirm compatibility mode is on
  3. Try ocr instead of screen_snapshot

Virtual display mode:
  1. Only ocr is available, there is no node tree
  2. Confirm an app is running on the display
```

---

## 📦 Package

| Item | Value |
|---|---|
| Size | about 56 MB |
| ABIs | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Minimum OS | Android 11 |

Most of the size is the bundled offline OCR engine and Chinese model, which runs
entirely on device.

---

## 📄 License

Released under the [GNU General Public License v3.0](LICENSE), with a GPLv3
section 7 additional permission allowing distribution linked with Google ML Kit.

The bundled OCR engine is Google ML Kit, distributed under Google's terms of
service and not a free-software licence. The additional permission only resolves
the GPL side of that combination.

Open-source projects referenced for technical approaches are listed, with their
scope, in [CREDITS.md](CREDITS.md).

---

## ⚠️ Disclaimer

This tool acts on a phone in place of the user and can therefore cause mistakes.
**The user accepts all risk.**

The author is not liable for data loss, financial loss or any other consequence.

Do not use it for anything unlawful.
