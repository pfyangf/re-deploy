package com.redeploy.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redeploy.model.DownloadSession;
import com.redeploy.model.Server;
import com.redeploy.repository.DownloadSessionMapper;
import com.redeploy.repository.ServerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 远端文件下载服务：server 调 agent 的 /api/download/* 协议，把 agent 上的文件落盘到
 * server 本地 ./data/downloads/<serverId>/。
 *
 * 设计要点：
 *  - 异步执行（@Async），HTTP 接口立即返回 session id
 *  - 流式 chunk 写盘，不读全文件到内存（用 java.net.HttpURLConnection 直连 agent + 字节流管道）
 *  - 增量更新 bytes_received 用于进度
 *  - 终态时计算 MD5 校验（仅 ≤ 200MB 文件做全量 MD5，避免大文件 hash 阻塞）
 *  - agent 30 秒无响应则标 FAILED（启动期扫描 stale downloading 兜底）
 */
@Service
public class RemoteFileDownloadService {

    private static final Logger log = LoggerFactory.getLogger(RemoteFileDownloadService.class);
    private static final int CHUNK_SIZE = 5 * 1024 * 1024; // 与 agent 协议对齐
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final long MD5_FULL_LIMIT = 200L * 1024 * 1024; // 200MB
    private static final Pattern SANITIZE_REPLACE = Pattern.compile("[\\\\/:*?\"<>|\\s]");

    @Autowired private DownloadSessionMapper downloadMapper;
    @Autowired private ServerMapper serverMapper;

    /**
     * 自注入代理：@Async 只在经过 Spring 代理调用时生效，类内直接调用 runDownload()
     * 会绕过代理导致同步阻塞请求线程。通过 self 代理调用确保走 downloadExecutor 线程池。
     */
    @Autowired @Lazy
    private RemoteFileDownloadService self;

    @Value("${redeploy.download.dir:./data/downloads}")
    private String downloadDir;

    /**
     * 同步 init：调用 agent /api/download/init，拿到 session 信息后落 DB 立即返回。
     * 实际拉数据由 @Async runDownload 异步完成。
     */
    public Long initDownload(Long serverId, String remotePath, String initiator, String initiatorIp) {
        Server server = serverMapper.findById(serverId).orElseThrow(
                () -> new IllegalArgumentException("server not found: " + serverId));
        if (server.getAgentToken() == null || server.getAgentToken().isBlank()) {
            throw new IllegalStateException("server has no agent token registered");
        }

        String baseUrl = String.format("http://%s:%d", server.getHost(), server.getPort());
        String url = baseUrl + "/api/download/init";

        Map<String, Object> req = new HashMap<>();
        req.put("remote_path", remotePath);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(server.getAgentToken());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(req, headers);

        try {
            RestTemplate rt = new RestTemplate();
            ResponseEntity<DownloadInitResponse> resp = rt.exchange(
                    url, HttpMethod.POST, entity, DownloadInitResponse.class);

            if (resp.getBody() == null) {
                throw new IllegalStateException("agent returned empty body");
            }
            DownloadInitResponse body = resp.getBody();
            if (!"DOWNLOADING".equals(body.getStatus()) && body.getDownloadId() == null) {
                throw new IllegalStateException("agent rejected: " + body);
            }

            DownloadSession ds = new DownloadSession();
            ds.setServerId(serverId);
            ds.setRemotePath(remotePath);
            ds.setFileSize(body.getTotalBytes());
            ds.setBytesReceived(0L);
            ds.setStatus(DownloadSession.STATUS_DOWNLOADING);
            ds.setInitiator(initiator);
            ds.setInitiatorIp(initiatorIp);
            downloadMapper.insert(ds);

            // 立即启动异步下载（经 self 代理调用，@Async 才生效）
            self.runDownload(ds.getId(), server.getHost(), server.getPort(), server.getAgentToken(),
                    body.getDownloadId(), body.getChunkSize() > 0 ? body.getChunkSize() : CHUNK_SIZE,
                    body.getTotalBytes(), remotePath);

            return ds.getId();
        } catch (Exception e) {
            log.error("下载 init 失败 server={} path={}", serverId, remotePath, e);
            // 失败也要落一条 FAILED 记录方便 UI 看到
            try {
                DownloadSession ds = new DownloadSession();
                ds.setServerId(serverId);
                ds.setRemotePath(remotePath);
                ds.setStatus(DownloadSession.STATUS_FAILED);
                ds.setErrorMessage("init failed: " + truncate(e.getMessage(), 500));
                ds.setInitiator(initiator);
                ds.setInitiatorIp(initiatorIp);
                downloadMapper.insert(ds);
            } catch (Exception ignored) {}
            throw new IllegalStateException("agent init failed: " + e.getMessage(), e);
        }
    }

    @Async("downloadExecutor")
    public void runDownload(Long sessionId, String host, int port, String token,
                            String agentDownloadId, int chunkSize, long totalBytes, String remotePath) {
        Path localFile = null;
        try {
            localFile = generateLocalPath(Paths.get(downloadDir), sessionId, remotePath);
            Files.createDirectories(localFile.getParent());

            MessageDigest md = (totalBytes <= MD5_FULL_LIMIT) ? md5Digest() : null;
            long received = 0;
            int seq = 0;
            int totalChunks = (int) ((totalBytes + chunkSize - 1) / chunkSize);
            if (totalBytes == 0) totalChunks = 0; // 0 字节文件

            boolean cancelled = false;
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(localFile))) {
                while (seq < totalChunks) {
                    // 用户可能在下载过程中点了取消：检测到 DB 状态为 CANCELLED 则中断
                    if (isCancelled(sessionId)) {
                        cancelled = true;
                        break;
                    }
                    byte[] chunk = fetchChunk(host, port, token, agentDownloadId, seq);
                    if (chunk.length == 0) {
                        // 0 字节 chunk：可能是最后一片 EOF
                        break;
                    }
                    out.write(chunk);
                    if (md != null) md.update(chunk);
                    received += chunk.length;
                    seq++;
                    // 每 5 个 chunk 更新一次进度（避免 DB 写入过频）
                    if (seq % 5 == 0 || seq == totalChunks) {
                        downloadMapper.updateBytes(sessionId, received);
                    }
                }
            }

            if (cancelled || isCancelled(sessionId)) {
                // 取消：清理部分落盘文件 + 通知 agent 释放它那边的 session；不覆盖 DB 的 CANCELLED 状态
                try { Files.deleteIfExists(localFile); } catch (IOException ignored) {}
                try { notifyAgentCancel(host, port, token, agentDownloadId); } catch (Exception ignored) {}
                log.info("下载被用户取消 session={} 已接收={}", sessionId, received);
                return;
            }

            // 基本完整性校验：实际接收字节数应与 agent 声明的一致（0 字节文件除外）
            if (totalBytes > 0 && received != totalBytes) {
                throw new IOException("下载不完整: 期望 " + totalBytes + " 字节, 实际 " + received + " 字节");
            }

            if (md != null) {
                String md5Hex = toHex(md.digest());
                downloadMapper.markSuccess(sessionId, md5Hex, received, localFile.toString());
                log.info("下载完成 session={} bytes={} md5={} path={}", sessionId, received, md5Hex, localFile);
            } else {
                // 大文件不验 MD5，但标记为 SUCCESS
                downloadMapper.markSuccess(sessionId, null, received, localFile.toString());
                log.info("下载完成（跳过 MD5） session={} bytes={} path={}", sessionId, received, localFile);
            }

            // 通知 agent 完成（清理它那边的 session）
            notifyAgentComplete(host, port, token, agentDownloadId);
        } catch (Exception e) {
            log.error("下载失败 session={}", sessionId, e);
            // 清理落盘的部分文件
            if (localFile != null) {
                try { Files.deleteIfExists(localFile); } catch (IOException ignored) {}
            }
            downloadMapper.markFailed(sessionId, truncate(e.getMessage(), 500));
            // 通知 agent cancel
            try { notifyAgentCancel(host, port, token, agentDownloadId); } catch (Exception ignored) {}
        }
    }

    /** 查询 DB 中会话是否已被用户取消（异步线程据此主动中断）。 */
    private boolean isCancelled(Long sessionId) {
        try {
            DownloadSession cur = downloadMapper.findById(sessionId);
            return cur != null && DownloadSession.STATUS_CANCELLED.equals(cur.getStatus());
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] fetchChunk(String host, int port, String token, String downloadId, int seq) throws IOException {
        String url = String.format("http://%s:%d/api/download/%s/chunk?seq=%d", host, port, downloadId, seq);
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/octet-stream");

        int code = conn.getResponseCode();
        if (code != 200) {
            String err;
            try (InputStream es = conn.getErrorStream()) {
                err = (es == null) ? "" : new String(es.readAllBytes());
            }
            conn.disconnect();
            throw new IOException("agent chunk " + seq + " returned " + code + ": " + truncate(err, 200));
        }

        try (InputStream is = conn.getInputStream()) {
            return is.readAllBytes();
        } finally {
            conn.disconnect();
        }
    }

    private void notifyAgentComplete(String host, int port, String token, String downloadId) {
        try {
            String url = String.format("http://%s:%d/api/download/%s/complete", host, port, downloadId);
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.getResponseCode(); // 触发
            conn.disconnect();
        } catch (Exception e) {
            log.debug("agent complete 通知失败（忽略）: {}", e.getMessage());
        }
    }

    private void notifyAgentCancel(String host, int port, String token, String downloadId) {
        try {
            String url = String.format("http://%s:%d/api/download/%s/cancel", host, port, downloadId);
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {
            log.debug("agent cancel 通知失败（忽略）: {}", e.getMessage());
        }
    }

    /**
     * 落盘路径：./data/downloads/session-{id}/{原始文件名}。
     * 每个会话独占 session-{id} 目录，因此直接用原始文件名（保留扩展名）也不会冲突；
     * 文件名做一次文件系统安全字符替换（去掉 / \ : * ? " < > | 等非法字符）。
     */
    public Path generateLocalPath(Path baseDir, Long sessionId, String remotePath) {
        String filename = sanitizeFileName(baseName(remotePath));
        if (filename.isBlank()) {
            filename = "file-" + sessionId;
        }
        if (filename.length() > 200) {
            filename = filename.substring(filename.length() - 200);
        }
        return baseDir.resolve("session-" + sessionId).resolve(filename);
    }

    /** 取路径的文件名部分（兼容 / 与 \ 分隔符，远端是 Linux 路径但本地 server 可能在 Windows）。 */
    public static String baseName(String path) {
        if (path == null) return "";
        String s = path.replace('\\', '/');
        int idx = s.lastIndexOf('/');
        return idx >= 0 ? s.substring(idx + 1) : s;
    }

    /** 仅把文件系统非法字符替换为 _，保留文件名与扩展名（不拍平目录、不加 .bin）。 */
    public static String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) return "";
        return SANITIZE_REPLACE.matcher(name).replaceAll("_");
    }

    private static MessageDigest md5Digest() {
        try { return MessageDigest.getInstance("MD5"); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** agent init 响应。download_id 在成功时一定存在，status 字段保留兼容。 */
    public static class DownloadInitResponse {
        @JsonProperty("download_id") private String downloadId;
        @JsonProperty("chunk_size") private int chunkSize;
        @JsonProperty("total_bytes") private long totalBytes;
        @JsonProperty("total_chunks") private int totalChunks;
        private String status; // optional

        public String getDownloadId() { return downloadId; }
        public void setDownloadId(String downloadId) { this.downloadId = downloadId; }
        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
        public long getTotalBytes() { return totalBytes; }
        public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
        public int getTotalChunks() { return totalChunks; }
        public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
