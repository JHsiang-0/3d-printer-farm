package com.example.farm.entity.dto;

import lombok.Data;

/**
 * 接收 Moonraker 打印机实时状态的 DTO
 */
@Data
public class MoonrakerStatusDTO {
    // 喷嘴当前温度
    private Double nozzleTemp;
    // 喷嘴目标温度
    private Double nozzleTarget;

    // 热床当前温度
    private Double bedTemp;
    // 热床目标温度
    private Double bedTarget;

    // 打印进度 (0.00 - 100.00)
    private Double progress;

    // 机器状态: standby(待机), printing(打印中), paused(暂停), error(故障), complete(完成)
    private String state;
}