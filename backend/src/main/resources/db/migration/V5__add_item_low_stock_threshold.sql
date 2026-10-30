-- ============================================================================
-- V5__add_item_low_stock_threshold.sql —— items 增加「低库存提醒阈值」
-- 背景：可选的低库存提醒。quantity < low_stock_threshold 时首页提示；
-- 列为空表示该物品不启用提醒。纯增量 DDL，既有行默认不提醒。
-- ============================================================================

ALTER TABLE items
    ADD COLUMN low_stock_threshold INT NULL COMMENT '低库存提醒阈值（数量低于该值时提醒；NULL 不提醒）';
