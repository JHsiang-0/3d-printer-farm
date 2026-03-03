package com.example.farm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.farm.entity.FarmPrintJob;
import com.example.farm.entity.dto.FarmPrintJobCreateDTO;

import java.util.List;

/**
 * <p>
 * 打印任务 服务类
 * </p>
 *
 * @author codexiang
 * @since 2026-03-01
 */
public interface FarmPrintJobService extends IService<FarmPrintJob> {

    /**
     * 提交打印任务（旧接口，兼容）
     */
    Long submitJob(Long fileId, Long userId, Integer priority);

    /**
     * 获取排队中的任务列表
     */
    List<FarmPrintJob> getQueuedJobs();

    /**
     * 创建打印任务（新接口）
     */
    Long createJob(FarmPrintJobCreateDTO req);

    /**
     * 创建打印任务（新接口，带用户ID）
     * @param req 创建任务请求
     * @param userId 当前登录用户ID
     * @return 任务ID
     */
    Long createJob(FarmPrintJobCreateDTO req, Long userId);

    /**
     * 分配任务并开始打印
     */
    boolean assignAndStartPrint(Long jobId, Long printerId);
}
