package com.example.farm.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.farm.common.api.Result;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.dto.FarmPrinterAddDTO;
import com.example.farm.entity.dto.FarmPrinterQueryDTO;
import com.example.farm.entity.dto.FarmPrinterUpdateDTO;
import com.example.farm.service.FarmPrinterService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/printers")
@RequiredArgsConstructor
public class FarmPrinterController {

    private final FarmPrinterService printerService;

    /**
     * 获取打印机分页列表 (大屏监控核心接口)
     * GET /api/v1/printers/page?pageNum=1&pageSize=20&status=PRINTING
     */
    @GetMapping("/page")
    public Result<Page<FarmPrinter>> getPrinterPage(FarmPrinterQueryDTO queryDTO) {
        // 调用 Service 获取分页数据
        Page<FarmPrinter> pageData = printerService.pagePrinters(queryDTO);

        // 包装成统一的 JSON 格式返回给前端
        return Result.success(pageData);
    }
    /**
     * 添加打印机 (设备入库)
     * POST /api/v1/printers/add
     */
    @PostMapping("/add")
    public Result<String> addPrinter(@RequestBody FarmPrinterAddDTO addDTO) {
        // 简单的基础校验 (实际企业开发中推荐引入 spring-boot-starter-validation 做注解校验)
        if (addDTO.getName() == null || addDTO.getIpAddress() == null) {
            return Result.failed("打印机名称和 IP 地址不能为空");
        }

        // 调用业务逻辑
        printerService.addPrinter(addDTO);

        return Result.success(null, "打印机添加成功！");
    }

    /**
     * 修改打印机信息
     * PUT /api/v1/printers/update
     */
    @PutMapping("/update")
    public Result<String> updatePrinter(@RequestBody FarmPrinterUpdateDTO updateDTO) {
        if (updateDTO.getId() == null) {
            return Result.failed("设备 ID 不能为空");
        }
        printerService.updatePrinter(updateDTO);
        return Result.success(null, "打印机信息更新成功！");
    }

    /**
     * 删除打印机
     * DELETE /api/v1/printers/delete/{id}
     */
    @DeleteMapping("/delete/{id}")
    public Result<String> deletePrinter(@PathVariable Long id) {
        printerService.deletePrinter(id);
        return Result.success(null, "打印机删除成功！");
    }

    /**
     * 极速扫描局域网内的 Klipper 设备
     * GET /api/v1/printers/scan?subnet=192.168.1
     */
    @GetMapping("/scan")
    public Result<List<String>> scanDevices(@RequestParam String subnet) {
        if (!StringUtils.hasText(subnet)) {
            return Result.failed("必须提供网段前缀，例如 192.168.1");
        }
        List<String> discoveredIps = printerService.scanNewKlipperDevices(subnet);
        return Result.success(discoveredIps, "扫描完成，发现 " + discoveredIps.size() + " 台新设备！");
    }

    /**
     * 一键批量入库
     * POST /api/v1/printers/batch-add
     * Body: ["192.168.1.101", "192.168.1.102"]
     */
    @PostMapping("/batch-add")
    public Result<String> batchAdd(@RequestBody List<String> ips) {
        printerService.batchAddPrinters(ips);
        return Result.success(null, "成功批量录入 " + ips.size() + " 台设备！");
    }
}