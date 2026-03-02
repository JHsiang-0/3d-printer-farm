package com.example.farm.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 修改打印机请求 DTO
 */
@Data
@Schema(description = "修改打印机请求参数")
public class FarmPrinterUpdateDTO {

    @Schema(description = "设备ID (必填)", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "打印机名称")
    private String name;

    @Schema(description = "局域网 IP 地址")
    private String ipAddress;

    @Schema(description = "MAC 地址")
    private String macAddress;

    @Schema(description = "固件类型")
    private String firmwareType;

    @Schema(description = "上位机 API 通信密钥")
    private String apiKey;
}
