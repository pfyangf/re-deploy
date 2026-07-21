package com.redeploy.service;

import com.redeploy.model.Artifact;
import com.redeploy.repository.ArtifactMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

@Service
public class ArtifactService {

    @Autowired
    private ArtifactMapper artifactMapper;

    @Value("${redeploy.upload-dir:./data/uploads}")
    private String uploadDir;

    public Artifact storeArtifact(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String filename = file.getOriginalFilename();
        String storedFilename = System.currentTimeMillis() + "_" + filename;
        Path filePath = uploadPath.resolve(storedFilename);
        
        file.transferTo(filePath.toFile());

        String md5 = calculateMD5(filePath);

        Artifact artifact = new Artifact();
        artifact.setFilename(filename);
        artifact.setFilePath(filePath.toString());
        artifact.setFileSize(file.getSize());
        artifact.setMd5(md5);

        artifactMapper.insert(artifact);
        return artifact;
    }

    public void deleteArtifactFile(Artifact artifact) {
        try {
            Path filePath = Paths.get(artifact.getFilePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // Log error but don't fail
        }
    }

    private String calculateMD5(Path filePath) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] digest = md.digest(fileBytes);
            
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }
}
