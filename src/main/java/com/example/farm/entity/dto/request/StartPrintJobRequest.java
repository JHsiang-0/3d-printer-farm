package com.example.farm.entity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 现场启动打印任务请求 DTO
 */
@Data
@Schema(description = "启动打印任务请求参数")
public class StartPrintJobRequest {

    @Schema(description = "任务ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long jobId;

    @Schema(description = "操作员ID（从安全上下文获取，可选）")
    private Long operatorId;
}