package com.example.farm.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.dto.FarmPrinterAddDTO;
import com.example.farm.entity.dto.FarmPrinterQueryDTO;
import com.example.farm.entity.dto.FarmPrinterUpdateDTO;

import java.util.List;

public interface FarmPrinterService extends IService<FarmPrinter> {

    /**
     * 根据条件分页查询打印机列表
     */
    Page<FarmPrinter> pagePrinters(FarmPrinterQueryDTO queryDTO);

    /**
     * 添加新打印机
     */
    void addPrinter(FarmPrinterAddDTO addDTO);

    /**
     * 修改打印机信息
     */
    void updatePrinter(FarmPrinterUpdateDTO updateDTO);

    /**
     * 删除打印机
     */
    void deletePrinter(Long id);
    /**
     * 扫描局域网内未录入的 Klipper 设备 (基于 7125 端口)
     * @param subnet 网段前缀，例如 "192.168.1"
     */
    List<String> scanNewKlipperDevices(String subnet);

    /**
     * 一键批量添加设备
     */
    void batchAddPrinters(List<String> ipList);
}