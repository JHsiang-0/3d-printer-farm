package com.example.farm.common.utils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class GCodeParser {

    // 内部类，用于一次性返回解析到的所有元数据
    @Data
    public static class GCodeMeta {
        private Integer estTime = 0;           // 预计耗时 (秒)
        private String materialType;           // 耗材类型 (如 PLA, ABS)
        private BigDecimal nozzleSize;         // 喷嘴直径 (如 0.4)
    }

    /**
     * 从 G-code 文本中全面提取硬件要求和耗时
     */
    public static GCodeMeta parseMetadata(String content) {
        GCodeMeta meta = new GCodeMeta();
        if (content == null || content.isEmpty()) return meta;

        // 1. 解析预计时间 (兼容 Cura 的 TIME: 和 Prusa/Orca 的 estimated printing time)
        Matcher timeMatcher = Pattern.compile("(?:TIME:|estimated printing time.*=)\\s*(\\d+)").matcher(content);
        if (timeMatcher.find()) {
            meta.setEstTime(Integer.parseInt(timeMatcher.group(1)));
        }

        // 2. 解析耗材类型 (匹配 filament_type = ABS 或 Filament type: PLA)
        Matcher matMatcher = Pattern.compile("(?i)(?:filament_type|Filament type)\\s*[:=]\\s*([a-zA-Z0-9_]+)").matcher(content);
        if (matMatcher.find()) {
            meta.setMaterialType(matMatcher.group(1).toUpperCase());
        }

        // 3. 解析喷嘴直径 (匹配 nozzle_diameter = 0.4)
        Matcher nozzleMatcher = Pattern.compile("(?i)nozzle_diameter\\s*[:=]\\s*([0-9.]+)").matcher(content);
        if (nozzleMatcher.find()) {
            try {
                meta.setNozzleSize(new BigDecimal(nozzleMatcher.group(1)));
            } catch (Exception e) {
                log.warn("无法转换喷嘴直径数字: {}", nozzleMatcher.group(1));
            }
        }

        return meta;
    }
}