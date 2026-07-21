package com.redeploy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class JenkinsService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsService.class);

    @Value("${redeploy.upload-dir:./data/uploads}")
    private String uploadDir;

    private final RestTemplate restTemplate = new RestTemplate();

    public File downloadArtifact(String jenkinsUrl, String jobName, String buildNumber,
                                 String artifactPath, String jenkinsUser, String jenkinsToken) {
        try {
            // Construct Jenkins artifact URL
            String artifactUrl;
            if ("lastSuccessfulBuild".equals(buildNumber)) {
                artifactUrl = String.format("%s/job/%s/lastSuccessfulBuild/artifact/%s",
                        jenkinsUrl, jobName, artifactPath);
            } else {
                artifactUrl = String.format("%s/job/%s/%s/artifact/%s",
                        jenkinsUrl, jobName, buildNumber, artifactPath);
            }

            log.info("Downloading artifact from: {}", artifactUrl);

            // Create download directory
            Path downloadPath = Paths.get(uploadDir, "jenkins");
            Files.createDirectories(downloadPath);

            // Extract filename from path
            String filename = artifactPath.contains("/") ?
                    artifactPath.substring(artifactPath.lastIndexOf("/") + 1) : artifactPath;

            File outputFile = downloadPath.resolve(filename).toFile();

            // Download with authentication
            HttpHeaders headers = new HttpHeaders();
            if (jenkinsUser != null && jenkinsToken != null) {
                String auth = jenkinsUser + ":" + jenkinsToken;
                String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
                headers.set("Authorization", "Basic " + encodedAuth);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    artifactUrl, HttpMethod.GET, entity, byte[].class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    fos.write(response.getBody());
                }
                log.info("Downloaded artifact: {} ({} bytes)", filename, outputFile.length());
                return outputFile;
            }

            throw new RuntimeException("Failed to download artifact, status: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("Failed to download artifact from Jenkins", e);
            throw new RuntimeException("Failed to download artifact: " + e.getMessage(), e);
        }
    }

    public File downloadArtifactDirect(String artifactUrl, String jenkinsUser, String jenkinsToken) {
        try {
            log.info("Downloading artifact from: {}", artifactUrl);

            Path downloadPath = Paths.get(uploadDir, "jenkins");
            Files.createDirectories(downloadPath);

            // Extract filename from URL
            String filename = artifactUrl.contains("/") ?
                    artifactUrl.substring(artifactUrl.lastIndexOf("/") + 1) : "artifact.zip";

            File outputFile = downloadPath.resolve(filename).toFile();

            HttpHeaders headers = new HttpHeaders();
            if (jenkinsUser != null && jenkinsToken != null) {
                String auth = jenkinsUser + ":" + jenkinsToken;
                String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
                headers.set("Authorization", "Basic " + encodedAuth);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    artifactUrl, HttpMethod.GET, entity, byte[].class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    fos.write(response.getBody());
                }
                log.info("Downloaded artifact: {} ({} bytes)", filename, outputFile.length());
                return outputFile;
            }

            throw new RuntimeException("Failed to download artifact, status: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("Failed to download artifact", e);
            throw new RuntimeException("Failed to download artifact: " + e.getMessage(), e);
        }
    }
}
