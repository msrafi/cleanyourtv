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
import java.net.SocketTimeoutException;
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
                } catch (Throwable ignored) {
                    // One phone closing early, or one bad save, leaves the page up for the next one.
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
        // A phone often connects, then pauses before the request. Closing that pause used to
        // leave the browser with an empty reply.
        socket.setSoTimeout(60_000);
        byte[] request;
        try {
            request = EditorPage.readMessage(socket.getInputStream());
        } catch (SocketTimeoutException idle) {
            return;
        } catch (IOException ignored) {
            request = new byte[0];
        }
        if (request.length == 0) return;
        byte[] stored = Wallpaper.bytes(app);
        EditorPage.Result result = EditorPage.reply(request, Reminders.text(app), socket.getInetAddress(),
                stored != null, stored == null ? "" : EditorPage.pictureDetail(stored));
        try {
            if (result.write) Reminders.save(app, result.text);
            if (result.image != null) {
                if (!Wallpaper.store(app, result.image)) result = EditorPage.pictureRejected();
            } else if (result.clearPicture) {
                Wallpaper.clear(app);
            }
            if (result.servePicture) {
                byte[] jpeg = Wallpaper.bytes(app);
                result = jpeg == null ? EditorPage.pictureRejected() : EditorPage.jpeg(jpeg);
            }
        } catch (RuntimeException failed) {
            Log.w(TAG, "reminders page could not save", failed);
            result = result.image != null || result.clearPicture ? EditorPage.pictureRejected() : EditorPage.failed();
        }
        write(socket.getOutputStream(), result);
        Log.i(TAG, "reminders page " + result.status);
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
