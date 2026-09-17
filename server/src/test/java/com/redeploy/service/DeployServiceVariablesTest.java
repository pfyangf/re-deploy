package com.redeploy.service;

import com.redeploy.model.DeployHistory;
import com.redeploy.model.Server;
import com.redeploy.model.Task;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeployServiceVariablesTest {

    @Test
    void builtinVariablesInjected() {
        DeployHistory history = new DeployHistory();
        history.setId(456L);
        history.setVersion("#123");
        history.setStartedAt(LocalDateTime.of(2026, 9, 17, 9, 45, 0));

        Task task = new Task();
        task.setId(7L);
        task.setName("网关部署");

        Server server = new Server();
        server.setName("录像合成服务器-dat");
        server.setHost("10.28.3.44");

        Map<String, String> user = new HashMap<>();
        user.put("jenkinsBuildNumber", "123");

        Map<String, String> p = DeployService.buildServerParams(user, history, task, server);

        assertEquals("456", p.get("deploy.id"));
        assertEquals("#123", p.get("deploy.version"));
        assertEquals("20260917-094500", p.get("deploy.time"));
        assertEquals(String.valueOf(LocalDateTime.of(2026, 9, 17, 9, 45, 0)
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond()), p.get("deploy.timestamp"));
        assertEquals("7", p.get("task.id"));
        assertEquals("网关部署", p.get("task.name"));
        assertEquals("录像合成服务器-dat", p.get("server.name"));
        assertEquals("10.28.3.44", p.get("server.host"));
        assertEquals("123", p.get("jenkinsBuildNumber"), "user param must survive");
    }

    @Test
    void nullVersionBecomesEmptyString() {
        DeployHistory history = new DeployHistory();
        history.setId(1L);
        history.setStartedAt(LocalDateTime.now());
        Task task = new Task();
        task.setId(1L);
        Server server = new Server();
        server.setHost("h1");

        Map<String, String> p = DeployService.buildServerParams(null, history, task, server);
        assertEquals("", p.get("deploy.version"));
    }

    @Test
    void eachServerGetsIndependentMap() {
        DeployHistory history = new DeployHistory();
        history.setId(9L);
        history.setStartedAt(LocalDateTime.now());
        Task task = new Task();
        task.setId(9L);

        Map<String, String> shared = new HashMap<>();
        shared.put("k", "v");

        Server s1 = new Server();
        s1.setName("s1");
        s1.setHost("10.0.0.1");
        Server s2 = new Server();
        s2.setName("s2");
        s2.setHost("10.0.0.2");

        Map<String, String> p1 = DeployService.buildServerParams(shared, history, task, s1);
        Map<String, String> p2 = DeployService.buildServerParams(shared, history, task, s2);

        assertNotSame(p1, p2);
        assertEquals("10.0.0.1", p1.get("server.host"));
        assertEquals("10.0.0.2", p2.get("server.host"));
        p1.put("artifactFilename", "a.jar");
        assertFalse(p2.containsKey("artifactFilename"), "maps must not be shared between servers");
    }
}
