package com.redeploy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redeploy.util.AgentPayloadCrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * server → agent 的统一 HTTP 通道：JSON 收发、AES-GCM v1 载荷加密（开关
 * redeploy.agent.payload-encryption，默认开）、4xx/5xx 错误体先解密再以标准异常抛出。
 * chunk 分片端点不走这里（multipart/二进制流保持明文直发）。
 */
@Component
public class AgentHttp {

    private static final Logger log = LoggerFactory.getLogger(AgentHttp.class);

    @Value("${redeploy.agent.payload-encryption:true}")
    private boolean encryptionEnabled;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 派生密钥缓存：数量上限约等于历史 server 数，token 轮换后孤儿条目可接受
    private final Map<String, byte[]> keyCache = new ConcurrentHashMap<>();

    private final RestTemplate defaultClient = buildClient(0, 0);
    private final RestTemplate shortClient = buildClient(5000, 5000);

    public <T> T postJson(String url, String token, Object body, Class<T> respType) {
        return postJson(url, token, body, respType, false);
    }

    public <T> T postJson(String url, String token, Object body, Class<T> respType, boolean shortTimeout) {
        byte[] resp = exchange(url, token, HttpMethod.POST, toBytes(body), shortTimeout);
        return fromBytes(resp, respType);
    }

    public <T> T getJson(String url, String token, Class<T> respType) {
        return getJson(url, token, respType, false);
    }

    public <T> T getJson(String url, String token, Class<T> respType, boolean shortTimeout) {
        byte[] resp = exchange(url, token, HttpMethod.GET, null, shortTimeout);
        return fromBytes(resp, respType);
    }

    /** ndjson 等文本响应（UTF-8）。 */
    public String getText(String url, String token) {
        byte[] resp = exchange(url, token, HttpMethod.GET, null, false);
        return new String(resp, StandardCharsets.UTF_8);
    }

    /** 无请求体 POST（如 upload complete）；加密开启时仍带头并解密响应。 */
    public void postEmpty(String url, String token) {
        exchange(url, token, HttpMethod.POST, null, false);
    }

    private byte[] exchange(String url, String token, HttpMethod method, byte[] plainBody, boolean shortTimeout) {
        URI uri = URI.create(url);
        boolean hasBody = plainBody != null && plainBody.length > 0;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        byte[] requestBytes = null;
        if (encryptionEnabled) {
            // 无论有无请求体都带头：宣告客户端可解密加密响应
            headers.set(AgentPayloadCrypto.VERSION_HEADER, AgentPayloadCrypto.VERSION_V1);
            if (hasBody) {
                requestBytes = AgentPayloadCrypto.seal(key(token), plainBody,
                        AgentPayloadCrypto.aad(method.name(), uri));
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            }
        } else if (hasBody) {
            requestBytes = plainBody;
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        HttpEntity<byte[]> entity = new HttpEntity<>(requestBytes, headers);
        RestTemplate client = shortTimeout ? shortClient : defaultClient;
        ResponseEntity<byte[]> resp = client.exchange(uri, method, entity, byte[].class);

        byte[] respBytes = resp.getBody() == null ? new byte[0] : resp.getBody();
        boolean respEncrypted = AgentPayloadCrypto.VERSION_V1.equals(
                resp.getHeaders().getFirst(AgentPayloadCrypto.VERSION_HEADER));
        byte[] plain = respEncrypted
                ? AgentPayloadCrypto.open(key(token), respBytes, AgentPayloadCrypto.aad(method.name(), uri))
                : respBytes;

        int sc = resp.getStatusCode().value();
        if (sc >= 400) {
            // 无头 HTML 错误页 = WAF/中间盒拦截（agent 的错误永远是 JSON），留证并告警
            if (!respEncrypted && looksLikeBlockPage(plain, resp.getHeaders())) {
                log.warn("[AgentHttp] 疑似 WAF/中间盒拦截（非 agent JSON 响应）url={} status={} snippet={}",
                        url, sc, snippet(plain));
            }
            String statusText = sc < 600 && HttpStatus.resolve(sc) != null
                    ? HttpStatus.resolve(sc).getReasonPhrase() : "";
            if (sc >= 500) {
                throw HttpServerErrorException.create(resp.getStatusCode(), statusText,
                        resp.getHeaders(), plain, StandardCharsets.UTF_8);
            }
            throw HttpClientErrorException.create(resp.getStatusCode(), statusText,
                    resp.getHeaders(), plain, StandardCharsets.UTF_8);
        }
        return plain;
    }

    private byte[] key(String token) {
        return keyCache.computeIfAbsent(token, AgentPayloadCrypto::deriveKey);
    }

    private byte[] toBytes(Object body) {
        if (body == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new IllegalStateException("serialize agent request failed", e);
        }
    }

    private <T> T fromBytes(byte[] body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException e) {
            throw new IllegalStateException("parse agent response failed: " + snippet(body), e);
        }
    }

    private static boolean looksLikeBlockPage(byte[] body, HttpHeaders headers) {
        MediaType ct = headers.getContentType();
        if (ct != null && ct.includes(MediaType.TEXT_HTML)) {
            return true;
        }
        String head = new String(body, 0, Math.min(body.length, 512), StandardCharsets.UTF_8)
                .trim().toLowerCase();
        return head.startsWith("<!doctype") || head.startsWith("<html");
    }

    private static String snippet(byte[] body) {
        int n = Math.min(body.length, 200);
        return new String(body, 0, n, StandardCharsets.UTF_8).replaceAll("\\s+", " ");
    }

    private static RestTemplate buildClient(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        if (connectTimeoutMs > 0) {
            factory.setConnectTimeout(connectTimeoutMs);
        }
        if (readTimeoutMs > 0) {
            factory.setReadTimeout(readTimeoutMs);
        }
        RestTemplate rt = new RestTemplate(factory);
        // 只做 byte[] 收发，JSON 编解码由本组件负责，避免 Content-Type 协商问题
        rt.getMessageConverters().clear();
        rt.getMessageConverters().add(new org.springframework.http.converter.ByteArrayHttpMessageConverter());
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return rt;
    }
}
