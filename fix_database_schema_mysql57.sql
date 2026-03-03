-- ============================================
-- 数据库表结构修复脚本（兼容 MySQL 5.7+）
-- 修复 farm_print_job 和 farm_print_file 表字段
-- ============================================

-- ============================================
-- 1. 删除并重建 farm_print_job 表（如果表结构有问题）
-- 注意：这会清空表数据！如果已有数据请使用 ALTER TABLE
-- ============================================

-- 先查看当前表结构
-- DESC farm_print_job;

-- 方案A：如果表为空或可以删除，直接重建
DROP TABLE IF EXISTS farm_print_job;

CREATE TABLE farm_print_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务流水号',
    file_id BIGINT COMMENT '关联的切片文件ID',
    printer_id BIGINT COMMENT '分配的打印机ID（排队中为NULL）',
    user_id BIGINT NOT NULL COMMENT '发起任务的用户ID',
    priority INT DEFAULT 0 COMMENT '排队优先级（数值越高越优先）',
    status VARCHAR(20) NOT NULL COMMENT '任务状态：QUEUED, ASSIGNED, PRINTING, COMPLETED, FAILED, CANCELED',
    progress DECIMAL(5,2) DEFAULT 0.00 COMMENT '打印进度（0.00 - 100.00）',
    started_at DATETIME COMMENT '实际开始打印时间',
    completed_at DATETIME COMMENT '实际完成/失败时间',
    error_reason VARCHAR(255) COMMENT '失败原因（炒面、断料等）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    file_url VARCHAR(500) COMMENT 'RustFS中的文件访问地址',
    est_time INT COMMENT '预计打印耗时（秒）',
    material_type VARCHAR(20) COMMENT '要求耗材类型(如 PLA, PETG, ABS)',
    nozzle_size DECIMAL(3,2) COMMENT '要求喷嘴直径(如 0.40, 0.60)',
    INDEX idx_status (status),
    INDEX idx_printer_id (printer_id),
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打印任务与排队调度表';

-- ============================================
-- 2. 删除并重建 farm_print_file 表
-- ============================================
DROP TABLE IF EXISTS farm_print_file;

CREATE TABLE farm_print_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    safe_name VARCHAR(255) NOT NULL COMMENT '安全文件名（带时间戳）',
    file_url VARCHAR(500) COMMENT '文件存储URL',
    file_size BIGINT COMMENT '文件大小（字节）',
    user_id BIGINT COMMENT '上传用户ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    est_time INT COMMENT '预计打印耗时（秒）',
    material_type VARCHAR(20) COMMENT '耗材类型（如 PLA, PETG, ABS）',
    nozzle_size DECIMAL(3,2) COMMENT '喷嘴直径（如 0.40, 0.60）',
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打印文件表';

-- ============================================
-- 3. 检查并补充 farm_printer 表字段
-- ============================================

-- 检查字段是否存在，如果不存在则添加（MySQL 5.7 兼容方式）
SET @dbname = DATABASE();
SET @tablename = 'farm_printer';

-- 添加 current_material 字段（如果不存在）
SET @columnname = 'current_material';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname
     AND TABLE_NAME = @tablename
     AND COLUMN_NAME = @columnname) = 0,
    CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' VARCHAR(20) COMMENT "当前装载耗材";'),
    'SELECT 1;'
));
PREPARE addColumn FROM @preparedStatement;
EXECUTE addColumn;
DEALLOCATE PREPARE addColumn;

-- 添加 nozzle_size 字段（如果不存在）
SET @columnname = 'nozzle_size';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname
     AND TABLE_NAME = @tablename
     AND COLUMN_NAME = @columnname) = 0,
    CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' DECIMAL(3,2) COMMENT "当前安装的喷嘴直径";'),
    'SELECT 1;'
));
PREPARE addColumn FROM @preparedStatement;
EXECUTE addColumn;
DEALLOCATE PREPARE addColumn;

-- 添加 current_job_id 字段（如果不存在）
SET @columnname = 'current_job_id';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname
     AND TABLE_NAME = @tablename
     AND COLUMN_NAME = @columnname) = 0,
    CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' BIGINT COMMENT "当前正在执行的打印任务ID";'),
    'SELECT 1;'
));
PREPARE addColumn FROM @preparedStatement;
EXECUTE addColumn;
DEALLOCATE PREPARE addColumn;

-- ============================================
-- 4. 检查并补充 farm_user 表字段
-- ============================================
SET @tablename = 'farm_user';

-- 添加 email 字段（如果不存在）
SET @columnname = 'email';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname
     AND TABLE_NAME = @tablename
     AND COLUMN_NAME = @columnname) = 0,
    CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' VARCHAR(100) COMMENT "邮箱";'),
    'SELECT 1;'
));
PREPARE addColumn FROM @preparedStatement;
EXECUTE addColumn;
DEALLOCATE PREPARE addColumn;

-- 添加 phone 字段（如果不存在）
SET @columnname = 'phone';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname
     AND TABLE_NAME = @tablename
     AND COLUMN_NAME = @columnname) = 0,
    CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' VARCHAR(20) COMMENT "手机号";'),
    'SELECT 1;'
));
PREPARE addColumn FROM @preparedStatement;
EXECUTE addColumn;
DEALLOCATE PREPARE addColumn;

-- ============================================
-- 5. 验证表结构
-- ============================================
SHOW TABLES LIKE 'farm%';
