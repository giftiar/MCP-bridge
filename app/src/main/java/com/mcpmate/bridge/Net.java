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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class Net {

    public static String wifiIp() {
        try {
            List<NetworkInterface> nis = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface ni : nis) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr.isLoopbackAddress() || !(addr instanceof Inet4Address)) {
                        continue;
                    }
                    String ip = addr.getHostAddress();
                    if (ip.startsWith("192.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                        return ip;
                    }
                }
            }
            for (NetworkInterface ni : nis) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    public static String urlLocal() {
        int p = Config.getPortLocal();
        if (p <= 0) {
            return "(已关闭)";
        }
        return "http://127.0.0.1:" + p + "/mcp";
    }

    public static String urlLan() {
        int p = Config.getPortLan();
        if (p <= 0 || !Config.isLan()) {
            return "(已关闭)";
        }
        String ip = wifiIp();
        if (ip.length() == 0) {
            return "(未连 WiFi)";
        }
        return "http://" + ip + ":" + p + "/mcp";
    }

    public static String addrText() {
        return "本机 " + urlLocal() + " | 局域网 " + urlLan();
    }
}
