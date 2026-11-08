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

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class OnboardActivity extends Activity {

    private interface Act {
        void run();
    }

    private TextView tvS1v;
    private TextView tvS2v;
    private TextView tvS3v;
    private TextView tvS5v;
    private TextView tvSuv;
    private View cardS1;
    private View cardSu;
    private TextView tvSub;
    private TextView tvT1;
    private TextView tvT2;
    private TextView tvT3;
    private TextView tvT4;
    private TextView tvT5;
    private TextView tvT6;


    @Override
    protected void onCreate(Bundle bundle) {
        Theme2.apply(this);
        super.onCreate(bundle);
        try {
            Config.init(this);
        } catch (Throwable t) {
            Log.add("引导页配置失败: " + t);
        }
        setContentView(R.layout.activity_onboard);

        tvS1v = (TextView) findViewById(R.id.tv_s1v);
        tvS2v = (TextView) findViewById(R.id.tv_s2v);
        tvS3v = (TextView) findViewById(R.id.tv_s3v);
        tvS5v = (TextView) findViewById(R.id.tv_s5v);
        tvSuv = (TextView) findViewById(R.id.tv_suv);
        cardS1 = findViewById(R.id.card_s1);
        cardSu = findViewById(R.id.card_su);
        tvSub = (TextView) findViewById(R.id.tv_sub);
        tvT1 = (TextView) findViewById(R.id.tv_t1);
        tvT2 = (TextView) findViewById(R.id.tv_t2);
        tvT3 = (TextView) findViewById(R.id.tv_t3);
        tvT4 = (TextView) findViewById(R.id.tv_t4);
        tvT5 = (TextView) findViewById(R.id.tv_t5);
        tvT6 = (TextView) findViewById(R.id.tv_t6);
        applyModeLayout();

        click(R.id.btn_s1, new Act() {
            public void run() {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    toast("请找到「MCP交互器」并开启");
                } catch (Throwable t) {
                    toast("无法打开设置");
                }
            }
        });

        click(R.id.btn_su, new Act() {
            public void run() {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    toast("请找到「MCP交互器 兼容模式」并开启");
                } catch (Throwable t) {
                    toast("无法打开设置");
                }
            }
        });

        click(R.id.btn_s2, new Act() {
            public void run() {
                askNotification();
            }
        });

        click(R.id.btn_s3, new Act() {
            public void run() {
                try {
                    Intent i = new Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
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
        });

        click(R.id.btn_s4, new Act() {
            public void run() {
                try {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                    toast("请在「其他权限」中开启「后台弹出界面」");
                } catch (Throwable t) {
                    toast("无法打开应用详情");
                }
            }
        });

        click(R.id.btn_s5, new Act() {
            public void run() {
                enableBall();
            }
        });

        click(R.id.btn_done, new Act() {
            public void run() {
                if (Config.isVdMode()) {
                    if (!VdMgr.shizukuAlive()) {
                        mustShizuku();
                        return;
                    }
                    if (!VdMgr.hasPerm()) {
                        VdMgr.requestPerm();
                        toast("请先允许 Shizuku 授权，再点完成");
                        return;
                    }
                } else {
                    if (!a11yOn()) {
                        toast("请先开启无障碍服务（必需）");
                        return;
                    }
                }
                if (!emergencyReady()) {
                    mustCalibrate();
                    return;
                }
                Setup.mark(OnboardActivity.this);
                goMain();
            }
        });

        try {
            refresh();
        } catch (Throwable t) {
            Log.add("引导页刷新失败: " + t);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            applyModeLayout();
            syncBall();
            refresh();
        } catch (Throwable t) {
            Log.add("引导页 onResume 失败: " + t);
        }
    }

    private void applyModeLayout() {
        boolean vd = Config.isVdMode();
        if (cardS1 != null) {
            cardS1.setVisibility(vd ? View.GONE : View.VISIBLE);
        }
        if (cardSu != null) {
            cardSu.setVisibility(vd ? View.GONE : View.VISIBLE);
        }

        if (vd) {
            setTitle(tvT3, "1. 通知权限");
            setTitle(tvT4, "2. 电池优化白名单");
            setTitle(tvT5, "3. 后台弹出界面");
            setTitle(tvT6, "4. 急停悬浮球");
        } else {
            setTitle(tvT1, "1. 无障碍服务");
            setTitle(tvT2, "2. 兼容模式");
            setTitle(tvT3, "3. 通知权限");
            setTitle(tvT4, "4. 电池优化白名单");
            setTitle(tvT5, "5. 后台弹出界面");
            setTitle(tvT6, "6. 急停悬浮球");
        }

        if (tvSub != null) {
            tvSub.setText(vd
                    ? "虚拟屏模式：需完成通知、电池、后台弹窗、悬浮球四项，并授予 Shizuku"
                    : "普通模式：第 1 项和第 6 项是必需的");
        }
        StopBar.setSmall(vd);
    }

    private void setTitle(TextView tv, String text) {
        if (tv != null) {
            tv.setText(text);
        }
    }

    private void mustShizuku() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("需要 Shizuku")
                .setMessage("虚拟屏模式依赖 Shizuku 在后台创建独立屏幕。\n\n"
                        + "请先安装 Shizuku，并在 Shizuku 应用内以 root 或 ADB 方式启动服务，"
                        + "然后回到这里重新点击完成。")
                .setCancelable(false)
                .setPositiveButton("知道了", null)
                .setNegativeButton("退出应用", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        finishAffinity();
                    }
                })
                .show();
    }

    private void syncBall() {
        if (!StopBar.canDraw(this)) {
            return;
        }
        if (!StopBar.isShown()) {
            StopBar.show(this);
        }
    }

    private boolean emergencyReady() {
        if (!StopBar.canDraw(this)) {
            return false;
        }
        if (!StopBar.isShown()) {
            StopBar.show(this);
        }
        return StopBar.isShown();
    }

    private void mustCalibrate() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("必须开启急停悬浮球")
                .setMessage("这个应用会代替你操作手机。如果没有随时能停下的按钮，"
                        + "万一 AI 误操作（比如误触卸载、误删、误付款），你没有办法拦住。\n\n"
                        + "急停悬浮球是唯一的急停方式，必须开启才能使用。")
                .setCancelable(false)
                .setPositiveButton("去授权", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        try {
                            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                            i.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                            toast("请允许「显示在其他应用上层」");
                        } catch (Throwable t) {
                            toast("无法打开悬浮窗设置");
                        }
                    }
                })
                .setNegativeButton("退出应用", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        finishAffinity();
                    }
                })
                .show();
    }

    private void goMain() {
        try {
            startActivity(new Intent(this, MainActivity.class));
        } catch (Throwable ignored) {
        }
        finish();
    }

    private void click(int id, final Act act) {
        View v = findViewById(id);
        if (v != null) {
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    act.run();
                }
            });
        }
    }

    private void refresh() {
        if (tvS1v == null || tvS2v == null || tvS3v == null || tvS5v == null) {
            return;
        }
        try {
            boolean a = a11yOn();
            tvS1v.setText(a ? "已开启" : "未开启");
            tvS1v.setTextColor(a ? 0xFF2E7D32 : 0xFFC62828);

            boolean u;
            try {
                u = com.google.android.accessibility.selecttospeak.SelectToSpeakService
                        .isEnabled(this);
            } catch (Throwable t) {
                u = false;
            }
            if (tvSuv != null) {
                tvSuv.setText(u ? "已开启" : "未开启");
                tvSuv.setTextColor(u ? 0xFF2E7D32 : 0xFFC62828);
            }

            boolean n = notifOk();
            tvS2v.setText(n ? "已授予" : "未授予");
            tvS2v.setTextColor(n ? 0xFF2E7D32 : 0xFFC62828);

            boolean bg = batteryOk();
            tvS3v.setText(bg ? "已忽略" : "未设置");
            tvS3v.setTextColor(bg ? 0xFF2E7D32 : 0xFFC62828);

            String m;
            int c;
            if (StopBar.isShown()) {
                m = "已开启";
                c = 0xFF2E7D32;
            } else if (StopBar.canDraw(this)) {
                m = "待启动";
                c = 0xFFF57C00;
            } else {
                m = "未开启";
                c = 0xFFC62828;
            }
            if (tvS5v != null) {
                tvS5v.setText(m);
                tvS5v.setTextColor(c);
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean a11yOn() {
        if (Ui.ready()) {
            return true;
        }
        try {
            String s = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (s == null) {
                return false;
            }
            String full = getPackageName() + "/" + A11ySvc.class.getName();
            for (String one : s.split(":")) {
                String v = one.trim();
                if (v.equalsIgnoreCase(full)) {
                    return true;
                }
                if (v.equalsIgnoreCase(getPackageName() + "/.A11ySvc")) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean notifOk() {
        try {
            if (Build.VERSION.SDK_INT < 33) {
                return true;
            }
            return checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return true;
        }
    }

    private boolean batteryOk() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null) {
                return false;
            }
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private void askNotification() {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
            } else {
                toast("本系统无需申请通知权限");
            }
        } catch (Throwable t) {
            toast("申请失败");
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        refresh();
    }

    private void enableBall() {
        if (!StopBar.canDraw(this)) {
            mustCalibrate();
            return;
        }
        StopBar.show(this);
        toast("已开启急停悬浮球：点一下暂停，再点恢复，长按拖动");
        refresh();
    }

    private void toast(String s) {
        try {
            Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}
