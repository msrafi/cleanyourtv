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
    /** A phone photo, before it is scaled down to the screen. */
    static final int MAX_PICTURE = 8 * 1024 * 1024;
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
        /** A picture to store. Null unless the phone uploaded one. */
        final byte[] image;
        /** The phone asked to go back to the theme picture. */
        final boolean clearPicture;
        /** The reply is the stored picture. The server fills {@link #body}. */
        final boolean servePicture;

        private Result(int status, String type, byte[] body, String location, boolean write, String text,
                byte[] image, boolean clearPicture, boolean servePicture) {
            this.status = status;
            this.type = type;
            this.body = body;
            this.location = location;
            this.write = write;
            this.text = text;
            this.image = image;
            this.clearPicture = clearPicture;
            this.servePicture = servePicture;
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
        byte[] buf = new byte[8192];
        while (true) {
            byte[] soFar = raw.toByteArray();
            int end = indexOf(soFar, HEADER_END);
            if (end >= 0) {
                int length = contentLength(soFar, end);
                if (length == Integer.MIN_VALUE) return soFar;
                int cap = bodyCap(soFar, end);
                if (length < 0 || length > cap) throw new IOException("body");
                int need = end + HEADER_END.length + length;
                if (need > MAX_HEAD + cap) throw new IOException("body");
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
        return reply(request, current, remote, false);
    }

    static Result reply(byte[] request, String current, InetAddress remote, boolean pictureOn) {
        return reply(request, current, remote, pictureOn, "");
    }

    static Result reply(byte[] request, String current, InetAddress remote, boolean pictureOn, String pictureDetail) {
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
            if ("/wallpaper".equals(target)) {
                return pictureOn ? servePicture() : plain(404, "No picture.\n");
            }
            String notice = notice(target);
            if (notice == null) return plain(404, "Not found.\n");
            return html(page(current, notice, pictureOn, pictureDetail));
        }
        if (!"POST".equals(method)) return plain(405, "Use the form.\n");
        if ("/wallpaper".equals(target)) return wallpaper(request, end, lines);
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
        return new Result(200, "text/html; charset=utf-8", body, null, false, null, null, false, false);
    }

    private static Result seeOther(String text) {
        return new Result(303, null, new byte[0], "/?saved=1", true, text, null, false, false);
    }

    private static Result seePicture(byte[] image) {
        return new Result(303, null, new byte[0], "/?wallpaper=1", false, null, image, false, false);
    }

    private static Result seeClear() {
        return new Result(303, null, new byte[0], "/?wallpaper=0", false, null, null, true, false);
    }

    private static Result servePicture() {
        return new Result(200, "image/jpeg", new byte[0], null, false, null, null, false, true);
    }

    static Result jpeg(byte[] image) {
        return new Result(200, "image/jpeg", image, null, false, null, null, false, false);
    }

    static Result failed() {
        return plain(500, "Could not save.\n");
    }

    /** The picture was not a JPEG, PNG or WebP this TV can show. */
    static Result pictureRejected() {
        return new Result(303, null, new byte[0], "/?wallpaper=bad", false, null, null, false, false);
    }

    private static Result plain(int status, String text) {
        return new Result(status, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8),
                null, false, null, null, false, false);
    }

    /** Empty when the page is just open. saved, picture, theme or bad after a save. */
    private static String notice(String target) {
        if ("/".equals(target)) return "";
        if ("/?saved=1".equals(target)) return "saved";
        if ("/?wallpaper=1".equals(target)) return "picture";
        if ("/?wallpaper=0".equals(target)) return "theme";
        if ("/?wallpaper=bad".equals(target)) return "bad";
        return null;
    }

    private static Result wallpaper(byte[] request, int end, String[] lines) {
        int length = contentLength(request, end);
        if (length < 0 || length > MAX_PICTURE) return pictureRejected();
        int bodyAt = end + HEADER_END.length;
        if (request.length < bodyAt + length) return pictureRejected();
        String type = header(lines, "content-type");
        if (type != null && type.toLowerCase(Locale.US).startsWith("multipart/")) {
            byte[] body = new byte[length];
            System.arraycopy(request, bodyAt, body, 0, length);
            try {
                return seePicture(picture(body, type));
            } catch (IllegalArgumentException bad) {
                return pictureRejected();
            }
        }
        String form = new String(request, bodyAt, length, StandardCharsets.US_ASCII);
        return clearing(form) ? seeClear() : pictureRejected();
    }

    /** A picture upload may be larger than a reminder form. Anything else stays at {@link #MAX_BODY}. */
    private static int bodyCap(byte[] head, int end) {
        String first = new String(head, 0, end, StandardCharsets.ISO_8859_1);
        int line = first.indexOf("\r\n");
        String start = line < 0 ? first : first.substring(0, line);
        return start.startsWith("POST /wallpaper ") ? MAX_PICTURE : MAX_BODY;
    }

    private static byte[] page(String current, String notice, boolean pictureOn, String pictureDetail) {
        String[] lines = split(current);
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        html.append("<title>");
        html.append(title(notice));
        html.append("</title><style>");
        html.append("body{font:18px/1.4 sans-serif;margin:24px;background:#111;color:#fff;}");
        html.append("h2{font-size:22px;margin:32px 0 0;}");
        html.append("p{color:#bbb;}label{display:block;margin-top:14px;color:#bbb;font-size:14px;}");
        html.append("input{width:100%;box-sizing:border-box;font-size:18px;padding:12px;margin-top:6px;border:0;border-radius:8px;}");
        html.append("input[type=file]{background:#fff;color:#111;}");
        html.append("button{font-size:18px;font-weight:700;padding:14px 20px;margin-top:18px;width:100%;border:0;border-radius:10px;background:#fff;color:#111;}");
        html.append("button.quiet{background:#2a2a2a;color:#fff;}");
        html.append(".saved{background:#123524;border:3px solid #2BB673;border-radius:14px;padding:18px 16px;margin:16px 0 8px;}");
        html.append(".saved h2{margin:0 0 8px;font-size:28px;line-height:1.2;color:#7DDBA8;}");
        html.append(".bad{background:#3a1a1a;border:3px solid #e06a6a;border-radius:14px;padding:18px 16px;margin:16px 0 8px;}");
        html.append(".bad h2{margin:0;font-size:28px;line-height:1.2;color:#f0b4b4;}");
        html.append(".saved p,.saved li,.bad p{color:#fff;}");
        html.append(".saved p,.bad p{margin:0;font-size:17px;}");
        html.append(".saved ul{margin:12px 0 0;padding-left:20px;}");
        html.append(".saved li{margin:6px 0;font-size:18px;}");
        html.append(".spec{color:#fff;font-size:22px;margin:10px 0;}");
        html.append("b{color:#fff;}");
        html.append("img{width:100%;max-height:220px;object-fit:cover;border-radius:12px;margin-top:12px;background:#000;}");
        html.append("</style></head><body><h1>Reminders</h1>");
        html.append("<p>Up to five lines. A blank line is hidden on the TV.</p>");
        if ("saved".equals(notice)) html.append(savedBanner(lines));
        html.append("<form method=\"post\" action=\"/\">");
        for (int i = 0; i < MAX_LINES; i++) {
            String value = i < lines.length ? lines[i] : "";
            html.append("<label>Line ").append(i + 1);
            html.append("<input name=\"r").append(i).append("\" maxlength=\"").append(MAX_CHARS);
            html.append("\" autocomplete=\"off\" autocapitalize=\"off\" value=\"");
            html.append(escape(value)).append("\"></label>");
        }
        html.append("<button type=\"submit\">Save on the TV</button></form>");
        html.append(wallpaperSection(notice, pictureOn, pictureDetail));
        html.append("</body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String title(String notice) {
        if ("saved".equals(notice)) return "Saved on the TV";
        if ("picture".equals(notice)) return "Picture saved on the TV";
        if ("bad".equals(notice)) return "Picture not used";
        return "Reminders";
    }

    /** Width, height and file size of a stored JPEG, or empty when the size cannot be read. */
    static String pictureDetail(byte[] jpeg) {
        int[] size = jpegSize(jpeg);
        if (size == null) return "";
        int kb = Math.max(1, (jpeg.length + 512) / 1024);
        return size[0] + " × " + size[1] + ", " + kb + " KB";
    }

    private static int[] jpegSize(byte[] jpeg) {
        if (jpeg == null || jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) return null;
        int i = 2;
        while (i + 3 < jpeg.length) {
            if ((jpeg[i] & 0xFF) != 0xFF) return null;
            while (i < jpeg.length && (jpeg[i] & 0xFF) == 0xFF) i++;
            if (i >= jpeg.length) return null;
            int marker = jpeg[i++] & 0xFF;
            if (marker == 0xD9 || marker == 0xDA) return null;
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD8)) continue;
            if (i + 1 >= jpeg.length) return null;
            int length = ((jpeg[i] & 0xFF) << 8) | (jpeg[i + 1] & 0xFF);
            if (length < 2 || i + length > jpeg.length) return null;
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                if (length < 7) return null;
                int height = ((jpeg[i + 3] & 0xFF) << 8) | (jpeg[i + 4] & 0xFF);
                int width = ((jpeg[i + 5] & 0xFF) << 8) | (jpeg[i + 6] & 0xFF);
                if (width <= 0 || height <= 0) return null;
                return new int[] {width, height};
            }
            i += length;
        }
        return null;
    }

    private static String wallpaperSection(String notice, boolean pictureOn, String pictureDetail) {
        StringBuilder html = new StringBuilder();
        html.append("<h2>Wallpaper</h2>");
        html.append("<p class=\"spec\"><b>1920 × 1080</b> · under <b>300 KB</b></p>");
        html.append("<p>A dark JPEG, PNG or WebP, behind the clock. A larger picture is scaled down. Up to 8 MB. It stays until you put the theme picture back.</p>");
        if ("picture".equals(notice)) {
            html.append("<div class=\"saved\" role=\"status\"><h2>Picture saved on the TV</h2>");
            html.append("<p>Home is showing it behind the clock.</p></div>");
        } else if ("theme".equals(notice)) {
            html.append("<div class=\"saved\" role=\"status\"><h2>Theme picture restored</h2>");
            html.append("<p>Home is using its theme picture again.</p></div>");
        } else if ("bad".equals(notice)) {
            html.append("<div class=\"bad\" role=\"status\"><h2>That picture was not used</h2>");
            html.append("<p>Use 1920 × 1080, a JPEG, PNG or WebP under 8 MB.</p></div>");
        }
        if (pictureOn) {
            if (pictureDetail != null && !pictureDetail.isEmpty()) {
                html.append("<p>On the TV now: ").append(escape(pictureDetail)).append("</p>");
            }
            html.append("<img src=\"/wallpaper\" alt=\"The picture Home is showing\">");
            html.append("<form method=\"post\" action=\"/wallpaper\">");
            html.append("<button class=\"quiet\" type=\"submit\" name=\"clear\" value=\"1\">Use the theme picture</button></form>");
        }
        html.append("<form method=\"post\" action=\"/wallpaper\" enctype=\"multipart/form-data\">");
        html.append("<label>Picture<input type=\"file\" name=\"picture\" accept=\"image/jpeg,image/png,image/webp\" required></label>");
        html.append("<button type=\"submit\">Use this picture</button></form>");
        return html.toString();
    }

    /** The confirmation the phone sees after a save. It lists exactly what Home will show. */
    private static String savedBanner(String[] lines) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"saved\" role=\"status\">");
        html.append("<h2>Saved on the TV</h2>");
        if (lines.length == 0) {
            html.append("<p>Nothing is listed. Blank lines stay hidden.</p>");
        } else {
            html.append("<p>Home now shows these lines:</p><ul>");
            for (String line : lines) {
                html.append("<li>").append(escape(line)).append("</li>");
            }
            html.append("</ul>");
        }
        html.append("</div>");
        return html.toString();
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

    /** The uploaded picture. A missing part, or a file that is not a JPEG, PNG or WebP, is refused. */
    static byte[] picture(byte[] body, String contentType) {
        String boundary = boundaryOf(contentType);
        if (boundary == null || boundary.isEmpty() || boundary.length() > 120) {
            throw new IllegalArgumentException("boundary");
        }
        byte[] dash = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        int at = indexOf(body, dash, 0);
        while (at >= 0) {
            int part = at + dash.length;
            if (part + 1 < body.length && body[part] == '-' && body[part + 1] == '-') break;
            if (part + 1 < body.length && body[part] == '\r' && body[part + 1] == '\n') part += 2;
            int next = indexOf(body, dash, part);
            if (next < 0) throw new IllegalArgumentException("part");
            int headerEnd = indexOf(body, HEADER_END, part);
            if (headerEnd < 0 || headerEnd > next) throw new IllegalArgumentException("part");
            String headers = new String(body, part, headerEnd - part, StandardCharsets.ISO_8859_1);
            int dataAt = headerEnd + HEADER_END.length;
            int dataEnd = next - 2;
            if (namedPicture(headers) && dataEnd >= dataAt) {
                byte[] file = new byte[dataEnd - dataAt];
                System.arraycopy(body, dataAt, file, 0, file.length);
                if (file.length == 0 || !image(file)) throw new IllegalArgumentException("image");
                return file;
            }
            at = next;
        }
        throw new IllegalArgumentException("picture");
    }

    private static boolean namedPicture(String headers) {
        String lower = headers.toLowerCase(Locale.US);
        return lower.contains("name=\"picture\"") || lower.contains("name=picture");
    }

    static boolean image(byte[] file) {
        if (file.length >= 3 && (file[0] & 0xFF) == 0xFF && (file[1] & 0xFF) == 0xD8 && (file[2] & 0xFF) == 0xFF) {
            return true;
        }
        if (file.length >= 8 && (file[0] & 0xFF) == 0x89 && file[1] == 0x50 && file[2] == 0x4E && file[3] == 0x47) {
            return true;
        }
        return file.length >= 12 && file[0] == 'R' && file[1] == 'I' && file[2] == 'F' && file[3] == 'F'
                && file[8] == 'W' && file[9] == 'E' && file[10] == 'B' && file[11] == 'P';
    }

    private static boolean clearing(String form) {
        try {
            for (String part : form.split("&", -1)) {
                int eq = part.indexOf('=');
                String key = decode(eq < 0 ? part : part.substring(0, eq));
                String value = decode(eq < 0 ? "" : part.substring(eq + 1));
                if ("clear".equals(key) && "1".equals(value)) return true;
            }
        } catch (IllegalArgumentException bad) {
            return false;
        }
        return false;
    }

    private static String boundaryOf(String contentType) {
        if (contentType == null) return null;
        int at = contentType.toLowerCase(Locale.US).indexOf("boundary=");
        if (at < 0) return null;
        String value = contentType.substring(at + "boundary=".length()).trim();
        if (value.startsWith("\"")) {
            int end = value.indexOf('"', 1);
            return end < 0 ? null : value.substring(1, end);
        }
        int semi = value.indexOf(';');
        if (semi >= 0) value = value.substring(0, semi).trim();
        return value;
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
        // toString(Charset) is API 33. This TV is Android 11.
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
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
        if (value.isEmpty() || value.length() > 8) return -1;
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
        return indexOf(hay, needle, 0);
    }

    private static int indexOf(byte[] hay, byte[] needle, int from) {
        if (needle.length == 0 || from < 0 || hay.length < from + needle.length) return -1;
        for (int i = from; i <= hay.length - needle.length; i++) {
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
