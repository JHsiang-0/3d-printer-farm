package com.example.farm.task;

import com.example.farm.controller.FarmWebSocketServer;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.dto.MoonrakerStatusDTO;
import com.example.farm.service.FarmPrinterService;
import com.example.farm.service.PrinterCacheService;
import com.example.farm.common.utils.MoonrakerApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 打印机监控任务
 * 定期获取打印机状态并推送至前端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrinterMonitorTask {

    private final FarmPrinterService printerService;
    private final PrinterCacheService printerCacheService;
    private final MoonrakerApiClient moonrakerApiClient;

    // 并发线程池，用于并行查询多个打印机状态
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * 每5秒执行一次状态监控
     */
    @Scheduled(fixedRate = 5000)
    public void checkPrinterStatus() {
        List<FarmPrinter> printers = printerService.list();
        if (printers.isEmpty()) {
            return;
        }

        // 并行查询所有打印机状态
        List<CompletableFuture<PrinterStatusResult>> futures = printers.stream()
                .map(printer -> CompletableFuture.supplyAsync(
                        () -> fetchAndUpdateStatus(printer), executorService))
                .collect(Collectors.toList());

        // 等待所有查询完成
        List<PrinterStatusResult> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // 批量推送到前端
        for (PrinterStatusResult result : results) {
            if (result.status != null) {
                pushToFrontend(result.printerId, result.status);
            }
        }

        // 统计信息（每30秒输出一次日志）
        long onlineCount = results.stream().filter(r -> r.online).count();
        long offlineCount = results.size() - onlineCount;
        if (System.currentTimeMillis() % 30000 < 5000) {
            log.info("📊 打印机状态扫描完成：在线 {} 台，离线 {} 台", onlineCount, offlineCount);
        }
    }

    /**
     * 获取并更新单个打印机状态
     */
    private PrinterStatusResult fetchAndUpdateStatus(FarmPrinter printer) {
        Long printerId = printer.getId();
        String printerName = printer.getName();

        try {
            // 1. 从打印机获取实时状态
            MoonrakerStatusDTO status = moonrakerApiClient.getPrinterStatus(printer.getIpAddress());

            if (status != null) {
                // 2. 缓存状态到 Redis（无论数据库是否更新成功，都先缓存）
                printerCacheService.cachePrinterStatus(printerId, status);

                // 3. 检查状态是否变化，需要更新数据库
                String newDbStatus = determineDbStatus(status.getState());
                if (!newDbStatus.equals(printer.getStatus())) {
                    // 使用分布式锁更新数据库，避免并发冲突
                    printer.setStatus(newDbStatus);
                    boolean updated = printerCacheService.updatePrinterStatusWithLock(printer);
                    if (updated) {
                        log.info("🔄 打印机 [{}] 状态变更: {} → {}", 
                                printerName, printer.getStatus(), newDbStatus);
                    }
                }

                return new PrinterStatusResult(printerId, status, true);

            } else {
                // 打印机离线
                handleOfflinePrinter(printer);
                return new PrinterStatusResult(printerId, null, false);
            }

        } catch (Exception e) {
            log.error("获取打印机 [{}] 状态失败: {}", printerName, e.getMessage());
            return new PrinterStatusResult(printerId, null, false);
        }
    }

    /**
     * 处理离线打印机
     */
    private void handleOfflinePrinter(FarmPrinter printer) {
        if (!"OFFLINE".equals(printer.getStatus())) {
            printer.setStatus("OFFLINE");
            boolean updated = printerCacheService.updatePrinterStatusWithLock(printer);
            if (updated) {
                log.warn("❌ 打印机 [{}] 离线", printer.getName());
            }
        }
        // 清除缓存
        printerCacheService.clearStatusCache(printer.getId());
    }

    /**
     * 根据 Moonraker 状态确定数据库状态
     */
    private String determineDbStatus(String moonrakerState) {
        if (moonrakerState == null) {
            return "OFFLINE";
        }
        switch (moonrakerState.toLowerCase()) {
            case "printing":
                return "PRINTING";
            case "paused":
                return "PRINTING"; // 暂停也算在打印中
            case "standby":
                return "IDLE";
            case "complete":
                return "IDLE";
            case "error":
                return "ERROR";
            case "offline":
                return "OFFLINE";
            default:
                return "IDLE";
        }
    }

    /**
     * 推送状态到前端
     */
    private void pushToFrontend(Long printerId, MoonrakerStatusDTO status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("printerId", printerId);
        payload.put("data", status);
        payload.put("timestamp", System.currentTimeMillis());

        FarmWebSocketServer.broadcastPrinterStatus(payload);
    }

    /**
     * 状态查询结果内部类
     */
    private static class PrinterStatusResult {
        final Long printerId;
        final MoonrakerStatusDTO status;
        final boolean online;

        PrinterStatusResult(Long printerId, MoonrakerStatusDTO status, boolean online) {
            this.printerId = printerId;
            this.status = status;
            this.online = online;
        }
    }
}
