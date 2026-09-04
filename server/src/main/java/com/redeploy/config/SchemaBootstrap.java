package com.redeploy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 启动期建表兜底（schema 演进第②层的运行时落地）。
 *
 * 背景：application.yml 中 spring.sql.init.mode=never，schema.sql / migration/*.sql 不会被
 * Spring 自动执行；老库从旧版本升级时缺少 notification_config / notification_history /
 * download_session 等新表。这里在启动期幂等执行 classpath:migration/V*.sql
 * （全部为 CREATE TABLE IF NOT EXISTS），保证新表存在。
 *
 * - 用 @Order(0) 确保早于 NotificationConfigMigrator(@Order(10)) 等依赖新表的 Runner。
 * - 每条语句独立执行、失败仅告警不中断（IF NOT EXISTS 本身幂等，重复执行安全）。
 */
@Component
@Order(0)
public class SchemaBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaBootstrap.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaBootstrap(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(String... args) {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:migration/*.sql");
        } catch (Exception e) {
            log.warn("扫描 migration/*.sql 失败，跳过启动期建表: {}", e.getMessage());
            return;
        }

        Arrays.sort(resources, (a, b) -> {
            String an = a.getFilename() == null ? "" : a.getFilename();
            String bn = b.getFilename() == null ? "" : b.getFilename();
            return an.compareTo(bn);
        });

        for (Resource res : resources) {
            String filename = res.getFilename();
            try {
                String sql = StreamUtils.copyToString(res.getInputStream(), StandardCharsets.UTF_8);
                int executed = executeStatements(sql);
                log.info("启动期迁移脚本 {} 执行完成（{} 条语句）", filename, executed);
            } catch (Exception e) {
                log.warn("启动期迁移脚本 {} 执行失败: {}", filename, e.getMessage());
            }
        }
    }

    /** 按 ';' 切分并逐条执行 DDL，返回成功执行的语句数。 */
    private int executeStatements(String sql) {
        // 去掉 -- 行注释，避免注释里的分号干扰切分
        StringBuilder cleaned = new StringBuilder();
        for (String line : sql.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--")) continue;
            cleaned.append(line).append('\n');
        }

        int count = 0;
        for (String stmt : cleaned.toString().split(";")) {
            String s = stmt.trim();
            if (s.isEmpty()) continue;
            try {
                jdbcTemplate.execute(s);
                count++;
            } catch (Exception e) {
                // 幂等脚本重复执行 / 列已存在等情况：记录但不中断
                log.debug("迁移语句跳过: {} | err={}", firstLine(s), e.getMessage());
            }
        }
        return count;
    }

    private static String firstLine(String stmt) {
        String s = stmt.replaceAll("\\s+", " ");
        return s.length() <= 80 ? s : s.substring(0, 80) + "...";
    }
}
