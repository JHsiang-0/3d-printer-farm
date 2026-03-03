-- ============================================
-- 数据库表结构修复脚本
-- 修复 farm_print_job 和 farm_print_file 表字段
-- ============================================

-- ============================================
-- 1. 检查并修复 farm_print_job 表
-- ============================================

-- 查看当前表结构
-- DESC farm_print_job;

-- 如果表不存在，创建新表
CREATE TABLE IF NOT EXISTS farm_print_job (
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

-- 如果表已存在但缺少字段，添加字段
ALTER TABLE farm_print_job 
    ADD COLUMN IF NOT EXISTS file_id BIGINT COMMENT '关联的切片文件ID' AFTER id,
    ADD COLUMN IF NOT EXISTS printer_id BIGINT COMMENT '分配的打印机ID' AFTER file_id,
    ADD COLUMN IF NOT EXISTS user_id BIGINT NOT NULL COMMENT '发起任务的用户ID' AFTER printer_id,
    ADD COLUMN IF NOT EXISTS priority INT DEFAULT 0 COMMENT '排队优先级' AFTER user_id,
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL COMMENT '任务状态' AFTER priority,
    ADD COLUMN IF NOT EXISTS progress DECIMAL(5,2) DEFAULT 0.00 COMMENT '打印进度' AFTER status,
    ADD COLUMN IF NOT EXISTS started_at DATETIME COMMENT '实际开始打印时间' AFTER progress,
    ADD COLUMN IF NOT EXISTS completed_at DATETIME COMMENT '实际完成/失败时间' AFTER started_at,
    ADD COLUMN IF NOT EXISTS error_reason VARCHAR(255) COMMENT '失败原因' AFTER completed_at,
    ADD COLUMN IF NOT EXISTS created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务创建时间' AFTER error_reason,
    ADD COLUMN IF NOT EXISTS updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER created_at,
    ADD COLUMN IF NOT EXISTS file_url VARCHAR(500) COMMENT 'RustFS中的文件访问地址' AFTER updated_at,
    ADD COLUMN IF NOT EXISTS est_time INT COMMENT '预计打印耗时（秒）' AFTER file_url,
    ADD COLUMN IF NOT EXISTS material_type VARCHAR(20) COMMENT '要求耗材类型' AFTER est_time,
    ADD COLUMN IF NOT EXISTS nozzle_size DECIMAL(3,2) COMMENT '要求喷嘴直径' AFTER material_type;

-- ============================================
-- 2. 检查并修复 farm_print_file 表
-- ============================================

CREATE TABLE IF NOT EXISTS farm_print_file (
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

-- 如果表已存在但缺少字段，添加字段
ALTER TABLE farm_print_file 
    ADD COLUMN IF NOT EXISTS original_name VARCHAR(255) COMMENT '原始文件名' AFTER id,
    ADD COLUMN IF NOT EXISTS safe_name VARCHAR(255) COMMENT '安全文件名' AFTER original_name,
    ADD COLUMN IF NOT EXISTS file_url VARCHAR(500) COMMENT '文件存储URL' AFTER safe_name,
    ADD COLUMN IF NOT EXISTS file_size BIGINT COMMENT '文件大小' AFTER file_url,
    ADD COLUMN IF NOT EXISTS user_id BIGINT COMMENT '上传用户ID' AFTER file_size,
    ADD COLUMN IF NOT EXISTS created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER user_id,
    ADD COLUMN IF NOT EXISTS est_time INT COMMENT '预计打印耗时' AFTER created_at,
    ADD COLUMN IF NOT EXISTS material_type VARCHAR(20) COMMENT '耗材类型' AFTER est_time,
    ADD COLUMN IF NOT EXISTS nozzle_size DECIMAL(3,2) COMMENT '喷嘴直径' AFTER material_type;

-- ============================================
-- 3. 检查 farm_printer 表字段
-- ============================================

-- 添加可能缺少的字段
ALTER TABLE farm_printer 
    ADD COLUMN IF NOT EXISTS current_material VARCHAR(20) COMMENT '当前装载耗材' AFTER status,
    ADD COLUMN IF NOT EXISTS nozzle_size DECIMAL(3,2) COMMENT '当前安装的喷嘴直径' AFTER current_material,
    ADD COLUMN IF NOT EXISTS current_job_id BIGINT COMMENT '当前正在执行的打印任务ID' AFTER nozzle_size;

-- ============================================
-- 4. 检查 farm_user 表字段
-- ============================================

-- 添加可能缺少的字段
ALTER TABLE farm_user 
    ADD COLUMN IF NOT EXISTS email VARCHAR(100) COMMENT '邮箱' AFTER role,
    ADD COLUMN IF NOT EXISTS phone VARCHAR(20) COMMENT '手机号' AFTER email;

-- ============================================
-- 5. 验证表结构
-- ============================================
SHOW TABLES LIKE 'farm%';
