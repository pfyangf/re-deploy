package com.redeploy.service;

import com.fasterxml.jackson.databind.util.JSONPObject;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

@Service
public class FileTransferService {

    private static final Logger log = LoggerFactory.getLogger(FileTransferService.class);
    private static final int CHUNK_SIZE = 5 * 1024 * 1024; // 5MB

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean uploadFileToAgent(String agentHost, int agentPort, String agentToken,
                                     File file, String filename) {
        try {
            String baseUrl = String.format("http://%s:%d", agentHost, agentPort);

            // Step 1: Initialize upload
            String md5 = calculateMD5(file);
            long fileSize = file.length();

            // Init request is JSON, not multipart (agent expects JSON)
            HttpHeaders headers = createHeaders(agentToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> initRequest = new HashMap<>();
            initRequest.put("filename", filename);
            initRequest.put("file_size", fileSize);
            initRequest.put("md5", md5);

            HttpEntity<Map<String, Object>> initEntity = new HttpEntity<>(initRequest, headers);
            String initUrl = baseUrl + "/api/upload/init";
            log.info("[Upload] POST {} filename={} size={} md5={}", initUrl, filename, fileSize, md5);
            ResponseEntity<UploadInitResponse> initResponse = restTemplate.exchange(
                    initUrl,
                    HttpMethod.POST,
                    initEntity,
                    UploadInitResponse.class
            );
            log.info("[Upload] POST {} response={}", initUrl, initResponse.getBody());

            if (initResponse.getStatusCode() != HttpStatus.OK || initResponse.getBody() == null) {
                log.error("[Upload] Failed to initialize upload to agent, url={}", initUrl);
                return false;
            }

            String uploadId = initResponse.getBody().getUploadId();
            int chunkSize = initResponse.getBody().getChunkSize();
            if (chunkSize <= 0) {
                chunkSize = CHUNK_SIZE;
            }
            log.info("[Upload] Init response uploadId={} chunkSize={}", uploadId, chunkSize);

            // Step 2: Upload chunks - stream file instead of loading all into memory
            // This avoids high memory usage with large files
            int totalChunks = (int) Math.ceil((double) file.length() / chunkSize);
            byte[] chunk = new byte[chunkSize];
            int seq = 0;

            try (InputStream is = Files.newInputStream(file.toPath());
                 BufferedInputStream bis = new BufferedInputStream(is)) {

                int bytesRead;
                while ((bytesRead = bis.read(chunk)) != -1) {
                    // Create a new byte array for exact length of this chunk
                    byte[] exactChunk = chunk;
                    if (bytesRead != chunk.length) {
                        exactChunk = new byte[bytesRead];
                        System.arraycopy(chunk, 0, exactChunk, 0, bytesRead);
                    }

                    MultiValueMap<String, Object> chunkRequest = new LinkedMultiValueMap<>();
                    chunkRequest.add("chunk", new ByteArrayResource(exactChunk) {
                        @Override
                        public String getFilename() {
                            return "chunk";
                        }
                    });
                    chunkRequest.add("seq", String.valueOf(seq));

                    // Chunk upload needs multipart/form-data
                    HttpHeaders chunkHeaders = createHeaders(agentToken);
                    chunkHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
                    HttpEntity<MultiValueMap<String, Object>> chunkEntity = new HttpEntity<>(chunkRequest, chunkHeaders);
                    String chunkUrl = baseUrl + "/api/upload/" + uploadId + "/chunk";
                    log.debug("[Upload] POST {} seq={} bytes={}", chunkUrl, seq, bytesRead);
                    ResponseEntity<String> chunkResponse = restTemplate.exchange(
                            chunkUrl,
                            HttpMethod.POST,
                            chunkEntity,
                            String.class
                    );

                    if (chunkResponse.getStatusCode() != HttpStatus.OK) {
                        log.error("[Upload] Failed to upload chunk {} to agent, url={}", seq, chunkUrl);
                        return false;
                    }

                    log.debug("[Upload] Uploaded chunk {}/{} to agent", seq + 1, totalChunks);
                    seq++;
                }
            } catch (IOException e) {
                log.error("Failed to read file for chunked upload", e);
                return false;
            }

            // Step 3: Complete upload
            HttpEntity<Void> completeEntity = new HttpEntity<>(createHeaders(agentToken));
            String completeUrl = baseUrl + "/api/upload/" + uploadId + "/complete";
            log.info("[Upload] POST {} totalChunks={}", completeUrl, seq);
            ResponseEntity<String> completeResponse = restTemplate.exchange(
                    completeUrl,
                    HttpMethod.POST,
                    completeEntity,
                    String.class
            );

            if (completeResponse.getStatusCode() != HttpStatus.OK) {
                log.error("[Upload] Failed to complete upload to agent, url={}", completeUrl);
                return false;
            }

            log.info("Successfully uploaded file {} to agent", filename);
            return true;

        } catch (Exception e) {
            log.error("Failed to upload file to agent", e);
            return false;
        }
    }

    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    private String calculateMD5(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        int bytesRead;

        try (InputStream is = Files.newInputStream(file.toPath());
             BufferedInputStream bis = new BufferedInputStream(is)) {

            while ((bytesRead = bis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }

        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @ToString
    private static class UploadInitResponse {
        private String upload_id;
        private int chunk_size;

        @JsonProperty("upload_id")
        public String getUploadId() {
            return upload_id;
        }

        public void setUploadId(String uploadId) {
            this.upload_id = uploadId;
        }

        @JsonProperty("chunk_size")
        public int getChunkSize() {
            return chunk_size;
        }

        public void setChunkSize(int chunkSize) {
            this.chunk_size = chunkSize;
        }
    }
}
