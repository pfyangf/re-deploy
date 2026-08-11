/*
 Navicat Premium Dump SQL

 Source Server Type    : MySQL
 Source Server Version : 80018 (8.0.18)
 Source Schema         : redeploy

 Target Server Type    : MySQL
 Target Server Version : 80018 (8.0.18)
 File Encoding         : 65001

 Date: 11/08/2026 12:04:04
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for agents
-- ----------------------------
DROP TABLE IF EXISTS `agents`;
CREATE TABLE `agents`  (
                           `id` bigint(20) NOT NULL AUTO_INCREMENT,
                           `server_id` bigint(20) NULL DEFAULT NULL,
                           `hostname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                           `ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                           `port` int(11) NULL DEFAULT 9009,
                           `token` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                           `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'offline',
                           `last_heartbeat` timestamp NULL DEFAULT NULL,
                           `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                           PRIMARY KEY (`id`) USING BTREE,
                           INDEX `server_id`(`server_id` ASC) USING BTREE,
                           CONSTRAINT `agents_ibfk_1` FOREIGN KEY (`server_id`) REFERENCES `servers` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for artifacts
-- ----------------------------
DROP TABLE IF EXISTS `artifacts`;
CREATE TABLE `artifacts`  (
                              `id` bigint(20) NOT NULL AUTO_INCREMENT,
                              `filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
                              `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
                              `file_size` bigint(20) NULL DEFAULT NULL,
                              `md5` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                              `uploaded_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for deploy_history
-- ----------------------------
DROP TABLE IF EXISTS `deploy_history`;
CREATE TABLE `deploy_history`  (
                                   `id` bigint(20) NOT NULL AUTO_INCREMENT,
                                   `task_id` bigint(20) NULL DEFAULT NULL,
                                   `server_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
                                   `version` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                                   `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
                                   `started_at` timestamp NULL DEFAULT NULL,
                                   `completed_at` timestamp NULL DEFAULT NULL,
                                   `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
                                   `logs` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
                                   `detail_logs` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
                                   `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                                   PRIMARY KEY (`id`) USING BTREE,
                                   INDEX `task_id`(`task_id` ASC) USING BTREE,
                                   CONSTRAINT `deploy_history_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `tasks` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for groups
-- ----------------------------
DROP TABLE IF EXISTS `groups`;
CREATE TABLE `groups`  (
                           `id` bigint(20) NOT NULL AUTO_INCREMENT,
                           `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
                           `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
                           `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                           `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                           PRIMARY KEY (`id`) USING BTREE,
                           UNIQUE INDEX `name`(`name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for servers
-- ----------------------------
DROP TABLE IF EXISTS `servers`;
CREATE TABLE `servers`  (
                            `id` bigint(20) NOT NULL AUTO_INCREMENT,
                            `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
                            `host` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
                            `port` int(11) NULL DEFAULT 9009,
                            `agent_token` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                            `group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                            `group_id` bigint(20) NULL DEFAULT NULL,
                            `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
                            `ssh_username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                            `ssh_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                            `ssh_private_key` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
                            `ssh_port` int(11) NULL DEFAULT 22,
                            `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'offline',
                            `last_heartbeat` timestamp NULL DEFAULT NULL,
                            `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                            `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tasks
-- ----------------------------
DROP TABLE IF EXISTS `tasks`;
CREATE TABLE `tasks`  (
                          `id` bigint(20) NOT NULL AUTO_INCREMENT,
                          `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
                          `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
                          `task_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
                          `group_id` bigint(20) NULL DEFAULT NULL,
                          `deploy_path` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
                          `before_command` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
                          `after_command` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
                          `steps_definition` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
                          `jenkins_enabled` tinyint(1) NULL DEFAULT 0,
                          `jenkins_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                          `jenkins_job_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                          `jenkins_artifact_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                          `jenkins_user` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                          `jenkins_token` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                          `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                          `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                          PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
