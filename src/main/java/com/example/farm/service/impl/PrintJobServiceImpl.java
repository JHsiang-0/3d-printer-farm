package com.example.farm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.common.utils.LogUtil;
import com.example.farm.common.utils.MoonrakerApiClient;
import com.example.farm.common.utils.RustFsClient;
import com.example.farm.common.utils.SecurityContextUtil;
import com.example.farm.entity.PrintFile;
import com.example.farm.entity.PrintJob;
import com.example.farm.entity.Printer;
import com.example.farm.entity.dto.PrintJobCreateDTO;
import com.example.farm.mapper.PrintFileMapper;
import com.example.farm.mapper.PrintJobMapper;
import com.example.farm.service.PrintJobService;
import com.example.farm.service.PrinterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintJobServiceImpl extends ServiceImpl<PrintJobMapper, PrintJob> implements PrintJobService {

    private final PrintJobMapper farmPrintJobMapper;
    private final PrintFileMapper printFileMapper;
    private final PrinterService printerService;
    private final RustFsClient rustFsClient;
    private final MoonrakerApiClient moonrakerApiClient;

    @Override
    public PrintJobMapper getBaseMapper() {
        return farmPrintJobMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitJob(Long fileId, Long userId, Integer priority) {
        PrintJob job = new PrintJob();
        job.setFileId(fileId);
        job.setUserId(userId);
        job.setPriority(priority != null ? priority : 0);
        job.setStatus("QUEUED");
        job.setProgress(BigDecimal.ZERO);
        this.save(job);
        log.info("提交打印任务成功: jobId={}, userId={}, fileId={}, priority={}", job.getId(), userId, fileId, job.getPriority());
        return job.getId();
    }

    @Override
    public List<PrintJob> getQueuedJobs() {
        return this.list(new LambdaQueryWrapper<PrintJob>()
                .in(PrintJob::getStatus, "QUEUED", "MANUAL")
                .orderByDesc(PrintJob::getPriority)
                .orderByAsc(PrintJob::getCreatedAt));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createJob(PrintJobCreateDTO req) {
        Long currentUserId = SecurityContextUtil.getCurrentUserId();
        return createJob(req, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createJob(PrintJobCreateDTO req, Long userId) {
        if (userId == null) {
            throw new BusinessException("用户未登录，无法创建任务");
        }

        PrintFile fileRecord = printFileMapper.selectById(req.getFileId());
        if (fileRecord == null) {
            log.warn("创建打印任务失败：切片文件不存在，fileId={}, userId={}", req.getFileId(), userId);
            throw new BusinessException("所选的切片文件不存在");
        }

        PrintJob job = new PrintJob();
        job.setUserId(userId);
        job.setFileId(req.getFileId());
        job.setFileUrl(fileRecord.getFileUrl());
        job.setEstTime(fileRecord.getEstTime());
        job.setMaterialType(req.getMaterialType() != null ? req.getMaterialType() : fileRecord.getMaterialType());
        job.setNozzleSize(req.getNozzleSize() != null ? req.getNozzleSize() : fileRecord.getNozzleSize());
        job.setPriority(req.getPriority() != null ? req.getPriority() : 0);
        job.setProgress(BigDecimal.ZERO);
        job.setCreatedAt(LocalDateTime.now());

        // 将“自动调度”和“人工确认后调度”拆成两个状态，便于前端和调度器精确协同。
        job.setStatus(Boolean.TRUE.equals(req.getAutoAssign()) ? "QUEUED" : "MANUAL");

        this.save(job);
        LogUtil.dataChange("创建打印任务", "FarmPrintJob", job.getId(),
                String.format("用户=%d，调度方式=%s，材料=%s，喷嘴=%s",
                        userId, job.getStatus(), job.getMaterialType(), job.getNozzleSize()));
        return job.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignAndStartPrint(Long jobId, Long printerId) {
        PrintJob job = this.getById(jobId);
        Printer printer = printerService.getById(printerId);

        if (job == null || printer == null) {
            log.warn("派发打印任务失败：任务或打印机不存在，jobId={}, printerId={}", jobId, printerId);
            throw new BusinessException("找不到对应的任务或打印机");
        }
        if (!"IDLE".equals(printer.getStatus())) {
            log.warn("派发打印任务失败：打印机非空闲状态，jobId={}, printerId={}, status={}", jobId, printerId, printer.getStatus());
            throw new BusinessException("该打印机正在忙碌，无法派单");
        }
        if (!"QUEUED".equals(job.getStatus()) && !"MANUAL".equals(job.getStatus())) {
            log.warn("派发打印任务失败：任务状态不支持派发，jobId={}, status={}", jobId, job.getStatus());
            throw new BusinessException("任务状态不支持派发");
        }

        // 在下发前做材料与喷嘴校验，避免设备执行后才发现工艺不匹配导致中途失败。
        if (job.getNozzleSize() != null && printer.getNozzleSize() != null
                && job.getNozzleSize().compareTo(printer.getNozzleSize()) != 0) {
            throw new BusinessException("喷嘴尺寸不匹配(" + job.getNozzleSize() + " vs " + printer.getNozzleSize() + ")");
        }
        if (job.getMaterialType() != null && printer.getCurrentMaterial() != null
                && !job.getMaterialType().equalsIgnoreCase(printer.getCurrentMaterial())) {
            throw new BusinessException("装载耗材不匹配(" + job.getMaterialType() + " vs " + printer.getCurrentMaterial() + ")");
        }

        PrintFile fileRecord = printFileMapper.selectById(job.getFileId());
        if (fileRecord == null) {
            throw new BusinessException("切片文件数据缺失");
        }

        String filename = fileRecord.getOriginalName();
        String safeName = fileRecord.getSafeName() != null
                ? fileRecord.getSafeName()
                : job.getFileUrl().substring(job.getFileUrl().lastIndexOf("/") + 1);

        LogUtil.bizInfo("任务派发", "任务ID", jobId, "打印机ID", printerId, "文件名", filename);

        org.springframework.core.io.Resource fileStream = rustFsClient.getFileStream(safeName);
        if (fileStream == null) {
            throw new BusinessException("无法从对象存储中读取切片文件");
        }

        boolean isSuccess = moonrakerApiClient.uploadAndPrint(printer.getIpAddress(), fileStream, filename);
        if (!isSuccess) {
            log.warn("派发打印任务失败：下发到打印机失败，jobId={}, printerId={}", jobId, printerId);
            throw new BusinessException("物理机接收文件超时或失败，请检查打印机网络连接");
        }

        job.setPrinterId(printerId);
        job.setStatus("PRINTING");
        job.setStartedAt(LocalDateTime.now());
        this.updateById(job);

        printer.setStatus("PRINTING");
        printerService.updateById(printer);

        LogUtil.dataChange("启动打印任务", "FarmPrintJob", job.getId(), "已分配到打印机: " + printer.getName());
        return true;
    }
}