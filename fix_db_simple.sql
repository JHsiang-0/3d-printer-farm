-- ============================================
-- 简单修复方案：删除并重建表
-- 警告：这会清空表数据！如果已有重要数据请勿执行
-- ============================================

-- 1. 删除旧表
DROP TABLE IF EXISTS farm_print_job;
DROP TABLE IF EXISTS farm_print_file;

-- 2. 创建打印任务表
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

-- 3. 创建打印文件表
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

-- 4. 查看创建的表
SHOW TABLES LIKE 'farm%';
