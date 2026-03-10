package com.example.farm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.common.utils.GCodeParser;
import com.example.farm.common.utils.RustFsClient;
import com.example.farm.common.utils.SecurityContextUtil;
import com.example.farm.entity.PrintFile;
import com.example.farm.entity.dto.PrintFileQueryDTO;
import com.example.farm.mapper.PrintFileMapper;
import com.example.farm.service.PrintFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintFileServiceImpl extends ServiceImpl<PrintFileMapper, PrintFile> implements PrintFileService {

    private static final int META_SAMPLE_SIZE = 8192;
    private static final int DEEP_TAIL_SAMPLE_SIZE = 512 * 1024; // 512KB
    private static final long FULL_PARSE_MAX_BYTES = 100L * 1024 * 1024; // 100MB

    private final RustFsClient rustFsClient;

    @Override
    public Page<PrintFile> pageFiles(PrintFileQueryDTO queryDTO) {
        Page<PrintFile> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<PrintFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrintFile::getUserId, SecurityContextUtil.getCurrentUserId())
                .orderByDesc(PrintFile::getCreatedAt);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrintFile uploadAndParseFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        Long userId = SecurityContextUtil.getCurrentUserId();
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "unknown.gcode";
        }
        String safeName = System.currentTimeMillis() + "_" + originalName;

        GCodeParser.GCodeMeta filenameMeta = GCodeParser.parseMetadataFromFilename(originalName);
        boolean filenameHit = hasUsableMeta(filenameMeta);

        String metaContent = extractHeadAndTail(file);
        GCodeParser.GCodeMeta contentMeta = GCodeParser.parseMetadata(metaContent);
        boolean headTailHit = hasUsableMeta(contentMeta);
        boolean deepTailTriggered = false;
        boolean deepTailHit = false;
        if (!headTailHit) {
            deepTailTriggered = true;
            String deepTailContent = extractTail(file, DEEP_TAIL_SAMPLE_SIZE);
            if (!deepTailContent.isEmpty()) {
                contentMeta = GCodeParser.parseMetadata(deepTailContent);
                deepTailHit = hasUsableMeta(contentMeta);
            }
        }

        boolean fullFallbackTriggered = false;
        boolean fullFallbackHit = false;
        boolean fullFallbackSkippedBySize = false;
        if (!headTailHit && !deepTailHit) {
            fullFallbackTriggered = true;
            String fullContent = extractFullContentIfAffordable(file);
            if (!fullContent.isEmpty()) {
                contentMeta = GCodeParser.parseMetadata(fullContent);
                fullFallbackHit = hasUsableMeta(contentMeta);
            } else {
                fullFallbackSkippedBySize = file.getSize() > FULL_PARSE_MAX_BYTES;
            }
        }

        GCodeParser.GCodeMeta meta = new GCodeParser.GCodeMeta();
        meta.setEstTime((filenameMeta.getEstTime() != null && filenameMeta.getEstTime() > 0)
                ? filenameMeta.getEstTime()
                : contentMeta.getEstTime());
        meta.setMaterialType(isNotBlank(filenameMeta.getMaterialType())
                ? filenameMeta.getMaterialType()
                : contentMeta.getMaterialType());
        meta.setNozzleSize(contentMeta.getNozzleSize());
        meta.setLineWidth(contentMeta.getLineWidth());

        String materialSource = isNotBlank(filenameMeta.getMaterialType())
                ? "filename"
                : (isNotBlank(contentMeta.getMaterialType())
                ? (fullFallbackTriggered ? "full-file" : (deepTailTriggered ? "deep-tail" : "head-tail"))
                : "default");
        String estTimeSource = (filenameMeta.getEstTime() != null && filenameMeta.getEstTime() > 0)
                ? "filename"
                : ((contentMeta.getEstTime() != null && contentMeta.getEstTime() > 0)
                ? (fullFallbackTriggered ? "full-file" : (deepTailTriggered ? "deep-tail" : "head-tail"))
                : "default");

        log.info("gcode meta parse path: userId={}, file={}, filenameHit={}, headTailHit={}, deepTailTriggered={}, deepTailHit={}, fullFallbackTriggered={}, fullFallbackHit={}, fullFallbackSkippedBySize={}, fileSize={}, materialSource={}, estTimeSource={}",
                userId, originalName, filenameHit, headTailHit, deepTailTriggered, deepTailHit, fullFallbackTriggered, fullFallbackHit, fullFallbackSkippedBySize, file.getSize(), materialSource, estTimeSource);
        log.info("切片元数据解析结果: userId={}, 文件={}, 预计耗时(s)={}, 喷嘴={}, 线宽={}, 材料={}",
                userId, originalName, meta.getEstTime(), meta.getNozzleSize(), meta.getLineWidth(), meta.getMaterialType());

        String fileUrl = rustFsClient.uploadFile(safeName, file);

        PrintFile printFile = new PrintFile();
        printFile.setOriginalName(originalName);
        printFile.setSafeName(safeName);
        printFile.setFileUrl(fileUrl);
        printFile.setFileSize(file.getSize());
        printFile.setUserId(userId);
        printFile.setCreatedAt(LocalDateTime.now());

        printFile.setEstTime(meta.getEstTime());
        printFile.setMaterialType(meta.getMaterialType() != null ? meta.getMaterialType() : "PLA");
        printFile.setNozzleSize(meta.getNozzleSize() != null ? meta.getNozzleSize() : new BigDecimal("0.40"));

        this.save(printFile);
        log.info("切片文件入库成功: fileId={}, userId={}, safeName={}", printFile.getId(), userId, safeName);
        return printFile;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long id) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        LambdaQueryWrapper<PrintFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrintFile::getId, id)
                .eq(PrintFile::getUserId, userId);
        PrintFile target = this.getOne(wrapper, false);
        if (target == null) {
            log.warn("delete print file ignored: not found or no permission, fileId={}, userId={}", id, userId);
            return;
        }

        String objectKey = target.getSafeName();
        rustFsClient.deleteFile(objectKey);
        this.removeById(target.getId());
        log.info("print file deleted from rustfs and db: fileId={}, userId={}, key={}", id, userId, objectKey);
    }

    private String extractHeadAndTail(MultipartFile file) {
        try {
            long size = file.getSize();
            if (size <= META_SAMPLE_SIZE * 2L) {
                return new String(file.getBytes(), StandardCharsets.UTF_8);
            }

            byte[] head = readFirstBytes(file, META_SAMPLE_SIZE);
            byte[] tail = readLastBytes(file, META_SAMPLE_SIZE);
            return new String(head, StandardCharsets.UTF_8) + "\n...\n" + new String(tail, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("读取切片文件头尾信息失败", e);
            return "";
        }
    }

    private String extractTail(MultipartFile file, int bytes) {
        try {
            byte[] tail = readLastBytes(file, bytes);
            return new String(tail, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("failed to read full gcode content for metadata fallback", e);
            return "";
        }
    }

    private byte[] readFirstBytes(MultipartFile file, int maxBytes) throws IOException {
        try (InputStream is = file.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream(maxBytes)) {
            byte[] buffer = new byte[4096];
            int remaining = maxBytes;
            int n;
            while (remaining > 0 && (n = is.read(buffer, 0, Math.min(buffer.length, remaining))) != -1) {
                out.write(buffer, 0, n);
                remaining -= n;
            }
            return out.toByteArray();
        }
    }

    private byte[] readLastBytes(MultipartFile file, int maxBytes) throws IOException {
        try (InputStream is = file.getInputStream()) {
            byte[] ring = new byte[maxBytes];
            byte[] buffer = new byte[8192];
            long total = 0;
            int n;
            while ((n = is.read(buffer)) != -1) {
                for (int i = 0; i < n; i++) {
                    ring[(int) ((total + i) % maxBytes)] = buffer[i];
                }
                total += n;
            }

            if (total == 0) {
                return new byte[0];
            }

            int actual = (int) Math.min(total, maxBytes);
            int start = (int) ((total - actual) % maxBytes);
            byte[] tail = new byte[actual];
            for (int i = 0; i < actual; i++) {
                tail[i] = ring[(start + i) % maxBytes];
            }
            return tail;
        }
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean hasUsableMeta(GCodeParser.GCodeMeta meta) {
        if (meta == null) {
            return false;
        }
        boolean hasMaterial = isNotBlank(meta.getMaterialType());
        boolean hasTime = meta.getEstTime() != null && meta.getEstTime() > 0;
        return hasMaterial || hasTime;
    }

    private String extractFullContentIfAffordable(MultipartFile file) {
        try {
            if (file.getSize() <= 0 || file.getSize() > FULL_PARSE_MAX_BYTES) {
                return "";
            }
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("failed to read full gcode content for metadata fallback", e);
            return "";
        }
    }
}
