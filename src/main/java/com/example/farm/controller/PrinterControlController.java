package com.example.farm.controller;

import com.example.farm.common.api.Result;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.common.utils.MoonrakerApiClient;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.service.FarmPrinterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 打印机硬件控制 API
 */
@RestController
@RequestMapping("/api/v1/control")
@RequiredArgsConstructor
public class PrinterControlController {

    private final FarmPrinterService printerService;

    private final MoonrakerApiClient moonrakerApiClient;

    /**
     * 紧急停止 (Emergency Stop)
     */
    @PostMapping("/{id}/emergency-stop")
    public Result<String> emergencyStop(@PathVariable Long id) {
        FarmPrinter printer = printerService.getById(id);
        if (printer == null) throw new BusinessException("打印机不存在");

        boolean success = moonrakerApiClient.emergencyStop(printer.getIpAddress());

        if (success) {
            return Result.success(null, "急停指令已发送，机器已锁死！");
        } else {
            return Result.failed("指令发送失败，机器可能已离线。");
        }
    }

    /**
     * 暂停当前打印任务
     */
    @PostMapping("/{id}/pause")
    public Result<String> pausePrint(@PathVariable Long id) {
        FarmPrinter printer = printerService.getById(id);
        if (printer == null) throw new BusinessException("打印机不存在");

        boolean success = moonrakerApiClient.pausePrint(printer.getIpAddress());

        if (success) {
            return Result.success(null, "已触发暂停动作。");
        } else {
            return Result.failed("指令发送失败。");
        }
    }
}