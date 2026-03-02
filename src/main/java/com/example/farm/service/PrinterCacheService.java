package com.example.farm.service;

import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.dto.MoonrakerStatusDTO;

import java.util.List;

/**
 * 打印机缓存服务
 * 管理打印机实时状态的 Redis 缓存
 */
public interface PrinterCacheService {

    /**
     * 缓存打印机状态
     *
     * @param printerId 打印机ID
     * @param status    状态数据
     */
    void cachePrinterStatus(Long printerId, MoonrakerStatusDTO status);

    /**
     * 获取缓存的打印机状态
     *
     * @param printerId 打印机ID
     * @return 状态数据，如果没有缓存返回 null
     */
    MoonrakerStatusDTO getCachedStatus(Long printerId);

    /**
     * 批量获取打印机状态
     *
     * @param printerIds 打印机ID列表
     * @return 状态列表
     */
    List<MoonrakerStatusDTO> getCachedStatusBatch(List<Long> printerIds);

    /**
     * 清除打印机状态缓存
     *
     * @param printerId 打印机ID
     */
    void clearStatusCache(Long printerId);

    /**
     * 更新打印机状态到数据库（带分布式锁）
     *
     * @param printer 打印机实体
     * @return 是否更新成功
     */
    boolean updatePrinterStatusWithLock(FarmPrinter printer);

    /**
     * 尝试获取打印机状态更新锁
     *
     * @param printerId 打印机ID
     * @return 是否获取成功
     */
    boolean tryLockPrinterStatus(Long printerId);

    /**
     * 释放打印机状态更新锁
     *
     * @param printerId 打印机ID
     */
    void unlockPrinterStatus(Long printerId);

    /**
     * 记录打印机状态历史
     *
     * @param printerId 打印机ID
     * @param status    状态数据
     */
    void recordStatusHistory(Long printerId, MoonrakerStatusDTO status);

    /**
     * 获取打印机状态历史（最近N条）
     *
     * @param printerId 打印机ID
     * @param count     记录数量
     * @return 状态历史列表
     */
    List<MoonrakerStatusDTO> getStatusHistory(Long printerId, int count);

    /**
     * 获取在线打印机数量
     *
     * @return 在线数量
     */
    long getOnlinePrinterCount();

    /**
     * 获取忙碌打印机数量
     *
     * @return 忙碌数量
     */
    long getBusyPrinterCount();

    /**
     * 缓存系统统计数据
     *
     * @param stats 统计数据
     */
    void cacheSystemStats(Object stats);

    /**
     * 获取系统统计数据
     *
     * @return 统计数据
     */
    Object getSystemStats();
}
