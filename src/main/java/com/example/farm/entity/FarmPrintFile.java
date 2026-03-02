package com.example.farm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 打印文件表（切片文件/G-code文件）
 * </p>
 *
 * @author codexiang
 * @since 2026-03-01
 */
@Data
@TableName("farm_print_file")
@Schema(name = "FarmPrintFile", description = "打印文件表")
public class FarmPrintFile {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "安全文件名（带时间戳）")
    private String safeName;

    @Schema(description = "文件存储URL")
    private String fileUrl;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "上传用户ID")
    private Long userId;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "预计打印耗时（秒）")
    private Integer estTime;

    @Schema(description = "耗材类型（如 PLA, PETG, ABS）")
    private String materialType;

    @Schema(description = "喷嘴直径（如 0.40, 0.60）")
    private BigDecimal nozzleSize;
}
