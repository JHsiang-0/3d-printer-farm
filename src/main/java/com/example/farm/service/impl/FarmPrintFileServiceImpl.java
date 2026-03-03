package com.example.farm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.common.utils.GCodeParser;
import com.example.farm.common.utils.RustFsClient;
import com.example.farm.entity.FarmPrintFile;
import com.example.farm.entity.dto.FarmPrintFileQueryDTO;
import com.example.farm.mapper.FarmPrintFileMapper;
import com.example.farm.service.FarmPrintFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/**
 * <p>
 * 打印文件表 服务实现类
 * </p>
 *
 * @author codexiang
 * @since 2026-03-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FarmPrintFileServiceImpl extends ServiceImpl<FarmPrintFileMapper, FarmPrintFile> implements FarmPrintFileService {

    private final RustFsClient rustFsClient;
    private final FarmPrintFileMapper farmPrintFileMapper;

    @Override
    public FarmPrintFileMapper getBaseMapper() {
        return farmPrintFileMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FarmPrintFile uploadFile(MultipartFile file, Long userId) {
        // 1. 参数校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName)) {
            throw new BusinessException("文件名不能为空");
        }

        // 2. 生成安全文件名
        String safeName = System.currentTimeMillis() + "_" + originalName;

        // 3. 上传到 RustFS
        String fileUrl;
        try {
            fileUrl = rustFsClient.uploadFile(safeName, file);
        } catch (Exception e) {
            log.error("文件上传到RustFS失败: {}", originalName, e);
            throw new BusinessException("文件上传失败，请稍后重试");
        }

        // 4. 极速读取文件头并解析GCode元数据
        GCodeParser.GCodeMeta meta;
        try {
            String headerContent = rustFsClient.readHeader(safeName);
            meta = GCodeParser.parseMetadata(headerContent);
        } catch (Exception e) {
            log.warn("GCode文件解析失败: {}", originalName, e);
            // 解析失败不影响文件上传，使用默认值
            meta = new GCodeParser.GCodeMeta();
        }

        // 5. 构建文件实体
        FarmPrintFile farmPrintFile = new FarmPrintFile();
        farmPrintFile.setOriginalName(originalName);
        farmPrintFile.setSafeName(safeName);
        farmPrintFile.setFileUrl(fileUrl);
        farmPrintFile.setFileSize(file.getSize());
        farmPrintFile.setUserId(userId);
        farmPrintFile.setCreatedAt(LocalDateTime.now());

        // 保存解析出的元数据
        farmPrintFile.setEstTime(meta.getEstTime());
        farmPrintFile.setMaterialType(meta.getMaterialType());
        farmPrintFile.setNozzleSize(meta.getNozzleSize());

        // 6. 保存到数据库
        farmPrintFileMapper.insert(farmPrintFile);

        log.info("文件上传成功: id={}, name={}, userId={}", farmPrintFile.getId(), originalName, userId);

        return farmPrintFile;
    }

    @Override
    public Page<FarmPrintFile> pageFiles(FarmPrintFileQueryDTO queryDTO) {
        // 1. 构造分页对象
        Page<FarmPrintFile> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());

        // 2. 构造查询条件
        LambdaQueryWrapper<FarmPrintFile> wrapper = new LambdaQueryWrapper<>();

        // 文件名模糊查询
        wrapper.like(StringUtils.hasText(queryDTO.getFileName()), 
                FarmPrintFile::getOriginalName, queryDTO.getFileName());

        // 耗材类型精确匹配
        wrapper.eq(StringUtils.hasText(queryDTO.getMaterialType()), 
                FarmPrintFile::getMaterialType, queryDTO.getMaterialType());

        // 用户ID精确匹配
        wrapper.eq(queryDTO.getUserId() != null, 
                FarmPrintFile::getUserId, queryDTO.getUserId());

        // 按创建时间倒序排列
        wrapper.orderByDesc(FarmPrintFile::getCreatedAt);

        // 3. 执行分页查询并返回
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long id, Long userId) {
        // 1. 校验文件是否存在
        FarmPrintFile file = farmPrintFileMapper.selectById(id);
        if (file == null) {
            throw new BusinessException("文件不存在或已被删除");
        }

        // 2. 权限校验：只能删除自己上传的文件（管理员除外，此处简化处理）
        if (!file.getUserId().equals(userId)) {
            throw new BusinessException("无权删除他人上传的文件");
        }

        // 3. 删除数据库记录
        farmPrintFileMapper.deleteById(id);

        // 4. 异步删除RustFS上的文件（可选，根据业务需求）
        // 注意：这里选择保留文件或异步删除，避免事务回滚时文件已删除的问题
        // rustFsClient.deleteFile(file.getSafeName());

        log.info("文件删除成功: id={}, name={}, userId={}", id, file.getOriginalName(), userId);
    }
}
