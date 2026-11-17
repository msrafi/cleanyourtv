package com.msrafi.homeinfo;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/** Listens on {@link EditorPage#PORT} while this process is alive. One phone at a time. */
final class EditorServer {
    private static final String TAG = "HomeInfo";
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private EditorServer() { }

    static void start(Context ctx) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        if (!STARTED.compareAndSet(false, true)) return;
        Thread thread = new Thread(() -> loop(app), "home-info-page");
        thread.setDaemon(true);
        thread.start();
    }

    /** The address to type on a phone, or null when the TV has no home-network address yet. */
    static String address() {
        String ip = ipv4();
        return ip == null ? null : "http://" + ip + ":" + EditorPage.PORT;
    }

    private static void loop(Context app) {
        try (ServerSocket server = new ServerSocket()) {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(EditorPage.PORT), 8);
            Log.i(TAG, "reminders page " + address());
            while (!server.isClosed()) {
                Socket socket = server.accept();
                try {
                    exchange(app, socket);
                } catch (IOException ignored) {
                    // One phone closing early leaves the page up for the next one.
                } finally {
                    try {
                        socket.close();
                    } catch (IOException ignored) { }
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "reminders page stopped", e);
        } finally {
            STARTED.set(false);
        }
    }

    private static void exchange(Context app, Socket socket) throws IOException {
        socket.setSoTimeout(4000);
        EditorPage.Result result;
        try {
            byte[] request = EditorPage.readMessage(socket.getInputStream());
            result = EditorPage.reply(request, Reminders.text(app), socket.getInetAddress());
        } catch (IOException ignored) {
            result = EditorPage.reply(new byte[0], "", socket.getInetAddress());
        }
        if (result.write) Reminders.save(app, result.text);
        write(socket.getOutputStream(), result);
    }

    private static void write(OutputStream out, EditorPage.Result result) throws IOException {
        String reason = result.status == 200 ? "OK"
                : result.status == 303 ? "See Other"
                : result.status == 403 ? "Forbidden"
                : result.status == 404 ? "Not Found"
                : result.status == 405 ? "Method Not Allowed"
                : "Bad Request";
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(result.status).append(' ').append(reason).append("\r\n");
        if (result.location != null) head.append("Location: ").append(result.location).append("\r\n");
        if (result.type != null) head.append("Content-Type: ").append(result.type).append("\r\n");
        head.append("Cache-Control: no-store\r\n");
        head.append("Content-Length: ").append(result.body.length).append("\r\n");
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(result.body);
        out.flush();
    }

    private static String ipv4() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress address : Collections.list(nif.getInetAddresses())) {
                    if (address instanceof Inet4Address && EditorPage.allowed(address) && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }
}
