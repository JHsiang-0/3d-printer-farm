package com.example.farm.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.entity.PrintFile;
import com.example.farm.entity.dto.PrintFileQueryDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 打印文件服务接口。
 */
public interface PrintFileService extends IService<PrintFile> {

    /**
     * 分页查询当前用户的文件列表。
     *
     * @param queryDTO 查询参数
     * @return 文件分页结果
     */
    Page<PrintFile> pageFiles(PrintFileQueryDTO queryDTO);

    /**
     * 上传并解析切片文件。
     *
     * @param file 上传文件
     * @return 入库后的文件实体
     * @throws BusinessException 当文件为空、解析失败或存储失败时抛出
     */
    PrintFile uploadAndParseFile(MultipartFile file);

    /**
     * 删除文件。
     *
     * @param id 文件 ID
     * @throws BusinessException 当文件不存在或当前用户无权删除时抛出
     */
    void deleteFile(Long id);
}