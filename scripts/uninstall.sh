#!/bin/bash
set -e

# Deploy Agent Uninstallation Script

echo "Stopping deploy-agent service..."
sudo systemctl stop deploy-agent || true
sudo systemctl disable deploy-agent || true

echo "Removing systemd service..."
sudo rm -f /etc/systemd/system/deploy-agent.service
sudo systemctl daemon-reload

echo "Removing installation directory..."
sudo rm -rf /opt/deploy-agent

echo "Uninstallation complete!"
