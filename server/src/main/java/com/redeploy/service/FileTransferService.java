package com.redeploy.service;

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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

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

            HttpHeaders headers = createHeaders(agentToken);

            MultiValueMap<String, Object> initRequest = new LinkedMultiValueMap<>();
            initRequest.add("filename", filename);
            initRequest.add("file_size", fileSize);
            initRequest.add("md5", md5);

            HttpEntity<MultiValueMap<String, Object>> initEntity = new HttpEntity<>(initRequest, headers);
            ResponseEntity<UploadInitResponse> initResponse = restTemplate.exchange(
                    baseUrl + "/api/upload/init",
                    HttpMethod.POST,
                    initEntity,
                    UploadInitResponse.class
            );

            if (initResponse.getStatusCode() != HttpStatus.OK || initResponse.getBody() == null) {
                log.error("Failed to initialize upload to agent");
                return false;
            }

            String uploadId = initResponse.getBody().getUploadId();
            int chunkSize = initResponse.getBody().getChunkSize();

            // Step 2: Upload chunks
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            int totalChunks = (int) Math.ceil((double) fileBytes.length / chunkSize);

            for (int i = 0; i < totalChunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, fileBytes.length);
                byte[] chunk = new byte[end - start];
                System.arraycopy(fileBytes, start, chunk, 0, end - start);

                MultiValueMap<String, Object> chunkRequest = new LinkedMultiValueMap<>();
                chunkRequest.add("chunk", new ByteArrayResource(chunk) {
                    @Override
                    public String getFilename() {
                        return "chunk";
                    }
                });
                chunkRequest.add("seq", String.valueOf(i));

                HttpEntity<MultiValueMap<String, Object>> chunkEntity = new HttpEntity<>(chunkRequest, headers);
                ResponseEntity<String> chunkResponse = restTemplate.exchange(
                        baseUrl + "/api/upload/" + uploadId + "/chunk",
                        HttpMethod.POST,
                        chunkEntity,
                        String.class
                );

                if (chunkResponse.getStatusCode() != HttpStatus.OK) {
                    log.error("Failed to upload chunk {} to agent", i);
                    return false;
                }

                log.debug("Uploaded chunk {}/{} to agent", i + 1, totalChunks);
            }

            // Step 3: Complete upload
            HttpEntity<Void> completeEntity = new HttpEntity<>(headers);
            ResponseEntity<String> completeResponse = restTemplate.exchange(
                    baseUrl + "/api/upload/" + uploadId + "/complete",
                    HttpMethod.POST,
                    completeEntity,
                    String.class
            );

            if (completeResponse.getStatusCode() != HttpStatus.OK) {
                log.error("Failed to complete upload to agent");
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
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    private String calculateMD5(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        byte[] digest = md.digest(fileBytes);

        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static class UploadInitResponse {
        private String uploadId;
        private int chunkSize;

        public String getUploadId() {
            return uploadId;
        }

        public void setUploadId(String uploadId) {
            this.uploadId = uploadId;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }
    }
}
