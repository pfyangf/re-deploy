package com.redeploy.model;

import java.time.LocalDateTime;

public class Artifact {

    private Long id;
    private String filename;
    private String filePath;
    private Long fileSize;
    private String md5;
    private LocalDateTime uploadedAt;

    public Artifact() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getMd5() { return md5; }
    public void setMd5(String md5) { this.md5 = md5; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
