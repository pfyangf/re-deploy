## ADDED Requirements

### Requirement: Chunked file upload
The agent SHALL support chunked file upload for large files.

#### Scenario: Initialize upload
- **WHEN** POST /api/upload/init is called with filename, filesize, md5
- **THEN** agent creates upload session and returns upload_id

#### Scenario: Upload chunk
- **WHEN** POST /api/upload/{upload_id}/chunk is called with chunk data
- **THEN** agent receives and stores chunk
- **AND** returns received chunk sequence number

#### Scenario: Complete upload
- **WHEN** POST /api/upload/{upload_id}/complete is called
- **THEN** agent reassembles all chunks into complete file
- **AND** verifies MD5 checksum
- **AND** returns success/failure status

### Requirement: Resumable upload
The upload process SHALL support resuming from last successful chunk.

#### Scenario: Resume after interruption
- **WHEN** upload is interrupted and client calls GET /api/upload/{upload_id}/status
- **THEN** agent returns last successfully received chunk sequence
- **AND** client can resume from next chunk

#### Scenario: Upload session expiry
- **WHEN** upload session is inactive for more than 1 hour
- **THEN** agent cleans up partial upload data

### Requirement: File integrity verification
The system SHALL verify file integrity using MD5 checksum.

#### Scenario: MD5 verification on complete
- **WHEN** upload is completed
- **THEN** agent calculates MD5 of reassembled file
- **AND** compares with client-provided MD5
- **AND** returns verification result

#### Scenario: MD5 mismatch handling
- **WHEN** MD5 verification fails
- **THEN** agent returns error status
- **AND** client can retry upload

### Requirement: Server-side artifact management
The server SHALL manage build artifacts from Jenkins.

#### Scenario: Store artifact from Jenkins
- **WHEN** server receives artifact from Jenkins
- **THEN** server stores artifact with metadata (filename, size, md5, timestamp)

#### Scenario: List artifacts
- **WHEN** GET /api/artifacts is called
- **THEN** server returns list of stored artifacts

#### Scenario: Download artifact
- **WHEN** GET /api/artifacts/{id}/download is called
- **THEN** server returns artifact file

#### Scenario: Delete artifact
- **WHEN** DELETE /api/artifacts/{id} is called
- **THEN** server removes artifact file and metadata

### Requirement: Artifact distribution to agents
The server SHALL distribute artifacts to target agents.

#### Scenario: Send artifact to agent
- **WHEN** deploy task requires file upload
- **THEN** server reads artifact file
- **AND** uploads to target agent using chunked upload protocol
