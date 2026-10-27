package com.redeploy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 表由 SchemaBootstrap 先建好，这里补内置菜单和管理员角色。 */
@Component
@Order(5)
public class RbacSeed implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RbacSeed.class);
    private final RbacService rbacService;

    public RbacSeed(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Override
    public void run(String... args) {
        try {
            rbacService.seed();
            log.info("权限菜单与管理员角色已就绪");
        } catch (Exception e) {
            log.warn("初始化权限数据失败: {}", e.getMessage());
        }
    }
}
