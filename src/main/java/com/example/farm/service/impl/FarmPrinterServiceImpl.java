package com.example.farm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.common.utils.MacAddressUtil;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.dto.FarmPrinterAddDTO;
import com.example.farm.entity.dto.FarmPrinterQueryDTO;
import com.example.farm.entity.dto.FarmPrinterUpdateDTO;
import com.example.farm.entity.dto.PrinterScanResultDTO;
import com.example.farm.mapper.FarmPrinterMapper;
import com.example.farm.service.FarmPrinterService;
import com.example.farm.service.PrinterCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 打印机服务实现类
 * <p>核心业务：基于 MAC 地址的 Upsert 机制，解决 DHCP 动态分配导致的设备重复录入问题</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FarmPrinterServiceImpl extends ServiceImpl<FarmPrinterMapper, FarmPrinter> implements FarmPrinterService {

    private final PrinterCacheService printerCacheService;
    private final MacAddressUtil macAddressUtil;

    // ==================== 基础 CRUD 操作 ====================

    @Override
    public Page<FarmPrinter> pagePrinters(FarmPrinterQueryDTO queryDTO) {
        Page<FarmPrinter> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<FarmPrinter> wrapper = new LambdaQueryWrapper<>();

        wrapper.like(StringUtils.hasText(queryDTO.getName()), FarmPrinter::getName, queryDTO.getName());
        wrapper.eq(StringUtils.hasText(queryDTO.getStatus()), FarmPrinter::getStatus, queryDTO.getStatus());
        wrapper.orderByDesc(FarmPrinter::getCreatedAt);

        return this.page(page, wrapper);
    }

    /**
     * 【重构核心】新增打印机 - 基于 MAC 地址的 Upsert 机制
     * <p>业务逻辑：</p>
     * <ol>
     *     <li>如果提供了 MAC 地址，先按 MAC 查询数据库</li>
     *     <li>MAC 存在 → 更新该设备的 IP 和状态（设备换了 IP 重新上线）</li>
     *     <li>MAC 不存在 → 检查 IP 是否被占用，如果被占用先释放，然后插入新记录</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addPrinter(FarmPrinterAddDTO dto) {
        // 参数校验
        if (!StringUtils.hasText(dto.getIpAddress())) {
            throw new BusinessException("IP 地址不能为空");
        }

        String ipAddress = dto.getIpAddress();
        String macAddress = dto.getMacAddress();

        // 步骤 1: 尝试获取 MAC 地址（如果前端没传）
        if (!StringUtils.hasText(macAddress)) {
            macAddress = macAddressUtil.getMacAddress(ipAddress);
            log.info("自动获取到设备 MAC 地址: IP={}, MAC={}", ipAddress, macAddress);
        } else {
            // 标准化 MAC 地址格式
            macAddress = macAddressUtil.normalizeMacAddress(macAddress);
        }

        // 步骤 2: 如果有 MAC 地址，执行 Upsert 逻辑
        if (StringUtils.hasText(macAddress)) {
            upsertPrinterByMac(dto, macAddress);
        } else {
            // 没有 MAC 地址时的降级处理（兼容旧逻辑）
            fallbackAddWithoutMac(dto);
        }

        // 刷新缓存
        printerCacheService.refreshPrinterCache();
    }

    /**
     * 基于 MAC 地址的 Upsert 核心逻辑
     */
    private void upsertPrinterByMac(FarmPrinterAddDTO dto, String macAddress) {
        String ipAddress = dto.getIpAddress();

        // 查询是否已存在该 MAC 地址的设备
        FarmPrinter existingPrinter = baseMapper.selectByMacAddress(macAddress);

        if (existingPrinter != null) {
            // ========== MAC 已存在：更新现有设备 ==========
            log.info("检测到已知设备重新上线: MAC={}, 旧IP={}, 新IP={}",
                    macAddress, existingPrinter.getIpAddress(), ipAddress);

            // 如果 IP 发生变化，需要处理 IP 冲突
            if (!ipAddress.equals(existingPrinter.getIpAddress())) {
                // 检查新 IP 是否被其他设备占用
                releaseIpIfOccupied(ipAddress, macAddress);
            }

            // 更新设备信息
            existingPrinter.setIpAddress(ipAddress);
            existingPrinter.setStatus("ONLINE");
            existingPrinter.setUpdatedAt(LocalDateTime.now());

            // 可选更新字段
            if (StringUtils.hasText(dto.getName())) {
                existingPrinter.setName(dto.getName());
            }
            if (StringUtils.hasText(dto.getFirmwareType())) {
                existingPrinter.setFirmwareType(dto.getFirmwareType());
            }
            if (StringUtils.hasText(dto.getApiKey())) {
                existingPrinter.setApiKey(dto.getApiKey());
            }

            this.updateById(existingPrinter);
            log.info("更新已知设备成功: ID={}, MAC={}, IP={}",
                    existingPrinter.getId(), macAddress, ipAddress);

        } else {
            // ========== MAC 不存在：插入新设备 ==========
            log.info("发现新设备: MAC={}, IP={}", macAddress, ipAddress);

            // 防 IP 冲突处理
            releaseIpIfOccupied(ipAddress, macAddress);

            // 创建新设备
            FarmPrinter newPrinter = new FarmPrinter();
            newPrinter.setName(StringUtils.hasText(dto.getName())
                    ? dto.getName()
                    : macAddressUtil.generateDefaultPrinterName(macAddress));
            newPrinter.setIpAddress(ipAddress);
            newPrinter.setMacAddress(macAddress);
            newPrinter.setFirmwareType(StringUtils.hasText(dto.getFirmwareType())
                    ? dto.getFirmwareType() : "Klipper");
            newPrinter.setApiKey(dto.getApiKey());
            newPrinter.setStatus("ONLINE");
            newPrinter.setCurrentMaterial("ABS");
            newPrinter.setNozzleSize(new BigDecimal("0.40"));
            newPrinter.setCreatedAt(LocalDateTime.now());
            newPrinter.setUpdatedAt(LocalDateTime.now());

            this.save(newPrinter);
            log.info("新增设备成功: ID={}, MAC={}, IP={}",
                    newPrinter.getId(), macAddress, ipAddress);
        }
    }

    /**
     * 无 MAC 地址时的降级处理（兼容旧逻辑）
     */
    private void fallbackAddWithoutMac(FarmPrinterAddDTO dto) {
        log.warn("无法获取设备 MAC 地址，使用 IP 作为唯一标识进行添加: IP={}", dto.getIpAddress());

        // 检查 IP 是否已存在
        long count = this.count(new LambdaQueryWrapper<FarmPrinter>()
                .eq(FarmPrinter::getIpAddress, dto.getIpAddress()));
        if (count > 0) {
            throw new BusinessException("该 IP 地址的打印机已存在！");
        }

        FarmPrinter printer = new FarmPrinter();
        printer.setName(dto.getName());
        printer.setIpAddress(dto.getIpAddress());
        printer.setFirmwareType(dto.getFirmwareType());
        printer.setApiKey(dto.getApiKey());
        printer.setStatus("OFFLINE");
        printer.setCreatedAt(LocalDateTime.now());
        printer.setUpdatedAt(LocalDateTime.now());

        this.save(printer);
        log.info("新增打印机成功（无 MAC）: id={}, ip={}", printer.getId(), printer.getIpAddress());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePrinter(FarmPrinterUpdateDTO dto) {
        FarmPrinter existingPrinter = this.getById(dto.getId());
        if (existingPrinter == null) {
            log.warn("更新打印机失败：打印机不存在，id={}", dto.getId());
            throw new BusinessException("该打印机不存在！");
        }

        // 如果修改了 IP，需要处理 IP 冲突
        if (dto.getIpAddress() != null && !dto.getIpAddress().equals(existingPrinter.getIpAddress())) {
            String currentMac = existingPrinter.getMacAddress();
            releaseIpIfOccupied(dto.getIpAddress(), currentMac);
        }

        existingPrinter.setName(dto.getName());
        existingPrinter.setIpAddress(dto.getIpAddress());
        existingPrinter.setMacAddress(dto.getMacAddress());
        existingPrinter.setFirmwareType(dto.getFirmwareType());
        existingPrinter.setApiKey(dto.getApiKey());
        existingPrinter.setCurrentMaterial(dto.getCurrentMaterial());
        existingPrinter.setNozzleSize(dto.getNozzleSize());
        existingPrinter.setUpdatedAt(LocalDateTime.now());

        this.updateById(existingPrinter);
        log.info("更新打印机成功：id={}, name={}, ip={}",
                existingPrinter.getId(), existingPrinter.getName(), existingPrinter.getIpAddress());

        printerCacheService.refreshPrinterCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePrinter(Long id) {
        FarmPrinter printer = this.getById(id);
        if (printer == null) {
            log.warn("删除打印机失败：打印机不存在，id={}", id);
            throw new BusinessException("打印机不存在或已被删除！");
        }

        if ("PRINTING".equals(printer.getStatus())) {
            log.warn("删除打印机被阻止：打印机正在打印中，id={}, name={}", id, printer.getName());
            throw new BusinessException("危险操作：该机器正在打印中，无法删除！请先中止打印任务。");
        }

        this.removeById(id);
        log.info("删除打印机成功：id={}, name={}", id, printer.getName());

        printerCacheService.refreshPrinterCache();
    }

    // ==================== 扫描与批量操作 ====================

    /**
     * 【重构核心】扫描网段内的 Klipper 设备，返回带 MAC 地址的详细信息
     * <p>步骤：</p>
     * <ol>
     *     <li>并发扫描网段内所有 IP 的 7125 端口</li>
     *     <li>对响应的设备尝试获取 MAC 地址（ARP 表或 Moonraker API）</li>
     *     <li>查询数据库判断是新设备还是已知设备</li>
     * </ol>
     */
    @Override
    public List<PrinterScanResultDTO> scanKlipperDevices(String subnet) {
        log.info("开始扫描局域网 Klipper 设备：subnet={}", subnet);

        // 获取数据库中所有已存在的 MAC 地址（用于判断新旧设备）
        Set<String> existingMacs = this.list().stream()
                .map(FarmPrinter::getMacAddress)
                .filter(StringUtils::hasText)
                .map(mac -> macAddressUtil.normalizeMacAddress(mac))
                .collect(Collectors.toSet());

        ExecutorService executor = Executors.newFixedThreadPool(50);
        List<CompletableFuture<PrinterScanResultDTO>> futures = new ArrayList<>();

        try {
            for (int i = 1; i <= 254; i++) {
                final String targetIp = subnet + "." + i;

                CompletableFuture<PrinterScanResultDTO> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // 步骤 1: 探测 Klipper 端口
                        if (!isKlipperDevice(targetIp)) {
                            return null;
                        }

                        // 步骤 2: 获取 MAC 地址
                        String macAddress = macAddressUtil.getMacAddress(targetIp);

                        // 步骤 3: 构建扫描结果
                        PrinterScanResultDTO result = new PrinterScanResultDTO();
                        result.setIpAddress(targetIp);
                        result.setMacAddress(macAddress);
                        result.setFirmwareType("Klipper");

                        if (StringUtils.hasText(macAddress)) {
                            String normalizedMac = macAddressUtil.normalizeMacAddress(macAddress);
                            boolean isNew = !existingMacs.contains(normalizedMac);
                            result.setIsNewDevice(isNew);
                            result.setStatus(isNew ? "NEW" : "EXISTING");
                            result.setSuggestedName(macAddressUtil.generateDefaultPrinterName(macAddress));
                        } else {
                            // 无法获取 MAC，标记为需要手动处理
                            result.setIsNewDevice(true);
                            result.setStatus("UNKNOWN_MAC");
                            result.setSuggestedName("Printer_" + targetIp.substring(targetIp.lastIndexOf('.') + 1));
                        }

                        return result;

                    } catch (Exception e) {
                        log.debug("扫描设备异常: IP={}, 原因={}", targetIp, e.getMessage());
                        return null;
                    }
                }, executor);

                futures.add(future);
            }

            // 收集扫描结果
            List<PrinterScanResultDTO> results = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            int newCount = (int) results.stream().filter(r -> Boolean.TRUE.equals(r.getIsNewDevice())).count();
            int existingCount = results.size() - newCount;

            log.info("局域网扫描完成：总发现 {} 台设备，其中新设备 {} 台，已知设备 {} 台，subnet={}",
                    results.size(), newCount, existingCount, subnet);

            return results;

        } finally {
            executor.shutdown();
        }
    }

    /**
     * 检测目标 IP 是否为 Klipper 设备
     */
    private boolean isKlipperDevice(String ipAddress) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipAddress, 7125), 200);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 【兼容旧版】仅返回新设备的 IP 列表
     */
    @Override
    @Deprecated
    public List<String> scanNewKlipperDevices(String subnet) {
        List<PrinterScanResultDTO> allResults = scanKlipperDevices(subnet);
        return allResults.stream()
                .filter(PrinterScanResultDTO::getIsNewDevice)
                .map(PrinterScanResultDTO::getIpAddress)
                .collect(Collectors.toList());
    }

    /**
     * 【重构核心】批量新增/更新打印机（基于 MAC 地址的 Upsert 机制）
     * <p>这是解决 DHCP 问题的关键方法：</p>
     * <ul>
     *     <li>每个设备通过 MAC 地址唯一标识</li>
     *     <li>MAC 存在 → 更新 IP 和状态（设备换了 IP 重新上线）</li>
     *     <li>MAC 不存在 → 插入新记录（真正的新设备）</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchUpsertResult batchUpsertPrinters(List<PrinterScanResultDTO> scanResults) {
        if (scanResults == null || scanResults.isEmpty()) {
            log.warn("批量 Upsert 跳过：扫描结果为空");
            return new BatchUpsertResult(0, 0, 0, 0);
        }

        int totalCount = scanResults.size();
        int insertedCount = 0;
        int updatedCount = 0;
        int failedCount = 0;

        log.info("开始批量 Upsert 打印机：共 {} 台设备", totalCount);

        // 逐个处理每台设备
        for (PrinterScanResultDTO result : scanResults) {
            try {
                processSingleDevice(result, insertedCount, updatedCount);
                if (Boolean.TRUE.equals(result.getIsNewDevice())) {
                    insertedCount++;
                } else {
                    updatedCount++;
                }
            } catch (Exception e) {
                log.error("处理设备失败: IP={}, MAC={}, 原因={}",
                        result.getIpAddress(), result.getMacAddress(), e.getMessage());
                failedCount++;
            }
        }

        // 刷新缓存
        printerCacheService.refreshPrinterCache();

        BatchUpsertResult batchResult = new BatchUpsertResult(totalCount, insertedCount, updatedCount, failedCount);
        batchResult.setMessage(String.format("批量处理完成：新增 %d 台，更新 %d 台，失败 %d 台",
                insertedCount, updatedCount, failedCount));

        log.info("批量 Upsert 完成: {}", batchResult);
        return batchResult;
    }

    /**
     * 处理单台设备的 Upsert 逻辑
     */
    private void processSingleDevice(PrinterScanResultDTO result, int insertedCount, int updatedCount) {
        String ipAddress = result.getIpAddress();
        String macAddress = result.getMacAddress();

        // 如果没有 MAC 地址，尝试获取
        if (!StringUtils.hasText(macAddress)) {
            macAddress = macAddressUtil.getMacAddress(ipAddress);
            result.setMacAddress(macAddress);
        } else {
            macAddress = macAddressUtil.normalizeMacAddress(macAddress);
        }

        // 步骤 1: 防 IP 冲突处理
        releaseIpIfOccupied(ipAddress, macAddress);

        // 步骤 2: 基于 MAC 的 Upsert
        if (StringUtils.hasText(macAddress)) {
            // 有 MAC 地址，执行真正的 Upsert
            FarmPrinter printer = buildPrinterFromScanResult(result);
            baseMapper.upsertByMacAddress(printer);
        } else {
            // 无 MAC 地址，降级为普通插入
            log.warn("设备无 MAC 地址，降级处理: IP={}", ipAddress);
            FarmPrinter printer = buildPrinterFromScanResult(result);
            printer.setMacAddress(null);
            this.save(printer);
        }
    }

    /**
     * 从扫描结果构建设备实体
     */
    private FarmPrinter buildPrinterFromScanResult(PrinterScanResultDTO result) {
        FarmPrinter printer = new FarmPrinter();

        // 名称优先使用建议名称，否则自动生成
        String name = StringUtils.hasText(result.getSuggestedName())
                ? result.getSuggestedName()
                : macAddressUtil.generateDefaultPrinterName(result.getMacAddress());
        printer.setName(name);

        printer.setIpAddress(result.getIpAddress());
        printer.setMacAddress(macAddressUtil.normalizeMacAddress(result.getMacAddress()));
        printer.setFirmwareType(StringUtils.hasText(result.getFirmwareType())
                ? result.getFirmwareType() : "Klipper");
        printer.setApiKey(result.getApiKey());
        printer.setStatus("ONLINE");
        printer.setCurrentMaterial("ABS");
        printer.setNozzleSize(new BigDecimal("0.40"));
        printer.setCreatedAt(LocalDateTime.now());
        printer.setUpdatedAt(LocalDateTime.now());

        return printer;
    }

    /**
     * 【兼容旧版】批量新增打印机（不检查 MAC）
     */
    @Override
    @Deprecated
    public void batchAddPrinters(List<String> ipList) {
        if (ipList == null || ipList.isEmpty()) {
            log.warn("批量新增打印机跳过：IP 列表为空");
            return;
        }

        List<FarmPrinter> newPrinters = new ArrayList<>();
        for (String ip : ipList) {
            // 尝试获取 MAC 地址
            String mac = macAddressUtil.getMacAddress(ip);

            FarmPrinter printer = new FarmPrinter();
            printer.setName(StringUtils.hasText(mac)
                    ? macAddressUtil.generateDefaultPrinterName(mac)
                    : "Klipper-" + ip);
            printer.setIpAddress(ip);
            printer.setMacAddress(macAddressUtil.normalizeMacAddress(mac));
            printer.setFirmwareType("Klipper");
            printer.setStatus("OFFLINE");
            printer.setCurrentMaterial("ABS");
            printer.setNozzleSize(new BigDecimal("1.20"));
            printer.setCreatedAt(LocalDateTime.now());
            printer.setUpdatedAt(LocalDateTime.now());

            newPrinters.add(printer);
        }

        // 使用 MyBatis Plus 的 saveBatch
        this.saveBatch(newPrinters);
        log.info("批量新增打印机成功：共 {} 台", newPrinters.size());

        printerCacheService.refreshPrinterCache();
    }

    // ==================== 辅助查询方法 ====================

    @Override
    public FarmPrinter getByMacAddress(String macAddress) {
        if (!StringUtils.hasText(macAddress)) {
            return null;
        }
        String normalizedMac = macAddressUtil.normalizeMacAddress(macAddress);
        return baseMapper.selectByMacAddress(normalizedMac);
    }

    @Override
    public FarmPrinter getByIpAddress(String ipAddress) {
        if (!StringUtils.hasText(ipAddress)) {
            return null;
        }
        return baseMapper.selectByIpAddress(ipAddress);
    }

    /**
     * 【核心防冲突逻辑】释放被占用的 IP 地址
     * <p>当新设备要使用某个 IP 时，先将占用该 IP 的旧设备下线（IP 设为 NULL，状态设为 OFFLINE）</p>
     *
     * @param ipAddress 要使用的 IP 地址
     * @param excludeMac 当前要使用该 IP 的设备 MAC（排除自己）
     * @return 是否成功释放了其他设备的 IP
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean releaseIpAddress(String ipAddress, String excludeMac) {
        return releaseIpIfOccupied(ipAddress, excludeMac);
    }

    /**
     * 内部方法：如果 IP 被占用则释放
     */
    private boolean releaseIpIfOccupied(String ipAddress, String excludeMac) {
        // 查询占用该 IP 的其他设备
        FarmPrinter occupiedPrinter = baseMapper.selectByIpAddress(ipAddress);

        if (occupiedPrinter != null) {
            String occupiedMac = occupiedPrinter.getMacAddress();

            // 如果是同一台设备（MAC 相同），不需要释放
            if (StringUtils.hasText(occupiedMac) && occupiedMac.equals(excludeMac)) {
                return false;
            }

            // 释放该 IP：将旧设备的 IP 设为 NULL，状态设为 OFFLINE
            log.warn("检测到 IP 冲突，释放旧设备 IP: IP={}, 旧设备ID={}, 旧设备MAC={}",
                    ipAddress, occupiedPrinter.getId(), occupiedMac);

            int affected = baseMapper.releaseIpAddress(ipAddress, excludeMac);

            if (affected > 0) {
                log.info("成功释放 IP: {}, 影响行数: {}", ipAddress, affected);
                return true;
            }
        }

        return false;
    }
}