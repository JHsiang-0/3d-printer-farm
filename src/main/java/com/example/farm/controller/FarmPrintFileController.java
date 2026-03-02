package com.example.farm.controller;

import com.example.farm.common.api.Result;
import com.example.farm.common.utils.GCodeParser;
import com.example.farm.common.utils.RustFsClient;
import com.example.farm.entity.FarmPrintFile;
import com.example.farm.entity.FarmPrintJob;
import com.example.farm.entity.dto.FarmPrintJobCreateDTO;
import com.example.farm.mapper.FarmPrintFileMapper;
import com.example.farm.service.FarmPrintJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 打印文件管理控制器
 */
@Tag(name = "打印文件管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/print-files")
public class FarmPrintFileController {

    private final RustFsClient rustFsClient;
    private final FarmPrintFileMapper farmPrintFileMapper;
    private final FarmPrintJobService farmPrintJobService;

    @Operation(summary = "上传切片文件")
    @PostMapping("/upload")
    public Result<FarmPrintFile> uploadFile(@RequestParam("file") MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String safeName = System.currentTimeMillis() + "_" + originalName;

        // 1. 上传到 RustFS
        String fileUrl = rustFsClient.uploadFile(safeName, file);

        // 2. 极速读取 8KB 文件头并全面解析
        String headerContent = rustFsClient.readHeader(safeName);
        GCodeParser.GCodeMeta meta = GCodeParser.parseMetadata(headerContent);

        // 3. 存入文件档案库
        FarmPrintFile farmPrintFile = new FarmPrintFile();
        farmPrintFile.setOriginalName(originalName);
        farmPrintFile.setSafeName(safeName);
        farmPrintFile.setFileUrl(fileUrl);
        farmPrintFile.setFileSize(file.getSize());
        farmPrintFile.setUserId(1L);
        farmPrintFile.setCreatedAt(java.time.LocalDateTime.now());

        // 保存解析出的元数据
        farmPrintFile.setEstTime(meta.getEstTime());
        farmPrintFile.setMaterialType(meta.getMaterialType());
        farmPrintFile.setNozzleSize(meta.getNozzleSize());

        farmPrintFileMapper.insert(farmPrintFile);

        return Result.success(farmPrintFile, "数字资产入库成功！");
    }

    @Operation(summary = "创建打印任务")
    @PostMapping("/create-job")
    public Result<Long> createJob(@RequestBody FarmPrintJobCreateDTO req) {
        FarmPrintJob job = new FarmPrintJob();
        job.setUserId(1L); // 模拟当前登录用户
        job.setFileId(req.getFileId());

        // 记录这项任务的硬件要求
        job.setMaterialType(req.getMaterialType());
        job.setNozzleSize(req.getNozzleSize());

        job.setStatus("QUEUED");
        job.setPriority(req.getPriority() != null ? req.getPriority() : 0);
        job.setProgress(java.math.BigDecimal.ZERO);

        farmPrintJobService.save(job);

        return Result.success(job.getId(), "新生产任务已下达队列！");
    }
}
