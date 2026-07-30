package com.redeploy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class JenkinsService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsService.class);
    private static final int MAX_RETAINED_BUILDS = 3;

    @Value("${redeploy.upload-dir:./data/uploads}")
    private String uploadDir;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.redeploy.repository.ArtifactMapper artifactMapper;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public File downloadArtifact(String jenkinsUrl, String jobName, String buildNumber,
                                 String artifactPath, String jenkinsUser, String jenkinsToken) {
        try {
            // Construct Jenkins artifact URL
            String artifactUrl;
            if ("lastSuccessfulBuild".equals(buildNumber)) {
                artifactUrl = String.format("%s/job/%s/lastSuccessfulBuild/artifact/%s",
                        jenkinsUrl, jobName, artifactPath);
            } else {
                artifactUrl = String.format("%s/%s/%s/artifact/%s",
                        jenkinsUrl, jobName, buildNumber, artifactPath);
            }

            log.info("[Jenkins] GET {} (downloadArtifact) job={} build={}", artifactUrl, jobName, buildNumber);

            // Create download directory
            Path downloadPath = Paths.get(uploadDir, "jenkins");
            Files.createDirectories(downloadPath);

            // Extract filename from path
            String filename = artifactPath.contains("/") ?
                    artifactPath.substring(artifactPath.lastIndexOf("/") + 1) : artifactPath;

            // Sanitize job name for filesystem (replace slashes with dashes)
            String safeJobName = jobName.replaceAll("[^a-zA-Z0-9.-]", "-");
            // Store with job prefix: {job}-{build}-{filename}
            String outputFilename = String.format("%s-%s-%s", safeJobName, buildNumber, filename);
            File outputFile = downloadPath.resolve(outputFilename).toFile();

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
                log.info("Downloaded artifact: {} ({} bytes)", outputFilename, outputFile.length());

                // Cleanup old builds - keep only MAX_RETAINED_BUILDS
                cleanupOldBuilds(downloadPath, safeJobName);

                return outputFile;
            }

            throw new RuntimeException("Failed to download artifact, status: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("Failed to download artifact from Jenkins", e);
            throw new RuntimeException("Failed to download artifact: " + e.getMessage(), e);
        }
    }

    private void cleanupOldBuilds(Path downloadPath, String safeJobName) {
        try {
            // List all files for this job
            File dir = downloadPath.toFile();
            File[] files = dir.listFiles((dir1, name) -> name.startsWith(safeJobName + "-"));
            if (files == null || files.length <= MAX_RETAINED_BUILDS) {
                return; // No cleanup needed
            }

            // Sort by last modified time (oldest first)
            List<File> jobFiles = new ArrayList<>();
            for (File file : files) {
                jobFiles.add(file);
            }
            jobFiles.sort(Comparator.comparingLong(File::lastModified));

            // Delete oldest files until we have only MAX_RETAINED_BUILDS left
            int filesToDelete = jobFiles.size() - MAX_RETAINED_BUILDS;
            for (int i = 0; i < filesToDelete; i++) {
                File fileToDelete = jobFiles.get(i);
                String pathToDelete = fileToDelete.getAbsolutePath();
                if (fileToDelete.delete()) {
                    log.info("Cleaned up old Jenkins artifact: {}", fileToDelete.getName());
                    if (artifactMapper != null) {
                        try {
                            artifactMapper.deleteByFilePath(pathToDelete);
                        } catch (Exception ex) {
                            log.warn("Failed to remove artifact DB record for {}: {}", pathToDelete, ex.getMessage());
                        }
                    }
                } else {
                    log.warn("Failed to delete old Jenkins artifact: {}", fileToDelete.getName());
                }
            }

            log.info("Cleaned up {} old artifacts for job {}, kept {} most recent",
                    filesToDelete, safeJobName, MAX_RETAINED_BUILDS);

        } catch (Exception e) {
            log.warn("Error cleaning up old Jenkins artifacts: {}", e.getMessage());
            // Don't fail the download just because cleanup failed
        }
    }

    public List<Map<String, Object>> getBuildHistory(String jenkinsUrl, String jobName,
                                                     String jenkinsUser, String jenkinsToken, int limit) {
        try {
            String treeValue = String.format("builds[number,result,timestamp,description]{0,%d}", limit);

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(jenkinsUrl);
            for (String segment : jobName.split("/")) {
                if (!segment.isEmpty()) {
                    builder.pathSegment(segment);
                }
            }
            builder.path("api/json").queryParam("tree", treeValue);
            URI uri = builder.build().encode(StandardCharsets.UTF_8).toUri();

            log.info("[Jenkins] GET {} (getBuildHistory) job={}", uri, jobName);

            HttpHeaders headers = new HttpHeaders();
            if (jenkinsUser != null && jenkinsToken != null) {
                String auth = jenkinsUser + ":" + jenkinsToken;
                String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
                headers.set("Authorization", "Basic " + encodedAuth);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode builds = root.path("builds");
                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode build : builds) {
                    result.add(Map.of(
                            "number", build.path("number").asInt(),
                            "result", build.path("result").isNull() ? "BUILDING" : build.path("result").asText(),
                            "timestamp", build.path("timestamp").asLong(),
                            "description", build.path("description").isNull() ? "" : build.path("description").asText()
                    ));
                }
                return result;
            }

            throw new RuntimeException("Failed to fetch build history, status: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("Failed to fetch build history from Jenkins", e);
            throw new RuntimeException("Failed to fetch build history: " + e.getMessage(), e);
        }
    }

    public File downloadArtifactDirect(String artifactUrl, String jenkinsUser, String jenkinsToken) {
        try {
            log.info("[Jenkins] GET {} (downloadArtifactDirect)", artifactUrl);

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
