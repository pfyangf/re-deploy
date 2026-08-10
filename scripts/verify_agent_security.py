#!/usr/bin/env python3
"""
Agent 9009 端口安全加固验证脚本（v2 - 修复超时测试方法）

用法:
    python verify_agent_security.py <agent_host:port> [token] [--quick]

示例:
    python verify_agent_security.py 10.5.2.152:9009
    python verify_agent_security.py 10.5.2.152:9009 my-token --quick

--quick: 只测安全头，跳过耗时超时测试

验证项:
    1.  6 个安全响应头是否注入（<1s）
    1b. 401 未授权响应也带安全头（<1s）
    2.  Slow headers: 发部分 header 后停止 → ReadHeaderTimeout(10s) 断开（~10s）
    3.  Slow body: 发部分 body 后停止 → ReadTimeout(60s) 断开（~60s）
    4.  Slow read / Idle: 请求后不发送新请求 → IdleTimeout(120s) 断开（~120s，可跳过）

原理:
    Go 的 SetReadDeadline 设置绝对截止时间，仅在 Read() 阻塞时检查。
    若持续发送数据，Read() 总有数据可读，跳过截止时间检查。
    正确测试方法: 发送部分数据后停止，让 Read() 阻塞触发截止时间。
"""

import select
import socket
import sys
import time
import urllib.request

EXPECTED_TIMEOUTS = {
    "read_header": 10,
    "read": 60,
    "idle": 120,
}

EXPECTED_HEADERS = {
    "Permissions-Policy": "geolocation=(), microphone=()",
    "Cross-Origin-Embedder-Policy": "require-corp",
    "Cross-Origin-Opener-Policy": "same-origin",
    "Cross-Origin-Resource-Policy": "same-origin",
    "Access-Control-Allow-Origin": None,
    "Clear-Site-Data": '"cache"',
}


def print_header(title):
    print(f"\n{'='*60}")
    print(f"  {title}")
    print(f"{'='*60}")


def print_result(passed, msg):
    mark = "[PASS]" if passed else "[FAIL]"
    print(f"  {mark} {msg}")
    return passed


def wait_for_close(sock, expected_sec, max_wait=None, label=""):
    """
    等待 socket 被服务器关闭。
    服务器可能先发响应数据，再关闭连接。本函数持续读取直到收到 EOF (b"")。

    返回: (closed: bool, elapsed: float)
    """
    if max_wait is None:
        max_wait = expected_sec + 10
    start = time.time()
    while time.time() - start < max_wait:
        remaining = max_wait - (time.time() - start)
        ready, _, _ = select.select([sock], [], [], min(5, remaining))
        if ready:
            try:
                data = sock.recv(4096)
            except (ConnectionResetError, ConnectionAbortedError, OSError):
                elapsed = time.time() - start
                return True, elapsed
            if data == b"":
                elapsed = time.time() - start
                return True, elapsed
            elapsed = time.time() - start
            if label and elapsed > 2:
                print(f"  [{elapsed:.0f}s] 收到 {len(data)} 字节响应，等待连接关闭...")
        else:
            elapsed = time.time() - start
            if label and int(elapsed) % 10 == 0 and int(elapsed) > 0:
                print(f"  [{elapsed:.0f}s] 连接仍活跃，等待中...")
    return False, time.time() - start


# ─── 测试 1: 安全响应头 ───────────────────────────────────

def test_security_headers(host, port):
    print_header("测试 1: 安全响应头验证")
    url = f"http://{host}:{port}/api/health"
    try:
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=5) as resp:
            headers = dict(resp.headers)
    except Exception as e:
        print(f"  请求失败: {e}")
        return False

    all_pass = True
    for name, expected_val in EXPECTED_HEADERS.items():
        actual = headers.get(name)
        if actual is None:
            all_pass &= print_result(False, f"{name}: 缺失")
        elif expected_val is None:
            print_result(True, f"{name}: {actual} (存在即可)")
        elif actual == expected_val:
            print_result(True, f"{name}: {actual}")
        else:
            all_pass &= print_result(False, f"{name}: 期望 '{expected_val}', 实际 '{actual}'")
    return all_pass


# ─── 测试 1b: 401 响应安全头 ───────────────────────────────

def test_security_headers_401(host, port):
    print_header("测试 1b: 401 未授权响应安全头验证")
    url = f"http://{host}:{port}/api/info"
    try:
        req = urllib.request.Request(url, method="GET")
        urllib.request.urlopen(req, timeout=5)
    except urllib.error.HTTPError as e:
        headers = dict(e.headers)
        all_pass = True
        for name in EXPECTED_HEADERS:
            if headers.get(name) is None:
                all_pass &= print_result(False, f"{name}: 401 响应中缺失")
            else:
                print_result(True, f"{name}: 存在")
        return all_pass
    except Exception as e:
        print(f"  请求异常 (预期 401): {e}")
        return False
    print("  未返回 401")
    return False


# ─── 测试 2: Slow Headers (ReadHeaderTimeout) ──────────────

def test_slow_headers(host, port):
    """
    发送部分 HTTP header（不含结尾 \\r\\n\\r\\n），然后停止发送。
    服务器 Read() 阻塞等待更多 header 数据 → 10s 后 ReadHeaderTimeout 触发 → 断开连接。

    关键: 发送后不能再发任何数据，否则 Read() 总有数据可读，跳过截止时间检查。
    """
    print_header("测试 2: Slow Headers 防御 (ReadHeaderTimeout ~10s)")
    print(f"  策略: 发送部分 header 后停止发送，等待 {EXPECTED_TIMEOUTS['read_header']}s 超时断开")

    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(5)
    try:
        sock.connect((host, port))
    except Exception as e:
        print(f"  连接失败: {e}")
        return False

    start = time.time()
    partial = f"GET /api/health HTTP/1.1\r\nHost: {host}:{port}\r\n".encode()
    sock.sendall(partial)
    print(f"  已发送部分 header（无结尾 \\r\\n\\r\\n），停止发送，等待服务器断开...")

    closed, elapsed = wait_for_close(sock, EXPECTED_TIMEOUTS["read_header"], max_wait=20)
    if closed:
        return print_result(
            elapsed <= EXPECTED_TIMEOUTS["read_header"] + 3,
            f"服务器在 {elapsed:.1f}s 后断开连接 (预期 ~{EXPECTED_TIMEOUTS['read_header']}s)"
        )
    return print_result(False, f"连接 {elapsed:.0f}s 未断开，ReadHeaderTimeout 未生效")
    sock.close()


# ─── 测试 3: Slow Body (ReadTimeout) ───────────────────────

def test_slow_body(host, port):
    """
    发送完整 header (Content-Length: 100) + 1 字节 body，然后停止发送。
    服务器处理完请求返回响应后，会尝试读取剩余 99 字节 body（用于 keep-alive 复用）。
    Read() 阻塞 → 60s 后 ReadTimeout 触发 → 断开连接。

    注意: 服务器会先返回响应数据，然后才尝试清理 body。测试需先读响应，再等断开。
    """
    print_header("测试 3: Slow Body 防御 (ReadTimeout ~60s)")
    print(f"  策略: 发送完整 header + 1 字节 body 后停止，等待 {EXPECTED_TIMEOUTS['read']}s 超时断开")
    print(f"  注意: 此测试约需 60 秒，服务器会先返回响应再断开连接")

    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(5)
    try:
        sock.connect((host, port))
    except Exception as e:
        print(f"  连接失败: {e}")
        return False

    start = time.time()
    request = (
        f"GET /api/health HTTP/1.1\r\n"
        f"Host: {host}:{port}\r\n"
        f"Content-Length: 100\r\n"
        f"\r\n"
    ).encode()
    sock.sendall(request + b"x")  # 1 字节 body
    print(f"  已发送完整 header (Content-Length: 100) + 1 字节 body，停止发送...")

    closed, elapsed = wait_for_close(sock, EXPECTED_TIMEOUTS["read"], max_wait=75, label="slow-body")
    if closed:
        # 服务器可能先发响应(~0s)再在 60s 后断开，elapsed 应接近 60s
        return print_result(
            elapsed >= EXPECTED_TIMEOUTS["read"] - 5,
            f"服务器在 {elapsed:.1f}s 后断开连接 (预期 ~{EXPECTED_TIMEOUTS['read']}s)"
        )
    return print_result(False, f"连接 {elapsed:.0f}s 未断开，ReadTimeout 未生效")
    sock.close()


# ─── 测试 4: Idle Timeout ──────────────────────────────────

def test_idle_timeout(host, port):
    """
    发送完整请求并读取响应后，保持连接但不发新请求。
    服务器等待新请求 → Read() 阻塞 → 120s 后 IdleTimeout 触发 → 断开连接。

    注意:
    - IdleTimeout 测试需等待 120 秒
    - WriteTimeout 对小响应（如 {"status":"ok"}）无法触发，因为响应在 TCP 缓冲区内
      写入立即完成，不会阻塞。如需测试 WriteTimeout 需要大响应 + 零 TCP 窗口。
    """
    print_header("测试 4: Idle Timeout (~120s)")
    print(f"  策略: 请求完成后保持连接空闲，等待 {EXPECTED_TIMEOUTS['idle']}s 超时断开")
    print(f"  注意: 此测试约需 120 秒")
    print(f"  说明: WriteTimeout 对小响应不触发（写入立即完成），改测 IdleTimeout")

    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(5)
    try:
        sock.connect((host, port))
    except Exception as e:
        print(f"  连接失败: {e}")
        return False

    start = time.time()
    request = (
        f"GET /api/health HTTP/1.1\r\n"
        f"Host: {host}:{port}\r\n"
        f"Connection: keep-alive\r\n"
        f"\r\n"
    ).encode()
    sock.sendall(request)
    print(f"  已发送请求，等待响应后保持空闲...")

    # 先读响应
    ready, _, _ = select.select([sock], [], [], 5)
    if ready:
        data = sock.recv(4096)
        if data:
            elapsed = time.time() - start
            print(f"  [{elapsed:.1f}s] 收到响应 ({len(data)} 字节)，保持连接空闲...")

    # 等待 IdleTimeout 断开
    closed, elapsed = wait_for_close(sock, EXPECTED_TIMEOUTS["idle"], max_wait=135, label="idle")
    if closed:
        return print_result(
            elapsed >= EXPECTED_TIMEOUTS["idle"] - 10,
            f"服务器在 {elapsed:.1f}s 后断开连接 (预期 ~{EXPECTED_TIMEOUTS['idle']}s)"
        )
    return print_result(False, f"连接 {elapsed:.0f}s 未断开，IdleTimeout 未生效")
    sock.close()


# ─── 主函数 ────────────────────────────────────────────────

def main():
    args = sys.argv[1:]
    quick = "--quick" in args
    args = [a for a in args if a != "--quick"]

    if not args:
        print("用法: python verify_agent_security.py <agent_host:port> [token] [--quick]")
        print("示例: python verify_agent_security.py 10.5.2.152:9009")
        print("      python verify_agent_security.py 10.5.2.152:9009 --quick  (仅测安全头)")
        sys.exit(1)

    target = args[0]
    if ":" in target:
        host, port_str = target.rsplit(":", 1)
        port = int(port_str)
    else:
        host = target
        port = 9009

    print(f"目标: {host}:{port}")
    print(f"模式: {'快速（仅安全头）' if quick else '完整（含超时测试，约 3 分钟）'}")

    results = []

    # 测试 1 & 1b: 安全头（<1s）
    results.append(("安全响应头", test_security_headers(host, port)))
    results.append(("401 响应安全头", test_security_headers_401(host, port)))

    if not quick:
        # 测试 2: Slow headers (~10s)
        results.append(("Slow Headers 防御", test_slow_headers(host, port)))

        # 测试 3: Slow body (~60s)
        results.append(("Slow Body 防御", test_slow_body(host, port)))

        # 测试 4: Idle timeout (~120s)
        print("\n  [提示] 此测试约需 120 秒，可用 --quick 跳过")
        results.append(("Idle Timeout 防御", test_idle_timeout(host, port)))

    # 汇总
    print_header("验证结果汇总")
    all_pass = True
    for name, passed in results:
        mark = "[PASS]" if passed else "[FAIL]"
        print(f"  {mark} {name}")
        if not passed:
            all_pass = False

    total = len(results)
    passed_count = sum(1 for _, p in results if p)
    print(f"\n  总计: {passed_count}/{total} 通过")
    if all_pass:
        print("\n  所有验证通过!")
    else:
        print("\n  部分验证未通过，请检查对应项。")
    sys.exit(0 if all_pass else 1)


if __name__ == "__main__":
    main()
