package com.example.farm.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.farm.common.api.Result;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.dto.FarmPrinterAddDTO;
import com.example.farm.entity.dto.FarmPrinterQueryDTO;
import com.example.farm.entity.dto.FarmPrinterUpdateDTO;
import com.example.farm.entity.dto.PrinterPositionUpdateDTO;
import com.example.farm.entity.dto.PrinterScanResultDTO;
import com.example.farm.service.FarmPrinterService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 打印机管理接口。
 * <p>提供打印机的分页查询、增删改、局域网扫描与批量录入能力。</p>
 * <p><b>【重构重点】基于 MAC 地址的 Upsert 机制，解决 DHCP 动态分配导致的设备重复录入问题</b></p>
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
     * 【重构核心】新增打印机 - 基于 MAC 地址的 Upsert 机制
     * <p>业务逻辑：</p>
     * <ul>
     *     <li>如果提供了 MAC 地址 → 按 MAC 查询数据库</li>
     *     <li>MAC 存在 → 更新该设备的 IP 和状态为 ONLINE（设备换了 IP 重新上线）</li>
     *     <li>MAC 不存在 → 检查 IP 是否被占用，如果被占用先释放旧设备，然后插入新记录</li>
     * </ul>
     *
     * @param addDTO 新增参数（ipAddress 必填，macAddress 可选，系统会自动获取）
     * @return 新增/更新结果
     * @throws BusinessException 当 IP 为空或处理失败时抛出
     */
    @PostMapping("/add")
    public Result<String> addPrinter(@RequestBody FarmPrinterAddDTO addDTO) {
        if (!StringUtils.hasText(addDTO.getIpAddress())) {
            throw new BusinessException("打印机 IP 地址不能为空");
        }

        // 调用重构后的 Service 方法（内部实现 Upsert 逻辑）
        printerService.addPrinter(addDTO);

        return Result.success(null, "打印机添加/更新成功");
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
     * 【重构核心】扫描指定网段中的 Klipper 设备，返回带 MAC 地址的详细信息
     * <p>相比旧版 scan 接口，新版会：</p>
     * <ol>
     *     <li>扫描网段内所有 IP 的 7125 端口</li>
     *     <li>尝试获取每个设备的 MAC 地址（通过 ARP 表或 Moonraker API）</li>
     *     <li>判断设备是新设备还是已知设备（基于 MAC 地址）</li>
     * </ol>
     *
     * @param subnet 网段前缀，例如 `192.168.1`
     * @return 扫描结果列表（包含 IP、MAC、是否为新设备等）
     * @throws BusinessException 当 subnet 为空时抛出
     */
    @GetMapping("/scan")
    public Result<List<PrinterScanResultDTO>> scanDevices(@RequestParam String subnet) {
        if (!StringUtils.hasText(subnet)) {
            throw new BusinessException("必须提供网段前缀，例如 192.168.1");
        }

        List<PrinterScanResultDTO> results = printerService.scanKlipperDevices(subnet);

        int newCount = (int) results.stream()
                .filter(PrinterScanResultDTO::getIsNewDevice)
                .count();
        int existingCount = results.size() - newCount;

        String message = String.format("扫描完成，发现 %d 台设备（新设备 %d 台，已知设备 %d 台）",
                results.size(), newCount, existingCount);

        return Result.success(results, message);
    }

    /**
     * 【兼容旧版】扫描指定网段中的新 Klipper 设备（仅返回 IP 列表）
     * <p>用于向后兼容旧版客户端</p>
     *
     * @param subnet 网段前缀，例如 `192.168.1`
     * @return 新发现设备 IP 列表
     * @deprecated 请使用 {@link #scanDevices(String)} 替代
     */
    @GetMapping("/scan-legacy")
    @Deprecated
    public Result<List<String>> scanDevicesLegacy(@RequestParam String subnet) {
        if (!StringUtils.hasText(subnet)) {
            throw new BusinessException("必须提供网段前缀，例如 192.168.1");
        }

        List<String> discoveredIps = printerService.scanNewKlipperDevices(subnet);
        return Result.success(discoveredIps, "扫描完成，发现 " + discoveredIps.size() + " 台新设备");
    }

    /**
     * 【重构核心】批量新增/更新打印机（基于 MAC 地址的 Upsert 机制）
     * <p>这是解决 DHCP 问题的关键 API：</p>
     * <ul>
     *     <li>接收扫描结果列表（包含 IP 和 MAC）</li>
     *     <li>根据 MAC 地址判断是更新还是插入</li>
     *     <li>MAC 存在 → 更新 IP 和状态为 ONLINE（设备换了 IP 重新上线）</li>
     *     <li>MAC 不存在 → 插入新记录（真正的新设备）</li>
     * </ul>
     *
     * @param scanResults 扫描结果列表（从 /scan 接口获取）
     * @return 批量操作结果统计
     * @throws BusinessException 当列表为空时抛出
     */
    @PostMapping("/batch-add")
    public Result<FarmPrinterService.BatchUpsertResult> batchAdd(
            @RequestBody List<PrinterScanResultDTO> scanResults) {
        if (scanResults == null || scanResults.isEmpty()) {
            throw new BusinessException("设备列表不能为空");
        }

        FarmPrinterService.BatchUpsertResult result = printerService.batchUpsertPrinters(scanResults);

        return Result.success(result, result.getMessage());
    }

    /**
     * 【兼容旧版】批量新增打印机（直接使用 IP 列表）
     * <p>不检查 MAC 地址，直接按 IP 添加。建议迁移到新的 batch-add 接口</p>
     *
     * @param ips 打印机 IP 列表
     * @return 批量录入结果
     * @deprecated 请使用 {@link #batchAdd(List)} 替代
     */
    @PostMapping("/batch-add-legacy")
    @Deprecated
    public Result<String> batchAddLegacy(@RequestBody List<String> ips) {
        if (ips == null || ips.isEmpty()) {
            throw new BusinessException("IP 列表不能为空");
        }
        printerService.batchAddPrinters(ips);
        return Result.success(null, "成功批量录入 " + ips.size() + " 台设备");
    }

    /**
     * 根据 MAC 地址查询打印机
     *
     * @param macAddress MAC 地址
     * @return 打印机信息
     */
    @GetMapping("/by-mac/{macAddress}")
    public Result<FarmPrinter> getByMacAddress(@PathVariable String macAddress) {
        if (!StringUtils.hasText(macAddress)) {
            throw new BusinessException("MAC 地址不能为空");
        }
        FarmPrinter printer = printerService.getByMacAddress(macAddress);
        if (printer == null) {
            return Result.success(null, "未找到该 MAC 地址的设备");
        }
        return Result.success(printer);
    }

    /**
     * 根据 IP 地址查询打印机
     *
     * @param ipAddress IP 地址
     * @return 打印机信息
     */
    @GetMapping("/by-ip/{ipAddress}")
    public Result<FarmPrinter> getByIpAddress(@PathVariable String ipAddress) {
        if (!StringUtils.hasText(ipAddress)) {
            throw new BusinessException("IP 地址不能为空");
        }
        FarmPrinter printer = printerService.getByIpAddress(ipAddress);
        if (printer == null) {
            return Result.success(null, "未找到该 IP 地址的设备");
        }
        return Result.success(printer);
    }

    /**
     * 【新增】批量更新打印机物理位置坐标（用于数字孪生看板拖拽）
     * <p>接收前端在数字孪生看板上拖拽后产生的坐标变更，批量更新设备的 grid_row 和 grid_col。</p>
     * <p>业务规则：</p>
     * <ul>
     *     <li>gridRow 范围：1-4</li>
     *     <li>gridCol 范围：1-12</li>
     *     <li>传入 null 表示将该设备移回待分配区</li>
     * </ul>
     *
     * @param positionUpdates 位置更新列表（包含 id, gridRow, gridCol）
     * @return 更新结果（成功更新的设备数量）
     * @throws BusinessException 当参数为空时抛出
     */
    @PutMapping("/positions")
    public Result<String> batchUpdatePositions(
            @RequestBody List<PrinterPositionUpdateDTO> positionUpdates) {
        if (positionUpdates == null || positionUpdates.isEmpty()) {
            throw new BusinessException("位置更新列表不能为空");
        }

        int successCount = printerService.batchUpdatePositions(positionUpdates);

        String message = String.format("成功更新 %d/%d 台设备的位置",
                successCount, positionUpdates.size());

        return Result.success(null, message);
    }
}