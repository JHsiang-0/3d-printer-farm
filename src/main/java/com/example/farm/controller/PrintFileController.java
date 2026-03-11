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
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    /**
     * 下载文件。
     *
     * @param id              文件 ID
     * @param expires         预签名 URL 过期时间（分钟），默认 60
     * @param useRedirect     是否使用 302 跳转到预签名 URL（默认 true）
     * @return 文件流或预签名 URL
     */
    @Operation(summary = "下载打印文件")
    @GetMapping("/{id}/download")
    public Object downloadFile(
            @PathVariable Long id,
            @RequestParam(value = "expires", required = false, defaultValue = "60") Integer expires,
            @RequestParam(value = "redirect", required = false, defaultValue = "true") Boolean useRedirect) {

        PrintFile file = farmPrintFileService.getById(id);
        if (file == null) {
            throw new BusinessException("文件不存在");
        }

        // 获取预签名 URL
        String presignedUrl = farmPrintFileService.getPresignedDownloadUrl(id, expires);

        if (useRedirect) {
            // 302 重定向到预签名 URL（适用于浏览器直接下载）
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(java.net.URI.create(presignedUrl))
                    .build();
        } else {
            // 返回预签名 URL（供前端自行处理）
            return Result.success(java.util.Map.of(
                    "url", presignedUrl,
                    "filename", file.getOriginalName(),
                    "expiresMinutes", expires
            ));
        }
    }

    /**
     * 批量删除文件。
     */
    @Operation(summary = "批量删除文件")
    @DeleteMapping("/batch")
    public Result<Void> batchDeleteFiles(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的文件");
        }
        farmPrintFileService.batchDeleteFiles(ids);
        return Result.success(null, "批量删除成功");
    }
}