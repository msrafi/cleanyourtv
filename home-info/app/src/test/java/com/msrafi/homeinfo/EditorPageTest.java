package com.msrafi.homeinfo;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
