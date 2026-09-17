// Package security 提供 server 与 agent 通信的载荷加解密（v1：裸二进制 AES-256-GCM）。
// 线格式：nonce(12B 随机) || ciphertext || GCM tag(16B)。
// 密钥派生：SHA-256(UTF-8 字节(agent token))，两端均不对 hex token 做 hex-decode。
// AAD：请求行 "METHOD PATH"（不含 query/host），响应复用同一 AAD。
package security

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"errors"
	"io"
)

const (
	NonceSize  = 12
	TagSize    = 16
	MinWireLen = NonceSize + TagSize // 最短合法报文 = nonce + tag（空明文）
)

var ErrShortCiphertext = errors.New("ciphertext shorter than nonce+tag")

// DeriveKey 返回 SHA-256(UTF-8(token))，不做 hex 解码。
func DeriveKey(token string) []byte {
	sum := sha256.Sum256([]byte(token))
	return sum[:]
}

// Seal 生成随机 nonce 并加密，输出 nonce || ciphertext || tag。
func Seal(key, plaintext, aad []byte) ([]byte, error) {
	nonce := make([]byte, NonceSize)
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return nil, err
	}
	return SealWithNonce(key, nonce, plaintext, aad)
}

// SealWithNonce 使用显式 nonce 加密，仅用于跨语言金标测试；生产路径走 Seal。
func SealWithNonce(key, nonce, plaintext, aad []byte) ([]byte, error) {
	gcm, err := newGCM(key)
	if err != nil {
		return nil, err
	}
	if len(nonce) != NonceSize {
		return nil, errors.New("invalid nonce length")
	}
	ct := gcm.Seal(nil, nonce, plaintext, aad) // 输出 ciphertext || tag
	wire := make([]byte, 0, NonceSize+len(ct))
	wire = append(wire, nonce...)
	wire = append(wire, ct...)
	return wire, nil
}

// Open 校验长度后验签解密，返回明文。
func Open(key, wire, aad []byte) ([]byte, error) {
	if len(wire) < MinWireLen {
		return nil, ErrShortCiphertext
	}
	gcm, err := newGCM(key)
	if err != nil {
		return nil, err
	}
	return gcm.Open(nil, wire[:NonceSize], wire[NonceSize:], aad)
}

func newGCM(key []byte) (cipher.AEAD, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	return cipher.NewGCM(block)
}
