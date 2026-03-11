package com.example.farm.controller;

import com.example.farm.common.exception.BusinessException;
import com.example.farm.entity.PrintFile;
import com.example.farm.entity.dto.PrintJobCreateDTO;
import com.example.farm.service.PrintFileService;
import com.example.farm.service.PrintJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Moonraker API 模拟控制器
 * 用于兼容 OrcaSlicer 等切片软件的 G-code 上传功能
 * 映射根路径 /server、/printer、/machine，避免 /api/v1 前缀
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Moonraker 模拟接口", description = "兼容 OrcaSlicer 等切片软件的 API 模拟服务")
public class MoonrakerMockController {

    private final PrintFileService printFileService;
    private final PrintJobService printJobService;

    @Value("${farm.moonraker-api-key:}")
    private String moonrakerApiKey;

    /**
     * 校验 API Key
     */
    private void validateApiKey(String apiKey) {
        if (moonrakerApiKey == null || moonrakerApiKey.isBlank()) {
            log.warn("未配置 farm.moonraker-api-key，建议配置以保证安全");
            return;
        }
        if (apiKey == null || !apiKey.equals(moonrakerApiKey)) {
            throw new BusinessException(401, "API Key 无效");
        }
    }

    /**
     * 获取 Moonraker 服务器信息
     * GET /server/info
     */
    @Operation(summary = "获取服务器信息", description = "返回 Moonraker 服务器版本信息")
    @GetMapping("/server/info")
    public Map<String, Object> getServerInfo(@RequestHeader(value = "X-Api-Key", required = false) String apiKey) {
        validateApiKey(apiKey);

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("moonraker_version", "v0.8.0");
        serverInfo.put("klipper_path", "/home/pi/klipper");
        serverInfo.put("klipper_version", "v0.12.0");
        serverInfo.put("api_version", "v1");
        serverInfo.put("api_version_string", "v1");

        result.put("result", serverInfo);
        log.debug("Moonraker /server/info called");
        return result;
    }

    /**
     * 获取打印机信息
     * GET /printer/info
     */
    @Operation(summary = "获取打印机信息", description = "返回打印机当前状态信息")
    @GetMapping("/printer/info")
    public Map<String, Object> getPrinterInfo(@RequestHeader(value = "X-Api-Key", required = false) String apiKey) {
        validateApiKey(apiKey);

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> printerInfo = new HashMap<>();
        printerInfo.put("state", "ready");
        printerInfo.put("state_message", "Printer is ready");
        printerInfo.put("hostname", "farm-printer");
        printerInfo.put("software_version", "v0.12.0");

        result.put("result", printerInfo);
        log.debug("Moonraker /printer/info called");
        return result;
    }

    /**
     * 获取机器更新状态
     * GET /machine/update/status
     * 返回空版本信息避免 Slicer 轮询报错
     */
    @Operation(summary = "获取机器更新状态", description = "返回空的版本信息，避免切片软件轮询报错")
    @GetMapping("/machine/update/status")
    public Map<String, Object> getMachineUpdateStatus(@RequestHeader(value = "X-Api-Key", required = false) String apiKey) {
        validateApiKey(apiKey);

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> versionInfo = new HashMap<>();
        versionInfo.put("configured", Collections.emptyList());
        versionInfo.put("offline", Collections.emptyMap());

        result.put("result", versionInfo);
        log.debug("Moonraker /machine/update/status called");
        return result;
    }

    /**
     * 上传 G-code 文件
     * POST /server/files/upload
     *
     * @param file   上传的文件
     * @param print  是否立即打印
     * @param apiKey API Key
     * @return Moonraker 格式的响应
     */
    @Operation(summary = "上传 G-code 文件", description = "接收切片软件上传的 G-code 文件，自动解析并上传到存储服务")
    @PostMapping("/server/files/upload")
    public Map<String, Object> uploadFile(
            @Parameter(description = "上传的 G-code 文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "是否立即创建打印任务") @RequestParam(value = "print", required = false, defaultValue = "false") Boolean print,
            @Parameter(description = "API Key 认证") @RequestHeader(value = "X-Api-Key", required = false) String apiKey) {

        validateApiKey(apiKey);

        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }

        log.info("Moonraker 收到文件上传: filename={}, size={}, print={}",
                file.getOriginalFilename(), file.getSize(), print);

        try {
            // 调用现有的文件解析、S3 上传和入库逻辑
            PrintFile printFile = printFileService.uploadAndParseFile(file);
            log.info("文件入库成功: fileId={}, safeName={}", printFile.getId(), printFile.getSafeName());

            // 如果 print=true，创建排队中的打印任务
            Long jobId = null;
            if (print) {
                PrintJobCreateDTO jobReq = new PrintJobCreateDTO();
                jobReq.setFileId(printFile.getId());
                jobReq.setPriority(0);
                jobReq.setAutoAssign(true);

                // 使用默认用户 ID (切片软件上传时没有用户上下文)
                // 这里可以从 API Key 中提取用户信息，目前简化为使用系统默认
                jobId = printJobService.submitJob(printFile.getId(), 1L, 0);
                log.info("创建打印任务成功: jobId={}", jobId);
            }

            // 返回 Moonraker 格式的响应
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> item = new HashMap<>();
            item.put("path", printFile.getSafeName());
            item.put("size", file.getSize());
            item.put("modified", printFile.getCreatedAt().toString());
            if (jobId != null) {
                item.put("job_id", jobId);
            }

            result.put("result", Collections.singletonMap("item", item));
            return result;

        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件列表
     * GET /server/files
     */
    @Operation(summary = "获取文件列表", description = "返回已上传的 G-code 文件列表")
    @GetMapping("/server/files")
    public Map<String, Object> getFileList(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey) {
        validateApiKey(apiKey);

        Map<String, Object> result = new HashMap<>();
        result.put("result", Collections.emptyList());
        log.debug("Moonraker /server/files called");
        return result;
    }

    /**
     * 删除文件
     * DELETE /server/files/{filename}
     */
    @Operation(summary = "删除 G-code 文件", description = "根据文件名删除指定的 G-code 文件")
    @DeleteMapping("/server/files/{filename:.+}")
    public Map<String, Object> deleteFile(
            @Parameter(description = "要删除的文件名") @PathVariable String filename,
            @Parameter(description = "API Key 认证") @RequestHeader(value = "X-Api-Key", required = false) String apiKey) {

        validateApiKey(apiKey);

        log.info("Moonraker 收到文件删除请求: filename={}", filename);

        Map<String, Object> result = new HashMap<>();
        result.put("result", Collections.singletonMap("deleted", filename));
        return result;
    }
}