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

public class Guard {

    public static final boolean ENABLED = true;

    private static final String[] DANGER = new String[]{
            "卸载", "删除", "清除数据", "清除所有", "格式化", "恢复出厂", "重置",
            "支付", "付款", "转账", "确认支付", "确认付款", "购买", "订阅",
            "退款", "充值", "提现", "绑定银行卡", "解绑",
            "注销", "退出登录", "删除账号", "关闭账号",
            "强制停止", "停止运行", "结束进程",
            "授权", "允许", "同意并", "确认安装", "安装",
            "覆盖安装", "降级安装", "清除缓存", "清理全部",
            "停用", "禁用", "卸载更新"
    };

    public static String hit(String text) {
        if (!ENABLED) {
            return null;
        }
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.length() == 0) {
            return null;
        }
        for (int i = 0; i < DANGER.length; i++) {
            if (t.contains(DANGER[i])) {
                return DANGER[i];
            }
        }
        return null;
    }

    public static String blocked(String word, String detail) {
        StringBuilder b = new StringBuilder();
        b.append("blocked\n");
        b.append("effect: none\n");
        b.append("reason: refused by the danger-word guard\n");
        b.append("hit_word: ").append(word).append('\n');
        b.append("target: ").append(detail).append('\n');
        b.append("hint: this action may lose data or money, so it was refused. ")
                .append("if it is really needed, ask the user to do it by hand on the phone.\n");
        b.append("do_not_retry: do not retry the same intent via click_text, raw coordinates, ")
                .append("confirm_risk or by expanding a menu first.\n");
        return b.toString();
    }
}
