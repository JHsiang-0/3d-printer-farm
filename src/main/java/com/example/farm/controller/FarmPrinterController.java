package com.example.farm.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.farm.common.api.Result;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.dto.FarmPrinterAddDTO;
import com.example.farm.entity.dto.FarmPrinterQueryDTO;
import com.example.farm.entity.dto.FarmPrinterUpdateDTO;
import com.example.farm.service.FarmPrinterService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 打印机管理接口。
 * <p>提供打印机的分页查询、增删改、局域网扫描与批量录入能力。</p>
 */
@RestController
@RequestMapping("/api/v1/printers")
@RequiredArgsConstructor
public class FarmPrinterController {

    private final FarmPrinterService printerService;

    /**
     * 分页查询打印机列表。
     *
     * @param queryDTO 查询条件（页码、页大小、名称、状态）
     * @return 打印机分页结果
     */
    @GetMapping("/page")
    public Result<Page<FarmPrinter>> getPrinterPage(FarmPrinterQueryDTO queryDTO) {
        return Result.success(printerService.pagePrinters(queryDTO));
    }

    /**
     * 新增打印机。
     *
     * @param addDTO 新增参数
     * @return 新增结果
     * @throws BusinessException 当名称或 IP 为空，或 IP 已存在时抛出
     */
    @PostMapping("/add")
    public Result<String> addPrinter(@RequestBody FarmPrinterAddDTO addDTO) {
        if (!StringUtils.hasText(addDTO.getName()) || !StringUtils.hasText(addDTO.getIpAddress())) {
            throw new BusinessException("打印机名称和 IP 地址不能为空");
        }
        printerService.addPrinter(addDTO);
        return Result.success(null, "打印机添加成功");
    }

    /**
     * 更新打印机信息。
     *
     * @param updateDTO 更新参数
     * @return 更新结果
     * @throws BusinessException 当设备 ID 为空或设备不存在时抛出
     */
    @PutMapping("/update")
    public Result<String> updatePrinter(@RequestBody FarmPrinterUpdateDTO updateDTO) {
        if (updateDTO.getId() == null) {
            throw new BusinessException("设备 ID 不能为空");
        }
        printerService.updatePrinter(updateDTO);
        return Result.success(null, "打印机信息更新成功");
    }

    /**
     * 删除打印机。
     *
     * @param id 打印机 ID
     * @return 删除结果
     * @throws BusinessException 当打印机不存在或处于打印中时抛出
     */
    @DeleteMapping("/delete/{id}")
    public Result<String> deletePrinter(@PathVariable Long id) {
        printerService.deletePrinter(id);
        return Result.success(null, "打印机删除成功");
    }

    /**
     * 扫描指定网段中的 Klipper 设备。
     *
     * @param subnet 网段前缀，例如 `192.168.1`
     * @return 新发现设备 IP 列表
     * @throws BusinessException 当 subnet 为空时抛出
     */
    @GetMapping("/scan")
    public Result<List<String>> scanDevices(@RequestParam String subnet) {
        if (!StringUtils.hasText(subnet)) {
            throw new BusinessException("必须提供网段前缀，例如 192.168.1");
        }
        List<String> discoveredIps = printerService.scanNewKlipperDevices(subnet);
        return Result.success(discoveredIps, "扫描完成，发现 " + discoveredIps.size() + " 台新设备");
    }

    /**
     * 批量新增打印机。
     *
     * @param ips 打印机 IP 列表
     * @return 批量录入结果
     * @throws BusinessException 当 IP 列表为空时抛出
     */
    @PostMapping("/batch-add")
    public Result<String> batchAdd(@RequestBody List<String> ips) {
        if (ips == null || ips.isEmpty()) {
            throw new BusinessException("IP 列表不能为空");
        }
        printerService.batchAddPrinters(ips);
        return Result.success(null, "成功批量录入 " + ips.size() + " 台设备");
    }
}