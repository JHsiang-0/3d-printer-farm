package com.example.farm.service.impl;

import com.example.farm.common.constant.RedisKeyConstant;
import com.example.farm.common.utils.RedisUtil;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.dto.MoonrakerStatusDTO;
import com.example.farm.service.FarmPrinterService;
import com.example.farm.service.PrinterCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 打印机缓存服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrinterCacheServiceImpl implements PrinterCacheService {

    private final RedisUtil redisUtil;
    private final FarmPrinterService farmPrinterService;

    // 缓存过期时间：10秒
    private static final long STATUS_CACHE_TTL = 10;
    // 分布式锁过期时间：5秒
    private static final long LOCK_TTL = 5;
    // 历史记录保留数量
    private static final int HISTORY_KEEP_COUNT = 2880; // 24小时，每30秒一条

    @Override
    public void cachePrinterStatus(Long printerId, MoonrakerStatusDTO status) {
        if (printerId == null || status == null) {
            return;
        }
        String key = RedisKeyConstant.getKey(RedisKeyConstant.PRINTER_STATUS, printerId);
        redisUtil.set(key, status, STATUS_CACHE_TTL, TimeUnit.SECONDS);
    }

    @Override
    public MoonrakerStatusDTO getCachedStatus(Long printerId) {
        if (printerId == null) {
            return null;
        }
        String key = RedisKeyConstant.getKey(RedisKeyConstant.PRINTER_STATUS, printerId);
        return redisUtil.get(key);
    }

    @Override
    public List<MoonrakerStatusDTO> getCachedStatusBatch(List<Long> printerIds) {
        if (printerIds == null || printerIds.isEmpty()) {
            return new ArrayList<>();
        }
        return printerIds.stream()
                .map(this::getCachedStatus)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public void clearStatusCache(Long printerId) {
        if (printerId == null) {
            return;
        }
        String key = RedisKeyConstant.getKey(RedisKeyConstant.PRINTER_STATUS, printerId);
        redisUtil.delete(key);
    }

    @Override
    public boolean updatePrinterStatusWithLock(FarmPrinter printer) {
        if (printer == null || printer.getId() == null) {
            return false;
        }

        Long printerId = printer.getId();
        String lockKey = RedisKeyConstant.getKey(RedisKeyConstant.PRINTER_LOCK, printerId);
        String lockValue = UUID.randomUUID().toString();

        boolean locked = false;
        try {
            // 尝试获取锁
            locked = redisUtil.tryLock(lockKey, lockValue, LOCK_TTL, TimeUnit.SECONDS);
            if (!locked) {
                log.debug("获取打印机状态锁失败，printerId={}", printerId);
                return false;
            }

            // 获取锁成功，更新数据库
            farmPrinterService.updateById(printer);
            log.debug("打印机状态更新成功，printerId={}, status={}", printerId, printer.getStatus());
            return true;

        } catch (Exception e) {
            log.error("更新打印机状态失败，printerId={}", printerId, e);
            return false;
        } finally {
            if (locked) {
                // 检查锁是否还是自己持有的，避免误删其他线程的锁
                String currentValue = redisUtil.getLockValue(lockKey);
                if (lockValue.equals(currentValue)) {
                    redisUtil.unlock(lockKey);
                }
            }
        }
    }

    @Override
    public boolean tryLockPrinterStatus(Long printerId) {
        String lockKey = RedisKeyConstant.getKey(RedisKeyConstant.PRINTER_LOCK, printerId);
        String lockValue = UUID.randomUUID().toString();
        return redisUtil.tryLock(lockKey, lockValue, LOCK_TTL, TimeUnit.SECONDS);
    }

    @Override
    public void unlockPrinterStatus(Long printerId) {
        String lockKey = RedisKeyConstant.getKey(RedisKeyConstant.PRINTER_LOCK, printerId);
        redisUtil.unlock(lockKey);
    }

    @Override
    public void recordStatusHistory(Long printerId, MoonrakerStatusDTO status) {
        // TODO: 使用 Redis List 存储历史记录，待实现
        // String key = RedisKeyConstant.getKey(RedisKeyConstant.PRINTER_HISTORY, printerId);
        // redisTemplate.opsForList().leftPush(key, status);
        // redisTemplate.opsForList().trim(key, 0, HISTORY_KEEP_COUNT - 1);
        // redisUtil.expire(key, 24, TimeUnit.HOURS);
    }

    @Override
    public List<MoonrakerStatusDTO> getStatusHistory(Long printerId, int count) {
        // TODO: 待实现
        return new ArrayList<>();
    }

    @Override
    public long getOnlinePrinterCount() {
        // TODO: 通过 Redis 统计在线数量，待实现
        // 临时返回所有打印机数量
        return farmPrinterService.count();
    }

    @Override
    public long getBusyPrinterCount() {
        // TODO: 通过 Redis 统计忙碌数量，待实现
        return 0;
    }

    @Override
    public void cacheSystemStats(Object stats) {
        if (stats == null) {
            return;
        }
        String key = RedisKeyConstant.getKey(RedisKeyConstant.SYSTEM_STATS, "overview");
        redisUtil.set(key, stats, 60, TimeUnit.SECONDS);
    }

    @Override
    public Object getSystemStats() {
        String key = RedisKeyConstant.getKey(RedisKeyConstant.SYSTEM_STATS, "overview");
        return redisUtil.get(key);
    }
}
