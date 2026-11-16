package com.msrafi.homeinfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** One HTTPS GET. Blocks, so callers run it off the main thread. */
final class Net {
    private static final int TIMEOUT_MS = 8_000;
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private Net() { }

    static String get(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "HomeInfo/1.0");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("HTTP " + status);
            try (InputStream input = connection.getInputStream()) {
                return readLimited(input);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        for (int n; (n = input.read(buffer)) != -1; ) {
            total += n;
            if (total > MAX_BYTES) throw new IOException("response too large");
            output.write(buffer, 0, n);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
