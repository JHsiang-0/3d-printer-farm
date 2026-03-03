package com.example.farm.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.farm.common.api.Result;
import com.example.farm.common.utils.SecurityContextUtil;
import com.example.farm.entity.FarmPrintFile;
import com.example.farm.entity.dto.FarmPrintFileQueryDTO;
import com.example.farm.entity.dto.FarmPrintJobCreateDTO;
import com.example.farm.service.FarmPrintFileService;
import com.example.farm.service.FarmPrintJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 打印文件管理控制器
 * 负责处理打印文件的上传、查询、删除等HTTP请求
 */
@Tag(name = "打印文件管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/print-files")
public class FarmPrintFileController {

    private final FarmPrintFileService farmPrintFileService;
    private final FarmPrintJobService farmPrintJobService;

    /**
     * 上传切片文件
     *
     * @param file 上传的GCode文件
     * @return 上传成功的文件信息
     */
    @Operation(summary = "上传切片文件")
    @PostMapping("/upload")
    public Result<FarmPrintFile> uploadFile(
            @Parameter(description = "GCode切片文件", required = true)
            @RequestParam("file") MultipartFile file) {
        // 获取当前登录用户ID
        Long currentUserId = SecurityContextUtil.getCurrentUserId();
        
        // 调用Service层处理业务逻辑
        FarmPrintFile farmPrintFile = farmPrintFileService.uploadFile(file, currentUserId);
        
        return Result.success(farmPrintFile, "数字资产入库成功！");
    }

    /**
     * 分页查询打印文件列表
     *
     * @param queryDTO 查询参数
     * @return 分页结果
     */
    @Operation(summary = "分页查询打印文件列表")
    @PostMapping("/page")
    public Result<Page<FarmPrintFile>> pageFiles(
            @Parameter(description = "分页查询参数")
            @RequestBody FarmPrintFileQueryDTO queryDTO) {
        Page<FarmPrintFile> pageResult = farmPrintFileService.pageFiles(queryDTO);
        return Result.success(pageResult);
    }

    /**
     * 删除打印文件
     *
     * @param id 文件ID
     * @return 删除结果
     */
    @Operation(summary = "删除打印文件")
    @DeleteMapping("/{id}")
    public Result<Void> deleteFile(
            @Parameter(description = "文件ID", required = true)
            @PathVariable("id") Long id) {
        // 获取当前登录用户ID
        Long currentUserId = SecurityContextUtil.getCurrentUserId();
        
        // 调用Service层处理删除逻辑
        farmPrintFileService.deleteFile(id, currentUserId);
        
        return Result.success(null, "文件删除成功");
    }

    /**
     * 创建打印任务
     *
     * @param req 创建任务请求参数
     * @return 创建的任务ID
     */
    @Operation(summary = "创建打印任务")
    @PostMapping("/create-job")
    public Result<Long> createJob(
            @Parameter(description = "创建打印任务参数", required = true)
            @RequestBody FarmPrintJobCreateDTO req) {
        // 获取当前登录用户ID
        Long currentUserId = SecurityContextUtil.getCurrentUserId();
        
        // 调用Service层创建任务
        Long jobId = farmPrintJobService.createJob(req, currentUserId);
        
        return Result.success(jobId, "新生产任务已下达队列！");
    }
}
