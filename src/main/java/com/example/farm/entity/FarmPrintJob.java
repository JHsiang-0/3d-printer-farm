package com.example.farm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 打印任务与排队调度表
 * </p>
 *
 * @author codexiang
 * @since 2026-03-01
 */
@Getter
@Setter
@ToString
@TableName("farm_print_job")
@Schema(name = "FarmPrintJob", description = "打印任务与排队调度表")
public class FarmPrintJob implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务流水号
     */
    @Schema(description = "任务流水号")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联的切片文件 ID
     */
    @TableField("file_id")
    @Schema(description = "关联的切片文件 ID")
    private Long fileId;

    /**
     * 分配的打印机 ID（排队中为 NULL）
     */
    @TableField("printer_id")
    @Schema(description = "分配的打印机 ID（排队中为 NULL）")
    private Long printerId;

    /**
     * 发起任务的用户 ID
     */
    @TableField("user_id")
    @Schema(description = "发起任务的用户 ID")
    private Long userId;

    /**
     * 排队优先级（数值越高越优先）
     */
    @TableField("priority")
    @Schema(description = "排队优先级（数值越高越优先）")
    private Integer priority;

    /**
     * 任务状态：QUEUED, ASSIGNED, PRINTING, COMPLETED, FAILED, CANCELED
     */
    @TableField("status")
    @Schema(description = "任务状态：QUEUED, ASSIGNED, PRINTING, COMPLETED, FAILED, CANCELED")
    private String status;

    /**
     * 打印进度（0.00 - 100.00）
     */
    @TableField("progress")
    @Schema(description = "打印进度（0.00 - 100.00）")
    private BigDecimal progress;

    /**
     * 实际开始打印时间
     */
    @TableField("started_at")
    @Schema(description = "实际开始打印时间")
    private LocalDateTime startedAt;

    /**
     * 实际完成/失败时间
     */
    @TableField("completed_at")
    @Schema(description = "实际完成/失败时间")
    private LocalDateTime completedAt;

    /**
     * 失败原因（炒面、断料等）
     */
    @TableField("error_reason")
    @Schema(description = "失败原因（炒面、断料等）")
    private String errorReason;

    /**
     * 任务创建时间
     */
    @TableField("created_at")
    @Schema(description = "任务创建时间")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    /**
     * RustFS 中的文件访问地址
     */
    @TableField("file_url")
    @Schema(description = "RustFS中的文件访问地址")
    private String fileUrl;

    /**
     * 预计打印耗时（秒）
     */
    @TableField("est_time")
    @Schema(description = "预计打印耗时（秒）")
    private Integer estTime;

    /**
     * 要求耗材类型 (如 PLA, PETG, ABS)
     */
    @TableField("material_type")
    @Schema(description = "要求耗材类型 (如 PLA, PETG, ABS)")
    private String materialType;

    /**
     * 要求喷嘴直径 (如 0.40, 0.60)
     */
    @TableField("nozzle_size")
    @Schema(description = "要求喷嘴直径 (如 0.40, 0.60)")
    private java.math.BigDecimal nozzleSize;
}
