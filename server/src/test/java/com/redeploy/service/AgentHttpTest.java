package com.redeploy.service;

import com.redeploy.util.AgentPayloadCrypto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 用 JDK HttpServer 模拟支持 v1 加密协议的 agent。 */
class AgentHttpTest {

    private static final String TOKEN = "agent-token-123";

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastVersionHeader = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private AgentHttp newClient(boolean encryptionEnabled) {
        AgentHttp client = new AgentHttp();
        ReflectionTestUtils.setField(client, "encryptionEnabled", encryptionEnabled);
        return client;
    }

    private void handle(HttpExchange ex) throws IOException {
        byte[] key = AgentPayloadCrypto.deriveKey(TOKEN);
        String path = ex.getRequestURI().getPath();
        String version = ex.getRequestHeaders().getFirst(AgentPayloadCrypto.VERSION_HEADER);
        lastVersionHeader.set(version);
        byte[] aad = (ex.getRequestMethod() + " " + path).getBytes(StandardCharsets.UTF_8);

        byte[] reqBody = ex.getRequestBody().readAllBytes();
        if (version != null && reqBody.length > 0) {
            // 真 agent：AAD 不匹配会 400；测试正常路径用正确 AAD
            AgentPayloadCrypto.open(key, reqBody, aad);
        }

        if (path.equals("/api/blocked")) {
            byte[] html = "<html><body>您的请求已被拦截</body></html>".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html;charset=utf-8");
            ex.sendResponseHeaders(403, html.length);
            ex.getResponseBody().write(html);
            ex.close();
            return;
        }
        if (path.equals("/api/missing")) {
            respondPlain(ex, 404, "{\"error\":\"not found\"}");
            return;
        }

        byte[] resp = "{\"task_id\":\"t1\"}".getBytes(StandardCharsets.UTF_8);
        if (AgentPayloadCrypto.VERSION_V1.equals(version)) {
            byte[] useAad = path.equals("/api/bad-aad") ? "WRONG AAD".getBytes(StandardCharsets.UTF_8) : aad;
            byte[] sealed = AgentPayloadCrypto.seal(key, resp, useAad);
            ex.getResponseHeaders().set(AgentPayloadCrypto.VERSION_HEADER, AgentPayloadCrypto.VERSION_V1);
            ex.getResponseHeaders().set("Content-Type", "application/octet-stream");
            ex.sendResponseHeaders(200, sealed.length);
            ex.getResponseBody().write(sealed);
            ex.close();
        } else {
            respondPlain(ex, 200, new String(resp, StandardCharsets.UTF_8));
        }
    }

    private void respondPlain(HttpExchange ex, int status, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    @Test
    void encryptedPostJsonRoundtrip() {
        AgentHttp client = newClient(true);
        Map<String, Object> req = new HashMap<>();
        req.put("task_name", "demo");

        Map<?, ?> resp = client.postJson(baseUrl + "/api/task/execute", TOKEN, req, Map.class);

        assertEquals("t1", resp.get("task_id"));
        assertEquals("1", lastVersionHeader.get(), "request should carry v1 header");
    }

    @Test
    void plaintextFallbackWhenDisabled() {
        AgentHttp client = newClient(false);
        Map<?, ?> resp = client.postJson(baseUrl + "/api/task/execute", TOKEN, Map.of("a", "b"), Map.class);

        assertEquals("t1", resp.get("task_id"));
        assertNull(lastVersionHeader.get(), "no version header when encryption disabled");
    }

    @Test
    void notFoundThrownAsSubclass() {
        AgentHttp client = newClient(true);
        assertThrows(HttpClientErrorException.NotFound.class,
                () -> client.getJson(baseUrl + "/api/missing", TOKEN, Map.class));
    }

    @Test
    void wafHtmlBlockPageSurfacesInException() {
        AgentHttp client = newClient(true);
        HttpClientErrorException.Forbidden ex = assertThrows(HttpClientErrorException.Forbidden.class,
                () -> client.getText(baseUrl + "/api/blocked", TOKEN));
        assertTrue(ex.getResponseBodyAsString().contains("拦截"),
                "decrypted/plain error body must be preserved: " + ex.getResponseBodyAsString());
    }

    @Test
    void wrongAadResponseRejected() {
        AgentHttp client = newClient(true);
        assertThrows(IllegalArgumentException.class,
                () -> client.getText(baseUrl + "/api/bad-aad", TOKEN));
    }
}
