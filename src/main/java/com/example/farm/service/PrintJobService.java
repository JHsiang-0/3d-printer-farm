package com.example.farm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.entity.PrintJob;
import com.example.farm.entity.dto.PrintJobCreateDTO;

import java.util.List;

/**
 * 打印任务服务接口。
 */
public interface PrintJobService extends IService<PrintJob> {

    /**
     * 提交打印任务（兼容旧接口）。
     *
     * @param fileId 文件 ID
     * @param userId 用户 ID
     * @param priority 优先级
     * @return 任务 ID
     * @throws BusinessException 当参数非法或文件不存在时抛出
     */
    Long submitJob(Long fileId, Long userId, Integer priority);

    /**
     * 查询可参与调度的任务列表。
     *
     * @return 任务列表
     */
    List<PrintJob> getQueuedJobs();

    /**
     * 创建打印任务（用户从上下文中获取）。
     *
     * @param req 创建请求
     * @return 任务 ID
     * @throws BusinessException 当用户未登录或文件不存在时抛出
     */
    Long createJob(PrintJobCreateDTO req);

    /**
     * 创建打印任务（显式指定用户）。
     *
     * @param req 创建请求
     * @param userId 用户 ID
     * @return 任务 ID
     * @throws BusinessException 当用户未登录或文件不存在时抛出
     */
    Long createJob(PrintJobCreateDTO req, Long userId);

    /**
     * 派发任务并启动打印。
     *
     * @param jobId 任务 ID
     * @param printerId 打印机 ID
     * @return 是否派发成功
     * @throws BusinessException 当任务状态、设备状态或工艺参数校验不通过时抛出
     */
    boolean assignAndStartPrint(Long jobId, Long printerId);
}