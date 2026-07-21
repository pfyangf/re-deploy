package com.redeploy.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/agent")
public class AgentDownloadController {

    @Value("${redeploy.agent-dir:./data/agents}")
    private String agentDir;

    @GetMapping("/download/{os}/{arch}")
    public ResponseEntity<Resource> downloadAgent(@PathVariable String os, @PathVariable String arch) {
        String filename = String.format("deploy-agent-%s-%s", os, arch);
        if ("windows".equals(os)) {
            filename += ".exe";
        }

        Path filePath = Paths.get(agentDir, filename);
        File file = filePath.toFile();

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/install.sh")
    public ResponseEntity<String> getInstallScript() {
        String script = "#!/bin/bash\n" +
                "set -e\n\n" +
                "# Deploy Agent Installation Script\n\n" +
                "SERVER_URL=${1:-http://localhost:9006}\n" +
                "AUTH_TOKEN=${2:-}\n\n" +
                "# Detect architecture\n" +
                "ARCH=$(uname -m)\n" +
                "if [ \"$ARCH\" = \"x86_64\" ]; then\n" +
                "    AGENT_ARCH=\"amd64\"\n" +
                "elif [ \"$ARCH\" = \"aarch64\" ]; then\n" +
                "    AGENT_ARCH=\"arm64\"\n" +
                "else\n" +
                "    echo \"Unsupported architecture: $ARCH\"\n" +
                "    exit 1\n" +
                "fi\n\n" +
                "echo \"Detected architecture: $AGENT_ARCH\"\n\n" +
                "# Create installation directory\n" +
                "INSTALL_DIR=\"/opt/deploy-agent\"\n" +
                "sudo mkdir -p $INSTALL_DIR/bin $INSTALL_DIR/conf $INSTALL_DIR/data $INSTALL_DIR/logs\n\n" +
                "# Download agent binary\n" +
                "echo \"Downloading agent binary...\"\n" +
                "sudo curl -fsSL \"$SERVER_URL/api/agent/download/linux/$AGENT_ARCH\" -o $INSTALL_DIR/bin/deploy-agent\n" +
                "sudo chmod +x $INSTALL_DIR/bin/deploy-agent\n\n" +
                "# Create systemd service\n" +
                "echo \"Creating systemd service...\"\n" +
                "sudo tee /etc/systemd/system/deploy-agent.service > /dev/null <<EOF\n" +
                "[Unit]\n" +
                "Description=Deploy Agent Service\n" +
                "After=network.target\n\n" +
                "[Service]\n" +
                "Type=simple\n" +
                "User=root\n" +
                "ExecStart=$INSTALL_DIR/bin/deploy-agent\n" +
                "Restart=always\n" +
                "RestartSec=5\n" +
                "LimitNOFILE=65536\n" +
                "Environment=AGENT_CONFIG_DIR=$INSTALL_DIR/conf\n\n" +
                "[Install]\n" +
                "WantedBy=multi-user.target\n" +
                "EOF\n\n" +
                "# Start service\n" +
                "echo \"Starting deploy-agent service...\"\n" +
                "sudo systemctl daemon-reload\n" +
                "sudo systemctl enable deploy-agent\n" +
                "sudo systemctl start deploy-agent\n\n" +
                "echo \"Installation complete!\"\n" +
                "echo \"Check logs: journalctl -u deploy-agent -f\"\n" +
                "echo \"Check status: systemctl status deploy-agent\"\n";

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(script);
    }
}
