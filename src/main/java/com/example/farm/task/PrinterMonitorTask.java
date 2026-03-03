package com.example.farm.task;

import com.example.farm.controller.FarmWebSocketServer;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.dto.MoonrakerStatusDTO;
import com.example.farm.service.FarmPrinterService;
import com.example.farm.service.PrinterCacheService;
import com.example.farm.common.utils.MoonrakerApiClient;
import jakarta.annotation.PreDestroy;
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

    // 核心改进 1：优雅停机，防止重启时产生脏数据
    @PreDestroy
    public void destroy() {
        log.info("🛑 正在优雅关闭打印机监控线程池...");
        executorService.shutdown();
    }

    /**
     * 每5秒执行一次状态监控
     * 优化：使用Redis缓存打印机列表，减少数据库查询压力
     */
    @Scheduled(fixedRate = 5000)
    public void checkPrinterStatus() {
        // 优先从Redis获取打印机列表，缓存未命中再从数据库加载
        List<FarmPrinter> printers = printerCacheService.getAllPrintersFromCache();
        if (printers == null) {
            // 缓存未命中时，从数据库加载并缓存
            log.info("🔄 Redis缓存未命中，从数据库加载打印机列表...");
            printers = printerService.list();
            if (!printers.isEmpty()) {
                printerCacheService.cacheAllPrinters(printers);
                log.info("📦 打印机列表已加载到Redis缓存，共 {} 台", printers.size());
            }
            return;
        }
        
        // 缓存存在但为空列表，说明数据库中确实没有打印机
        if (printers.isEmpty()) {
            return;
        }
        
        log.debug("✅ 从Redis缓存获取打印机列表，共 {} 台", printers.size());

        // 核心改进 2：极致优化！全异步非阻塞执行
        List<CompletableFuture<PrinterStatusResult>> futures = printers.stream()
                .map(printer -> CompletableFuture.supplyAsync(
                                () -> fetchAndUpdateStatus(printer), executorService)
                        .thenApply(result -> {
                            // 异步线程拿到数据后，自己推给前端，主线程不阻塞等待
                            if (result.status != null) {
                                pushToFrontend(result.printerId, result.status);
                            }
                            return result;
                        }))
                .toList();

        // 异步统计，仅供日志打印，绝不阻塞主 Scheduled 线程
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    if (System.currentTimeMillis() % 30000 < 5000) {
                        long onlineCount = futures.stream().map(CompletableFuture::join).filter(r -> r.online).count();
                        long offlineCount = futures.size() - onlineCount;
                        log.info("📊 打印机状态扫描完成：在线 {} 台，离线 {} 台", onlineCount, offlineCount);
                    }
                });
    }

    /**
     * 获取并更新单个打印机状态
     */
    private PrinterStatusResult fetchAndUpdateStatus(FarmPrinter printer) {
        Long printerId = printer.getId();
        String printerName = printer.getName();

        try {
            MoonrakerStatusDTO status = moonrakerApiClient.getPrinterStatus(printer.getIpAddress());

            if (status != null) {
                printerCacheService.cachePrinterStatus(printerId, status);
                
                // 记录状态历史
                printerCacheService.recordStatusHistory(printerId, status);
                
                // 更新在线状态统计
                printerCacheService.markPrinterOnline(printerId);

                String newDbStatus = determineDbStatus(status.getState());
                if (!newDbStatus.equals(printer.getStatus())) {
                    // 核心改进 3：局部更新！只构造带有 ID 和状态的新对象更新，避免覆盖别的字段
                    FarmPrinter updateEntity = new FarmPrinter();
                    updateEntity.setId(printerId);
                    updateEntity.setStatus(newDbStatus);

                    boolean updated = printerCacheService.updatePrinterStatusWithLock(updateEntity);
                    if (updated) {
                        log.info("🔄 打印机 [{}] 状态变更: {} → {}",
                                printerName, printer.getStatus(), newDbStatus);
                        printer.setStatus(newDbStatus); // 同步内存对象
                        
                        // 更新 Redis 统计状态
                        if ("PRINTING".equals(newDbStatus)) {
                            printerCacheService.markPrinterBusy(printerId);
                        } else if ("IDLE".equals(newDbStatus)) {
                            printerCacheService.markPrinterIdle(printerId);
                        }
                    }
                }
                return new PrinterStatusResult(printerId, status, true);
            } else {
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
        Long printerId = printer.getId();
        if (!"OFFLINE".equals(printer.getStatus())) {
            FarmPrinter updateEntity = new FarmPrinter();
            updateEntity.setId(printerId);
            updateEntity.setStatus("OFFLINE");

            boolean updated = printerCacheService.updatePrinterStatusWithLock(updateEntity);
            if (updated) {
                log.warn("❌ 打印机 [{}] 离线", printer.getName());
                printer.setStatus("OFFLINE"); // 同步内存对象
            }
        }
        // 更新 Redis 统计状态
        printerCacheService.markPrinterOffline(printerId);
        printerCacheService.clearStatusCache(printerId);
    }

    private String determineDbStatus(String moonrakerState) {
        if (moonrakerState == null) return "OFFLINE";
        return switch (moonrakerState.toLowerCase()) {
            case "printing", "paused" -> "PRINTING";
            case "standby", "complete" -> "IDLE";
            case "error" -> "ERROR";
            case "offline" -> "OFFLINE";
            default -> "IDLE";
        };
    }

    private void pushToFrontend(Long printerId, MoonrakerStatusDTO status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("printerId", printerId);
        payload.put("data", status);
        payload.put("timestamp", System.currentTimeMillis());

        FarmWebSocketServer.broadcastPrinterStatus(payload);
    }

    private record PrinterStatusResult(Long printerId, MoonrakerStatusDTO status, boolean online) {
    }
}