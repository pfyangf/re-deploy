package security

import (
	"bytes"
	"encoding/hex"
	"testing"
)

// 跨语言金标参数（与 server 端 AgentPayloadCryptoTest 共享）：
// token=0123456789abcdef0123456789abcdef, nonce=12B 0x00,
// AAD="POST /api/v1/ping", plaintext=`{"msg":"ping"}`
const (
	goldenToken = "0123456789abcdef0123456789abcdef"
	goldenAAD   = "POST /api/v1/ping"
	goldenPlain = `{"msg":"ping"}`
)

func goldenNonce() []byte { return make([]byte, NonceSize) }

func TestRoundtrip(t *testing.T) {
	key := DeriveKey("test-token-abc")
	plain := []byte(`{"task_name":"x","steps":[{"command":"whoami && rm -rf /tmp/x"}]}`)
	aad := []byte("POST /api/task/execute")

	wire, err := Seal(key, plain, aad)
	if err != nil {
		t.Fatalf("Seal: %v", err)
	}
	if len(wire) != MinWireLen+len(plain) {
		t.Fatalf("wire len = %d, want %d", len(wire), MinWireLen+len(plain))
	}
	got, err := Open(key, wire, aad)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	if !bytes.Equal(got, plain) {
		t.Fatalf("plaintext mismatch")
	}
}

func TestEmptyPlaintext(t *testing.T) {
	key := DeriveKey(goldenToken)
	wire, err := SealWithNonce(key, goldenNonce(), nil, []byte(goldenAAD))
	if err != nil {
		t.Fatalf("Seal: %v", err)
	}
	if len(wire) != MinWireLen {
		t.Fatalf("empty wire len = %d, want %d", len(wire), MinWireLen)
	}
	got, err := Open(key, wire, []byte(goldenAAD))
	if err != nil {
		t.Fatalf("Open empty: %v", err)
	}
	if len(got) != 0 {
		t.Fatalf("expected empty plaintext, got %q", got)
	}
}

func TestWrongKeyAndTamper(t *testing.T) {
	key := DeriveKey(goldenToken)
	wire, _ := SealWithNonce(key, goldenNonce(), []byte(goldenPlain), []byte(goldenAAD))

	if _, err := Open(DeriveKey("other-token"), wire, []byte(goldenAAD)); err == nil {
		t.Fatal("expected auth failure with wrong key")
	}
	if _, err := Open(key, wire, []byte("GET /api/v1/ping")); err == nil {
		t.Fatal("expected auth failure with wrong AAD")
	}
	tampered := bytes.Clone(wire)
	tampered[len(wire)-1] ^= 0xff
	if _, err := Open(key, tampered, []byte(goldenAAD)); err == nil {
		t.Fatal("expected auth failure with tampered tag")
	}
}

func TestShortWire(t *testing.T) {
	if _, err := Open(DeriveKey("x"), make([]byte, MinWireLen-1), nil); err != ErrShortCiphertext {
		t.Fatalf("want ErrShortCiphertext, got %v", err)
	}
}

// TestGoldenVector 固化跨语言向量；首次实现时用 -v 输出回填 goldenWantWire。
func TestGoldenVector(t *testing.T) {
	key := DeriveKey(goldenToken)
	wire, err := SealWithNonce(key, goldenNonce(), []byte(goldenPlain), []byte(goldenAAD))
	if err != nil {
		t.Fatalf("Seal: %v", err)
	}
	t.Logf("GOLDEN key hex  = %s", hex.EncodeToString(key))
	t.Logf("GOLDEN wire hex = %s", hex.EncodeToString(wire))

	const goldenWantKey = "3eb1bd439947eb762998e566ccc2e099c791118b2f40579cc4f7da2b5061b7f9"
	const goldenWantWire = "00000000000000000000000059d36b3d97f92617bc388c2aeae19900f9096bf421f202105c4180997aba"
	if goldenWantWire != "REPLACE" {
		if hex.EncodeToString(key) != goldenWantKey {
			t.Fatalf("key mismatch")
		}
		if hex.EncodeToString(wire) != goldenWantWire {
			t.Fatalf("wire mismatch:\n got %s\nwant %s", hex.EncodeToString(wire), goldenWantWire)
		}
	}
}
