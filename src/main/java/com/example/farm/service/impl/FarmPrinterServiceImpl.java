package com.example.farm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.dto.FarmPrinterAddDTO;
import com.example.farm.entity.dto.FarmPrinterQueryDTO;
import com.example.farm.entity.dto.FarmPrinterUpdateDTO;
import com.example.farm.mapper.FarmPrinterMapper;
import com.example.farm.service.FarmPrinterService;
import com.example.farm.service.PrinterCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FarmPrinterServiceImpl extends ServiceImpl<FarmPrinterMapper, FarmPrinter> implements FarmPrinterService {

    private final PrinterCacheService printerCacheService;

    @Override
    public Page<FarmPrinter> pagePrinters(FarmPrinterQueryDTO queryDTO) {
        Page<FarmPrinter> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<FarmPrinter> wrapper = new LambdaQueryWrapper<>();

        wrapper.like(StringUtils.hasText(queryDTO.getName()), FarmPrinter::getName, queryDTO.getName());
        wrapper.eq(StringUtils.hasText(queryDTO.getStatus()), FarmPrinter::getStatus, queryDTO.getStatus());
        wrapper.orderByDesc(FarmPrinter::getCreatedAt);

        return this.page(page, wrapper);
    }

    @Override
    public void addPrinter(FarmPrinterAddDTO dto) {
        long count = this.count(new LambdaQueryWrapper<FarmPrinter>()
                .eq(FarmPrinter::getIpAddress, dto.getIpAddress()));
        if (count > 0) {
            log.warn("新增打印机失败：IP 已存在，ip={}", dto.getIpAddress());
            throw new BusinessException("该 IP 地址的打印机已存在，请勿重复添加！");
        }

        FarmPrinter printer = new FarmPrinter();
        printer.setName(dto.getName());
        printer.setIpAddress(dto.getIpAddress());
        printer.setMacAddress(dto.getMacAddress());
        printer.setFirmwareType(dto.getFirmwareType());
        printer.setApiKey(dto.getApiKey());
        printer.setStatus("OFFLINE");

        this.save(printer);
        log.info("新增打印机成功：id={}, name={}, ip={}", printer.getId(), printer.getName(), printer.getIpAddress());

        printerCacheService.refreshPrinterCache();
    }

    @Override
    public void updatePrinter(FarmPrinterUpdateDTO dto) {
        FarmPrinter existingPrinter = this.getById(dto.getId());
        if (existingPrinter == null) {
            log.warn("更新打印机失败：打印机不存在，id={}", dto.getId());
            throw new BusinessException("该打印机不存在！");
        }

        if (dto.getIpAddress() != null && !dto.getIpAddress().equals(existingPrinter.getIpAddress())) {
            long count = this.count(new LambdaQueryWrapper<FarmPrinter>()
                    .eq(FarmPrinter::getIpAddress, dto.getIpAddress())
                    .ne(FarmPrinter::getId, dto.getId()));
            if (count > 0) {
                log.warn("更新打印机失败：目标 IP 被占用，id={}, ip={}", dto.getId(), dto.getIpAddress());
                throw new BusinessException("新的 IP 地址已被其他打印机占用！");
            }
        }

        existingPrinter.setName(dto.getName());
        existingPrinter.setIpAddress(dto.getIpAddress());
        existingPrinter.setMacAddress(dto.getMacAddress());
        existingPrinter.setFirmwareType(dto.getFirmwareType());
        existingPrinter.setApiKey(dto.getApiKey());
        existingPrinter.setCurrentMaterial(dto.getCurrentMaterial());
        existingPrinter.setNozzleSize(dto.getNozzleSize());

        this.updateById(existingPrinter);
        log.info("更新打印机成功：id={}, name={}, ip={}", existingPrinter.getId(), existingPrinter.getName(), existingPrinter.getIpAddress());

        printerCacheService.refreshPrinterCache();
    }

    @Override
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

    @Override
    public List<String> scanNewKlipperDevices(String subnet) {
        log.info("开始扫描局域网 Klipper 设备：subnet={}", subnet);

        // 先把已入库 IP 放到 Set，避免扫描结果重复入库并降低 contains 查询开销。
        Set<String> existingIps = this.list().stream()
                .map(FarmPrinter::getIpAddress)
                .collect(Collectors.toSet());

        ExecutorService executor = Executors.newFixedThreadPool(50);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        try {
            for (int i = 1; i <= 254; i++) {
                final String targetIp = subnet + "." + i;

                if (existingIps.contains(targetIp)) {
                    continue;
                }

                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    try (Socket socket = new Socket()) {
                        // 仅探测 Klipper 默认端口，快速过滤掉非目标主机，减少整体扫描时长。
                        socket.connect(new InetSocketAddress(targetIp, 7125), 200);
                        return targetIp;
                    } catch (Exception e) {
                        return null;
                    }
                }, executor);

                futures.add(future);
            }

            List<String> discovered = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.info("局域网扫描完成：发现新设备 {} 台，subnet={}", discovered.size(), subnet);
            return discovered;
        } finally {
            executor.shutdown();
        }
    }

    @Override
    public void batchAddPrinters(List<String> ipList) {
        if (ipList == null || ipList.isEmpty()) {
            log.warn("批量新增打印机跳过：IP 列表为空");
            return;
        }

        List<FarmPrinter> newPrinters = new ArrayList<>();
        for (String ip : ipList) {
            FarmPrinter printer = new FarmPrinter();
            printer.setName("Klipper-" + ip);
            printer.setIpAddress(ip);
            printer.setFirmwareType("Klipper");
            printer.setStatus("OFFLINE");
            printer.setCurrentMaterial("ABS");
            printer.setNozzleSize(new BigDecimal("1.20"));
            newPrinters.add(printer);
        }

        this.saveBatch(newPrinters);
        log.info("批量新增打印机成功：共 {} 台", newPrinters.size());

        printerCacheService.refreshPrinterCache();
    }
}
