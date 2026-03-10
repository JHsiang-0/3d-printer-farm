package com.example.farm.controller;

import com.example.farm.common.api.Result;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.entity.PrintJob;
import com.example.farm.entity.dto.PrintJobCreateDTO;
import com.example.farm.service.PrintJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 打印任务管理接口。
 */
@Tag(name = "打印任务管理")
@RestController
@Slf4j
@RequestMapping("/api/v1/print-jobs")
@RequiredArgsConstructor
public class PrintJobController {

    private final PrintJobService printJobService;

    /**
     * 查询排队中的任务。
     *
     * @return 当前队列任务列表
     */
    @Operation(summary = "获取排队中的任务队列")
    @GetMapping("/queue")
    public Result<List<PrintJob>> getQueue() {
        return Result.success(printJobService.getQueuedJobs());
    }

    /**
     * 创建打印任务。
     *
     * @param req 创建参数
     * @return 新任务 ID
     * @throws BusinessException 当参数非法或关联文件不存在时抛出
     */
    @Operation(summary = "创建打印任务")
    @PostMapping("create")
    public Result<Long> createJob(@RequestBody PrintJobCreateDTO req) {
        Long jobId = printJobService.createJob(req);
        log.info("创建打印任务请求完成: jobId={}, autoAssign={}", jobId, req.getAutoAssign());
        return Result.success(jobId, "任务创建成功");
    }

    /**
     * 取消任务（仅允许取消排队中的任务）。
     *
     * @param id 任务 ID
     * @return 取消结果
     * @throws BusinessException 当任务状态不允许取消时抛出
     */
    @Operation(summary = "取消任务")
    @DeleteMapping("/{id}")
    public Result<String> cancelJob(@PathVariable Long id) {
        PrintJob job = printJobService.getById(id);
        if (job != null && "QUEUED".equals(job.getStatus())) {
            job.setStatus("CANCELED");
            printJobService.updateById(job);
            log.info("取消打印任务成功: jobId={}", id);
            return Result.success(null, "任务已取消");
        }
        throw new BusinessException("仅排队中的任务允许取消");
    }

    /**
     * 手动派发任务并立即启动打印。
     *
     * @param jobId 任务 ID
     * @param printerId 打印机 ID
     * @return 派发结果
     * @throws BusinessException 当任务状态、打印机状态或参数不满足要求时抛出
     */
    @Operation(summary = "手动指派任务给打印机并开始")
    @PostMapping("/{jobId}/assign")
    public Result<String> assignJob(@PathVariable Long jobId, @RequestParam Long printerId) {
        boolean success = printJobService.assignAndStartPrint(jobId, printerId);
        if (!success) {
            throw new BusinessException("任务派发失败，请检查任务与打印机状态");
        }
        log.info("手动派发任务成功: jobId={}, printerId={}", jobId, printerId);
        return Result.success(null, "任务已下发，打印机开始执行");
    }
}
