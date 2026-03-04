package com.example.farm.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.dto.FarmPrinterAddDTO;
import com.example.farm.entity.dto.FarmPrinterQueryDTO;
import com.example.farm.entity.dto.FarmPrinterUpdateDTO;

import java.util.List;

/**
 * 打印机服务接口。
 */
public interface FarmPrinterService extends IService<FarmPrinter> {

    /**
     * 分页查询打印机列表。
     *
     * @param queryDTO 查询参数
     * @return 打印机分页结果
     */
    Page<FarmPrinter> pagePrinters(FarmPrinterQueryDTO queryDTO);

    /**
     * 新增打印机。
     *
     * @param addDTO 新增参数
     * @throws BusinessException 当 IP 已存在或参数非法时抛出
     */
    void addPrinter(FarmPrinterAddDTO addDTO);

    /**
     * 更新打印机信息。
     *
     * @param updateDTO 更新参数
     * @throws BusinessException 当打印机不存在或目标 IP 被占用时抛出
     */
    void updatePrinter(FarmPrinterUpdateDTO updateDTO);

    /**
     * 删除打印机。
     *
     * @param id 打印机 ID
     * @throws BusinessException 当打印机不存在或正在打印时抛出
     */
    void deletePrinter(Long id);

    /**
     * 扫描网段内尚未录入的 Klipper 设备。
     *
     * @param subnet 网段前缀，例如 `192.168.1`
     * @return 新发现设备 IP 列表
     */
    List<String> scanNewKlipperDevices(String subnet);

    /**
     * 批量新增打印机。
     *
     * @param ipList 打印机 IP 列表
     * @throws BusinessException 当入参为空且业务不允许时抛出
     */
    void batchAddPrinters(List<String> ipList);
}