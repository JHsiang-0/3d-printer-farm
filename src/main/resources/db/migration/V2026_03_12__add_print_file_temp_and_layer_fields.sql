-- ============================================
-- 添加打印文件表温度和层高字段
-- 日期: 2026-03-12
-- 说明: 支持 OrcaSlicer 切片参数解析
-- ============================================

-- 1. 添加 nozzle_temp 字段（喷头温度，单位：℃）
ALTER TABLE farm_print_file
    ADD COLUMN nozzle_temp INT DEFAULT NULL COMMENT '喷头温度（℃）' AFTER bed_temp;

-- 2. 添加 bed_temp 字段（热床温度，单位：℃）
ALTER TABLE farm_print_file
    ADD COLUMN bed_temp INT DEFAULT NULL COMMENT '热床温度（℃）' AFTER nozzle_temp;

-- 3. 添加 layer_height 字段（层高，单位：mm）
ALTER TABLE farm_print_file
    ADD COLUMN layer_height DECIMAL(10,2) DEFAULT NULL COMMENT '层高（mm）' AFTER bed_temp;