package com.msrafi.homeinfo;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class EditorPageTest {
    @Test
    public void aHomeAddressIsAcceptedAndAPublicOneIsNot() throws Exception {
        assertTrue(EditorPage.allowed(InetAddress.getByName("192.168.1.20")));
        assertTrue(EditorPage.allowed(InetAddress.getByName("10.1.2.3")));
        assertTrue(EditorPage.allowed(InetAddress.getByName("172.16.0.1")));
        assertTrue(EditorPage.allowed(InetAddress.getByName("127.0.0.1")));
        assertFalse(EditorPage.allowed(InetAddress.getByName("8.8.8.8")));
        assertFalse(EditorPage.allowed(InetAddress.getByName("172.15.255.255")));
        assertFalse(EditorPage.allowed(InetAddress.getByName("172.32.0.1")));
    }

    @Test
    public void theHostMustBeTheHomeAddressAndPort() {
        assertTrue(EditorPage.hostOk("192.168.1.20:8787"));
        assertTrue(EditorPage.hostOk("10.0.0.4"));
        assertFalse(EditorPage.hostOk("8.8.8.8:8787"));
        assertFalse(EditorPage.hostOk("192.168.1.20:80"));
        assertFalse(EditorPage.hostOk("tv.example:8787"));
        assertFalse(EditorPage.hostOk(null));
    }

    @Test
    public void anotherSiteCannotSave() {
        assertTrue(EditorPage.originOk(null, "192.168.1.20:8787"));
        assertTrue(EditorPage.originOk("http://192.168.1.20:8787", "192.168.1.20:8787"));
        assertFalse(EditorPage.originOk("https://evil.example", "192.168.1.20:8787"));
        assertFalse(EditorPage.originOk("null", "192.168.1.20:8787"));
    }

    @Test
    public void blanksAreDroppedAndTheSixthLineIsNotKept() {
        assertEquals("Nuha\nRafi", EditorPage.join(new String[] {"  Nuha  ", "", "Rafi", " "}));
        assertEquals("1\n2\n3\n4\n5", EditorPage.join(new String[] {"1", "2", "3", "4", "5", "6"}));
        StringBuilder longLine = new StringBuilder();
        for (int i = 0; i < 130; i++) longLine.append('x');
        assertEquals(120, EditorPage.join(new String[] {longLine.toString()}).length());
    }

    @Test
    public void aLineBreakInsideOneFieldStaysOneLine() {
        assertEquals("Nuha Meet Dr", EditorPage.join(EditorPage.form("r0=Nuha%0AMeet+Dr")));
    }

    @Test
    public void theFormEscapesATitleThatLooksLikeMarkup() {
        byte[] page = pageOf("GET / HTTP/1.1\r\nHost: 192.168.1.20:8787\r\n\r\n", "<script>");
        String html = new String(page, StandardCharsets.UTF_8);
        assertTrue(html.contains("value=\"&lt;script&gt;\""));
        assertFalse(html.contains("<script>"));
    }

    @Test
    public void aSavedPageSaysSoAndListsWhatHomeShows() {
        byte[] page = pageOf("GET /?saved=1 HTTP/1.1\r\nHost: 192.168.1.20:8787\r\n\r\n", "Nuha\nRafi");
        String html = new String(page, StandardCharsets.UTF_8);
        assertTrue(html.contains("Saved on the TV"));
        assertTrue(html.contains("<li>Nuha</li>"));
        assertTrue(html.contains("<li>Rafi</li>"));
        assertTrue(html.contains("<title>Saved on the TV</title>"));
    }

    @Test
    public void theOpenPageDoesNotClaimASave() {
        byte[] page = pageOf("GET / HTTP/1.1\r\nHost: 192.168.1.20:8787\r\n\r\n", "Nuha");
        String html = new String(page, StandardCharsets.UTF_8);
        assertFalse(html.contains("Saved on the TV"));
    }

    @Test
    public void thePageOffersAPictureFromThePhone() {
        String html = new String(pageOf("GET / HTTP/1.1\r\nHost: 192.168.1.20:8787\r\n\r\n", "Nuha"),
                StandardCharsets.UTF_8);
        assertTrue(html.contains("Use this picture"));
        assertTrue(html.contains("1920 × 1080"));
        assertTrue(html.contains("300 KB"));
        assertTrue(html.contains("accept=\"image/jpeg,image/png,image/webp\""));
        assertFalse(html.contains("Use the theme picture"));
    }

    @Test
    public void aPictureAlreadyOnHomeCanBeTakenOff() throws Exception {
        EditorPage.Result result = EditorPage.reply(
                "GET / HTTP/1.1\r\nHost: 192.168.1.20:8787\r\n\r\n".getBytes(StandardCharsets.US_ASCII),
                "", InetAddress.getByName("192.168.1.20"), true);
        String html = new String(result.body, StandardCharsets.UTF_8);
        assertTrue(html.contains("Use the theme picture"));
        assertTrue(html.contains("src=\"/wallpaper\""));
        EditorPage.Result sized = EditorPage.reply(
                "GET / HTTP/1.1\r\nHost: 192.168.1.20:8787\r\n\r\n".getBytes(StandardCharsets.US_ASCII),
                "", InetAddress.getByName("192.168.1.20"), true, "1920 × 1080, 240 KB");
        assertTrue(new String(sized.body, StandardCharsets.UTF_8).contains("On the TV now: 1920 × 1080, 240 KB"));
    }

    @Test
    public void aStoredJpegReportsItsSize() {
        byte[] jpeg = new byte[] {
                (byte) 0xFF, (byte) 0xD8,
                (byte) 0xFF, (byte) 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x04, 0x00, 0x08, 0x01, 0x01, 0x11, 0x00,
                (byte) 0xFF, (byte) 0xD9
        };
        assertEquals("8 × 4, 1 KB", EditorPage.pictureDetail(jpeg));
        assertEquals("", EditorPage.pictureDetail(new byte[] {1, 2, 3}));
    }

    @Test
    public void aJpegFromThePhoneIsKeptForHome() throws Exception {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x11};
        EditorPage.Result result = postWallpaper(jpeg, "image/jpeg", "a.jpg");
        assertEquals(303, result.status);
        assertEquals("/?wallpaper=1", result.location);
        assertArrayEquals(jpeg, result.image);
        assertFalse(result.write);
        assertFalse(result.clearPicture);
    }

    @Test
    public void aFileThatIsNotAPictureIsRefused() throws Exception {
        EditorPage.Result result = postWallpaper("hello".getBytes(StandardCharsets.US_ASCII), "text/plain", "a.txt");
        assertEquals(303, result.status);
        assertEquals("/?wallpaper=bad", result.location);
        assertNull(result.image);
    }

    @Test
    public void clearingPutsTheThemePictureBack() throws Exception {
        String body = "clear=1";
        String raw = "POST /wallpaper HTTP/1.1\r\nHost: 192.168.1.20:8787\r\n"
                + "Origin: http://192.168.1.20:8787\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\n"
                + "Content-Length: " + body.length() + "\r\n\r\n" + body;
        EditorPage.Result result = EditorPage.reply(raw.getBytes(StandardCharsets.US_ASCII),
                "", InetAddress.getByName("192.168.1.20"));
        assertEquals(303, result.status);
        assertEquals("/?wallpaper=0", result.location);
        assertTrue(result.clearPicture);
        assertNull(result.image);
    }

    @Test
    public void aWallpaperMayBeLargerThanAReminder() throws Exception {
        byte[] body = new byte[5000];
        body[0] = (byte) 0xFF;
        body[1] = (byte) 0xD8;
        body[2] = (byte) 0xFF;
        String head = "POST /wallpaper HTTP/1.1\r\nHost: 192.168.1.20:8787\r\nContent-Length: 5000\r\n\r\n";
        byte[] raw = join(head.getBytes(StandardCharsets.US_ASCII), body);
        assertEquals(raw.length, EditorPage.readMessage(new ByteArrayInputStream(raw)).length);
        String reminder = "POST / HTTP/1.1\r\nHost: 192.168.1.20:8787\r\nContent-Length: 5000\r\n\r\n";
        assertThrows(IOException.class, () -> EditorPage.readMessage(new ByteArrayInputStream(
                join(reminder.getBytes(StandardCharsets.US_ASCII), body))));
    }

    @Test
    public void aSavedPictureSaysSo() {
        byte[] page = pageOf("GET /?wallpaper=1 HTTP/1.1\r\nHost: 192.168.1.20:8787\r\n\r\n", "");
        String html = new String(page, StandardCharsets.UTF_8);
        assertTrue(html.contains("Picture saved on the TV"));
        assertTrue(html.contains("<title>Picture saved on the TV</title>"));
    }

    @Test
    public void aSaveFromTheHomeNetworkReplacesTheList() throws Exception {
        String body = "r0=Nuha+-+8%3A30&r1=&r2=Rafi";
        String raw = "POST / HTTP/1.1\r\nHost: 192.168.1.20:8787\r\n"
                + "Origin: http://192.168.1.20:8787\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\n"
                + "Content-Length: " + body.length() + "\r\n\r\n" + body;
        EditorPage.Result result = EditorPage.reply(raw.getBytes(StandardCharsets.US_ASCII),
                "old", InetAddress.getByName("192.168.1.20"));
        assertEquals(303, result.status);
        assertEquals("/?saved=1", result.location);
        assertTrue(result.write);
        assertEquals("Nuha - 8:30\nRafi", result.text);
    }

    @Test
    public void aPublicClientIsRefusedBeforeTheListChanges() throws Exception {
        String raw = "POST / HTTP/1.1\r\nHost: 192.168.1.20:8787\r\nContent-Length: 4\r\n\r\nr0=x";
        EditorPage.Result result = EditorPage.reply(raw.getBytes(StandardCharsets.US_ASCII),
                "keep", InetAddress.getByName("8.8.8.8"));
        assertEquals(403, result.status);
        assertFalse(result.write);
    }

    @Test
    public void aRenamedHostIsRefused() throws Exception {
        String raw = "GET / HTTP/1.1\r\nHost: tv.example:8787\r\n\r\n";
        EditorPage.Result result = EditorPage.reply(raw.getBytes(StandardCharsets.US_ASCII),
                "", InetAddress.getByName("192.168.1.20"));
        assertEquals(403, result.status);
    }

    @Test
    public void aBodyPastTheCapIsNotRead() {
        String head = "POST / HTTP/1.1\r\nHost: 192.168.1.20:8787\r\nContent-Length: 99999\r\n\r\n";
        assertThrows(IOException.class, () -> EditorPage.readMessage(new ByteArrayInputStream(
                (head + "xxxxxxxx").getBytes(StandardCharsets.US_ASCII))));
    }

    private static EditorPage.Result postWallpaper(byte[] file, String type, String name) throws Exception {
        String boundary = "----bound";
        byte[] body = join(join(
                ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"picture\"; filename=\""
                        + name + "\"\r\nContent-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII),
                file),
                ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        String head = "POST /wallpaper HTTP/1.1\r\nHost: 192.168.1.20:8787\r\n"
                + "Origin: http://192.168.1.20:8787\r\n"
                + "Content-Type: multipart/form-data; boundary=" + boundary + "\r\n"
                + "Content-Length: " + body.length + "\r\n\r\n";
        return EditorPage.reply(join(head.getBytes(StandardCharsets.US_ASCII), body),
                "", InetAddress.getByName("192.168.1.20"));
    }

    private static byte[] join(byte[] left, byte[] right) {
        byte[] out = new byte[left.length + right.length];
        System.arraycopy(left, 0, out, 0, left.length);
        System.arraycopy(right, 0, out, left.length, right.length);
        return out;
    }

    private static byte[] pageOf(String raw, String current) {
        try {
            EditorPage.Result result = EditorPage.reply(raw.getBytes(StandardCharsets.US_ASCII),
                    current, InetAddress.getByName("192.168.1.20"));
            assertEquals(200, result.status);
            return result.body;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
