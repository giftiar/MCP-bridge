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

import android.inputmethodservice.InputMethodService;
import android.os.IBinder;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class McpIme extends InputMethodService {

    public static volatile McpIme inst;

    private volatile InputConnection conn;
    private volatile EditorInfo info;

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
    }

    @Override
    public void onDestroy() {
        inst = null;
        conn = null;
        info = null;
        super.onDestroy();
    }

    public static volatile String lastEvent = "nothing yet";

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        info = attribute;
        InputConnection c = getCurrentInputConnection();
        if (c != null) {
            conn = c;
        }
        lastEvent = "onStartInput conn=" + (c != null)
                + " restarting=" + restarting
                + " type=" + (attribute == null ? -1 : attribute.inputType);
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        info = attribute;
        InputConnection c = getCurrentInputConnection();
        if (c != null) {
            conn = c;
        }
        lastEvent = "onStartInputView conn=" + (c != null)
                + " restarting=" + restarting
                + " type=" + (attribute == null ? -1 : attribute.inputType);
    }

    @Override
    public void onFinishInput() {
        conn = null;
        info = null;
        lastEvent = "onFinishInput";
        super.onFinishInput();
    }

    @Override
    public View onCreateInputView() {
        return null;
    }

    @Override
    public boolean onEvaluateInputViewShown() {
        return false;
    }

    public boolean hasConnection() {
        InputConnection c = conn;
        if (c == null) {
            return false;
        }
        try {
            return c.getTextBeforeCursor(1, 0) != null
                    || c.getTextAfterCursor(1, 0) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean hasRealField() {
        EditorInfo ei = info;
        if (ei == null || ei.inputType == 0) {
            return false;
        }
        return hasConnection();
    }

    public int inputType() {
        EditorInfo ei = info;
        return ei == null ? 0 : ei.inputType;
    }

    public String describe() {
        InputConnection c = conn;
        if (c == null) {
            return "no_connection";
        }
        EditorInfo ei = info;
        StringBuilder b = new StringBuilder();
        b.append("connected");
        if (ei != null) {
            b.append(" inputType=").append(ei.inputType);
            if (ei.hintText != null) {
                b.append(" hint=").append(ei.hintText);
            }
        }
        return b.toString();
    }

    public boolean clear() {
        InputConnection c = conn;
        if (c == null) {
            return false;
        }
        try {
            c.beginBatchEdit();
            CharSequence cur = c.getTextBeforeCursor(4096, 0);
            int n = cur == null ? 0 : cur.length();
            if (n > 0) {
                c.deleteSurroundingText(n, 0);
            }
            c.endBatchEdit();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean commit(String text) {
        InputConnection c = conn;
        if (c == null) {
            return false;
        }
        try {
            c.beginBatchEdit();
            CharSequence cur = c.getTextBeforeCursor(4096, 0);
            int n = cur == null ? 0 : cur.length();
            if (n > 0) {
                c.deleteSurroundingText(n, 0);
            }
            c.commitText(text, 1);
            c.endBatchEdit();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean sendEnter() {
        InputConnection c = conn;
        if (c == null) {
            return false;
        }
        try {
            c.sendKeyEvent(new android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_DOWN,
                    android.view.KeyEvent.KEYCODE_ENTER));
            c.sendKeyEvent(new android.view.KeyEvent(
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.ACTION_UP));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

}
