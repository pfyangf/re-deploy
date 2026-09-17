package com.redeploy.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 跨语言金标向量（与 agent/internal/security/payload_test.go 共享同一组值）：
 * token=0123456789abcdef0123456789abcdef, nonce=12B 0x00,
 * AAD="POST /api/v1/ping", plaintext=`{"msg":"ping"}`。
 */
class AgentPayloadCryptoTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    private static final String WANT_KEY_HEX =
            "3eb1bd439947eb762998e566ccc2e099c791118b2f40579cc4f7da2b5061b7f9";
    private static final String WANT_WIRE_HEX =
            "00000000000000000000000059d36b3d97f92617bc388c2aeae19900f9096bf421f202105c4180997aba";
    private static final byte[] AAD = "POST /api/v1/ping".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PLAIN = "{\"msg\":\"ping\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void goldenVectorMatchesGo() {
        byte[] key = AgentPayloadCrypto.deriveKey(TOKEN);
        assertEquals(WANT_KEY_HEX, hex(key), "derived key diverges from Go golden vector");

        byte[] wire = AgentPayloadCrypto.sealWithNonce(key, new byte[12], PLAIN, AAD);
        assertEquals(WANT_WIRE_HEX, hex(wire), "sealed wire diverges from Go golden vector");
    }

    @Test
    void openGoldenVector() {
        byte[] plain = AgentPayloadCrypto.open(
                AgentPayloadCrypto.deriveKey(TOKEN), fromHex(WANT_WIRE_HEX), AAD);
        assertArrayEquals(PLAIN, plain);
    }

    @Test
    void roundtripWithRandomNonce() {
        byte[] key = AgentPayloadCrypto.deriveKey("some-agent-token");
        byte[] aad = "GET /api/task/abc/status".getBytes(StandardCharsets.UTF_8);
        byte[] plain = "{\"status\":\"running\"}".getBytes(StandardCharsets.UTF_8);

        byte[] wire = AgentPayloadCrypto.seal(key, plain, aad);
        assertArrayEquals(plain, AgentPayloadCrypto.open(key, wire, aad));
    }

    @Test
    void wrongKeyAndAadFail() {
        byte[] wire = AgentPayloadCrypto.seal(
                AgentPayloadCrypto.deriveKey(TOKEN), PLAIN, AAD);
        assertThrows(IllegalArgumentException.class,
                () -> AgentPayloadCrypto.open(AgentPayloadCrypto.deriveKey("other"), wire, AAD));
        assertThrows(IllegalArgumentException.class,
                () -> AgentPayloadCrypto.open(AgentPayloadCrypto.deriveKey(TOKEN), wire,
                        "GET /api/v1/ping".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shortWireRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentPayloadCrypto.open(AgentPayloadCrypto.deriveKey(TOKEN), new byte[27], AAD));
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xF, 16));
            sb.append(Character.forDigit(x & 0xF, 16));
        }
        return sb.toString();
    }

    private static byte[] fromHex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
