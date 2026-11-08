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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HttpSrv {

    private static final List<HttpSrv> SERVERS = new ArrayList<HttpSrv>();
    private static Context appCtx;

    private final int port;
    private final boolean lan;
    private volatile boolean running;
    private volatile int actualPort = -1;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public HttpSrv(int port, boolean lan) {
        this.port = port;
        this.lan = lan;
    }

    public static synchronized void startAll(Context ctx) {
        appCtx = ctx.getApplicationContext();
        stopAll();

        int localPort = Config.getPortLocal();
        int lanPort = Config.getPortLan();
        boolean allowLan = Config.isLan();

        if (localPort > 0) {
            bind(localPort, false);
        }
        if (allowLan && lanPort > 0) {
            if (lanPort == localPort) {
                Log.add("局域网端口与本机端口相同，局域网监听已跳过；"
                        + "请到设置页把两个端口改成不同值");
            } else {
                bind(lanPort, true);
            }
        }
    }

    private static void bind(int port, boolean lan) {
        try {
            HttpSrv s = new HttpSrv(port, lan);
            s.start();
            synchronized (SERVERS) {
                SERVERS.add(s);
            }
            Log.add((lan ? "局域网" : "本机") + "服务已启动 :" + s.actualPort + "/mcp");
            final HttpSrv inst = s;
            Thread th = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.add("自检: " + inst.selfCheck());
                    } catch (Throwable ignored) {
                    }
                }
            }, "mcp-selfcheck");
            th.setDaemon(true);
            th.start();
        } catch (Throwable t) {
            Log.add("端口 " + port + " 启动失败: " + t);
        }
    }

    public static synchronized void stopAll() {
        List<HttpSrv> copy;
        synchronized (SERVERS) {
            copy = new ArrayList<HttpSrv>(SERVERS);
            SERVERS.clear();
        }
        for (HttpSrv s : copy) {
            s.stop();
        }
    }

    public static synchronized boolean isRunning() {
        synchronized (SERVERS) {
            for (HttpSrv s : SERVERS) {
                if (s.running) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int actualLocalPort() {
        List<HttpSrv> copy;
        synchronized (SERVERS) {
            copy = new ArrayList<HttpSrv>(SERVERS);
        }
        for (HttpSrv s : copy) {
            if (s.running && !s.lan && s.actualPort > 0) {
                return s.actualPort;
            }
        }
        return -1;
    }

    public static synchronized int runningCount() {
        int n = 0;
        synchronized (SERVERS) {
            for (HttpSrv s : SERVERS) {
                if (s.running) {
                    n++;
                }
            }
        }
        return n;
    }


    private void start() throws IOException {
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        if (lan) {
            ss.bind(new InetSocketAddress(port));
        } else {
            ss.bind(new InetSocketAddress("127.0.0.1", port));
        }
        serverSocket = ss;
        actualPort = ss.getLocalPort();
        running = true;

        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        }, "mcp-accept-" + port);
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public static String testLocalBlocking() {
        int p = actualLocalPort();
        if (p <= 0) {
            return "未监听（服务器没起来）";
        }
        return probe(p, "127.0.0.1");
    }

    public static String testLanBlocking() {
        int p = Config.getPortLan();
        if (p <= 0) {
            return "局域网：端口未设置";
        }
        String ip = Net.wifiIp();
        if (ip.length() == 0) {
            return "局域网：没连上 Wi-Fi，拿不到本机地址";
        }
        if (p == Config.getPortLocal()) {
            return "局域网：端口与本机相同，监听被跳过";
        }
        String r = probe(p, ip);
        return "局域网 " + ip + ":" + p + "\n" + r;
    }

    private static String probe(int p, String host) {
        Socket s = null;
        try {
            s = new Socket();
            s.connect(new InetSocketAddress(host, p), 2000);
            s.setSoTimeout(2500);

            String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
            String token = Config.getToken();
            StringBuilder req = new StringBuilder();
            req.append("POST /mcp HTTP/1.1\r\n");
            req.append("Host: ").append(host).append(':').append(p).append("\r\n");
            req.append("Content-Type: application/json\r\n");
            req.append("Content-Length: ").append(body.length()).append("\r\n");
            if (token.length() > 0) {
                req.append("Authorization: Bearer ").append(token).append("\r\n");
            }
            req.append("Connection: close\r\n\r\n").append(body);

            java.io.OutputStream out = s.getOutputStream();
            out.write(req.toString().getBytes("UTF-8"));
            out.flush();

            java.io.InputStream in = s.getInputStream();
            byte[] buf = new byte[256];
            int n = in.read(buf);
            if (n <= 0) {
                return "端口已监听但无响应";
            }
            String head = new String(buf, 0, n, "ISO-8859-1");
            int sp = head.indexOf(' ');
            String code = sp > 0 && head.length() > sp + 3
                    ? head.substring(sp + 1, sp + 4) : "?";
            if ("200".equals(code) || "204".equals(code)) {
                return "MCP 响应正常";
            }
            if ("401".equals(code)) {
                return "端口通，但令牌被拒（401）\n检查设置页令牌是否与客户端一致";
            }
            if ("404".equals(code)) {
                return "端口被别的程序占用（404）\n换个端口试试";
            }
            return "端口有响应但状态异常 HTTP " + code;
        } catch (Throwable t) {
            return "连接失败 -> " + t.getClass().getSimpleName();
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public String selfCheck() {
        if (!running || actualPort <= 0) {
            return "未监听";
        }
        StringBuilder b = new StringBuilder();
        b.append("port=").append(actualPort);
        try {
            ServerSocket ss = serverSocket;
            b.append(" bound=").append(ss != null && ss.isBound());
            b.append(" closed=").append(ss == null || ss.isClosed());
        } catch (Throwable ignored) {
        }
        return b.toString();
    }

    private void stop() {
        running = false;
        actualPort = -1;
        ServerSocket ss = serverSocket;
        serverSocket = null;
        if (ss != null) {
            try {
                ss.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void acceptLoop() {
        while (running) {
            ServerSocket ss = serverSocket;
            if (ss == null) {
                running = false;
                break;
            }
            Socket socket;
            try {
                socket = ss.accept();
            } catch (Throwable e) {
                if (!running || serverSocket == null) {
                    break;
                }
                try {
                    Thread.sleep(50L);
                } catch (Throwable ignored) {
                }
                continue;
            }
            final Socket s = socket;
            try {
                Log.add("收到连接 " + s.getInetAddress() + ":" + s.getPort());
            } catch (Throwable ignored) {
            }
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    handle(s);
                }
            }, "mcp-conn");
            t.setDaemon(true);
            t.start();
        }
    }

    private void handle(Socket socket) {
        try {
            socket.setSoTimeout(60000);
            InputStream in = socket.getInputStream();
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());

            String requestLine = readLine(in);
            if (requestLine == null || requestLine.length() == 0) {
                return;
            }
            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0].toUpperCase(Locale.US) : "GET";
            String target = parts.length > 1 ? parts[1] : "/";
            String path = target;
            int qi = target.indexOf('?');
            if (qi >= 0) {
                path = target.substring(0, qi);
            }

            Map<String, String> headers = new HashMap<String, String>();
            String line;
            while ((line = readLine(in)) != null && line.length() > 0) {
                int idx = line.indexOf(':');
                if (idx > 0) {
                    headers.put(line.substring(0, idx).trim().toLowerCase(Locale.US),
                            line.substring(idx + 1).trim());
                }
            }

            String body = "";
            String cl = headers.get("content-length");
            if (cl != null) {
                try {
                    int len = Integer.parseInt(cl.trim());
                    if (len > 0) {
                        byte[] buf = new byte[Math.min(len, 8 * 1024 * 1024)];
                        int read = 0;
                        while (read < buf.length) {
                            int n = in.read(buf, read, buf.length - read);
                            if (n < 0) {
                                break;
                            }
                            read += n;
                        }
                        body = new String(buf, 0, read, "UTF-8");
                    }
                } catch (Throwable ignored) {
                }
            }

            Log.add("请求 " + method + " " + path);

            if ("OPTIONS".equals(method)) {
                sendEmpty(out, 204);
                return;
            }

            if (!"/mcp".equals(path)) {
                if ("/".equals(path) || path.length() == 0) {
                    sendText(out, 200, "MCP桥运行中\n端点: POST /mcp\n"
                            + "无障碍: " + (Ui.ready() ? "已连接" : "未连接"));
                    return;
                }
                sendText(out, 404, "not found");
                return;
            }

            String token = Config.getToken();
            if (token.length() > 0) {
                String auth = headers.get("authorization");
                if (auth == null) {
                    auth = headers.get("x-mcp-token");
                }
                if (!authOk(auth, token)) {
                    Log.add("鉴权失败");
                    sendJson(out, 401, "{\"error\":\"unauthorized\"}");
                    return;
                }
            }

            if ("GET".equals(method)) {
                String accept = headers.get("accept");
                if (accept != null && accept.contains("text/event-stream")) {
                    sendSse(out, "{\"jsonrpc\":\"2.0\",\"method\":\"ping\"}");
                    return;
                }
                sendJson(out, 200, "{\"status\":\"ok\",\"endpoint\":\"/mcp\"}");
                return;
            }

            if (!"POST".equals(method)) {
                sendText(out, 405, "method not allowed");
                return;
            }

            String resp = Mcp.handle(appCtx, body);
            if (resp == null) {
                sendEmpty(out, 202);
                return;
            }

            String accept = headers.get("accept");
            if (accept != null && accept.contains("text/event-stream")) {
                sendSse(out, resp);
            } else {
                sendJson(out, 200, resp);
            }
        } catch (Throwable t) {
            Log.add("连接异常: " + t);
        } finally {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                buf.write(c);
            }
        }
        if (c == -1 && buf.size() == 0) {
            return null;
        }
        return new String(buf.toByteArray(), "ISO-8859-1");
    }

    private static boolean authOk(String auth, String token) {
        if (auth == null) {
            return false;
        }
        String v = auth.trim();
        if (v.length() > 7 && v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            v = v.substring(7).trim();
        }
        if (v.length() != token.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < v.length(); i++) {
            diff |= v.charAt(i) ^ token.charAt(i);
        }
        return diff == 0;
    }

    private static void head(OutputStream out, int code, String type, int len) throws IOException {
        String h = "HTTP/1.1 " + code + " " + status(code) + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + len + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Headers: *\r\n"
                + "Access-Control-Allow-Methods: POST, GET, OPTIONS\r\n"
                + "Connection: close\r\n\r\n";
        out.write(h.getBytes("ISO-8859-1"));
    }

    private static void sendJson(OutputStream out, int code, String json) throws IOException {
        byte[] b = json.getBytes("UTF-8");
        head(out, code, "application/json; charset=utf-8", b.length);
        out.write(b);
        out.flush();
    }

    private static void sendText(OutputStream out, int code, String text) throws IOException {
        byte[] b = text.getBytes("UTF-8");
        head(out, code, "text/plain; charset=utf-8", b.length);
        out.write(b);
        out.flush();
    }

    private static void sendEmpty(OutputStream out, int code) throws IOException {
        head(out, code, "text/plain; charset=utf-8", 0);
        out.flush();
    }

    private static void sendSse(OutputStream out, String json) throws IOException {
        StringBuilder b = new StringBuilder();
        b.append("event: message\n");
        b.append("data: ").append(json).append("\n\n");
        byte[] bytes = b.toString().getBytes("UTF-8");
        String h = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/event-stream; charset=utf-8\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(h.getBytes("ISO-8859-1"));
        out.write(bytes);
        out.flush();
    }

    private static String status(int code) {
        if (code == 200) {
            return "OK";
        }
        if (code == 202) {
            return "Accepted";
        }
        if (code == 204) {
            return "No Content";
        }
        if (code == 401) {
            return "Unauthorized";
        }
        if (code == 404) {
            return "Not Found";
        }
        if (code == 405) {
            return "Method Not Allowed";
        }
        return "OK";
    }
}
