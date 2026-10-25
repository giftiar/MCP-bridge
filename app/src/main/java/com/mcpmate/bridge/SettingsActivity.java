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
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;

import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends Activity {

    private interface Act {
        void run();
    }

    private EditText etToken;
    private EditText etPortLocal;
    private EditText etPortLan;
    private EditText etInterval;
    private MaterialSwitch swLan;
    private MaterialSwitch swDyn;
    private MaterialSwitch swOcrImg;
    private android.widget.RadioGroup rgMode;
    private android.widget.RadioButton rbModeScreen;
    private android.widget.RadioButton rbModeVd;
    private android.widget.TextView tvVdList;
    private View boxVd;
    private LinearLayout boxSwatch;
    private TextView tvLanWarn;

    @Override
    protected void onCreate(Bundle b) {
        Theme2.apply(this);
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        try {
            Config.init(this);
        } catch (Throwable ignored) {
        }

        etToken = (EditText) findViewById(R.id.et_token);
        etPortLocal = (EditText) findViewById(R.id.et_port_local);
        etPortLan = (EditText) findViewById(R.id.et_port_lan);
        etInterval = (EditText) findViewById(R.id.et_interval);
        swLan = (MaterialSwitch) findViewById(R.id.sw_lan);
        swDyn = (MaterialSwitch) findViewById(R.id.sw_dyn);
        swOcrImg = (MaterialSwitch) findViewById(R.id.sw_ocr_img);
        rgMode = (android.widget.RadioGroup) findViewById(R.id.rg_mode);
        rbModeScreen = (android.widget.RadioButton) findViewById(R.id.rb_mode_screen);
        rbModeVd = (android.widget.RadioButton) findViewById(R.id.rb_mode_vd);
        tvVdList = (android.widget.TextView) findViewById(R.id.tv_vd_list);
        boxVd = findViewById(R.id.box_vd);
        boxSwatch = (LinearLayout) findViewById(R.id.box_swatch);
        tvLanWarn = (TextView) findViewById(R.id.tv_lan_warn);

        fill();

        click(R.id.btn_back, new Act() {
            public void run() {
                finish();
            }
        });
        click(R.id.btn_token, new Act() {
            public void run() {
                etToken.setText(Config.randomToken());
            }
        });
        click(R.id.btn_save, new Act() {
            public void run() {
                save();
            }
        });
        click(R.id.btn_testconn, new Act() {
            public void run() {
                testConn();
            }
        });
        click(R.id.btn_resume, new Act() {
            public void run() {
                boolean next = !Config.isPaused();
                Config.setPaused(next);
                toast(next ? "已暂停：AI 无法操作" : "已恢复：允许 AI 操作");
            }
        });
        click(R.id.btn_selftest, new Act() {
            public void run() {
                showText("本机能力自检", Ui.selfTest());
            }
        });
        click(R.id.btn_battery, new Act() {
            public void run() {
                openBattery();
            }
        });
        click(R.id.btn_bgpopup, new Act() {
            public void run() {
                openAppDetail("请在「其他权限」中开启「后台弹出界面」");
            }
        });
        click(R.id.btn_storage, new Act() {
            public void run() {
                openStorage();
            }
        });
        click(R.id.btn_onboard, new Act() {
            public void run() {
                try {
                    startActivity(new Intent(SettingsActivity.this,
                            OnboardActivity.class));
                } catch (Throwable ignored) {
                }
            }
        });
        click(R.id.btn_about, new Act() {
            public void run() {
                showAbout();
            }
        });

        swLan.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton v, boolean on) {
                updateWarn();
            }
        });

        if (rgMode != null) {
            rgMode.setOnCheckedChangeListener(
                    new android.widget.RadioGroup.OnCheckedChangeListener() {
                        public void onCheckedChanged(android.widget.RadioGroup g, int id) {
                            boolean vd = id == R.id.rb_mode_vd;
                            if (vd == Config.isVdMode()) {
                                return;
                            }
                            Config.setVdMode(vd);
                            BridgeService.applyMode(SettingsActivity.this);
                            toast("已切换，MCP 客户端需重新连接");
                        }
                    });
        }

        click(R.id.btn_vd_list, new Act() {
            public void run() {
                refreshVdList();
            }
        });

        click(R.id.btn_vd_kill, new Act() {
            public void run() {
                confirmKillVd();
            }
        });

        click(R.id.btn_shizuku, new Act() {
            public void run() {
                if (!VdMgr.shizukuAlive()) {
                    toast("Shizuku 未运行，请先启动 Shizuku");
                    return;
                }
                if (VdMgr.hasPerm()) {
                    toast("Shizuku 已授权");
                    return;
                }
                VdMgr.requestPerm();
                toast("已弹出授权框");
            }
        });

        if (swOcrImg != null) {
            swOcrImg.setOnCheckedChangeListener(
                    new android.widget.CompoundButton.OnCheckedChangeListener() {
                        public void onCheckedChanged(android.widget.CompoundButton v, boolean on) {
                            Config.setOcrImageOn(on);
                        }
                    });
        }

        if (boxVd != null) {
            boxVd.setVisibility(Config.isVdMode() ? View.VISIBLE : View.GONE);
        }
        if (Config.isVdMode() && VdMgr.ready()) {
            refreshVdList();
        }

        buildThemeRow();
    }

    private void refreshVdList() {
        if (tvVdList == null) {
            return;
        }
        if (!VdMgr.ready()) {
            tvVdList.setText("服务未连接。请先打开服务开关。");
            return;
        }
        tvVdList.setText("查询中…");
        new Thread(new Runnable() {
            public void run() {
                final String r = VdMgr.listDisplays();
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (tvVdList != null) {
                            tvVdList.setText(r);
                        }
                    }
                });
            }
        }, "vd-list").start();
    }

    private void confirmKillVd() {
        if (!VdMgr.ready()) {
            toast("服务未连接");
            return;
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("清理本应用虚拟屏")
                .setMessage("只会清理 MCP交互器 自己创建的虚拟屏和孤儿进程。\n"
                        + "其他应用的虚拟屏不会被触碰。\n\n"
                        + "如果当前有 AI 任务正在运行，会被中断。")
                .setPositiveButton("确认清理",
                        new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) {
                                doKillVd();
                            }
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doKillVd() {
        if (tvVdList != null) {
            tvVdList.setText("清理中…");
        }
        new Thread(new Runnable() {
            public void run() {
                final String r = VdMgr.killAllDisplays();
                final String list = VdMgr.listDisplays();
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (tvVdList != null) {
                            tvVdList.setText(r + "\n---\n" + list);
                        }
                        toast("清理完成");
                    }
                });
            }
        }, "vd-kill").start();
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

    private void fill() {
        if (rbModeVd != null && rbModeScreen != null) {
            boolean vd = Config.isVdMode();
            rbModeVd.setChecked(vd);
            rbModeScreen.setChecked(!vd);
        }
        etToken.setText(Config.getToken());
        etPortLocal.setText(String.valueOf(Config.getPortLocal()));
        etPortLan.setText(String.valueOf(Config.getPortLan()));
        etInterval.setText(String.valueOf(Config.getInterval()));
        swLan.setChecked(Config.isLan());
        if (swOcrImg != null) {
            swOcrImg.setChecked(Config.isOcrImageOn());
        }
        updateWarn();
    }

    private void updateWarn() {
        if (tvLanWarn == null) {
            return;
        }
        boolean on = swLan.isChecked();
        String tk = etToken == null ? "" : etToken.getText().toString().trim();
        tvLanWarn.setVisibility(on && tk.length() == 0
                ? View.VISIBLE : View.GONE);
    }

    private void save() {
        String token = text(etToken);
        int pl = num(text(etPortLocal), -1);
        int pw = num(text(etPortLan), -1);
        int ivl = num(text(etInterval), -1);

        if (pl < 0 || pl > 65535 || pw < 0 || pw > 65535) {
            toast("端口需为 0-65535 的数字");
            return;
        }
        if (ivl < 0 || ivl > 60000) {
            toast("间隔需为 0-60000 毫秒");
            return;
        }

        Config.setToken(token);
        Config.setPortLocal(pl);
        Config.setPortLan(pw);
        Config.setInterval(ivl);
        Config.setLan(swLan.isChecked());

        if (Config.isEnabled()) {
            BridgeService.start(this);
        } else {
            BridgeService.stop(this);
        }
        toast("已保存，正在应用");
        updateWarn();
    }

    private void testConn() {
        toast("正在测试…");
        final android.os.Handler h = new android.os.Handler(
                android.os.Looper.getMainLooper());
        Thread th = new Thread(new Runnable() {
            public void run() {
                final String r = HttpSrv.testLocalBlocking();
                h.post(new Runnable() {
                    public void run() {
                        toast(r);
                    }
                });
            }
        }, "mcp-testconn");
        th.setDaemon(true);
        th.start();
    }

    private void buildThemeRow() {
        LinearLayout row = (LinearLayout) findViewById(R.id.row_swatch);
        if (row == null) {
            return;
        }

        if (swDyn != null) {
            if (!Theme2.canDyn()) {
                swDyn.setEnabled(false);
                swDyn.setChecked(false);
                swDyn.setText("跟随壁纸取色（本机不支持，需 Android 12+）");
            } else {
                swDyn.setChecked(Config.isFollowWallpaper());
                swDyn.setOnCheckedChangeListener(
                        new android.widget.CompoundButton.OnCheckedChangeListener() {
                            public void onCheckedChanged(android.widget.CompoundButton v, boolean on) {
                                Config.setFollowWallpaper(on);
                                updateSwatchVisible();
                                recreate();
                            }
                        });
            }
        }

        final int cur = Config.getSeed();
        int n = Theme2.count();
        float d = getResources().getDisplayMetrics().density;
        int size = (int) (48 * d + 0.5f);
        int gap = (int) (10 * d + 0.5f);

        for (int i = 0; i < n; i++) {
            View v = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = gap;
            v.setLayoutParams(lp);

            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(Theme2.SWATCH[i]);
            if (i == cur) {
                g.setStroke((int) (3 * d), 0xFFFFFFFF);
            }
            v.setBackground(g);
            v.setContentDescription(Theme2.NAMES[i]);

            final int idx = i;
            v.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    Config.setSeed(idx);
                    toast("已选择：" + Theme2.NAMES[idx]);
                    recreate();
                }
            });
            row.addView(v);
        }

        updateSwatchVisible();
    }

    private void updateSwatchVisible() {
        if (boxSwatch == null) {
            return;
        }
        boolean manual = !Theme2.useDyn();
        boxSwatch.setVisibility(manual ? View.VISIBLE : View.GONE);
    }

    private void openStorage() {
        try {
            if (Down.allFilesOk()) {
                toast("已拥有所有文件访问权限");
                return;
            }
            if (Build.VERSION.SDK_INT >= 30) {
                Intent i = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
                toast("请打开「允许管理所有文件」");
            } else {
                toast("当前系统无需此权限");
            }
        } catch (Throwable t) {
            toast("无法打开权限页，请手动到系统设置授权");
        }
    }

    private void openBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            try {
                startActivity(new Intent(
                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable t2) {
                toast("无法打开电池设置");
            }
        }
    }

    private void openAppDetail(String tip) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
            toast(tip);
        } catch (Throwable t) {
            toast("无法打开应用详情");
        }
    }

    private void showAbout() {
        try {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("关于")
                    .setMessage("全部代码由 DeepSeek 编写\n"
                            + "产品设计与调试：giftiar\n"
                            + "使用 MT 管理器 和 Android Code Studio\n\n"
                            + "版本 " + Mcp.VERSION_TEXT + "\n"
                            + "OCR：ML Kit 中文离线识别\n"
                            + "协议：MCP " + Mcp.PROTOCOL_TEXT)
                    .setNeutralButton("GitHub",
                            new android.content.DialogInterface.OnClickListener() {
                                public void onClick(android.content.DialogInterface d, int w) {
                                    openLink("https://github.com/giftiar");
                                }
                            })
                    .setPositiveButton("B 站主页",
                            new android.content.DialogInterface.OnClickListener() {
                                public void onClick(android.content.DialogInterface d, int w) {
                                    openLink("https://space.bilibili.com/3493126740118074");
                                }
                            })
                    .setNegativeButton("关闭", null)
                    .show();
        } catch (Throwable t) {
            toast("无法显示");
        }
    }

    private void openLink(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            toast("无法打开浏览器");
        }
    }

    private void showText(String title, String body) {
        try {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(title)
                    .setMessage(body)
                    .setPositiveButton("关闭", null)
                    .show();
        } catch (Throwable t) {
            toast("无法显示");
        }
    }

    private String text(EditText e) {
        return e == null ? "" : e.getText().toString().trim();
    }

    private int num(String s, int def) {
        if (s == null || s.length() == 0) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private void toast(String s) {
        try {
            Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}
