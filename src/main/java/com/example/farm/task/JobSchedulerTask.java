package com.example.farm.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.farm.common.constant.RedisKeyConstant;
import com.example.farm.common.utils.RedisUtil;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.FarmPrintJob;
import com.example.farm.service.FarmPrinterService;
import com.example.farm.service.FarmPrintJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 农场大脑：自动化任务调度器
 * 使用分布式锁确保集群环境下只有一个实例执行调度
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobSchedulerTask {

    private final FarmPrintJobService farmPrintJobService;
    private final FarmPrinterService farmPrinterService;
    private final RedisUtil redisUtil;

    // 分布式锁过期时间：10秒
    private static final long LOCK_TTL = 10;

    /**
     * 每10秒扫描一次队列进行自动派单
     * 使用分布式锁防止集群环境下重复调度
     */
    @Scheduled(fixedRate = 10000)
    public void scheduleJobs() {
        String lockKey = RedisKeyConstant.SCHEDULER_LOCK;
        String lockValue = UUID.randomUUID().toString();

        boolean locked = false;
        try {
            // 尝试获取分布式锁
            locked = redisUtil.tryLock(lockKey, lockValue, LOCK_TTL, TimeUnit.SECONDS);
            if (!locked) {
                // 其他实例正在执行，跳过
                log.debug("任务调度锁被占用，跳过本次调度");
                return;
            }

            // 获取锁成功，执行调度
            doSchedule();

        } finally {
            if (locked) {
                // 释放锁
                String currentValue = redisUtil.getLockValue(lockKey);
                if (lockValue.equals(currentValue)) {
                    redisUtil.unlock(lockKey);
                }
            }
        }
    }

    /**
     * 执行调度逻辑
     */
    @Transactional
    public void doSchedule() {
        // 1. 获取所有空闲且在线的打印机
        List<FarmPrinter> idlePrinters = farmPrinterService.list(new LambdaQueryWrapper<FarmPrinter>()
                .eq(FarmPrinter::getStatus, "IDLE"));

        if (idlePrinters.isEmpty()) {
            return;
        }

        // 2. 获取所有排队中的任务
        List<FarmPrintJob> queuedJobs = farmPrintJobService.list(new LambdaQueryWrapper<FarmPrintJob>()
                .eq(FarmPrintJob::getStatus, "QUEUED")
                .orderByDesc(FarmPrintJob::getPriority)
                .orderByAsc(FarmPrintJob::getCreatedAt));

        if (queuedJobs.isEmpty()) {
            return;
        }

        log.info("🤖 调度中心启动：发现 {} 台空闲机器，{} 个待打印任务", 
                idlePrinters.size(), queuedJobs.size());

        // 3. 开始配对
        int assignCount = Math.min(idlePrinters.size(), queuedJobs.size());
        int successCount = 0;

        for (int i = 0; i < assignCount; i++) {
            FarmPrinter printer = idlePrinters.get(i);
            FarmPrintJob job = queuedJobs.get(i);

            // 尝试分配任务
            boolean assigned = tryAssignJob(job, printer);
            if (assigned) {
                successCount++;
            }
        }

        if (successCount > 0) {
            log.info("✅ 自动派单完成：成功指派 {} 个任务", successCount);
        }
    }

    /**
     * 尝试分配任务给打印机（带任务锁）
     */
    private boolean tryAssignJob(FarmPrintJob job, FarmPrinter printer) {
        Long jobId = job.getId();
        String lockKey = RedisKeyConstant.getKey(RedisKeyConstant.JOB_LOCK, jobId);
        String lockValue = UUID.randomUUID().toString();

        boolean locked = false;
        try {
            // 获取任务锁
            locked = redisUtil.tryLock(lockKey, lockValue, 30, TimeUnit.SECONDS);
            if (!locked) {
                log.debug("任务 [{}] 已被其他调度器锁定，跳过", jobId);
                return false;
            }

            // 重新查询任务状态，防止状态已变更
            FarmPrintJob currentJob = farmPrintJobService.getById(jobId);
            if (currentJob == null || !"QUEUED".equals(currentJob.getStatus())) {
                log.debug("任务 [{}] 状态已变更，跳过", jobId);
                return false;
            }

            // 重新查询打印机状态
            FarmPrinter currentPrinter = farmPrinterService.getById(printer.getId());
            if (currentPrinter == null || !"IDLE".equals(currentPrinter.getStatus())) {
                log.debug("打印机 [{}] 状态已变更，跳过", printer.getName());
                return false;
            }

            // 执行派单
            return assignJob(currentJob, currentPrinter);

        } finally {
            if (locked) {
                String currentValue = redisUtil.getLockValue(lockKey);
                if (lockValue.equals(currentValue)) {
                    redisUtil.unlock(lockKey);
                }
            }
        }
    }

    /**
     * 执行任务分配
     */
    private boolean assignJob(FarmPrintJob job, FarmPrinter printer) {
        try {
            // TODO: 这里应该调用 Moonraker API 下发打印任务
            // boolean apiSuccess = moonrakerApiClient.startPrint(printer.getIpAddress(), job.getFileUrl());
            // if (!apiSuccess) {
            //     log.error("打印机 [{}] 接收任务失败", printer.getName());
            //     return false;
            // }

            // 更新任务状态
            job.setPrinterId(printer.getId());
            job.setStatus("ASSIGNED");
            job.setStartedAt(LocalDateTime.now());
            farmPrintJobService.updateById(job);

            // 更新打印机状态
            printer.setStatus("PREPARING");
            printer.setCurrentJobId(job.getId());
            farmPrinterService.updateById(printer);

            log.info("✅ 成功指派：任务 [#{}] -> 打印机 [{}]", 
                    job.getId(), printer.getName());
            return true;

        } catch (Exception e) {
            log.error("指派任务失败：jobId={}, printerId={}", job.getId(), printer.getId(), e);
            return false;
        }
    }
}
