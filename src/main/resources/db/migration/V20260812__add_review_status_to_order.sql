-- ============================================================
-- 评价体系数据库迁移 V20260812
-- 1. customer_order 表新增评价状态字段
-- 2. 回填历史已完成订单的评价状态
-- 3. customer_review 表的新字段在初始建表时已包含，此处无需变更
-- 执行方式：mysql -u root -p car_rental < 本文件
-- 幂等性：使用 IF NOT EXISTS（MySQL 8.0+），可重复执行
-- ============================================================

USE car_rental;

-- ---------- 1. customer_order 新增评价状态字段 ----------
-- review_status: 评价状态 unreviewed/reviewed/final_reviewed，仅 status=completed 时有意义
-- review_status_name: 评价状态中文名 待评价/已评价/已追评
ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(20) DEFAULT NULL COMMENT '评价状态: unreviewed=待评价, reviewed=已评价, final_reviewed=已追评',
    ADD COLUMN IF NOT EXISTS review_status_name VARCHAR(20) DEFAULT NULL COMMENT '评价状态中文名: 待评价/已评价/已追评';

-- ---------- 2. 回填历史已完成订单的评价状态 ----------
-- 已完成但未设置评价状态的订单，置为待评价
UPDATE customer_order
SET review_status = 'unreviewed',
    review_status_name = '待评价'
WHERE status = 'completed'
  AND (review_status IS NULL OR review_status = '');

-- ---------- 3. 评价数据清理（重置评价体系，可按需执行）----------
-- 清空评价表所有数据（含逻辑删除的），重置已完成订单的 reviewStatus 为待评价
-- 适用场景：评价体系改造后需要重新测试，清理历史脏数据
-- WARNING: 不可逆操作，执行前请确认
-- DELETE FROM customer_review WHERE 1=1;
-- UPDATE customer_order SET review_status='unreviewed', review_status_name='待评价'
--   WHERE status='completed' AND is_delete=0;

-- ---------- 4. 校验查询（执行后人工核对）----------
-- SELECT id, order_no, status, status_name, review_status, review_status_name
-- FROM customer_order WHERE is_delete = 0 ORDER BY id;
