package com.msrafi.homeinfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The reminders page. It answers only a numeric address on the home network, so a name that
 * points at the TV from the internet is refused, and a page on another site cannot save.
 */
final class EditorPage {
    static final int PORT = 8787;
    static final int MAX_LINES = 5;
    static final int MAX_CHARS = 120;
    static final int MAX_BODY = 4096;
    private static final int MAX_HEAD = 4096;
    private static final byte[] HEADER_END = new byte[] {'\r', '\n', '\r', '\n'};

    private EditorPage() { }

    static final class Result {
        final int status;
        final String type;
        final byte[] body;
        final String location;
        /** True when the phone saved a list. {@link #text} may be empty, which clears it. */
        final boolean write;
        final String text;

        private Result(int status, String type, byte[] body, String location, boolean write, String text) {
            this.status = status;
            this.type = type;
            this.body = body;
            this.location = location;
            this.write = write;
            this.text = text;
        }
    }

    static boolean allowed(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isMulticastAddress()) return false;
        if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) return true;
        byte[] raw = address.getAddress();
        // Unique-local IPv6 (fc00::/7). Java does not count that as site-local.
        return raw.length == 16 && (raw[0] & 0xFE) == 0xFC;
    }

    /** The list Home shows: trimmed lines, blanks dropped, five at most. */
    static String join(String[] lines) {
        StringBuilder out = new StringBuilder();
        int count = 0;
        if (lines == null) return "";
        for (String line : lines) {
            if (line == null || count == MAX_LINES) continue;
            String trimmed = line.replace('\r', ' ').replace('\n', ' ').trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.length() > MAX_CHARS) trimmed = trimmed.substring(0, MAX_CHARS).trim();
            if (trimmed.isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(trimmed);
            count++;
        }
        return out.toString();
    }

    static String[] split(String text) {
        if (text == null || text.isEmpty()) return new String[0];
        return text.split("\n", -1);
    }

    static byte[] readMessage(InputStream in) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        byte[] buf = new byte[512];
        while (true) {
            byte[] soFar = raw.toByteArray();
            int end = indexOf(soFar, HEADER_END);
            if (end >= 0) {
                int length = contentLength(soFar, end);
                if (length == Integer.MIN_VALUE) return soFar;
                if (length < 0 || length > MAX_BODY) throw new IOException("body");
                int need = end + HEADER_END.length + length;
                if (need > MAX_HEAD + MAX_BODY) throw new IOException("body");
                while (raw.size() < need) {
                    int n = in.read(buf, 0, Math.min(buf.length, need - raw.size()));
                    if (n < 0) return raw.toByteArray();
                    raw.write(buf, 0, n);
                }
                return copy(raw.toByteArray(), need);
            }
            if (raw.size() > MAX_HEAD) throw new IOException("head");
            int n = in.read(buf);
            if (n < 0) return raw.toByteArray();
            raw.write(buf, 0, n);
        }
    }

    static Result reply(byte[] request, String current, InetAddress remote) {
        if (!allowed(remote)) return plain(403, "This page is only for the home Wi-Fi.\n");
        if (request == null) return plain(400, "Bad request.\n");
        int end = indexOf(request, HEADER_END);
        if (end < 0) return plain(400, "Bad request.\n");
        String head = new String(request, 0, end, StandardCharsets.ISO_8859_1);
        String[] lines = head.split("\r\n", -1);
        if (lines.length == 0 || lines[0].isEmpty()) return plain(400, "Bad request.\n");
        String[] requestLine = lines[0].split(" ", 3);
        if (requestLine.length < 2) return plain(400, "Bad request.\n");
        String host = header(lines, "host");
        if (!hostOk(host)) return plain(403, "This page is only for the home Wi-Fi.\n");
        if (!originOk(header(lines, "origin"), host)) return plain(403, "This page is only for the home Wi-Fi.\n");
        String method = requestLine[0];
        String target = requestLine[1];
        if ("GET".equals(method)) {
            if (!"/".equals(target) && !"/?saved=1".equals(target)) return plain(404, "Not found.\n");
            return html(page(current, "/?saved=1".equals(target)));
        }
        if (!"POST".equals(method)) return plain(405, "Use the form.\n");
        if (!"/".equals(target)) return plain(404, "Not found.\n");
        int length = contentLength(request, end);
        if (length < 0 || length > MAX_BODY) return plain(400, "Bad request.\n");
        int bodyAt = end + HEADER_END.length;
        if (request.length < bodyAt + length) return plain(400, "Bad request.\n");
        String body = new String(request, bodyAt, length, StandardCharsets.US_ASCII);
        String[] fields;
        try {
            fields = form(body);
        } catch (IllegalArgumentException bad) {
            return plain(400, "Bad request.\n");
        }
        return seeOther(join(fields));
    }

    private static Result html(byte[] body) {
        return new Result(200, "text/html; charset=utf-8", body, null, false, null);
    }

    private static Result seeOther(String text) {
        return new Result(303, null, new byte[0], "/?saved=1", true, text);
    }

    private static Result plain(int status, String text) {
        return new Result(status, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8), null, false, null);
    }

    private static byte[] page(String current, boolean saved) {
        String[] lines = split(current);
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        html.append("<title>Reminders</title><style>");
        html.append("body{font:18px/1.4 sans-serif;margin:24px;background:#111;color:#fff;}");
        html.append("p{color:#bbb;}label{display:block;margin-top:14px;color:#bbb;font-size:14px;}");
        html.append("input{width:100%;box-sizing:border-box;font-size:18px;padding:12px;margin-top:6px;}");
        html.append("button{font-size:18px;padding:12px 20px;margin-top:18px;}");
        html.append("</style></head><body><h1>Reminders</h1>");
        html.append("<p>Up to five lines. A blank line is hidden on the TV.</p>");
        if (saved) html.append("<p>Saved. Home updates if it is on screen, or the next time you open it.</p>");
        html.append("<form method=\"post\" action=\"/\">");
        for (int i = 0; i < MAX_LINES; i++) {
            String value = i < lines.length ? lines[i] : "";
            html.append("<label>Line ").append(i + 1);
            html.append("<input name=\"r").append(i).append("\" maxlength=\"").append(MAX_CHARS);
            html.append("\" autocomplete=\"off\" autocapitalize=\"off\" value=\"");
            html.append(escape(value)).append("\"></label>");
        }
        html.append("<button type=\"submit\">Save on the TV</button></form></body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&': out.append("&amp;"); break;
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '"': out.append("&quot;"); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }

    /** Five fields, r0 through r4. A broken % sequence is refused. */
    static String[] form(String body) {
        String[] fields = new String[MAX_LINES];
        for (int i = 0; i < fields.length; i++) fields[i] = "";
        if (body == null || body.isEmpty()) return fields;
        for (String part : body.split("&", -1)) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            String key = decode(eq < 0 ? part : part.substring(0, eq));
            String value = decode(eq < 0 ? "" : part.substring(eq + 1));
            if (key.length() == 2 && key.charAt(0) == 'r') {
                int index = key.charAt(1) - '0';
                if (index >= 0 && index < MAX_LINES) fields[index] = value;
            }
        }
        return fields;
    }

    private static String decode(String raw) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '+') {
                bytes.write(' ');
            } else if (c == '%') {
                if (i + 2 >= raw.length()) throw new IllegalArgumentException("escape");
                int hi = hex(raw.charAt(i + 1));
                int lo = hex(raw.charAt(i + 2));
                if (hi < 0 || lo < 0) throw new IllegalArgumentException("escape");
                bytes.write((hi << 4) | lo);
                i += 2;
            } else if (c < 128) {
                bytes.write(c);
            } else {
                throw new IllegalArgumentException("char");
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    /** {@link Integer#MIN_VALUE} when the header is absent. -1 when it is not a plain length. */
    private static int contentLength(byte[] request, int headerEnd) {
        String head = new String(request, 0, headerEnd, StandardCharsets.ISO_8859_1);
        String value = header(head.split("\r\n", -1), "content-length");
        if (value == null) return Integer.MIN_VALUE;
        if (value.isEmpty() || value.length() > 6) return -1;
        int n = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return -1;
            n = n * 10 + (c - '0');
        }
        return n;
    }

    /** The first header of this name. A repeated header is treated as absent. */
    private static String header(String[] lines, String name) {
        String found = null;
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) continue;
            String key = lines[i].substring(0, colon).trim().toLowerCase(Locale.US);
            if (!name.equals(key)) continue;
            if (found != null) return null;
            found = lines[i].substring(colon + 1).trim();
        }
        return found;
    }

    static boolean hostOk(String host) {
        if (host == null || host.isEmpty()) return false;
        String ip = host;
        int colon = host.lastIndexOf(':');
        if (colon >= 0) {
            ip = host.substring(0, colon);
            if (!Integer.toString(PORT).equals(host.substring(colon + 1))) return false;
        }
        byte[] v4 = ipv4(ip);
        if (v4 == null) return false;
        try {
            return allowed(InetAddress.getByAddress(v4));
        } catch (java.net.UnknownHostException ignored) {
            return false;
        }
    }

    static boolean originOk(String origin, String host) {
        if (origin == null || origin.isEmpty()) return true;
        if ("null".equals(origin)) return false;
        return ("http://" + host).equalsIgnoreCase(origin);
    }

    private static byte[] ipv4(String text) {
        String[] parts = text.split("\\.", -1);
        if (parts.length != 4) return null;
        byte[] raw = new byte[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3) return null;
            int n = 0;
            for (int j = 0; j < parts[i].length(); j++) {
                char c = parts[i].charAt(j);
                if (c < '0' || c > '9') return null;
                n = n * 10 + (c - '0');
            }
            if (n > 255) return null;
            raw[i] = (byte) n;
        }
        return raw;
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        if (needle.length == 0 || hay.length < needle.length) return -1;
        for (int i = 0; i <= hay.length - needle.length; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    private static byte[] copy(byte[] raw, int length) {
        byte[] out = new byte[length];
        System.arraycopy(raw, 0, out, 0, length);
        return out;
    }
}
