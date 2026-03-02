package com.example.farm.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 添加打印机请求 DTO
 */
@Data
@Schema(description = "添加打印机请求参数")
public class FarmPrinterAddDTO {

    @Schema(description = "打印机名称 (必填)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "局域网 IP 地址 (必填)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ipAddress;

    @Schema(description = "MAC 地址 (选填，用于网络唤醒 WOL)")
    private String macAddress;

    @Schema(description = "固件类型 (默认 Klipper)")
    private String firmwareType = "Klipper";

    @Schema(description = "上位机 API 通信密钥")
    private String apiKey;
}
