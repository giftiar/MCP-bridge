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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

public class Down {

    public static final String DIR = "mcp-downloads";
    public static final String FULL_PATH = "/storage/emulated/0/Download/" + DIR + "/";

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final long MAX_SIZE = 1024L * 1024L * 1024L;

    public static String download(Context ctx, String url, String filename) {
        if (url == null || url.length() == 0) {
            return "ERROR: url 不能为空";
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "*/*");
            conn.connect();

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return "ERROR: 服务器返回 HTTP " + code + " " + url;
            }

            if (filename == null || filename.trim().length() == 0) {
                filename = guessName(conn, u);
            }
            filename = sanitize(filename);

            String mime = conn.getContentType();
            if (mime == null || mime.length() == 0) {
                mime = "application/octet-stream";
            }
            int semi = mime.indexOf(';');
            if (semi > 0) {
                mime = mime.substring(0, semi).trim();
            }

            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + DIR);

            Uri uri = ctx.getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) {
                return "ERROR: 无法在 Download/" + DIR + " 创建文件";
            }

            InputStream in = conn.getInputStream();
            OutputStream os = ctx.getContentResolver().openOutputStream(uri);
            if (os == null) {
                return "ERROR: 无法写入文件";
            }

            byte[] buf = new byte[16384];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                os.write(buf, 0, n);
                total += n;
                if (total > MAX_SIZE) {
                    break;
                }
            }
            os.flush();
            try {
                os.close();
            } catch (Throwable ignored) {
            }
            try {
                in.close();
            } catch (Throwable ignored) {
            }

            return "ok\neffect: changed\nfile: " + filename
                    + "\nsize: " + size(total)
                    + "\npath: " + FULL_PATH + filename;
        } catch (Throwable t) {
            return "ERROR: 下载失败 " + t;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static boolean allFilesOk() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                return android.os.Environment.isExternalStorageManager();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static String list(Context ctx) {
        Map<String, Long> files = new LinkedHashMap<String, Long>();
        String fileErr = null;
        boolean dirScanned = false;

        try {
            File dir = new File(FULL_PATH);
            File[] fs = dir.listFiles();
            if (fs != null) {
                dirScanned = true;
                for (int i = 0; i < fs.length; i++) {
                    if (fs[i].isFile()) {
                        files.put(fs[i].getName(), Long.valueOf(fs[i].length()));
                    }
                }
                if (files.isEmpty() && !allFilesOk()) {
                    fileErr = "缺少「所有文件访问权限」，读不到目录内容"
                            + "（下面只列出系统媒体库已知的文件，可能不全）";
                }
            } else {
                fileErr = allFilesOk()
                        ? "无法列目录，目录可能不存在"
                        : "缺少「所有文件访问权限」，无法直接读目录"
                        + "（下面只列出系统媒体库已知的文件，可能不全）";
            }
        } catch (Throwable t) {
            fileErr = String.valueOf(t);
        }

        Cursor c = null;
        try {
            Uri uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String[] proj = new String[]{
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.RELATIVE_PATH};
            String sel = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
            String[] args = new String[]{"%" + DIR + "%"};
            c = ctx.getContentResolver().query(uri, proj, sel, args, null);
            if (c != null) {
                while (c.moveToNext()) {
                    String name = c.getString(0);
                    if (name == null || files.containsKey(name)) {
                        continue;
                    }
                    files.put(name, Long.valueOf(c.getLong(1)));
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable ignored) {
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("dir: ").append(FULL_PATH).append('\n');
        sb.append("count: ").append(files.size()).append('\n');
        sb.append("direct_scan: ").append(dirScanned ? "ok" : "failed").append('\n');
        if (fileErr != null) {
            sb.append("warn: ").append(fileErr).append('\n');
            sb.append("fix: 设置 → 高级设置 → 授予所有文件访问权限\n");
        }
        for (Map.Entry<String, Long> e : files.entrySet()) {
            sb.append(e.getKey()).append("  (")
                    .append(size(e.getValue().longValue())).append(")\n");
        }
        if (files.size() == 0) {
            sb.append("(暂无文件)\n");
        }
        return sb.toString();
    }

    private static String guessName(HttpURLConnection conn, URL url) {
        try {
            String cd = conn.getHeaderField("Content-Disposition");
            if (cd != null) {
                int i = cd.toLowerCase().indexOf("filename=");
                if (i >= 0) {
                    String v = cd.substring(i + 9).trim();
                    if (v.startsWith("\"") && v.length() > 1) {
                        v = v.substring(1);
                        int e = v.indexOf('"');
                        if (e >= 0) {
                            v = v.substring(0, e);
                        }
                    }
                    if (v.length() > 0) {
                        return v;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        String path = url.getPath();
        if (path != null) {
            int s = path.lastIndexOf('/');
            if (s >= 0 && s + 1 < path.length()) {
                String v = path.substring(s + 1);
                if (v.length() > 0) {
                    return v;
                }
            }
        }
        return "download_" + System.currentTimeMillis();
    }

    public static String sanitize(String name) {
        if (name == null) {
            return "file";
        }
        name = name.trim();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch < 32 || "\\/:*?\"<>|".indexOf(ch) >= 0) {
                b.append('_');
            } else {
                b.append(ch);
            }
        }
        String r = b.toString().trim();
        if (r.length() > 120) {
            r = r.substring(0, 120);
        }
        if (r.length() == 0 || r.equals(".") || r.equals("..")) {
            return "file";
        }
        return r;
    }

    private static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return (bytes / 1024) + " KB";
        }
        return (bytes / (1024L * 1024L)) + " MB";
    }
}
