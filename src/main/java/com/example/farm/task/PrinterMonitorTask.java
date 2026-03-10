package com.example.farm.task;

import com.example.farm.common.utils.LogUtil;
import com.example.farm.controller.WebSocketServer;
import com.example.farm.entity.Printer;
import com.example.farm.entity.dto.MoonrakerStatusDTO;
import com.example.farm.service.PrinterService;
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
import java.util.concurrent.TimeUnit;

/**
 * 打印机监控任务
 * 定期获取打印机状态并推送至前端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrinterMonitorTask {

    private final PrinterService printerService;
    private final PrinterCacheService printerCacheService;
    private final MoonrakerApiClient moonrakerApiClient;

    // 并发线程池，用于并行查询多个打印机状态
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    // 慢查询阈值：5秒
    private static final long SLOW_THRESHOLD_MS = 5000;

    @PreDestroy
    public void destroy() {
        LogUtil.shutdown("PrinterMonitorTask");
        // 优雅关闭线程池：先停止接受新任务，然后等待现有任务完成
        executorService.shutdown();
        try {
            // 等待最多 5 秒让正在执行的任务完成
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                // 如果超时，强制关闭
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            // 如果被中断，强制关闭
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 每5秒执行一次状态监控
     */
    @Scheduled(fixedRate = 5000)
    public void checkPrinterStatus() {
        long startTime = System.currentTimeMillis();
        
        try {
            // 优先从Redis获取打印机列表
            List<Printer> printers = printerCacheService.getAllPrintersFromCache();
            if (printers == null) {
                printers = loadPrintersFromDb();
                if (printers.isEmpty()) {
                    return;
                }
            }

            if (printers.isEmpty()) {
                return;
            }

            // 并行查询打印机状态
            List<CompletableFuture<PrinterStatusResult>> futures = printers.stream()
                    .map(printer -> CompletableFuture.supplyAsync(
                                    () -> fetchAndUpdateStatus(printer), executorService)
                            .thenApply(result -> {
                                if (result.status != null) {
                                    pushToFrontend(result.printerId, result.status);
                                }
                                return result;
                            }))
                    .toList();

            // 异步统计
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> logScanResult(futures, startTime));
                    
        } catch (Exception e) {
            log.error("打印机状态巡检失败", e);
        }
    }

    private List<Printer> loadPrintersFromDb() {
        long startTime = System.currentTimeMillis();
        List<Printer> printers = printerService.list();
        long duration = System.currentTimeMillis() - startTime;
        
        LogUtil.slowOperation("loadPrintersFromDb", duration, 1000);
        
        if (!printers.isEmpty()) {
            printerCacheService.cacheAllPrinters(printers);
            log.info("已从数据库加载打印机列表到缓存: {} 台", printers.size());
        }
        return printers;
    }

    private void logScanResult(List<CompletableFuture<PrinterStatusResult>> futures, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        long onlineCount = futures.stream().map(CompletableFuture::join).filter(r -> r.online).count();
        long offlineCount = futures.size() - onlineCount;
        
        if (duration > SLOW_THRESHOLD_MS) {
            log.warn("打印机巡检较慢: 耗时 {}ms，在线 {} 台，离线 {} 台", 
                    duration, onlineCount, offlineCount);
        } else if (log.isDebugEnabled()) {
            log.debug("打印机巡检完成: 耗时 {}ms，在线 {} 台，离线 {} 台", 
                    duration, onlineCount, offlineCount);
        }
    }

    private PrinterStatusResult fetchAndUpdateStatus(Printer printer) {
        Long printerId = printer.getId();
        String printerName = printer.getName();
        long startTime = System.currentTimeMillis();

        try {
            MoonrakerStatusDTO status = moonrakerApiClient.getPrinterStatus(printer.getIpAddress());
            long duration = System.currentTimeMillis() - startTime;

            if (status != null) {
                handlePrinterOnline(printerId, printerName, status, printer, duration);
                return new PrinterStatusResult(printerId, status, true);
            } else {
                handlePrinterOffline(printer);
                return new PrinterStatusResult(printerId, null, false);
            }
        } catch (Exception e) {
            log.error("获取打印机状态失败: printerId={}, name={}", 
                    printerId, printerName, e);
            return new PrinterStatusResult(printerId, null, false);
        }
    }

    private void handlePrinterOnline(Long printerId, String printerName,
                                     MoonrakerStatusDTO status, Printer printer, long duration) {
        // 缓存状态
        printerCacheService.cachePrinterStatus(printerId, status);
        printerCacheService.recordStatusHistory(printerId, status);
        printerCacheService.markPrinterOnline(printerId);

        // 检查状态变更
        String newDbStatus = determineDbStatus(status.getState());
        if (!newDbStatus.equals(printer.getStatus())) {
            updatePrinterStatus(printer, newDbStatus);
        }

        // 记录慢查询
        LogUtil.slowOperation("fetchPrinterStatus", duration, SLOW_THRESHOLD_MS);
    }

    private void updatePrinterStatus(Printer printer, String newStatus) {
        Printer updateEntity = new Printer();
        updateEntity.setId(printer.getId());
        updateEntity.setStatus(newStatus);

        boolean updated = printerCacheService.updatePrinterStatusWithLock(updateEntity);
        if (updated) {
            LogUtil.dataChange("STATUS_CHANGE", "FarmPrinter", printer.getId(),
                    printer.getStatus() + " -> " + newStatus);
            printer.setStatus(newStatus);

            // 更新忙碌状态
            if ("PRINTING".equals(newStatus)) {
                printerCacheService.markPrinterBusy(printer.getId());
            } else if ("IDLE".equals(newStatus)) {
                printerCacheService.markPrinterIdle(printer.getId());
            }
        }
    }

    private void handlePrinterOffline(Printer printer) {
        if (!"OFFLINE".equals(printer.getStatus())) {
            Printer updateEntity = new Printer();
            updateEntity.setId(printer.getId());
            updateEntity.setStatus("OFFLINE");

            boolean updated = printerCacheService.updatePrinterStatusWithLock(updateEntity);
            if (updated) {
                LogUtil.dataChange("STATUS_CHANGE", "FarmPrinter", printer.getId(), "-> OFFLINE");
                printer.setStatus("OFFLINE");
            }
        }
        printerCacheService.markPrinterOffline(printer.getId());
        printerCacheService.clearStatusCache(printer.getId());
    }

    /**
     * 根据统一状态确定数据库状态
     * 注意：此处的 moonrakerState 已经是经过 calculateUnifiedState 处理后的统一状态
     * 可能包含系统级状态：shutdown, startup, error, ready 以及任务级状态：printing, paused 等
     */
    private String determineDbStatus(String moonrakerState) {
        if (moonrakerState == null) return "OFFLINE";

        return switch (moonrakerState.toLowerCase()) {
            // 任务级状态
            case "printing", "paused" -> "PRINTING";
            case "standby", "complete" -> "IDLE";
            // 系统级状态 - 需要特殊处理
            case "shutdown" -> "ERROR";  // 热失控等安全保护触发
            case "startup" -> "OFFLINE"; // 启动中视为离线
            case "error" -> "ERROR";     // 系统错误
            case "ready" -> "IDLE";      // 就绪但无任务
            case "offline", "unknown" -> "OFFLINE";
            default -> "IDLE";
        };
    }

    private void pushToFrontend(Long printerId, MoonrakerStatusDTO status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("printerId", printerId);
        payload.put("data", status);
        payload.put("timestamp", System.currentTimeMillis());
        WebSocketServer.broadcastPrinterStatus(payload);
    }

    private record PrinterStatusResult(Long printerId, MoonrakerStatusDTO status, boolean online) {
    }
}
