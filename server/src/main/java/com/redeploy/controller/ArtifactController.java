package com.redeploy.controller;

import com.redeploy.model.Artifact;
import com.redeploy.repository.ArtifactMapper;
import com.redeploy.service.ArtifactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/artifacts")
public class ArtifactController {

    @Autowired
    private ArtifactMapper artifactMapper;

    @Autowired
    private ArtifactService artifactService;

    @GetMapping
    public List<Artifact> listArtifacts() {
        return artifactMapper.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Artifact> getArtifact(@PathVariable Long id) {
        return artifactMapper.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadArtifact(@PathVariable Long id) {
        return artifactMapper.findById(id)
                .map(artifact -> {
                    Resource resource = new FileSystemResource(artifact.getFilePath());
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, 
                                    "attachment; filename=\"" + artifact.getFilename() + "\"")
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .body(resource);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteArtifact(@PathVariable Long id) {
        return artifactMapper.findById(id)
                .map(artifact -> {
                    artifactService.deleteArtifactFile(artifact);
                    artifactMapper.deleteById(id);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
