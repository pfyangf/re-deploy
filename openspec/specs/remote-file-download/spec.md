# remote-file-download Specification

## Purpose
TBD - created by archiving change remote-file-download. Update Purpose after archive.

## Requirements

### Requirement: Remote file download via agent
The server SHALL be able to download files from any registered agent's host and persist them locally for retrieval.

#### Scenario: Trigger a download
- **WHEN** `POST /api/servers/{id}/download` is called with `{ "remotePath": "/var/log/myapp/app.log" }`
- **THEN** the server creates a `download_session` row with status `pending`
- **AND** returns the session id immediately
- **AND** starts an asynchronous download job

#### Scenario: Download completion
- **WHEN** the asynchronous download job finishes successfully
- **THEN** the session status becomes `success`
- **AND** `local_path` points to the saved file on the server
- **AND** `md5` matches the file's MD5 reported by the agent

### Requirement: Chunked download protocol (agent side)
The agent SHALL expose a chunked download API mirroring the existing upload API.

#### Scenario: Initialize download session
- **WHEN** `POST /api/download/init` is called with `{ "remotePath": "..." }`
- **THEN** the agent validates the path against `allowed-paths`
- **AND** if valid, creates a local session and returns `{ "id": "...", "fileSize": N }`
- **AND** if invalid (path not allowed / not a regular file / does not exist), returns 4xx with reason

#### Scenario: Stream chunk N
- **WHEN** `POST /api/download/{id}/chunk?seq=N` is called
- **THEN** the agent returns the N-th 5 MB chunk of the file (or short chunk if file ends)
- **AND** the chunk content is sent as raw bytes (or base64 in JSON envelope)

#### Scenario: Query download status
- **WHEN** `GET /api/download/{id}/status` is called
- **THEN** the agent returns `{ "id": "...", "fileSize": N, "nextSeq": M, "status": "..." }` to enable resumption

#### Scenario: Complete download
- **WHEN** `POST /api/download/{id}/complete` is called
- **THEN** the agent computes and returns the full file MD5
- **AND** cleans up the local session

#### Scenario: Cancel download
- **WHEN** `POST /api/download/{id}/cancel` is called
- **THEN** the agent removes the local session

### Requirement: Path whitelist (security)
The agent SHALL restrict downloads to a configured whitelist of glob patterns.

#### Scenario: Default whitelist
- **WHEN** the agent starts with no `allowed-paths` configured
- **THEN** the agent permits `/var/log/**`, `/opt/*/log/**`, and `/tmp/redeploy-**`

#### Scenario: Custom whitelist
- **WHEN** `agent.download.allowed-paths` is configured in agent config
- **THEN** only paths matching at least one glob are permitted

#### Scenario: Reject unauthorized path
- **WHEN** `remotePath` does not match any allowed glob
- **THEN** the agent returns 403 and does not create a session

#### Scenario: Reject path traversal
- **WHEN** `remotePath` contains `..` or resolves to a path outside its literal form via symlinks
- **THEN** the agent returns 403

#### Scenario: Reject non-regular file
- **WHEN** `remotePath` is a directory, device, socket, or FIFO
- **THEN** the agent returns 400

### Requirement: Server-side persistence
The server SHALL persist downloaded files to a predictable local path.

#### Scenario: Local path layout
- **WHEN** a download completes
- **THEN** the file is saved under `./data/downloads/<serverId>/<sanitized-remote-path>-<unixSeconds>.bin`
- **AND** `/` in the remote path is replaced with `__` in the sanitized form

#### Scenario: No collision
- **WHEN** two downloads of the same file on the same day complete
- **THEN** both files are preserved (different `unixSeconds` suffixes)

### Requirement: Browser retrieval
The user SHALL be able to retrieve the downloaded file via the web UI.

#### Scenario: Download file
- **WHEN** `GET /api/downloads/{id}/file` is called and the session status is `success`
- **THEN** the server streams the file with `Content-Disposition: attachment; filename="<sanitized-remote-path>"`

#### Scenario: Cannot download incomplete
- **WHEN** `GET /api/downloads/{id}/file` is called and the session status is not `success`
- **THEN** the server returns 409

### Requirement: Download history
The system SHALL keep an audit trail of all downloads.

#### Scenario: List all downloads
- **WHEN** `GET /api/downloads?serverId=X&status=Y&from=Z&to=W` is called
- **THEN** the server returns paginated download sessions ordered by `created_at` DESC

#### Scenario: Download detail
- **WHEN** `GET /api/downloads/{id}` is called
- **THEN** the server returns full session metadata including `remote_path`, `file_size`, `md5`, `status`, `initiator`, `initiator_ip`, timestamps

### Requirement: Delete downloaded file
The user SHALL be able to delete a downloaded file from server storage.

#### Scenario: Delete
- **WHEN** `DELETE /api/downloads/{id}` is called
- **THEN** the server removes the file from disk and marks the session as `deleted`

#### Scenario: Delete idempotency
- **WHEN** `DELETE /api/downloads/{id}` is called on an already-deleted session
- **THEN** the server returns 200 with no error

### Requirement: 30-day retention
The system SHALL automatically clean up successful download sessions and their files after 30 days.

#### Scenario: Daily cleanup
- **WHEN** a scheduled task runs daily at 04:00
- **THEN** the system deletes all `download_session` rows where `status='success' AND created_at < NOW() - 30 day`
- **AND** deletes the corresponding local files (failures logged but do not throw)

### Requirement: Async download execution
The download job SHALL execute asynchronously and never block the HTTP request thread.

#### Scenario: Non-blocking trigger
- **WHEN** `POST /api/servers/{id}/download` is called
- **THEN** the response returns within 1 second with the session id
- **AND** the actual file transfer happens on a background executor

#### Scenario: Stale download detection
- **WHEN** the server starts and finds sessions with `status='downloading'`
- **THEN** the server marks them as `failed` (since no live transfer can still be in progress)

### Requirement: MD5 integrity
The server SHALL verify downloaded files against the MD5 reported by the agent.

#### Scenario: MD5 match
- **WHEN** the server finishes persisting chunks
- **THEN** it computes MD5 of the local file and compares with the agent's reported MD5
- **AND** on match, marks the session as `success`

#### Scenario: MD5 mismatch
- **WHEN** the computed MD5 differs from the agent's reported MD5
- **THEN** the server deletes the local file and marks the session as `failed` with `error_message` set

### Requirement: Large file support
The download protocol SHALL handle files larger than available memory.

#### Scenario: 2 GB file download
- **WHEN** downloading a 2 GB file
- **THEN** neither the agent nor the server holds the entire file in memory at once
- **AND** the agent streams chunks via `io.LimitReader` and the server writes them via `BufferedOutputStream`
