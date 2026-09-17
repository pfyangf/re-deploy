package api

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gorilla/mux"
	"github.com/redeploy/agent/internal/config"
	"github.com/redeploy/agent/internal/security"
)

const testToken = "0123456789abcdef0123456789abcdef"

func newCryptoTestServer(t *testing.T) *httptest.Server {
	t.Helper()
	s := &Server{cfg: &config.Config{Token: testToken}}
	r := mux.NewRouter()
	r.Use(s.authMiddleware)
	r.Use(s.payloadCryptoMiddleware)

	r.HandleFunc("/api/echo", func(w http.ResponseWriter, r *http.Request) {
		var m map[string]any
		_ = json.NewDecoder(r.Body).Decode(&m)
		writeJSON(w, http.StatusOK, m)
	}).Methods("POST")

	r.HandleFunc("/api/get", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"ok": "true"})
	}).Methods("GET")

	// 模拟 chunk 分片端点：原样回显 body
	r.HandleFunc("/api/upload/{id}/chunk", func(w http.ResponseWriter, r *http.Request) {
		b, _ := io.ReadAll(r.Body)
		w.Header().Set("Content-Type", "application/octet-stream")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(b)
	}).Methods("POST")

	return httptest.NewServer(r)
}

func sealFor(t *testing.T, method, path, plain string) []byte {
	t.Helper()
	key := security.DeriveKey(testToken)
	wire, err := security.Seal(key, []byte(plain), []byte(method+" "+path))
	if err != nil {
		t.Fatalf("seal: %v", err)
	}
	return wire
}

func openResp(t *testing.T, method, path string, body []byte) []byte {
	t.Helper()
	plain, err := security.Open(security.DeriveKey(testToken), body, []byte(method+" "+path))
	if err != nil {
		t.Fatalf("open response: %v", err)
	}
	return plain
}

func TestEncryptedRoundtrip(t *testing.T) {
	srv := newCryptoTestServer(t)
	defer srv.Close()

	plain := `{"hello":"世界"}`
	wire := sealFor(t, "POST", "/api/echo", plain)
	req, _ := http.NewRequest("POST", srv.URL+"/api/echo", bytes.NewReader(wire))
	req.Header.Set("Authorization", "Bearer "+testToken)
	req.Header.Set(payloadVersionHeader, payloadVersionV1)
	req.Header.Set("Content-Type", "application/octet-stream")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("do: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		t.Fatalf("status=%d", resp.StatusCode)
	}
	if resp.Header.Get(payloadVersionHeader) != payloadVersionV1 {
		t.Fatalf("missing version header")
	}
	body, _ := io.ReadAll(resp.Body)
	decrypted := openResp(t, "POST", "/api/echo", body)
	var m map[string]string
	if err := json.Unmarshal(decrypted, &m); err != nil {
		t.Fatalf("decrypted not json: %v (%s)", err, decrypted)
	}
	if m["hello"] != "世界" {
		t.Fatalf("echo mismatch: %v", m)
	}
}

func TestPlaintextLegacyStillWorks(t *testing.T) {
	srv := newCryptoTestServer(t)
	defer srv.Close()

	req, _ := http.NewRequest("POST", srv.URL+"/api/echo", strings.NewReader(`{"a":"b"}`))
	req.Header.Set("Authorization", "Bearer "+testToken)
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("do: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 || resp.Header.Get(payloadVersionHeader) != "" {
		t.Fatalf("legacy plaintext request broken: status=%d vh=%q", resp.StatusCode, resp.Header.Get(payloadVersionHeader))
	}
}

func TestChunkEndpointExemptEvenWithHeader(t *testing.T) {
	srv := newCryptoTestServer(t)
	defer srv.Close()

	raw := []byte("RAW-MULTIPART-BYTES")
	req, _ := http.NewRequest("POST", srv.URL+"/api/upload/u1/chunk", bytes.NewReader(raw))
	req.Header.Set("Authorization", "Bearer "+testToken)
	req.Header.Set(payloadVersionHeader, payloadVersionV1)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("do: %v", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 || resp.Header.Get(payloadVersionHeader) != "" {
		t.Fatalf("chunk should be exempt, status=%d vh=%q", resp.StatusCode, resp.Header.Get(payloadVersionHeader))
	}
	if string(body) != string(raw) {
		t.Fatalf("chunk body altered: %q", body)
	}
}

func TestTamperedCiphertextReturnsPlain400(t *testing.T) {
	srv := newCryptoTestServer(t)
	defer srv.Close()

	wire := sealFor(t, "POST", "/api/echo", `{}`)
	wire[len(wire)-1] ^= 0xff
	req, _ := http.NewRequest("POST", srv.URL+"/api/echo", bytes.NewReader(wire))
	req.Header.Set("Authorization", "Bearer "+testToken)
	req.Header.Set(payloadVersionHeader, payloadVersionV1)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("do: %v", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 400 || resp.Header.Get(payloadVersionHeader) != "" {
		t.Fatalf("want plain 400, got %d vh=%q", resp.StatusCode, resp.Header.Get(payloadVersionHeader))
	}
	if !strings.Contains(string(body), "invalid encrypted payload") {
		t.Fatalf("unexpected body: %s", body)
	}
}

func TestWrongTokenGivesPlain401(t *testing.T) {
	srv := newCryptoTestServer(t)
	defer srv.Close()

	wire := sealFor(t, "POST", "/api/echo", `{}`)
	req, _ := http.NewRequest("POST", srv.URL+"/api/echo", bytes.NewReader(wire))
	req.Header.Set("Authorization", "Bearer wrong-token")
	req.Header.Set(payloadVersionHeader, payloadVersionV1)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("do: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != 401 || resp.Header.Get(payloadVersionHeader) != "" {
		t.Fatalf("want plain 401, got %d vh=%q", resp.StatusCode, resp.Header.Get(payloadVersionHeader))
	}
}

func TestUnknownVersionRejected(t *testing.T) {
	srv := newCryptoTestServer(t)
	defer srv.Close()

	req, _ := http.NewRequest("POST", srv.URL+"/api/echo", strings.NewReader(`{}`))
	req.Header.Set("Authorization", "Bearer "+testToken)
	req.Header.Set(payloadVersionHeader, "9")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("do: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != 400 {
		t.Fatalf("want 400 for unknown version, got %d", resp.StatusCode)
	}
}

func TestEncryptedGetResponse(t *testing.T) {
	srv := newCryptoTestServer(t)
	defer srv.Close()

	req, _ := http.NewRequest("GET", srv.URL+"/api/get", nil)
	req.Header.Set("Authorization", "Bearer "+testToken)
	req.Header.Set(payloadVersionHeader, payloadVersionV1)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("do: %v", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.Header.Get(payloadVersionHeader) != payloadVersionV1 {
		t.Fatalf("GET response should be encrypted when client advertises version")
	}
	plain := openResp(t, "GET", "/api/get", body)
	if !strings.Contains(string(plain), `"ok"`) {
		t.Fatalf("unexpected decrypted GET response: %s", plain)
	}
}

func TestUnmatchedRouteSkipsMiddleware(t *testing.T) {
	srv := newCryptoTestServer(t)
	defer srv.Close()

	req, _ := http.NewRequest("POST", srv.URL+"/api/nope", nil)
	req.Header.Set("Authorization", "Bearer "+testToken)
	req.Header.Set(payloadVersionHeader, payloadVersionV1)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("do: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != 404 || resp.Header.Get(payloadVersionHeader) != "" {
		t.Fatalf("404 should stay plain, got %d vh=%q", resp.StatusCode, resp.Header.Get(payloadVersionHeader))
	}
}
