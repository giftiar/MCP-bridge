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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;

public class MainActivity extends Activity {

    private interface Act {
        void run();
    }

    private MaterialSwitch swEnable;
    private TextView tvStatus;
    private TextView tvUrls;
    private TextView tvLog;
    private TextView tvCrash;

    private boolean loading = true;
    private boolean uiReady = false;
    private boolean pendingCheck = false;
    private androidx.appcompat.app.AlertDialog orientDialog;

    @Override
    protected void onCreate(Bundle b) {
        Theme2.apply(this);
        super.onCreate(b);
        try {
            Config.init(this);
        } catch (Throwable t) {
            Log.add("配置初始化失败: " + t);
        }
        try {
            if (!Config.isAgreed()) {
                showAgreement();
                return;
            }
            if (!Config.isModePicked()) {
                chooseMode();
                return;
            }
            if (!Setup.done(this)) {
                goOnboard();
                return;
            }
            initUi();
            enforcePermissions();
        } catch (Throwable t) {
            Log.add("启动异常: " + t);
            toast("启动异常: " + t);
        }
    }

    private void chooseMode() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("选择工作模式")
                .setMessage("普通模式：用无障碍操作当前屏幕。\n\n"
                        + "虚拟屏模式：在独立虚拟屏上运行，需要 Shizuku，"
                        + "不占用你的屏幕，也不需要无障碍。")
                .setCancelable(false)
                .setPositiveButton("虚拟屏模式",
                        new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) {
                                Config.setVdMode(true);
                                afterMode();
                            }
                        })
                .setNegativeButton("普通模式",
                        new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) {
                                Config.setVdMode(false);
                                afterMode();
                            }
                        })
                .setNeutralButton("退出",
                        new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) {
                                finishAffinity();
                            }
                        })
                .show();
    }

    private void afterMode() {
        Setup.reset(this);
        goOnboard();
    }

    private void goOnboard() {
        try {
            startActivity(new Intent(MainActivity.this, OnboardActivity.class));
        } catch (Throwable t) {
            Log.add("无法打开引导页: " + t);
        }
        finish();
    }

    private void enforcePermissions() {
        StopBar.setSmall(Config.isVdMode());
        if (!BridgeService.Settings0.canOverlay(this)) {
            showOverlayGate();
            return;
        }
        if (Config.isVdMode()) {
            InfoBar.hide();
            if (!VdMgr.shizukuAlive()) {
                Log.add("Shizuku 未运行");
            } else if (!VdMgr.hasPerm()) {
                VdMgr.requestPerm();
            }
            return;
        }
        if (Config.isInfoOn()) {
            InfoBar.show(this);
        }
    }

    private void askOrient() {
        try {
            int[] s = VdMgr.autoSize(this);
            float d = getResources().getDisplayMetrics().density;
            int gap = Math.round(10 * d);

            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            int padH = Math.round(22 * d);
            box.setPadding(padH, Math.round(4 * d), padH, 0);

            TextView hint = new TextView(this);
            hint.setText("横屏应用和游戏选横屏，避免上下黑边。"
                    + "建屏后也可以点小窗左上角的旋转按钮随时切换。");
            hint.setTextSize(13f);
            hint.setTextColor(0xFF9E9E9E);
            hint.setLineSpacing(Math.round(3 * d), 1f);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
            hlp.bottomMargin = Math.round(14 * d);
            box.addView(hint, hlp);

            box.addView(makeOrientButton("竖屏　" + s[0] + " × " + s[1],
                    "普通应用、聊天、浏览器", 0, gap, d));
            box.addView(makeOrientButton("横屏　" + s[1] + " × " + s[0],
                    "横屏应用、游戏、TV 版", 1, 0, d));

            orientDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("创建屏幕类型")
                    .setCancelable(false)
                    .setView(box)
                    .setNegativeButton("取消，不启动",
                            new android.content.DialogInterface.OnClickListener() {
                                public void onClick(android.content.DialogInterface dg, int w) {
                                    loading = true;
                                    Config.setEnabled(false);
                                    if (swEnable != null) {
                                        swEnable.setChecked(false);
                                    }
                                    loading = false;
                                    delayed();
                                }
                            })
                    .show();
        } catch (Throwable t) {
            BridgeService.start(this);
            delayed();
        }
    }

    private View makeOrientButton(String title, String sub, final int orient,
                                  int bottomMargin, float d) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16f);
        t.setTextColor(0xFF1F1F1F);

        TextView st = new TextView(this);
        st.setText(sub);
        st.setTextSize(12f);
        st.setTextColor(0xFF6E6E6E);

        col.addView(t, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = Math.round(2 * d);
        col.addView(st, slp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF2F2F5);
        bg.setCornerRadius(16 * d);
        bg.setStroke(Math.max(1, Math.round(d)), 0xFFD8D8DE);

        col.setBackground(bg);
        int ph = Math.round(18 * d);
        int pv = Math.round(14 * d);
        col.setPadding(ph, pv, ph, pv);
        col.setClickable(true);
        col.setFocusable(true);

        col.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                androidx.appcompat.app.AlertDialog dlg = orientDialog;
                orientDialog = null;
                if (dlg != null) {
                    try {
                        dlg.dismiss();
                    } catch (Throwable ignored) {
                    }
                }
                Config.setVdOrient(orient);
                BridgeService.start(MainActivity.this);
                delayed();
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = bottomMargin;
        col.setLayoutParams(lp);
        return col;
    }

    private void showOverlayGate() {
        ensureOverlay();
    }

    private void ensureOverlay() {
        if (BridgeService.Settings0.canOverlay(this)) {
            return;
        }
        try {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("需要悬浮窗权限")
                    .setMessage("两种模式都需要悬浮窗权限。"
                            + "请允许「显示在其他应用上层」。")
                    .setPositiveButton("去开启",
                            new android.content.DialogInterface.OnClickListener() {
                                public void onClick(android.content.DialogInterface d, int w) {
                                    try {
                                        startActivity(new android.content.Intent(
                                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                android.net.Uri.parse("package:" + getPackageName())));
                                    } catch (Throwable ignored) {
                                    }
                                }
                            })
                    .setNegativeButton("稍后", null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private void initUi() {
        setContentView(R.layout.activity_main);

        swEnable = (MaterialSwitch) findViewById(R.id.sw_enable);
        tvStatus = (TextView) findViewById(R.id.tv_status);
        tvUrls = (TextView) findViewById(R.id.tv_urls);
        View perm = findViewById(R.id.box_perm);
        if (perm != null) {
            perm.setVisibility(Config.isVdMode() ? View.GONE : View.VISIBLE);
        }
        tvLog = (TextView) findViewById(R.id.tv_log);
        tvCrash = (TextView) findViewById(R.id.tv_crash);

        if (swEnable != null) {
            swEnable.setChecked(Config.isEnabled());
            swEnable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton v, boolean checked) {
                    if (loading) {
                        return;
                    }
                    Config.setEnabled(checked);
                    if (checked) {
                        if (Config.isVdMode()) {
                            askOrient();
                        } else {
                            BridgeService.start(MainActivity.this);
                        }
                    } else {
                        BridgeService.stop(MainActivity.this);
                    }
                    delayed();
                }
            });
        }

        click(R.id.btn_a11y, new Act() {
            public void run() {
                openA11y();
            }
        });
        click(R.id.btn_unlock, new Act() {
            public void run() {
                openA11y();
                toast("请开启「MCP交互器 兼容模式」");
            }
        });
        click(R.id.btn_copy, new Act() {
            public void run() {
                copy();
            }
        });
        click(R.id.btn_refresh, new Act() {
            public void run() {
                refresh();
            }
        });
        click(R.id.btn_settings, new Act() {
            public void run() {
                try {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                } catch (Throwable t) {
                    toast("无法打开设置");
                }
            }
        });

        loading = false;
        uiReady = true;
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            syncSwitch();
            refresh();
            ensureReady();
        } catch (Throwable t) {
            Log.add("onResume 异常: " + t);
        }
    }

    private void syncSwitch() {
        if (swEnable == null) {
            return;
        }
        boolean real = BridgeService.running;
        if (swEnable.isChecked() != real) {
            loading = true;
            swEnable.setChecked(real);
            loading = false;
        }
    }

    private void ensureReady() {
        if (!uiReady || pendingCheck) {
            return;
        }

        if (!Config.isVdMode() && !Ui.ready()) {
            pendingCheck = true;
            ask("必须开启无障碍服务",
                    "MCP交互器需要无障碍服务才能读取屏幕和执行操作。\n\n"
                            + "没有它，任何人都无法操作手机；有了它，AI 才能帮你做事。",
                    "去开启", 0);
            return;
        }

        if (!StopBar.canDraw(this)) {
            pendingCheck = true;
            ask("必须开启急停悬浮球",
                    "这个应用会代替你操作手机。如果没有随时能停下的按钮，"
                            + "万一 AI 误操作（误触卸载、误删、误付款），你没有办法拦住。\n\n"
                            + "急停悬浮球是唯一的急停方式，必须开启才能使用。",
                    "去授权", 1);
            return;
        }

        if (Config.isVdMode()) {
            StopBar.setSmall(true);
            if (!StopBar.isShown()) {
                StopBar.show(this);
            }
            InfoBar.hide();
            return;
        }

        StopBar.setSmall(false);
        if (!StopBar.isShown()) {
            StopBar.show(this);
        }
        if (Config.isInfoOn() && !InfoBar.isShown()) {
            InfoBar.show(this);
        }
    }

    private void ask(String title, String msg, String ok, final int which) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton(ok, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        pendingCheck = false;
                        if (which == 0) {
                            openA11y();
                        } else {
                            openOverlay();
                        }
                    }
                })
                .setNegativeButton("退出应用", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        finishAffinity();
                    }
                })
                .show();
    }

    private void click(int id, final Act act) {
        View v = findViewById(id);
        if (v != null) {
            v.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    act.run();
                }
            });
        }
    }

    private void delayed() {
        if (tvStatus == null) {
            return;
        }
        tvStatus.postDelayed(new Runnable() {
            public void run() {
                refresh();
            }
        }, 800);
    }

    private String overlayText() {
        return BridgeService.Settings0.canOverlay(this) ? "已授权" : "未授权";
    }

    private String vdState() {
        if (!VdMgr.ready()) {
            String e = VdMgr.error();
            return e.length() > 0 ? "未连接 · " + e : "未连接";
        }
        String info = VdMgr.info();
        if (info.contains("ready=false")) {
            String last = VdMgr.lastCreate();
            return last.length() > 0 ? "建屏失败" : "无";
        }
        int w = VdMgr.pickInt(info, "width=");
        int h = VdMgr.pickInt(info, "height=");
        int id = VdMgr.pickInt(info, "displayId=");
        if (w > 0 && h > 0) {
            return id + "  " + w + "x" + h;
        }
        return "就绪";
    }

    private String realPortText() {
        int p = HttpSrv.actualLocalPort();
        return p > 0 ? String.valueOf(p) : "未监听";
    }

    private void refresh() {
        if (!uiReady || tvStatus == null || tvUrls == null
                || tvLog == null || tvCrash == null) {
            return;
        }
        try {
            String svc = BridgeService.running ? "运行中" : "已停止";
            String a11y = Ui.ready() ? "已开启" : "未开启";
            boolean unlock;
            try {
                unlock = com.google.android.accessibility.selecttospeak.SelectToSpeakService
                        .isEnabled(this);
            } catch (Throwable t) {
                unlock = false;
            }
            String mode = StopBar.isShown() ? "悬浮球" : "未开启";

            StringBuilder b = new StringBuilder();
            if (Config.isPaused()) {
                b.append("⚠ 已急停\n");
            }
            boolean vd = Config.isVdMode();
            b.append("模式  ").append(vd ? "虚拟屏" : "普通");
            b.append("\n服务  ").append(svc);
            if (vd) {
                String sh = !VdMgr.shizukuAlive() ? "未运行"
                        : (VdMgr.hasPerm() ? "已授权" : "未授权");
                b.append("\nShizuku  ").append(sh);
                b.append("\n虚拟屏  ").append(vdState());
                b.append("\n小窗  ").append(VdWin.isShowing() ? "已就绪" : "无");
            } else {
                b.append("\n无障碍  ").append(a11y);
                b.append("\n兼容模式  ").append(unlock ? "已开启" : "未开启");
                b.append("\n护栏  ").append(Guard.ENABLED ? "已开启" : "已关闭");
                b.append("\nrev  ").append(A11ySvc.rev);
            }
            b.append("\n监听  ").append(realPortText());
            b.append("\n悬浮窗  ").append(overlayText());
            b.append("\n急停球  ").append(mode);
            tvStatus.setText(b.toString());

            String tk = Config.getToken();
            tvUrls.setText("本机  " + Net.urlLocal()
                    + "\n局域网  " + Net.urlLan()
                    + "\n令牌  " + (tk.length() == 0 ? "(不验证)" : tk));

            tvLog.setText(Log.dump().trim());

            File f = App.crashFile(this);
            if (f.exists()) {
                tvCrash.setVisibility(View.VISIBLE);
                tvCrash.setText("上次崩溃信息:\n" + App.read(f));
            } else {
                tvCrash.setVisibility(View.GONE);
                tvCrash.setText("");
            }
        } catch (Throwable t) {
            try {
                tvStatus.setText("刷新异常: " + t);
            } catch (Throwable ignored) {
            }
        }
    }

    private void openA11y() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            toast("请找到「MCP交互器」并开启");
        } catch (Throwable t) {
            toast("无法打开无障碍设置");
        }
    }

    private void openOverlay() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
            toast("请允许「显示在其他应用上层」");
        } catch (Throwable t) {
            toast("无法打开悬浮窗设置");
        }
    }

    private void copy() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null) {
                return;
            }
            String s = "本机: " + Net.urlLocal()
                    + "\n局域网: " + Net.urlLan();
            String tk = Config.getToken();
            if (tk.length() > 0) {
                s = s + "\n令牌: " + tk;
            }
            cm.setPrimaryClip(ClipData.newPlainText("mcp", s));
            toast("已复制");
        } catch (Throwable t) {
            toast("复制失败");
        }
    }

    private void showAgreement() {
        try {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("用户协议 · 免责声明 · 隐私政策")
                    .setMessage("欢迎使用 MCP交互器，使用前请仔细阅读：\n\n"
                            + "【用户协议】\n"
                            + "1. 本应用提供本地 MCP 服务与屏幕自动化能力。\n"
                            + "2. 请勿将本应用用于任何违法违规用途。\n\n"
                            + "【免责声明】\n"
                            + "1. 本应用完全开源，使用者自行承担使用风险。\n"
                            + "2. AI 自动操作屏幕存在误操作可能（包括误点、误删等），"
                            + "使用者应对 AI 的操作负全部责任。\n"
                            + "3. 开发者不对任何数据丢失、资金损失或其它损失承担责任。\n\n"
                            + "【隐私政策】\n"
                            + "1. 本应用不收集、不上传任何个人数据。\n"
                            + "2. 除用户主动发起的文件下载外，应用不访问互联网。\n"
                            + "3. OCR 完全在本机离线运行，截图不会上传。\n"
                            + "4. 所有数据仅保存在本机。\n\n"
                            + "点击「同意并继续」即表示您已阅读并接受上述全部条款。")
                    .setCancelable(false)
                    .setPositiveButton("同意并继续",
                            new android.content.DialogInterface.OnClickListener() {
                                public void onClick(android.content.DialogInterface d, int w) {
                                    Config.setAgreed(true);
                                    if (!Config.isModePicked()) {
                                        chooseMode();
                                        return;
                                    }
                                    if (!Setup.done(MainActivity.this)) {
                                        goOnboard();
                                        return;
                                    }
                                    initUi();
                                    enforcePermissions();
                                }
                            })
                    .setNegativeButton("不同意",
                            new android.content.DialogInterface.OnClickListener() {
                                public void onClick(android.content.DialogInterface d, int w) {
                                    finishAffinity();
                                }
                            })
                    .show();
        } catch (Throwable t) {
            finishAffinity();
        }
    }

    private void toast(String s) {
        try {
            Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}
