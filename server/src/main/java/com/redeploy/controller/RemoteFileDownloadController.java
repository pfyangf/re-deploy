package com.redeploy.controller;

import com.redeploy.model.DownloadSession;
import com.redeploy.repository.DownloadSessionMapper;
import com.redeploy.service.RemoteFileDownloadService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 远端文件下载 REST API。
 *  - POST   /api/servers/{id}/download             触发下载（异步）
 *  - GET    /api/servers/{id}/downloads            按服务器分页历史
 *  - GET    /api/downloads                          全量分页历史
 *  - GET    /api/downloads/{id}                     详情
 *  - GET    /api/downloads/{id}/file                取文件
 *  - DELETE /api/downloads/{id}                     删文件 + 标 deleted
 */
@RestController
public class RemoteFileDownloadController {

    private static final Logger log = LoggerFactory.getLogger(RemoteFileDownloadController.class);

    @Autowired private RemoteFileDownloadService downloadService;
    @Autowired private DownloadSessionMapper sessionMapper;

    @PostMapping("/api/servers/{id}/download")
    public ResponseEntity<Map<String, Object>> trigger(@PathVariable("id") Long serverId,
                                                       @RequestBody Map<String, String> body,
                                                       HttpServletRequest request) {
        String remotePath = body.get("remotePath");
        if (remotePath == null || remotePath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "remotePath is required"));
        }
        String initiator = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "api";
        String ip = clientIp(request);

        Long sessionId = downloadService.initDownload(serverId, remotePath, initiator, ip);
        return ResponseEntity.accepted().body(Map.of(
                "sessionId", sessionId,
                "status", DownloadSession.STATUS_DOWNLOADING,
                "message", "下载任务已启动"));
    }

    @GetMapping("/api/servers/{id}/downloads")
    public Map<String, Object> listByServer(@PathVariable("id") Long serverId,
                                            @RequestParam(defaultValue = "50") int limit,
                                            @RequestParam(defaultValue = "0") int offset) {
        if (limit > 500) limit = 500;
        List<DownloadSession> rows = sessionMapper.listByServer(serverId, limit, offset);
        return Map.of("rows", rows, "limit", limit, "offset", offset);
    }

    @GetMapping("/api/downloads")
    public Map<String, Object> list(@RequestParam(required = false) Long serverId,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "50") int limit,
                                    @RequestParam(defaultValue = "0") int offset) {
        if (limit > 500) limit = 500;
        List<DownloadSession> rows = sessionMapper.listByFilter(serverId, status, limit, offset);
        long total = sessionMapper.countByFilter(serverId, status);
        Map<String, Object> resp = new HashMap<>();
        resp.put("rows", rows);
        resp.put("total", total);
        resp.put("limit", limit);
        resp.put("offset", offset);
        return resp;
    }

    @GetMapping("/api/downloads/{id}")
    public ResponseEntity<DownloadSession> get(@PathVariable Long id) {
        DownloadSession s = sessionMapper.findById(id);
        return s == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(s);
    }

    @GetMapping("/api/downloads/{id}/file")
    public ResponseEntity<?> downloadFile(@PathVariable Long id) {
        DownloadSession s = sessionMapper.findById(id);
        if (s == null) return ResponseEntity.notFound().build();
        if (!DownloadSession.STATUS_SUCCESS.equals(s.getStatus())) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "download not ready",
                    "status", s.getStatus()));
        }
        if (s.getLocalPath() == null) {
            return ResponseEntity.status(410).body(Map.of("error", "local file missing"));
        }
        File f = new File(s.getLocalPath());
        if (!f.exists()) {
            return ResponseEntity.status(410).body(Map.of("error", "file deleted from disk"));
        }

        // 浏览器下载名优先用远端原始文件名（保留扩展名），取不到再退回落盘文件名
        String downloadName = RemoteFileDownloadService.baseName(s.getRemotePath());
        if (downloadName == null || downloadName.isBlank()) {
            downloadName = f.getName();
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(downloadName, java.nio.charset.StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(f.length())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(f));
    }

    @DeleteMapping("/api/downloads/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        DownloadSession s = sessionMapper.findById(id);
        if (s == null) return ResponseEntity.notFound().build();

        // 删落盘文件（最佳努力）
        if (s.getLocalPath() != null) {
            try {
                Path p = Paths.get(s.getLocalPath());
                Files.deleteIfExists(p);
                // 顺便把 session-xxx 目录删了（如果空）
                Path parent = p.getParent();
                if (parent != null && parent.toFile().isDirectory()) {
                    try { Files.deleteIfExists(parent); } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                log.warn("删除下载文件失败: {}", s.getLocalPath(), e);
            }
        }
        sessionMapper.deleteById(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @PostMapping("/api/downloads/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long id) {
        int n = sessionMapper.markCancelled(id);
        if (n == 0) {
            return ResponseEntity.status(409).body(Map.of("error", "session not in cancellable state"));
        }
        return ResponseEntity.ok(Map.of("status", "cancelled"));
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
