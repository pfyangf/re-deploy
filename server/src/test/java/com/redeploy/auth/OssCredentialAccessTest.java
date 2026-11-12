package com.redeploy.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssCredentialAccessTest {

    @Test
    void menuAllowsTaskPageToReadCredentials() {
        assertEquals("ANY:/oss-credentials,/tasks", MenuAccess.requirement("GET", "/api/oss-credentials"));
        assertEquals("/oss-credentials", MenuAccess.requirement("POST", "/api/oss-credentials"));
        assertEquals("/oss-credentials", MenuAccess.requirement("POST", "/api/oss-credentials/test"));
        assertEquals("ANY:/dashboard,/history,/deploy", MenuAccess.requirement("POST", "/api/deploy/cancel-active"));
        assertEquals("/oss-credentials", MenuAccess.requirement("DELETE", "/api/oss-credentials/3"));
    }

    @Test
    void apiKeyCanReadAndCreateButNotChange() {
        assertTrue(ApiKeyAccess.allows("GET", "/api/oss-credentials"));
        assertTrue(ApiKeyAccess.allows("POST", "/api/oss-credentials"));
        assertFalse(ApiKeyAccess.allows("PUT", "/api/oss-credentials/3"));
        assertTrue(ApiKeyAccess.allows("POST", "/api/oss-credentials/test"));
        assertTrue(ApiKeyAccess.allows("POST", "/api/deploy/cancel-active"));
        assertFalse(ApiKeyAccess.allows("DELETE", "/api/oss-credentials/3"));
    }
}
