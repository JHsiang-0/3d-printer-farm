package com.example.farm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.farm.common.exception.BusinessException;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.dto.FarmPrinterAddDTO;
import com.example.farm.entity.dto.FarmPrinterQueryDTO;
import com.example.farm.entity.dto.FarmPrinterUpdateDTO;
import com.example.farm.mapper.FarmPrinterMapper;
import com.example.farm.service.FarmPrinterService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class FarmPrinterServiceImpl extends ServiceImpl<FarmPrinterMapper, FarmPrinter> implements FarmPrinterService {

    @Override
    public Page<FarmPrinter> pagePrinters(FarmPrinterQueryDTO queryDTO) {
        // 1. 构造分页对象
        Page<FarmPrinter> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());

        // 2. 构造查询条件
        LambdaQueryWrapper<FarmPrinter> wrapper = new LambdaQueryWrapper<>();

        // 如果前端传了名称，则进行 LIKE 模糊搜索
        wrapper.like(StringUtils.hasText(queryDTO.getName()), FarmPrinter::getName, queryDTO.getName());

        // 如果前端传了状态，则进行精确匹配 (比如只看正在报错的机器)
        wrapper.eq(StringUtils.hasText(queryDTO.getStatus()), FarmPrinter::getStatus, queryDTO.getStatus());

        // 按创建时间倒序排列，或者你可以改成按状态排序 (比如故障的排在最前面)
        wrapper.orderByDesc(FarmPrinter::getCreatedAt);

        // 3. 执行分页查询并返回
        return this.page(page, wrapper);
    }

    @Override
    public void addPrinter(FarmPrinterAddDTO dto) {
        // 1. 核心校验：检查局域网 IP 是否已被占用
        long count = this.count(new LambdaQueryWrapper<FarmPrinter>()
                .eq(FarmPrinter::getIpAddress, dto.getIpAddress()));
        if (count > 0) {
            // 抛出我们之前写好的业务异常，大管家会拦截并返回给前端 500
            throw new BusinessException("该 IP 地址的打印机已存在，请勿重复添加！");
        }

        // 2. 将 DTO 转换为数据库实体 Entity
        FarmPrinter printer = new FarmPrinter();
        printer.setName(dto.getName());
        printer.setIpAddress(dto.getIpAddress());
        printer.setMacAddress(dto.getMacAddress());
        printer.setFirmwareType(dto.getFirmwareType());
        printer.setApiKey(dto.getApiKey());

        // 3. 强行业务规则：新入库的机器，状态一律强制设为 "OFFLINE" (离线)
        // 等待后续系统通过 Ping 或 WebSocket 心跳检测到它在线后，再改为 "IDLE" (空闲)
        printer.setStatus("OFFLINE");

        // 4. 保存到数据库 (MyBatis-Plus 会自动帮我们填充 createdAt 时间)
        this.save(printer);
    }

    @Override
    public void updatePrinter(FarmPrinterUpdateDTO dto) {
        // 1. 确保这台机器真的存在
        FarmPrinter existingPrinter = this.getById(dto.getId());
        if (existingPrinter == null) {
            throw new BusinessException("该打印机不存在！");
        }

        // 2. 如果修改了 IP 地址，必须检查新 IP 是否被【其他机器】占用了
        if (dto.getIpAddress() != null && !dto.getIpAddress().equals(existingPrinter.getIpAddress())) {
            long count = this.count(new LambdaQueryWrapper<FarmPrinter>()
                    .eq(FarmPrinter::getIpAddress, dto.getIpAddress())
                    .ne(FarmPrinter::getId, dto.getId())); // 排除自己
            if (count > 0) {
                throw new BusinessException("新的 IP 地址已被其他打印机占用！");
            }
        }

        // 3. 将新的数据覆盖进去 (由于用了 MyBatis-Plus，只 set 需要修改的字段即可)
        existingPrinter.setName(dto.getName());
        existingPrinter.setIpAddress(dto.getIpAddress());
        existingPrinter.setMacAddress(dto.getMacAddress());
        existingPrinter.setFirmwareType(dto.getFirmwareType());
        existingPrinter.setApiKey(dto.getApiKey());

        // 4. 更新到数据库 (MyBatis-Plus 会自动更新 updatedAt 时间)
        this.updateById(existingPrinter);
    }

    @Override
    public void deletePrinter(Long id) {
        FarmPrinter printer = this.getById(id);
        if (printer == null) {
            throw new BusinessException("打印机不存在或已被删除！");
        }

        // 核心物联网安全拦截：正在打印的机器绝对不允许删除！
        if ("PRINTING".equals(printer.getStatus())) {
            throw new BusinessException("危险操作：该机器正在打印中，无法删除！请先中止打印任务。");
        }

        // 安全通过，执行物理删除 (如果你配置了逻辑删除 @TableLogic，这里会自动变成 Update 操作)
        this.removeById(id);
    }

    @Override
    public List<String> scanNewKlipperDevices(String subnet) {
        // 1. 查询数据库，获取已经录入过的所有 IP（防止重复扫描和添加）
        List<String> existingIps = this.list().stream()
                .map(FarmPrinter::getIpAddress)
                .collect(Collectors.toList());

        // 2. 创建一个临时的高并发线程池 (专门干脏活累活)
        ExecutorService executor = Executors.newFixedThreadPool(50);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        // 3. 遍历 1 到 254 的 IP 后缀
        for (int i = 1; i <= 254; i++) {
            final String targetIp = subnet + "." + i;

            // 如果数据库里已经有了，直接跳过，不扫了
            if (existingIps.contains(targetIp)) {
                continue;
            }

            // 派发异步扫描任务
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try (Socket socket = new Socket()) {
                    // 尝试连接该 IP 的 7125 端口 (Moonraker 默认端口)
                    // 超时时间设为短短的 200 毫秒！通就是通，不通就是不通，绝不墨迹
                    socket.connect(new InetSocketAddress(targetIp, 7125), 200);
                    return targetIp; // 没抛异常说明端口是开的！发现野生 Klipper！
                } catch (Exception e) {
                    return null; // 不通，返回 null
                }
            }, executor);

            futures.add(future);
        }

        // 4. 等待所有 254 个探测任务执行完毕，并收集结果
        List<String> newDevices = futures.stream()
                .map(CompletableFuture::join) // 阻塞等待单个任务完成
                .filter(Objects::nonNull)     // 剔除没扫到的 null 值
                .collect(Collectors.toList());

        // 5. 随手关门，释放线程池
        executor.shutdown();

        return newDevices; // 返回发现的新 IP 列表
    }

    @Override
    public void batchAddPrinters(List<String> ipList) {
        if (ipList == null || ipList.isEmpty()) {
            return;
        }

        List<FarmPrinter> newPrinters = new ArrayList<>();
        int counter = 1;
        for (String ip : ipList) {
            FarmPrinter printer = new FarmPrinter();
            // 自动生成一个默认名字，例如: Klipper-192.168.1.10
            printer.setName("Klipper-" + ip);
            printer.setIpAddress(ip);
            printer.setFirmwareType("Klipper");
            printer.setStatus("OFFLINE"); // 默认离线，等刚才写的心跳任务去激活它
            newPrinters.add(printer);
            counter++;
        }

        // MyBatis-Plus 提供的批量插入，性能极高
        this.saveBatch(newPrinters);
    }
}