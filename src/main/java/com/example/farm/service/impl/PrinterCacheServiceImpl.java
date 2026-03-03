package com.example.farm.service.impl;

import com.example.farm.common.constant.RedisKeyConstant;
import com.example.farm.common.utils.RedisUtil;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.dto.MoonrakerStatusDTO;
import com.example.farm.mapper.FarmPrinterMapper;
import com.example.farm.service.PrinterCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
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
    private final FarmPrinterMapper farmPrinterMapper;

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
        return redisUtil.get(key, MoonrakerStatusDTO.class);
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

            // 获取锁成功，更新数据库（直接使用Mapper，避免循环依赖）
            farmPrinterMapper.updateById(printer);
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
        if (printerId == null || status == null) {
            return;
        }
        try {
            String key = RedisKeyConstant.getKey(RedisKeyConstant.PRINTER_HISTORY, printerId);
            // 从左侧插入新记录（最新的在前面）
            redisUtil.listLeftPush(key, status);
            // 修剪列表，只保留最近 HISTORY_KEEP_COUNT 条
            redisUtil.listTrim(key, 0, HISTORY_KEEP_COUNT - 1);
            // 设置过期时间为 24 小时
            redisUtil.expire(key, 24, TimeUnit.HOURS);
            log.debug("📊 记录打印机状态历史: printerId={}, state={}", printerId, status.getState());
        } catch (Exception e) {
            log.error("❌ 记录状态历史失败: printerId={}", printerId, e);
        }
    }

    @Override
    public List<MoonrakerStatusDTO> getStatusHistory(Long printerId, int count) {
        if (printerId == null || count <= 0) {
            return new ArrayList<>();
        }
        try {
            String key = RedisKeyConstant.getKey(RedisKeyConstant.PRINTER_HISTORY, printerId);
            // 获取前 count 条记录（最新的在前面）
            return redisUtil.listRange(key, 0, count - 1, MoonrakerStatusDTO.class);
        } catch (Exception e) {
            log.error("❌ 获取状态历史失败: printerId={}, count={}", printerId, count, e);
            return new ArrayList<>();
        }
    }

    @Override
    public long getOnlinePrinterCount() {
        try {
            Long count = redisUtil.setSize(RedisKeyConstant.PRINTER_ONLINE_SET);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("❌ 获取在线打印机数量失败", e);
            return 0;
        }
    }

    @Override
    public long getBusyPrinterCount() {
        try {
            Long count = redisUtil.setSize(RedisKeyConstant.PRINTER_BUSY_SET);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("❌ 获取忙碌打印机数量失败", e);
            return 0;
        }
    }

    @Override
    public void markPrinterOnline(Long printerId) {
        if (printerId == null) return;
        try {
            redisUtil.setAdd(RedisKeyConstant.PRINTER_ONLINE_SET, printerId.toString());
            log.debug("✅ 标记打印机在线: printerId={}", printerId);
        } catch (Exception e) {
            log.error("❌ 标记打印机在线失败: printerId={}", printerId, e);
        }
    }

    @Override
    public void markPrinterOffline(Long printerId) {
        if (printerId == null) return;
        try {
            redisUtil.setRemove(RedisKeyConstant.PRINTER_ONLINE_SET, printerId.toString());
            // 离线时也从忙碌集合中移除
            redisUtil.setRemove(RedisKeyConstant.PRINTER_BUSY_SET, printerId.toString());
            log.debug("❌ 标记打印机离线: printerId={}", printerId);
        } catch (Exception e) {
            log.error("❌ 标记打印机离线失败: printerId={}", printerId, e);
        }
    }

    @Override
    public void markPrinterBusy(Long printerId) {
        if (printerId == null) return;
        try {
            // 忙碌必须先在线
            redisUtil.setAdd(RedisKeyConstant.PRINTER_ONLINE_SET, printerId.toString());
            redisUtil.setAdd(RedisKeyConstant.PRINTER_BUSY_SET, printerId.toString());
            log.debug("🔄 标记打印机忙碌: printerId={}", printerId);
        } catch (Exception e) {
            log.error("❌ 标记打印机忙碌失败: printerId={}", printerId, e);
        }
    }

    @Override
    public void markPrinterIdle(Long printerId) {
        if (printerId == null) return;
        try {
            redisUtil.setRemove(RedisKeyConstant.PRINTER_BUSY_SET, printerId.toString());
            log.debug("⏸️ 标记打印机空闲: printerId={}", printerId);
        } catch (Exception e) {
            log.error("❌ 标记打印机空闲失败: printerId={}", printerId, e);
        }
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
        return redisUtil.get(key, Object.class);
    }

    @Override
    public List<FarmPrinter> getAllPrintersFromCache() {
        String key = RedisKeyConstant.PRINTER_LIST;
        log.info("🔍 尝试从Redis获取打印机列表: key={}", key);
        // 使用 TypeReference 正确反序列化泛型集合
        List<FarmPrinter> result = redisUtil.get(key, new TypeReference<List<FarmPrinter>>() {});
        if (result == null) {
            log.info("⚠️ Redis缓存未命中: key={}", key);
        } else {
            log.info("✅ Redis缓存命中: key={}, size={}", key, result.size());
        }
        return result;
    }

    @Override
    public void cacheAllPrinters(List<FarmPrinter> printers) {
        if (printers == null || printers.isEmpty()) {
            log.warn("⚠️ 缓存打印机列表失败: 列表为空");
            return;
        }
        String key = RedisKeyConstant.PRINTER_LIST;
        log.info("💾 正在缓存打印机列表到Redis: key={}, size={}", key, printers.size());
        // 缓存1小时，打印机列表不会频繁变化
        redisUtil.set(key, printers, 1, TimeUnit.HOURS);
        log.info("✅ 打印机列表缓存完成: key={}", key);
    }

    @Override
    public void refreshPrinterCache() {
        String key = RedisKeyConstant.PRINTER_LIST;
        redisUtil.delete(key);
        log.info("🔄 打印机列表缓存已刷新");
    }
}
