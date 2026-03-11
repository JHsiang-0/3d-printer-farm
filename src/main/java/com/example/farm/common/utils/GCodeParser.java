package com.example.farm.common.utils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class GCodeParser {

    private static final Pattern TIME_SECONDS_PATTERN = Pattern.compile("(?im)^\\s*;?\\s*TIME\\s*[:=]\\s*(\\d+)\\s*$");
    private static final Pattern EST_TIME_PATTERN = Pattern.compile("(?im)^\\s*;?\\s*estimated_time\\s*[:=]\\s*(\\d+)\\s*$");
    private static final Pattern TIME_TEXT_PATTERN = Pattern.compile(
            "(?im)^\\s*;?\\s*(?:estimated printing time(?:\\s*\\([^)]*\\))?|total estimated time|estimated time)\\s*[:=]\\s*([^\\r\\n]+)");
    private static final Pattern DURATION_TOKEN_PATTERN = Pattern.compile("(?i)(\\d+)\\s*([dhms])");
    private static final Pattern DURATION_HMS_COLON_PATTERN = Pattern.compile("^(\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?$");
    private static final Pattern MATERIAL_TOKEN_PATTERN = Pattern.compile("(?i)^[a-z][a-z0-9+\\-_.]*$");
    private static final Pattern ORCA_FILENAME_META_PATTERN = Pattern.compile(
            "(?i)^.*_([0-9]+(?:\\.[0-9]+)?)mm_([^_]+)_(.+)_([^_]+)$");

    private static final Pattern MATERIAL_PATTERN = Pattern.compile(
            "(?im)^\\s*;?\\s*(?:filament_type|filament type|material_type|material)\\s*[:=]\\s*([^\\r\\n]+)");

    // OrcaSlicer/Bambu/Prusa style: nozzle_diameter = 0.4 or nozzle_diameter = 0.4,0.4
    private static final Pattern NOZZLE_PATTERN = Pattern.compile(
            "(?im)^\\s*;?\\s*nozzle_diameter\\s*[:=]\\s*\\[?\\s*([0-9]+(?:\\.[0-9]+)?)");

    // Common line width keys in different slicers
    private static final Pattern LINE_WIDTH_PATTERN = Pattern.compile(
            "(?im)^\\s*;?\\s*(?:line_width|default_line_width|outer_wall_line_width|inner_wall_line_width|wall_line_width|perimeter_line_width|external_perimeter_line_width)\\s*[:=]\\s*\\[?\\s*([0-9]+(?:\\.[0-9]+)?)");

    // Filament weight patterns (filament used = weight in grams)
    private static final Pattern FILAMENT_WEIGHT_PATTERN = Pattern.compile(
            "(?im)^\\s*;?\\s*(?:filament used|filament_used|total filament used|filament weight)\\s*[:=]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(?:g|gram|grams)?");

    // Alternative weight pattern for OrcaSlicer/Bambu Studio format: "; filament used [g] = 12.34"
    private static final Pattern FILAMENT_WEIGHT_ALT_PATTERN = Pattern.compile(
            "(?im)^\\s*;?\\s*filament used\\s*\\[g\\]\\s*[:=]\\s*([0-9]+(?:\\.[0-9]+)?)");

    // Filament length patterns (filament used = length in mm)
    private static final Pattern FILAMENT_LENGTH_PATTERN = Pattern.compile(
            "(?im)^\\s*;?\\s*(?:filament used|filament_used|total filament used)\\s*[:=]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(?:mm|meter|m)?");

    // Alternative length pattern for OrcaSlicer/Bambu Studio format: "; filament used [mm] = 1234.56"
    private static final Pattern FILAMENT_LENGTH_ALT_PATTERN = Pattern.compile(
            "(?im)^\\s*;?\\s*filament used\\s*\\[mm\\]\\s*[:=]\\s*([0-9]+(?:\\.[0-9]+)?)");

    // OrcaSlicer temperature patterns
    private static final Pattern NOZZLE_TEMP_PATTERN = Pattern.compile(
            "(?im)^\\s*;?\\s*nozzle_temperature(?:_initial|_first_layer)?\\s*[:=]\\s*(\\d+)");
    private static final Pattern BED_TEMP_PATTERN = Pattern.compile(
            "(?im)^\\s*;?\\s*bed_temperature(?:_initial|_first_layer)?\\s*[:=]\\s*(\\d+)");

    // OrcaSlicer layer height pattern
    private static final Pattern LAYER_HEIGHT_PATTERN = Pattern.compile(
            "(?im)^\\s*;?\\s*(?:layer_height|layer_height_(?:first|initial)_layer)\\s*[:=]\\s*([0-9]+(?:\\.[0-9]+)?)");

    // Thumbnail pattern for base64 encoded images in G-code
    private static final Pattern THUMBNAIL_START_PATTERN = Pattern.compile(
            "(?im)^\\s*;\\s*(?:thumbnail|thumbnail_JPG|thumbnail_PNG)\\s*(?:begin|start)");
    private static final Pattern THUMBNAIL_DATA_PATTERN = Pattern.compile(
            "(?im)^\\s*;\\s*([A-Za-z0-9+/=]{50,})");
    private static final Pattern THUMBNAIL_END_PATTERN = Pattern.compile(
            "(?im)^\\s*;\\s*(?:thumbnail|thumbnail_JPG|thumbnail_PNG)\\s*(?:end|stop)");

    @Data
    public static class GCodeMeta {
        private Integer estTime = 0;
        private String materialType;
        private BigDecimal nozzleSize;
        private BigDecimal lineWidth;
        private BigDecimal filamentWeight;  // 耗材重量(g)
        private BigDecimal filamentLength;  // 耗材长度(mm)
        private Integer nozzleTemp;         // 喷头温度(℃)
        private Integer bedTemp;            // 热床温度(℃)
        private BigDecimal layerHeight;     // 层高(mm)
    }

    public static GCodeMeta parseMetadata(String content) {
        GCodeMeta meta = new GCodeMeta();
        if (content == null || content.isEmpty()) {
            return meta;
        }

        meta.setEstTime(parseEstTime(content));

        Matcher matMatcher = MATERIAL_PATTERN.matcher(content);
        if (matMatcher.find()) {
            String normalizedMaterial = normalizeMaterial(matMatcher.group(1));
            if (normalizedMaterial != null && !normalizedMaterial.isEmpty()) {
                meta.setMaterialType(normalizedMaterial.toUpperCase());
            }
        }

        meta.setNozzleSize(parseDecimal(content, NOZZLE_PATTERN));
        meta.setLineWidth(parseDecimal(content, LINE_WIDTH_PATTERN));

        // Parse filament weight (g) - try multiple patterns
        BigDecimal weight = parseDecimal(content, FILAMENT_WEIGHT_ALT_PATTERN);
        if (weight == null) {
            weight = parseDecimal(content, FILAMENT_WEIGHT_PATTERN);
        }
        meta.setFilamentWeight(weight);

        // Parse filament length (mm) - try multiple patterns
        BigDecimal length = parseDecimal(content, FILAMENT_LENGTH_ALT_PATTERN);
        if (length == null) {
            length = parseDecimal(content, FILAMENT_LENGTH_PATTERN);
        }
        // Convert mm to meters if length is large (> 1000mm)
        if (length != null && length.compareTo(new BigDecimal("1000")) > 0) {
            length = length.divide(new BigDecimal("1000"), 2, java.math.RoundingMode.HALF_UP);
        }
        meta.setFilamentLength(length);

        // Parse OrcaSlicer temperature values
        meta.setNozzleTemp(parseInteger(content, NOZZLE_TEMP_PATTERN));
        meta.setBedTemp(parseInteger(content, BED_TEMP_PATTERN));

        // Parse layer height
        meta.setLayerHeight(parseDecimal(content, LAYER_HEIGHT_PATTERN));

        return meta;
    }

    public static GCodeMeta parseMetadataFromFilename(String filename) {
        GCodeMeta meta = new GCodeMeta();
        if (filename == null || filename.isBlank()) {
            return meta;
        }

        String base = stripExtension(filename.trim());
        if (base.isEmpty()) {
            return meta;
        }

        String timeToken = null;
        String materialToken = null;

        Matcher orcaMatcher = ORCA_FILENAME_META_PATTERN.matcher(base);
        if (orcaMatcher.matches()) {
            materialToken = orcaMatcher.group(2);
            timeToken = orcaMatcher.group(4);
        } else {
            String[] parts = base.split("_");
            if (parts.length >= 4) {
                timeToken = parts[parts.length - 1];
                materialToken = parts[parts.length - 3];
            }
        }

        Integer seconds = tryParseDurationText(timeToken);
        if (seconds != null && seconds > 0) {
            meta.setEstTime(seconds);
        }

        String material = normalizeMaterial(materialToken);
        if (material != null && MATERIAL_TOKEN_PATTERN.matcher(material).matches()) {
            meta.setMaterialType(material.toUpperCase());
        }

        return meta;
    }

    private static BigDecimal parseDecimal(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group(1));
        } catch (Exception e) {
            log.warn("Numeric parse failed: {}", matcher.group(1));
            return null;
        }
    }

    private static Integer parseInteger(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            log.warn("Integer parse failed: {}", matcher.group(1));
            return null;
        }
    }

    private static Integer parseEstTime(String content) {
        // Try TIME = seconds format
        Matcher secondMatcher = TIME_SECONDS_PATTERN.matcher(content);
        if (secondMatcher.find()) {
            try {
                return Integer.parseInt(secondMatcher.group(1));
            } catch (NumberFormatException ignore) {
                log.debug("TIME parse failed: {}", secondMatcher.group(1));
            }
        }

        // Try estimated_time = seconds format (OrcaSlicer)
        Matcher estTimeMatcher = EST_TIME_PATTERN.matcher(content);
        if (estTimeMatcher.find()) {
            try {
                return Integer.parseInt(estTimeMatcher.group(1));
            } catch (NumberFormatException ignore) {
                log.debug("estimated_time parse failed: {}", estTimeMatcher.group(1));
            }
        }

        // Try human-readable format: "estimated printing time = 1h 23m 45s"
        Matcher textMatcher = TIME_TEXT_PATTERN.matcher(content);
        if (!textMatcher.find()) {
            return 0;
        }

        String raw = textMatcher.group(1).trim();
        Integer durationSeconds = tryParseDurationText(raw);
        if (durationSeconds != null) {
            return durationSeconds;
        }

        log.debug("Unrecognized estimated time format: {}", raw);
        return 0;
    }

    private static Integer tryParseDurationText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        if (raw.matches("\\d+")) {
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ignore) {
                return null;
            }
        }

        Matcher hmsMatcher = DURATION_HMS_COLON_PATTERN.matcher(raw);
        if (hmsMatcher.matches()) {
            int part1 = Integer.parseInt(hmsMatcher.group(1));
            int part2 = Integer.parseInt(hmsMatcher.group(2));
            String part3 = hmsMatcher.group(3);
            if (part3 == null) {
                return part1 * 60 + part2;
            }
            return part1 * 3600 + part2 * 60 + Integer.parseInt(part3);
        }

        Matcher tokenMatcher = DURATION_TOKEN_PATTERN.matcher(raw);
        int total = 0;
        int found = 0;
        while (tokenMatcher.find()) {
            found++;
            int num = Integer.parseInt(tokenMatcher.group(1));
            char unit = Character.toLowerCase(tokenMatcher.group(2).charAt(0));
            if (unit == 'd') {
                total += num * 86400;
            } else if (unit == 'h') {
                total += num * 3600;
            } else if (unit == 'm') {
                total += num * 60;
            } else if (unit == 's') {
                total += num;
            }
        }
        return found > 0 ? total : null;
    }

    private static String normalizeMaterial(String raw) {
        if (raw == null) {
            return null;
        }

        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }

        value = value.replace("[", "").replace("]", "").trim();
        String[] tokens = value.split("[;,]");
        for (String token : tokens) {
            String item = token == null ? "" : token.trim();
            if ((item.startsWith("\"") && item.endsWith("\"")) || (item.startsWith("'") && item.endsWith("'"))) {
                if (item.length() > 1) {
                    item = item.substring(1, item.length() - 1).trim();
                }
            }
            if (!item.isEmpty()) {
                return item;
            }
        }
        return null;
    }

    private static String stripExtension(String filename) {
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = slash >= 0 ? filename.substring(slash + 1) : filename;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * 从 G-code 内容中提取 Base64 编码的缩略图
     *
     * @param content G-code 文件内容
     * @return 缩略图的 Base64 字符串，如果没有找到则返回 null
     */
    public static String extractThumbnailBase64(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        StringBuilder base64Builder = new StringBuilder();
        boolean inThumbnail = false;

        String[] lines = content.split("\\r?\\n");

        for (String line : lines) {
            // 检测缩略图开始
            if (THUMBNAIL_START_PATTERN.matcher(line).find()) {
                inThumbnail = true;
                base64Builder.setLength(0);
                log.debug("检测到缩略图开始标记");
                continue;
            }

            // 检测缩略图结束
            if (THUMBNAIL_END_PATTERN.matcher(line).find()) {
                inThumbnail = false;
                log.debug("检测到缩略图结束标记");
                break;
            }

            // 提取 Base64 数据
            if (inThumbnail) {
                Matcher dataMatcher = THUMBNAIL_DATA_PATTERN.matcher(line);
                if (dataMatcher.find()) {
                    base64Builder.append(dataMatcher.group(1));
                }
            }
        }

        String base64Data = base64Builder.toString();
        if (base64Data.isEmpty()) {
            return null;
        }

        log.info("成功从 G-code 提取缩略图，Base64 长度: {} 字符", base64Data.length());
        return base64Data;
    }
}
