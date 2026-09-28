-- ============================================================================
-- V3__add_location_description_image.sql — 位置表增加「具体说明」与「照片」
-- 背景：位置管理页（方案A）允许用户为位置填写摆放说明并配一张识别照片。
-- 纯增量 DDL：两个可空列，既有行无需回填；幂等由 Flyway 版本表保证。
-- ============================================================================

ALTER TABLE locations
    ADD COLUMN description VARCHAR(500) NULL COMMENT '位置具体说明',
    ADD COLUMN image_url VARCHAR(255) NULL COMMENT '位置照片URL';
