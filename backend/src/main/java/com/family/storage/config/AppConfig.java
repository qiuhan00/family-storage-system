package com.family.storage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 通用 Bean 配置：提供 TransactionTemplate。
 * AuthService 首用户注册用它在 JVM 锁内完成「count 判定 + save + 提交」，
 * 闭合并发出首增的 TOCTOU 窗口（code-review M7）。
 */
@Configuration
public class AppConfig {

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}