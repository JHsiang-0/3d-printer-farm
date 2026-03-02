package com.example.farm.controller;

import com.example.farm.common.api.Result;
import com.example.farm.entity.FarmPrintJob;
import com.example.farm.entity.dto.FarmPrintJobCreateDTO;
import com.example.farm.service.FarmPrintJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 打印任务管理控制器
 */
@Tag(name = "打印任务管理")
@RestController
@RequestMapping("/api/v1/print-jobs")
@RequiredArgsConstructor
public class FarmPrintJobController {

    private final FarmPrintJobService farmPrintJobService;

    @Operation(summary = "获取排队中的任务队列")
    @GetMapping("/queue")
    public Result<List<FarmPrintJob>> getQueue() {
        return Result.success(farmPrintJobService.getQueuedJobs());
    }

    @Operation(summary = "提交新打印任务")
    @PostMapping("/submit")
    public Result<Long> submit(@RequestParam Long fileId,
                               @RequestParam(required = false) Integer priority) {
        Long jobId = farmPrintJobService.submitJob(fileId, 1L, priority);
        return Result.success(jobId, "任务已进入排队序列");
    }

    @Operation(summary = "取消任务")
    @DeleteMapping("/{id}")
    public Result<String> cancelJob(@PathVariable Long id) {
        FarmPrintJob job = farmPrintJobService.getById(id);
        if (job != null && "QUEUED".equals(job.getStatus())) {
            job.setStatus("CANCELED");
            farmPrintJobService.updateById(job);
            return Result.success(null, "任务已取消");
        }
        return Result.failed("只有排队中的任务可以取消");
    }

    @Operation(summary = "手动指派任务给打印机并开始")
    @PostMapping("/{jobId}/assign")
    public Result<String> assignJob(
            @PathVariable Long jobId,
            @RequestParam Long printerId) {

        boolean success = farmPrintJobService.assignAndStartPrint(jobId, printerId);
        if (success) {
            return Result.success(null, "指令已下发，打印机开始轰鸣！");
        } else {
            return Result.failed("派单未执行：请确认任务是否处于'排队中'，且机器是否'空闲'");
        }
    }

    @Operation(summary = "创建打印任务")
    @PostMapping
    public Result<Long> createJob(@RequestBody FarmPrintJobCreateDTO req) {
        Long jobId = farmPrintJobService.createJob(req);
        return Result.success(jobId, "新生产任务已下达队列！");
    }
}
