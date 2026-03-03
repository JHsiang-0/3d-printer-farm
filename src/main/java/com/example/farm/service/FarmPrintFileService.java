package com.example.farm.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.farm.entity.FarmPrintFile;
import com.example.farm.entity.dto.FarmPrintFileQueryDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * <p>
 * 打印文件表 服务类
 * </p>
 *
 * @author codexiang
 * @since 2026-03-01
 */
public interface FarmPrintFileService extends IService<FarmPrintFile> {

    /**
     * 上传切片文件
     *
     * @param file 上传的文件
     * @param userId 当前登录用户ID
     * @return 保存后的文件记录
     */
    FarmPrintFile uploadFile(MultipartFile file, Long userId);

    /**
     * 根据条件分页查询打印文件列表
     *
     * @param queryDTO 查询参数
     * @return 分页结果
     */
    Page<FarmPrintFile> pageFiles(FarmPrintFileQueryDTO queryDTO);

    /**
     * 删除打印文件
     *
     * @param id 文件ID
     * @param userId 当前登录用户ID（用于权限校验）
     */
    void deleteFile(Long id, Long userId);
}
