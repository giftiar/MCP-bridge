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

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

public interface IVdSvc extends IInterface {

    String DESCRIPTOR = "com.mcpmate.bridge.IVdSvc";

    String ping() throws RemoteException;

    String create(int w, int h, int dpi) throws RemoteException;

    String destroy() throws RemoteException;

    String startApp(String pkg) throws RemoteException;

    String click(int x, int y, int holdMs) throws RemoteException;

    String longClick(int x, int y) throws RemoteException;

    String drag(int x1, int y1, int x2, int y2, int holdMs, int durationMs)
            throws RemoteException;

    String swipe(int x1, int y1, int x2, int y2, int durationMs) throws RemoteException;

    String key(int keycode) throws RemoteException;

    void capture(ParcelFileDescriptor out) throws RemoteException;

    void preview(int maxWidth, int quality, ParcelFileDescriptor out) throws RemoteException;

    String info() throws RemoteException;

    String rotate(int orientation) throws RemoteException;

    void registerClient(IBinder client) throws RemoteException;

    String listDisplays() throws RemoteException;

    String killAllDisplays() throws RemoteException;

    String activateIme() throws RemoteException;

    String deactivateIme() throws RemoteException;

    String touchDown(int x, int y) throws RemoteException;

    String touchMove(int x, int y) throws RemoteException;

    String touchUp(int x, int y) throws RemoteException;


    abstract class Stub extends Binder implements IVdSvc {

        private static final int T_PING = 1;
        private static final int T_CREATE = 2;
        private static final int T_DESTROY = 3;
        private static final int T_START = 4;
        private static final int T_CLICK = 5;
        private static final int T_LONGCLICK = 6;
        private static final int T_DRAG = 7;
        private static final int T_SWIPE = 8;
        private static final int T_KEY = 9;
        private static final int T_CAPTURE = 10;
        private static final int T_PREVIEW = 11;
        private static final int T_INFO = 12;
        private static final int T_SHELL = 13;
        private static final int T_ROTATE = 14;
        private static final int T_REGCLIENT = 15;
        private static final int T_LISTDISP = 16;
        private static final int T_KILLALL = 17;
        private static final int T_IME_ON = 18;
        private static final int T_IME_OFF = 19;
        private static final int T_DOWN = 20;
        private static final int T_MOVE = 21;
        private static final int T_UP = 22;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IVdSvc asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin instanceof IVdSvc) {
                return (IVdSvc) iin;
            }
            return new Proxy(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION:
                    reply.writeString(DESCRIPTOR);
                    return true;

                case T_PING:
                    data.enforceInterface(DESCRIPTOR);
                    reply.writeNoException();
                    reply.writeString(ping());
                    return true;

                case T_CREATE: {
                    data.enforceInterface(DESCRIPTOR);
                    int w = data.readInt();
                    int h = data.readInt();
                    int dpi = data.readInt();
                    reply.writeNoException();
                    reply.writeString(create(w, h, dpi));
                    return true;
                }

                case T_DESTROY:
                    data.enforceInterface(DESCRIPTOR);
                    reply.writeNoException();
                    reply.writeString(destroy());
                    return true;

                case T_START: {
                    data.enforceInterface(DESCRIPTOR);
                    String pkg = data.readString();
                    reply.writeNoException();
                    reply.writeString(startApp(pkg));
                    return true;
                }

                case T_CLICK: {
                    data.enforceInterface(DESCRIPTOR);
                    int x = data.readInt();
                    int y = data.readInt();
                    int hold = data.readInt();
                    reply.writeNoException();
                    reply.writeString(click(x, y, hold));
                    return true;
                }

                case T_LONGCLICK: {
                    data.enforceInterface(DESCRIPTOR);
                    int x = data.readInt();
                    int y = data.readInt();
                    reply.writeNoException();
                    reply.writeString(longClick(x, y));
                    return true;
                }

                case T_DRAG: {
                    data.enforceInterface(DESCRIPTOR);
                    int x1 = data.readInt();
                    int y1 = data.readInt();
                    int x2 = data.readInt();
                    int y2 = data.readInt();
                    int hold = data.readInt();
                    int dur = data.readInt();
                    reply.writeNoException();
                    reply.writeString(drag(x1, y1, x2, y2, hold, dur));
                    return true;
                }

                case T_SWIPE: {
                    data.enforceInterface(DESCRIPTOR);
                    int x1 = data.readInt();
                    int y1 = data.readInt();
                    int x2 = data.readInt();
                    int y2 = data.readInt();
                    int dur = data.readInt();
                    reply.writeNoException();
                    reply.writeString(swipe(x1, y1, x2, y2, dur));
                    return true;
                }

                case T_KEY: {
                    data.enforceInterface(DESCRIPTOR);
                    int kc = data.readInt();
                    reply.writeNoException();
                    reply.writeString(key(kc));
                    return true;
                }

                case T_CAPTURE: {
                    data.enforceInterface(DESCRIPTOR);
                    ParcelFileDescriptor fd = data.readInt() != 0
                            ? ParcelFileDescriptor.CREATOR.createFromParcel(data) : null;
                    reply.writeNoException();
                    capture(fd);
                    return true;
                }

                case T_PREVIEW: {
                    data.enforceInterface(DESCRIPTOR);
                    int mw = data.readInt();
                    int q = data.readInt();
                    ParcelFileDescriptor fd = data.readInt() != 0
                            ? ParcelFileDescriptor.CREATOR.createFromParcel(data) : null;
                    reply.writeNoException();
                    preview(mw, q, fd);
                    return true;
                }

                case T_INFO:
                    data.enforceInterface(DESCRIPTOR);
                    reply.writeNoException();
                    reply.writeString(info());
                    return true;

                case T_ROTATE: {
                    data.enforceInterface(DESCRIPTOR);
                    int o = data.readInt();
                    reply.writeNoException();
                    reply.writeString(rotate(o));
                    return true;
                }

                case T_REGCLIENT: {
                    data.enforceInterface(DESCRIPTOR);
                    IBinder client = data.readStrongBinder();
                    reply.writeNoException();
                    registerClient(client);
                    return true;
                }

                case T_LISTDISP:
                    data.enforceInterface(DESCRIPTOR);
                    reply.writeNoException();
                    reply.writeString(listDisplays());
                    return true;

                case T_KILLALL:
                    data.enforceInterface(DESCRIPTOR);
                    reply.writeNoException();
                    reply.writeString(killAllDisplays());
                    return true;

                case T_IME_ON:
                    data.enforceInterface(DESCRIPTOR);
                    reply.writeNoException();
                    reply.writeString(activateIme());
                    return true;

                case T_IME_OFF:
                    data.enforceInterface(DESCRIPTOR);
                    reply.writeNoException();
                    reply.writeString(deactivateIme());
                    return true;

                case T_DOWN:
                    data.enforceInterface(DESCRIPTOR);
                    reply.writeNoException();
                    reply.writeString(touchDown(data.readInt(), data.readInt()));
                    return true;

                case T_MOVE:
                    data.enforceInterface(DESCRIPTOR);
                    reply.writeNoException();
                    reply.writeString(touchMove(data.readInt(), data.readInt()));
                    return true;

                case T_UP:
                    data.enforceInterface(DESCRIPTOR);
                    reply.writeNoException();
                    reply.writeString(touchUp(data.readInt(), data.readInt()));
                    return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }

    class Proxy implements IVdSvc {

        private final IBinder remote;

        Proxy(IBinder remote) {
            this.remote = remote;
        }

        @Override
        public IBinder asBinder() {
            return remote;
        }

        private Parcel head() {
            Parcel p = Parcel.obtain();
            p.writeInterfaceToken(DESCRIPTOR);
            return p;
        }

        private String str(int code, Parcel data) throws RemoteException {
            Parcel reply = Parcel.obtain();
            try {
                remote.transact(code, data, reply, 0);
                reply.readException();
                return reply.readString();
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        @Override
        public String ping() throws RemoteException {
            return str(T_PING_CONST, head());
        }

        @Override
        public String create(int w, int h, int dpi) throws RemoteException {
            Parcel d = head();
            d.writeInt(w);
            d.writeInt(h);
            d.writeInt(dpi);
            return str(T_CREATE_CONST, d);
        }

        @Override
        public String destroy() throws RemoteException {
            return str(T_DESTROY_CONST, head());
        }

        @Override
        public String startApp(String pkg) throws RemoteException {
            Parcel d = head();
            d.writeString(pkg);
            return str(T_START_CONST, d);
        }

        @Override
        public String click(int x, int y, int holdMs) throws RemoteException {
            Parcel d = head();
            d.writeInt(x);
            d.writeInt(y);
            d.writeInt(holdMs);
            return str(T_CLICK_CONST, d);
        }

        @Override
        public String longClick(int x, int y) throws RemoteException {
            Parcel d = head();
            d.writeInt(x);
            d.writeInt(y);
            return str(T_LONGCLICK_CONST, d);
        }

        @Override
        public String drag(int x1, int y1, int x2, int y2, int holdMs, int durationMs)
                throws RemoteException {
            Parcel d = head();
            d.writeInt(x1);
            d.writeInt(y1);
            d.writeInt(x2);
            d.writeInt(y2);
            d.writeInt(holdMs);
            d.writeInt(durationMs);
            return str(T_DRAG_CONST, d);
        }

        @Override
        public String swipe(int x1, int y1, int x2, int y2, int durationMs)
                throws RemoteException {
            Parcel d = head();
            d.writeInt(x1);
            d.writeInt(y1);
            d.writeInt(x2);
            d.writeInt(y2);
            d.writeInt(durationMs);
            return str(T_SWIPE_CONST, d);
        }

        @Override
        public String key(int keycode) throws RemoteException {
            Parcel d = head();
            d.writeInt(keycode);
            return str(T_KEY_CONST, d);
        }

        @Override
        public void capture(ParcelFileDescriptor out) throws RemoteException {
            Parcel d = head();
            Parcel reply = Parcel.obtain();
            try {
                if (out != null) {
                    d.writeInt(1);
                    out.writeToParcel(d, 0);
                } else {
                    d.writeInt(0);
                }
                remote.transact(T_CAPTURE_CONST, d, reply, 0);
                reply.readException();
            } finally {
                reply.recycle();
                d.recycle();
            }
        }

        @Override
        public void preview(int maxWidth, int quality, ParcelFileDescriptor out)
                throws RemoteException {
            Parcel d = head();
            Parcel reply = Parcel.obtain();
            try {
                d.writeInt(maxWidth);
                d.writeInt(quality);
                if (out != null) {
                    d.writeInt(1);
                    out.writeToParcel(d, 0);
                } else {
                    d.writeInt(0);
                }
                remote.transact(T_PREVIEW_CONST, d, reply, 0);
                reply.readException();
            } finally {
                reply.recycle();
                d.recycle();
            }
        }

        @Override
        public String info() throws RemoteException {
            return str(T_INFO_CONST, head());
        }

        @Override
        public String rotate(int orientation) throws RemoteException {
            Parcel d = head();
            d.writeInt(orientation);
            return str(T_ROTATE_CONST, d);
        }

        @Override
        public void registerClient(IBinder client) throws RemoteException {
            Parcel d = head();
            Parcel reply = Parcel.obtain();
            try {
                d.writeStrongBinder(client);
                remote.transact(T_REGCLIENT_CONST, d, reply, 0);
                reply.readException();
            } finally {
                reply.recycle();
                d.recycle();
            }
        }

        @Override
        public String listDisplays() throws RemoteException {
            return str(T_LISTDISP_CONST, head());
        }

        @Override
        public String killAllDisplays() throws RemoteException {
            return str(T_KILLALL_CONST, head());
        }

        @Override
        public String activateIme() throws RemoteException {
            return str(T_IME_ON_CONST, head());
        }

        @Override
        public String deactivateIme() throws RemoteException {
            return str(T_IME_OFF_CONST, head());
        }

        @Override
        public String touchDown(int x, int y) throws RemoteException {
            Parcel d = head();
            d.writeInt(x);
            d.writeInt(y);
            return str(T_DOWN_CONST, d);
        }

        @Override
        public String touchMove(int x, int y) throws RemoteException {
            Parcel d = head();
            d.writeInt(x);
            d.writeInt(y);
            return str(T_MOVE_CONST, d);
        }

        @Override
        public String touchUp(int x, int y) throws RemoteException {
            Parcel d = head();
            d.writeInt(x);
            d.writeInt(y);
            return str(T_UP_CONST, d);
        }


        private static final int T_PING_CONST = 1;
        private static final int T_CREATE_CONST = 2;
        private static final int T_DESTROY_CONST = 3;
        private static final int T_START_CONST = 4;
        private static final int T_CLICK_CONST = 5;
        private static final int T_LONGCLICK_CONST = 6;
        private static final int T_DRAG_CONST = 7;
        private static final int T_SWIPE_CONST = 8;
        private static final int T_KEY_CONST = 9;
        private static final int T_CAPTURE_CONST = 10;
        private static final int T_PREVIEW_CONST = 11;
        private static final int T_INFO_CONST = 12;
        private static final int T_SHELL_CONST = 13;
        private static final int T_ROTATE_CONST = 14;
        private static final int T_REGCLIENT_CONST = 15;
        private static final int T_LISTDISP_CONST = 16;
        private static final int T_KILLALL_CONST = 17;
        private static final int T_IME_ON_CONST = 18;
        private static final int T_IME_OFF_CONST = 19;
        private static final int T_DOWN_CONST = 20;
        private static final int T_MOVE_CONST = 21;
        private static final int T_UP_CONST = 22;
    }
}
