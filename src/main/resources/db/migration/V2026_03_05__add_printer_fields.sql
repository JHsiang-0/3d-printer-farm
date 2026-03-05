-- ============================================
-- 3D 打印农场管理系统 - 数据库迁移脚本
-- 版本: V2026.03.05
-- 说明: 为 farm_printer 表添加 MAC Upsert 机制所需字段
-- ============================================

-- ----------------------------------------------------
-- 1. 添加设备编号字段（用于产线管理）
-- ----------------------------------------------------
# ALTER TABLE farm_printer
#     ADD COLUMN machine_number VARCHAR(50) NULL COMMENT '设备编号/机台号（用于产线管理）'
#     AFTER nozzle_size;
#
# -- ----------------------------------------------------
# -- 2. 添加数字孪生看板物理位置字段
# -- ----------------------------------------------------
# ALTER TABLE farm_printer
#     ADD COLUMN grid_row TINYINT NULL COMMENT '物理位置 - 网格行号（数字孪生看板用，1-4，null 表示待分配区）',
#     ADD COLUMN grid_col TINYINT NULL COMMENT '物理位置 - 网格列号（数字孪生看板用，1-12，null 表示待分配区）'
#     AFTER machine_number;

-- ----------------------------------------------------
-- 3. 确保 MAC 地址有唯一索引（Upsert 机制关键）
-- ----------------------------------------------------
-- 先检查是否已存在索引
SET @index_exists = (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'farm_printer'
      AND index_name = 'uk_mac_address'
);

-- 如果不存在则创建
SET @sql = IF(@index_exists = 0,
              'ALTER TABLE farm_printer ADD UNIQUE INDEX uk_mac_address (mac_address);',
              'SELECT "Index uk_mac_address already exists" AS message;');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------
-- 4. 添加 IP 地址索引（用于快速冲突检测）
-- ----------------------------------------------------
SET @index_exists = (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'farm_printer'
      AND index_name = 'idx_ip_address'
);

SET @sql = IF(@index_exists = 0,
              'ALTER TABLE farm_printer ADD INDEX idx_ip_address (ip_address);',
              'SELECT "Index idx_ip_address already exists" AS message;');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------
-- 5. 验证字段添加成功
-- ----------------------------------------------------
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE table_schema = DATABASE()
  AND table_name = 'farm_printer'
  AND COLUMN_NAME IN ('machine_number', 'grid_row', 'grid_col', 'mac_address', 'ip_address')
ORDER BY ORDINAL_POSITION;
