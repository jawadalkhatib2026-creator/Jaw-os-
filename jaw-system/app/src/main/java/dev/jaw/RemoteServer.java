package dev.jaw;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The phone remote: a small website served by the TV. Phones on the same Wi-Fi open it and sign in with a Jaw account. */
final class RemoteServer {
    private static final String JSON = "application/json; charset=utf-8";
    private static ServerSocket ss;
    private static volatile boolean running;
    private static Context ctx;
    static int port;
    private static final Map<String, String> sessions = new ConcurrentHashMap<>();
    private static final Map<String, long[]> attempts = new ConcurrentHashMap<>();
    private static final SecureRandom RNG = new SecureRandom();

    static synchronized void start(Context c) {
        if (running) return;
        ctx = c.getApplicationContext();
        ServerSocket s = null;
        for (int p = 8765; p < 8775; p++) {
            try { s = new ServerSocket(p); port = p; break; } catch (IOException e) { s = null; }
        }
        if (s == null) return;
        ss = s;
        running = true;
        final ServerSocket srv = s;
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    final Socket k = srv.accept();
                    new Thread(() -> handle(k)).start();
                } catch (IOException e) { break; }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    static synchronized void stop() {
        running = false;
        sessions.clear();
        try { if (ss != null) ss.close(); } catch (IOException e) { }
        ss = null;
    }

    static String url() {
        if (!running) return null;
        String ip = LocalServer.ip();
        return ip == null ? null : "http://" + ip + ":" + port;
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 4096) break;
        }
        if (c == -1 && sb.length() == 0) return null;
        return sb.toString();
    }

    private static void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(10000);
            InputStream in = new BufferedInputStream(sock.getInputStream());
            OutputStream out = sock.getOutputStream();
            String line = readLine(in);
            if (line == null) return;
            String[] parts = line.split(" ");
            if (parts.length < 2) return;
            String method = parts[0], target = parts[1], path = target, query = "";
            int q = target.indexOf('?');
            if (q >= 0) { path = target.substring(0, q); query = target.substring(q + 1); }
            int len = 0;
            String token = "", h;
            while ((h = readLine(in)) != null && !h.isEmpty()) {
                String lh = h.toLowerCase(Locale.US);
                if (lh.startsWith("content-length:")) len = Integer.parseInt(h.substring(15).trim());
                else if (lh.startsWith("x-token:")) token = h.substring(8).trim();
            }
            String body = "";
            if (len > 0 && len <= 4096) {
                byte[] b = new byte[len];
                int off = 0;
                while (off < len) {
                    int r = in.read(b, off, len - off);
                    if (r < 0) break;
                    off += r;
                }
                body = new String(b, 0, off, StandardCharsets.UTF_8);
            }
            route(out, method, path, query, token, body, sock.getInetAddress().getHostAddress());
        } catch (Exception e) { /* connection dropped */ }
    }

    private static void route(OutputStream out, String method, String path, String query,
                              String token, String body, String ip) throws IOException {
        if (path.equals("/") || path.equals("/index.html")) { page(out); return; }
        if (path.equals("/api/info")) {
            send(out, 200, JSON, "{\"name\":\"" + esc(Store.tvName(ctx)) + "\"}");
            return;
        }
        if (path.equals("/api/login") && method.equals("POST")) { login(out, body, ip); return; }
        if (token.isEmpty()) token = param(query, "t");
        if (!sessions.containsKey(token)) {
            send(out, 401, JSON, "{\"error\":\"unauthorized\"}");
            return;
        }
        switch (path) {
            case "/api/key":
                send(out, 200, JSON, "{\"result\":\"" + esc(RemoteBus.key(ctx, param(query, "k"))) + "\"}");
                break;
            case "/api/launch":
                send(out, 200, JSON, "{\"result\":\"" + esc(RemoteBus.launch(ctx, param(query, "pkg"))) + "\"}");
                break;
            case "/api/apps":
                send(out, 200, JSON, apps());
                break;
            case "/api/screen": {
                byte[] f = ScreenService.latest;
                if (f == null) send(out, 404, "text/plain", "off");
                else sendBytes(out, 200, "image/jpeg", f);
                break;
            }
            default:
                send(out, 404, "text/plain", "Not found");
        }
    }

    private static void login(OutputStream out, String body, String ip) throws IOException {
        long now = System.currentTimeMillis();
        long[] a = attempts.get(ip);
        if (a == null || now - a[1] > 60000) { a = new long[]{0, now}; attempts.put(ip, a); }
        if (a[0] >= 5) {
            send(out, 429, JSON, "{\"error\":\"Too many tries. Wait a minute. / \\u0645\\u062d\\u0627\\u0648\\u0644\\u0627\\u062a \\u0643\\u062b\\u064a\\u0631\\u0629\"}");
            return;
        }
        a[0]++;
        String email = "", pass = "";
        for (String kv : body.split("&")) {
            String[] p = kv.split("=", 2);
            if (p.length < 2) continue;
            try {
                String v = URLDecoder.decode(p[1], "UTF-8");
                if (p[0].equals("email")) email = v; else if (p[0].equals("pass")) pass = v;
            } catch (Exception e) { /* ignore bad field */ }
        }
        if (Accounts.count(ctx) == 0) {
            send(out, 403, JSON, "{\"error\":\"Create a Jaw account on the TV first. / \\u0623\\u0646\\u0634\\u0626 \\u062d\\u0633\\u0627\\u0628 \\u062c\\u0648 \\u0639\\u0644\\u0649 \\u0627\\u0644\\u062a\\u0644\\u0641\\u0627\\u0632 \\u0623\\u0648\\u0644\\u0627\\u064b\"}");
            return;
        }
        if (Accounts.verify(ctx, email, pass)) {
            byte[] r = new byte[16];
            RNG.nextBytes(r);
            StringBuilder sb = new StringBuilder();
            for (byte x : r) sb.append(String.format(Locale.US, "%02x", x));
            sessions.put(sb.toString(), email);
            a[0] = 0;
            send(out, 200, JSON, "{\"token\":\"" + sb + "\",\"email\":\"" + esc(email.trim().toLowerCase(Locale.US)) + "\"}");
        } else {
            send(out, 401, JSON, "{\"error\":\"Wrong email or password / \\u0627\\u0644\\u0628\\u064a\\u0627\\u0646\\u0627\\u062a \\u063a\\u064a\\u0631 \\u0635\\u062d\\u064a\\u062d\\u0629\"}");
        }
    }

    private static String apps() {
        PackageManager pm = ctx.getPackageManager();
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String pkg : Store.apps(ctx)) {
            Intent li = Store.launch(pm, pkg);
            if (li == null) continue;
            String name = pkg;
            try { name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); } catch (Exception e) { }
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"name\":\"").append(esc(name)).append("\",\"pkg\":\"").append(esc(pkg)).append("\"}");
        }
        return sb.append(']').toString();
    }

    private static void page(OutputStream out) throws IOException {
        try (InputStream in = ctx.getAssets().open("remote.html")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) bo.write(buf, 0, r);
            sendBytes(out, 200, "text/html; charset=utf-8", bo.toByteArray());
        }
    }

    private static String param(String query, String name) {
        for (String kv : query.split("&")) {
            String[] p = kv.split("=", 2);
            if (p.length == 2 && p[0].equals(name)) {
                try { return URLDecoder.decode(p[1], "UTF-8"); } catch (Exception e) { return p[1]; }
            }
        }
        return "";
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 32) sb.append(' ');
            else sb.append(c);
        }
        return sb.toString();
    }

    private static void send(OutputStream out, int code, String mime, String body) throws IOException {
        sendBytes(out, code, mime, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendBytes(OutputStream out, int code, String mime, byte[] b) throws IOException {
        String head = "HTTP/1.1 " + code + " X\r\nContent-Type: " + mime + "\r\nContent-Length: " + b.length
            + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(b);
        out.flush();
    }
}
