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

## 🏆 How it differs from ordinary automation tools

*   **🚀 True background operation** — in virtual display mode the model works on a
    fully isolated screen. You keep using your phone at the same time.
*   **👁️ Live preview** — a floating window shows the virtual display in real time.
    Drag it, pinch to resize, collapse it into a small ball.
*   **🧠 Mode isolation** — each mode exposes a different tool set and a different
    prompt. The model never has to reason about the other mode.
*   **🛡️ Safety rails** — danger-word guard, element failure counting, screen change
    detection, emergency stop ball.
*   **🔌 Offline recognition** — OCR runs entirely on device. Screenshots are never
    uploaded.
*   **🎨 Material 3** — six pastel themes, Monet colour extraction on Android 12+.

---

## 📱 Mode comparison

| Capability | Screen mode | Virtual display mode |
|---|---|---|
| Read the screen | node tree + OCR | OCR only |
| Tap / long press / swipe | ✅ | ✅ |
| Long-press drag | ✅ | ✅ |
| Text input | ✅ `set_text` | ✅ `type_text` |
| Key events | ✅ | ❌ no focused window |
| Floating preview | ❌ | ✅ |
| Status card | ✅ | ❌ |
| Tool count | 22 | 11 |
| Accessibility | **required** | not needed |
| Shizuku | not needed | **required** |

---

## 💻 Requirements

| Item | Requirement |
|---|---|
| Android | 11 or later (accessibility screenshots need API 30) |
| Overlay permission | **required in both modes** for the emergency stop ball |
| Storage permission | all-files access, for downloads |
| Shizuku | virtual display mode only |

### Shizuku deployment

Three deployment methods behave differently:

| Method | Process SELinux domain | Create display | Input injection |
|---|---|---|---|
| Official Shizuku (ADB) | `u:r:shell:s0` | ✅ | ✅ native injection |
| Official Shizuku (root) | `u:r:shell:s0` | ✅ | ✅ native injection |
| SUI / KernelSU module | `u:r:magisk:s0` | ✅ | ⚠️ falls back to `input` |

After the fallback each tap takes about 71ms. Everything works, just slower. The
create call reports which path is active:

```
OK id=56 1080x1920@440 inject=on(2)    ← native injection
OK id=56 1080x1920@440 inject=off(2)   ← fallback
```

---

## 🚀 Getting started

### 1. Install

```
adb install MCPBridge_0.1.0.apk
```

### 2. First launch

```
Accept the agreement → choose a mode → onboarding → main screen
```

The mode choice cannot be skipped. It can be changed later in settings, which
invalidates the onboarding marker and requires going through it again.

### 3. Onboarding items

**Screen mode:**

| # | Item | Required |
|---|---|---|
| 1 | Accessibility service | **yes** |
| 2 | Compatibility service | recommended |
| 3 | Notification permission | recommended |
| 4 | Battery optimisation whitelist | recommended |
| 5 | Background popup permission | recommended |
| 6 | Emergency stop ball | **yes** |

**Virtual display mode:**

| # | Item | Required |
|---|---|---|
| 1 | Notification permission | recommended |
| 2 | Battery optimisation whitelist | recommended |
| 3 | Background popup permission | recommended |
| 4 | Emergency stop ball | **yes** |
| — | Shizuku authorisation | **yes** |

---

## 🔗 Connecting a client

```
Local     http://127.0.0.1:18800/mcp
LAN       http://<phone-ip>:18801/mcp   (enable in settings)
```

**An empty token means no authentication.** Set one when enabling LAN access.

Any MCP-capable client works. Verify with `server_status`:

```json
{
  "name": "MCP Bridge",
  "version": "0.1.0",
  "guard": "on",
  "paused": "no",
  "estop": "floating_ball (running)"
}
```

---

## 🖥️ Screen mode

Drives the current screen through the accessibility service. **The model and your
hands interfere with each other.** Use it when:

*   you are not using the phone for anything else
*   you need precise access to the view hierarchy
*   you need text input and key events

### Capabilities

| Capability | Detail |
|---|---|
| Read the node tree | pixel-accurate bounds, text, clickability |
| Tap by ref | `@7` style references, invalidated when the screen changes |
| Tap by coordinate | centre computed from real bounds, most accurate |
| Text input | `set_text` writes directly into the field |
| Key events | back, home, recents, notifications and others |
| OCR | screenshot recognition when the tree is unreadable |

### On-screen elements

*   **Status card** — top left, shows what the model is doing
*   **Emergency stop ball** — top right, 52dp. Tap to pause, tap again to resume,
    drag to move

### 🛡️ Safety rails

| Mechanism | Behaviour |
|---|---|
| Danger-word guard | refuses uninstall, payment, deletion and similar actions |
| Element failure count | an element failing 5 times in a row is marked unusable |
| Screen change detection | actions on stale refs are refused |
| Emergency stop | all write operations are blocked immediately |
| System bar guard | swiping the status or navigation bar is refused |

---

## 📺 Virtual display mode

Runs apps on an isolated virtual display. **Your own screen is never touched.**

### How it works

```
Your phone (main display)
  │
  └─ MCP Bridge app (floating preview, control panel)
        │ Shizuku binding
        ▼
     Shizuku user service process (shell identity)
        │ createVirtualDisplay
        ▼
     Virtual display 1080×1920 (private, invisible on the physical screen)
        └─ runs any app
```

### 🪟 Floating preview

| State | Appearance | Interaction |
|---|---|---|
| Collapsed | 38dp green ball | tap to expand, drag to move |
| Expanded | video-ratio window, live frames | drag, pinch to resize, tap ▾ to collapse |

The position is saved and restored on the next launch.

### Capabilities

| Capability | Detail |
|---|---|
| Create display | adaptive size, short edge of the phone, 9:16 ratio |
| Screenshot | `ImageReader` at full resolution, no process forking |
| OCR | offline Chinese recognition with exact centre coordinates |
| Gestures | tap, long press, swipe, long-press drag |
| Launch apps | `am start --display` |
| Rotation | follows the foreground app, or call `vd_rotate` |
| Text input | bundled input method, `commitText`, supports Chinese |

### 🧹 Cleanup

Three layers protect against leftover displays and orphan processes:

```
1. Startup sweep  — clean before creating, on every cold start
2. Binder death   — cleaned as soon as the client process exits
3. Polling        — checks client liveness every 3 seconds
```

Cleanup is strictly limited to `com.mcpmate.bridge:vd`. **No third-party virtual
display is ever touched.**

The settings page can list and clean manually:

```
This app's displays: 1
id=56  mcp-vd-1761...  state=ON  rootTasks=1

This app's service processes: 1
shell  12345  ...  com.mcpmate.bridge:vd

Other apps' displays (read only): 0
```

---

## 🧰 Tool reference

### Shared

| Tool | Arguments | Detail |
|---|---|---|
| `list_apps` | — | launchable apps as label to package name |
| `wait` | `ms` | pause, maximum 10000ms |
| `server_status` | — | server state |

### Screen mode only

| Tool | Arguments | Detail |
|---|---|---|
| `screen_snapshot` | `include_offscreen` | node tree with element list and rev |
| `click` | `ref` or `x`,`y` | tap |
| `long_click` | `ref` or `x`,`y` | long press |
| `set_text` | `ref`, `text`, `submit` | write into a field |
| `swipe` | `x1`,`y1`,`x2`,`y2`,`duration` | swipe |
| `drag` | `x1`,`y1`,`x2`,`y2`,`hold`,`move` | long-press drag |
| `press_key` | `key` | back, home, recents, notifications, settings |
| `click_text` | `text` | tap by visible text |
| `find_text` | `text` | locate text without tapping |
| `wait_for_text` | `text`, `timeout` | wait until text appears |
| `download` | `url`, `filename` | save to `Download/mcp-downloads/` |
| `list_downloads` | — | list downloaded files |
| `ocr` | `find`, `grid`, `quality` | screenshot recognition |
| `set_status` | `text`, `step`, `clear` | update the status card |
| `self_test` | — | capability self-check |
| `open_app` | `target` | launch an app |
| `open_url` | `url` | open a link in the browser |
| `current_app` | — | foreground app and rev |
| `screen_info` | — | screen size and revision info |

### Virtual display mode only

| Tool | Arguments | Detail |
|---|---|---|
| `ocr` | `find`, `grid` | read the display, text with centre coordinates |
| `click` | `x`, `y` | tap |
| `long_click` | `x`, `y` | long press |
| `swipe` | `x1`,`y1`,`x2`,`y2`,`duration` | swipe |
| `long_drag` | `x1`,`y1`,`x2`,`y2`,`hold`,`duration` | long-press drag |
| `type_text` | `text`, `submit` | write into the focused field |
| `open_app` | `target` | launch on the virtual display |
| `vd_info` | — | display state and size |
| `vd_rotate` | `landscape` | switch orientation |

---

## ⚙️ Settings

### Operating mode

Changing it restarts the service. The MCP client must reconnect because the tool
set and prompt change.

| Group | Item | Default | Detail |
|---|---|---|---|
| Basic | Access token | empty | empty means no authentication |
| Network | Local port | 18800 | 0 disables |
| Network | LAN port | 18801 | 0 disables |
| Network | Allow LAN | off | set a token when enabling |
| Network | Operation interval (ms) | 300 | minimum gap between actions |
| Recognition | Attach image after OCR | on | off returns text only, for models without vision |
| Theme | Follow wallpaper | on | Monet colours on Android 12+ |
| Theme | Pick a colour | — | six pastel themes |

---

## ⚠️ Known issues

### Input method conflicts with the main screen

**Symptom:** while the model types on the virtual display, the keyboard you are
using on the main screen is switched away and your typing is interrupted.

**Cause:** Android has a single global input method. There is no per-display IME.
`commitText` is currently the only path that can type Chinese, and it requires
this app's input method to become the default.

**Current mitigation:** the original input method is restored immediately after
each `type_text`. The conflict window is about 120ms.

**Still unresolved:**

*   typing on the main screen at the same time makes both sides fail
*   the first `type_text` may fail (`ime_event` reports `type=0`) and needs a retry

**Advice:** avoid typing on the main screen while the model is typing.

**Why it cannot be fixed:** key event injection needs a focused window and the
virtual display has none; `ACTION_SET_TEXT` needs the accessibility node tree and
the virtual display is not in it; clipboard commands are unavailable on some
systems. There is no fourth option.

### No key events on the virtual display

The virtual display has no focused window, therefore:

*   `KEYCODE_BACK` and `HOME` do nothing
*   the system input method cannot bind to it

**Workaround:** use `open_app` to switch apps instead of pressing back.

### No danger-word guard on the virtual display

Screen mode refuses uninstall, payment and similar actions. **Virtual display mode
has no such protection.**

The reason is that the virtual display only exposes OCR text, which cannot
identify a tap target as precisely as a node tree can.

**Advice:** be careful when running sensitive apps on the virtual display.

### Heavy apps may cause the service to be reclaimed

**Symptom:** running a memory-hungry app on the virtual display can cause the
service process to be reclaimed, and the display disappears.

**Cause:** large app memory use combined with virtual display buffers and floating
preview frame cache may trigger low-memory reclaim. The service is declared as
`dataSync` and does not have high priority.

**Current behaviour:** the service restarts via `START_STICKY` and recreates the
display, but the app inside it is closed.

**Mitigation:** collapse the floating preview, which stops frame polling.

### Other

*   some apps detect accessibility services and refuse to expose their tree; enable
    the compatibility service for those
*   swiping the status or navigation bar is refused, because it crashes `SystemUI`;
    taps are unaffected
*   MIUI treats swipes shorter than 400ms as flings, so the default swipe duration
    is 800ms

---

## ❓ FAQ

### The service runs but the client cannot connect

```
1. the main screen must show "running" and a listening port
2. the address is http://127.0.0.1:18800/mcp
3. with a token set, send Authorization: Bearer <token>
4. check for a VPN that may intercept 127.0.0.1
```

### The virtual display shows "not connected"

```
1. check that Shizuku is running
2. check that Shizuku has granted permission to this app
3. settings → authorise Shizuku
4. if it keeps failing: settings → virtual display management → clean up
```

### The model reports paused

The emergency stop ball was pressed. Tap it once to resume, or clear the pause in
settings.

### The model sees no interface

```
Screen mode:
  1. confirm the accessibility service is enabled
  2. for apps such as WeChat, confirm the compatibility service is enabled
  3. try ocr instead of screen_snapshot

Virtual display mode:
  1. ocr is the only way to read it, there is no node tree
  2. confirm an app is actually running on the display
```

### Cleaning up leftovers manually

```bash
# inspect
ps -A | grep mcpmate
dumpsys display | grep -o 'mDisplayId=[0-9]*' | sort -u

# clean, requires root
for pid in $(ps -A -o PID,ARGS | grep 'mcpmate.bridge:vd' | grep -v grep | awk '{print $1}'); do kill -9 $pid; done
```

---

## 📦 Package

| Item | Value |
|---|---|
| File name | `MCPBridge_0.1.0.apk` |
| Size | about 56MB |
| Architectures | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Minimum Android | 11 |

Most of the size is the bundled offline OCR engine and the Chinese model, which
run entirely on device with no network access.

---

## ✍️ How this was built

**Every line of code in this project was written by DeepSeek.**

Product design, technical decisions, debugging and testing were all done by
giftiar.

Technical direction was informed by [Ruto-GLM](https://github.com/iamr0s/Ruto-GLM).

---

## 📄 Licence

This project is licensed under the
[GNU General Public License v3.0](LICENSE), with an additional permission
under section 7 that allows conveying this program linked with Google ML Kit.

The bundled offline OCR engine comes from Google ML Kit, which is licensed
under Google's own terms rather than a free software licence. The additional
permission only resolves the GPL side of that combination. It does not change
Google's terms for the ML Kit components themselves.

---

## ⚠️ Disclaimer

This tool can operate a phone on the user's behalf, so mistakes are possible.
**You use it entirely at your own risk.**

The author is not liable for any data loss, financial loss or other consequences.

Do not use it for anything unlawful.
