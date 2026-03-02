package com.example.farm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.farm.entity.FarmPrintFile;
import com.example.farm.entity.FarmPrintJob;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.dto.FarmPrintJobCreateDTO;
import com.example.farm.mapper.FarmPrintFileMapper;
import com.example.farm.mapper.FarmPrintJobMapper;
import com.example.farm.service.FarmPrintJobService;
import com.example.farm.service.FarmPrinterService;
import com.example.farm.common.utils.MoonrakerApiClient;
import com.example.farm.common.utils.RustFsClient;
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
public class FarmPrintJobServiceImpl extends ServiceImpl<FarmPrintJobMapper, FarmPrintJob> implements FarmPrintJobService {

    private final FarmPrintJobMapper farmPrintJobMapper;
    private final FarmPrintFileMapper farmPrintFileMapper;
    private final FarmPrinterService farmPrinterService;
    private final RustFsClient rustFsClient;
    private final MoonrakerApiClient moonrakerApiClient;

    @Override
    public FarmPrintJobMapper getBaseMapper() {
        return farmPrintJobMapper;
    }

    @Override
    @Transactional
    public Long submitJob(Long fileId, Long userId, Integer priority) {
        FarmPrintJob job = new FarmPrintJob();
        job.setFileId(fileId);
        job.setUserId(userId);
        job.setPriority(priority != null ? priority : 0);
        job.setStatus("QUEUED");
        job.setProgress(BigDecimal.ZERO);
        this.save(job);
        return job.getId();
    }

    @Override
    public List<FarmPrintJob> getQueuedJobs() {
        return this.list(new LambdaQueryWrapper<FarmPrintJob>()
                .eq(FarmPrintJob::getStatus, "QUEUED")
                .orderByDesc(FarmPrintJob::getPriority)
                .orderByAsc(FarmPrintJob::getCreatedAt));
    }

    @Override
    @Transactional
    public Long createJob(FarmPrintJobCreateDTO req) {
        FarmPrintFile fileRecord = farmPrintFileMapper.selectById(req.getFileId());
        if (fileRecord == null) {
            throw new RuntimeException("所选的切片文件不存在！");
        }

        FarmPrintJob job = new FarmPrintJob();
        job.setUserId(1L);
        job.setFileId(req.getFileId());
        job.setFileUrl(fileRecord.getFileUrl());
        job.setEstTime(fileRecord.getEstTime());
        job.setMaterialType(req.getMaterialType() != null ? req.getMaterialType() : fileRecord.getMaterialType());
        job.setNozzleSize(req.getNozzleSize() != null ? req.getNozzleSize() : fileRecord.getNozzleSize());
        job.setStatus("QUEUED");
        job.setPriority(req.getPriority() != null ? req.getPriority() : 0);
        job.setProgress(BigDecimal.ZERO);

        this.save(job);

        log.info("📝 生产订单已下达！任务ID: {}, 要求耗材: {}, 喷嘴: {}mm",
                job.getId(), job.getMaterialType(), job.getNozzleSize());

        return job.getId();
    }

    @Override
    @Transactional
    public boolean assignAndStartPrint(Long jobId, Long printerId) {
        FarmPrintJob job = this.getById(jobId);
        FarmPrinter printer = farmPrinterService.getById(printerId);

        if (job == null || printer == null) {
            log.warn("⚠️ 派单中止：找不到对应的任务(ID:{})或打印机(ID:{})", jobId, printerId);
            return false;
        }
        if (!"IDLE".equals(printer.getStatus())) {
            log.warn("⚠️ 派单中止：打印机 [{}] 正在忙碌 (当前状态:{})", printer.getName(), printer.getStatus());
            return false;
        }
        if (!"QUEUED".equals(job.getStatus())) {
            log.warn("⚠️ 派单中止：任务 [#{}] 不在排队状态 (当前状态:{})", job.getId(), job.getStatus());
            return false;
        }

        // 防呆校验
        if (job.getNozzleSize() != null && printer.getNozzleSize() != null) {
            if (!job.getNozzleSize().equals(printer.getNozzleSize())) {
                log.warn("💥 严重工艺冲突！任务要求喷嘴 {}mm，但机器 [{}] 安装的是 {}mm！",
                        job.getNozzleSize(), printer.getName(), printer.getNozzleSize());
                throw new RuntimeException("喷嘴尺寸不匹配，派单失败！");
            }
        }

        if (job.getMaterialType() != null && printer.getCurrentMaterial() != null) {
            if (!job.getMaterialType().equalsIgnoreCase(printer.getCurrentMaterial())) {
                log.warn("💥 耗材冲突！任务要求 {}，机器 [{}] 当前装载的是 {}！",
                        job.getMaterialType(), printer.getName(), printer.getCurrentMaterial());
                throw new RuntimeException("装载耗材不匹配，派单失败！");
            }
        }

        FarmPrintFile fileRecord = farmPrintFileMapper.selectById(job.getFileId());
        String filename = fileRecord != null ? fileRecord.getOriginalName() : "farm_print.gcode";
        String safeName = fileRecord != null ? fileRecord.getSafeName() : 
                job.getFileUrl().substring(job.getFileUrl().lastIndexOf("/") + 1);

        log.info("👨‍💼 把任务 [{}] 指派给机器 [{}]", filename, printer.getName());

        org.springframework.core.io.Resource fileStream = rustFsClient.getFileStream(safeName);
        boolean isSuccess = moonrakerApiClient.uploadAndPrint(
                printer.getIpAddress(),
                fileStream,
                filename
        );

        if (isSuccess) {
            job.setPrinterId(printerId);
            job.setStatus("PRINTING");
            job.setStartedAt(LocalDateTime.now());
            this.updateById(job);

            printer.setStatus("PRINTING");
            farmPrinterService.updateById(printer);

            log.info("🎉 手动派单成功！机器开始打印！");
            return true;
        } else {
            throw new RuntimeException("物理机接收文件失败，请检查机器网络");
        }
    }
}
