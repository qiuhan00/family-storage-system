-- ============================================================================
-- V2__add_items_version.sql — 为 items 增加 JPA 乐观锁版本列（code-review M2）
-- 说明：M2 修复"出入库并发丢更新"，Item 实体新增 @Version 乐观锁。
-- 在生产 ddl-auto=none + Flyway 管理 schema 的前提下，@Version 必须对应
-- 一个真实持久化列，因此本迁移做纯增量 DDL（新增列，非重建/改动既有列）：
--   * 既有行回填 version=0，后续任意更新自动递增；
--   * 不改变任何业务契约字段；仅新增基础设施列，幂等由 Flyway 版本表保证。
-- ============================================================================

ALTER TABLE items
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA optimistic lock version';