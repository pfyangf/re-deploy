package com.redeploy.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * server ↔ agent 载荷加解密 v1（AES-256-GCM，裸二进制）。
 * 线格式：nonce(12B) || ciphertext || tag(16B)；密钥 = SHA-256(UTF-8(token))，不做 hex-decode。
 * AAD = "METHOD PATH"，与 agent/internal/security/payload.go 严格对应，跨语言金标向量见两侧测试。
 */
public final class AgentPayloadCrypto {

    public static final String VERSION_HEADER = "X-Payload-Version";
    public static final String VERSION_V1 = "1";

    static final int NONCE_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final int MIN_WIRE_LEN = NONCE_LEN + 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private AgentPayloadCrypto() {
    }

    public static byte[] deriveKey(String token) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** AAD = METHOD + " " + PATH（不含 query/host）。 */
    public static byte[] aad(String method, URI uri) {
        return (method + " " + uri.getPath()).getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] seal(byte[] key, byte[] plaintext, byte[] aad) {
        byte[] nonce = new byte[NONCE_LEN];
        RANDOM.nextBytes(nonce);
        return sealWithNonce(key, nonce, plaintext, aad);
    }

    /** 固定 nonce 版本，仅用于跨语言金标测试。 */
    public static byte[] sealWithNonce(byte[] key, byte[] nonce, byte[] plaintext, byte[] aad) {
        if (nonce.length != NONCE_LEN) {
            throw new IllegalArgumentException("nonce must be 12 bytes");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            byte[] ctAndTag = cipher.doFinal(plaintext);
            byte[] out = new byte[NONCE_LEN + ctAndTag.length];
            System.arraycopy(nonce, 0, out, 0, NONCE_LEN);
            System.arraycopy(ctAndTag, 0, out, NONCE_LEN, ctAndTag.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("payload seal failed", e);
        }
    }

    public static byte[] open(byte[] key, byte[] wire, byte[] aad) {
        if (wire == null || wire.length < MIN_WIRE_LEN) {
            throw new IllegalArgumentException("invalid encrypted payload");
        }
        byte[] nonce = Arrays.copyOfRange(wire, 0, NONCE_LEN);
        byte[] ctAndTag = Arrays.copyOfRange(wire, NONCE_LEN, wire.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            return cipher.doFinal(ctAndTag);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid encrypted payload", e);
        }
    }
}
