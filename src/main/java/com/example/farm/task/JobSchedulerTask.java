package com.example.farm.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.FarmPrintJob;
import com.example.farm.service.FarmPrinterService;
import com.example.farm.service.FarmPrintJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 农场大脑：自动化任务调度器
 */
@Slf4j
@Component
public class JobSchedulerTask {

    @Autowired
    private FarmPrintJobService farmPrintJobService;

    @Autowired
    private FarmPrinterService printerService;

    /**
     * 每 10 秒扫描一次队列进行自动派单
     */
//    @Scheduled(fixedRate = 10000)
    @Transactional
    public void scheduleJobs() {
        // 1. 获取所有空闲且在线的打印机
        List<FarmPrinter> idlePrinters = printerService.list(new LambdaQueryWrapper<FarmPrinter>()
                .eq(FarmPrinter::getStatus, "IDLE"));

        if (idlePrinters.isEmpty()) {
            return; // 没有空闲机器，下次再看
        }

        // 2. 获取所有排队中的任务（按优先级降序，创建时间升序）
        List<FarmPrintJob> queuedJobs = farmPrintJobService.list(new LambdaQueryWrapper<FarmPrintJob>()
                .eq(FarmPrintJob::getStatus, "QUEUED")
                .orderByDesc(FarmPrintJob::getPriority)
                .orderByAsc(FarmPrintJob::getCreatedAt));

        if (queuedJobs.isEmpty()) {
            return; // 队列里没活儿，休息
        }

        log.info("🤖 调度中心启动：发现 {} 台空闲机器，{} 个待打印任务", idlePrinters.size(), queuedJobs.size());

        // 3. 开始配对（双指针逻辑，或者简单的循环配对）
        int assignCount = Math.min(idlePrinters.size(), queuedJobs.size());

        for (int i = 0; i < assignCount; i++) {
            FarmPrinter printer = idlePrinters.get(i);
            FarmPrintJob job = queuedJobs.get(i);

            // --- 派单动作 ---

            // A. 更新任务状态
            job.setPrinterId(printer.getId());
            job.setStatus("ASSIGNED"); // 已分配，准备开始
            job.setStartedAt(LocalDateTime.now());
            farmPrintJobService.updateById(job);

            // B. 更新打印机状态
            printer.setStatus("PREPARING"); // 变为准备中（加载文件、预热等）
            printerService.updateById(printer);

            log.info("✅ 成功指派：任务 [#{} - {}] -> 打印机 [{}]",
                    job.getId(), job.getFileId(), printer.getName());

            // 这里后续可以扩展：调用 Moonraker API 真正下发 G-code 文件开始打印
        }
    }
}