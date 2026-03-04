package com.example.farm.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建打印任务请求 DTO
 */
@Data
@Schema(description = "创建打印任务请求参数")
public class FarmPrintJobCreateDTO {

    @Schema(description = "切片文件ID (必填)", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long fileId;

    @Schema(description = "要求耗材类型 (如 PLA, PETG, ABS)")
    private String materialType;

    @Schema(description = "要求喷嘴直径 (如 0.40, 0.60)")
    private BigDecimal nozzleSize;

    @Schema(description = "任务优先级 (数字越大越优先，默认 0)")
    private Integer priority;

    @Schema(description = "是否自动分配打印机 (true: 自动, false: 手动)")
    private Boolean autoAssign;
}
