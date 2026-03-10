package com.example.farm.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.farm.common.api.Result;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.entity.PrintFile;
import com.example.farm.entity.dto.PrintFileQueryDTO;
import com.example.farm.service.PrintFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 打印文件管理接口。
 */
@Tag(name = "打印文件管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/print-files")
public class PrintFileController {

    private final PrintFileService farmPrintFileService;

    /**
     * 上传并解析切片文件。
     */
    @Operation(summary = "上传并解析切片文件")
    @PostMapping("/upload")
    public Result<PrintFile> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        PrintFile savedFile = farmPrintFileService.uploadAndParseFile(file);
        return Result.success(savedFile, "文件上传成功");
    }

    /**
     * 分页查询当前用户文件列表。
     */
    @Operation(summary = "分页查询打印文件列表")
    @PostMapping("/page")
    public Result<Page<PrintFile>> pageFiles(@RequestBody PrintFileQueryDTO queryDTO) {
        return Result.success(farmPrintFileService.pageFiles(queryDTO));
    }

    /**
     * 删除文件。
     */
    @Operation(summary = "删除文件")
    @DeleteMapping("/{id}")
    public Result<Void> deleteFile(@PathVariable Long id) {
        farmPrintFileService.deleteFile(id);
        return Result.success(null, "删除成功");
    }
}