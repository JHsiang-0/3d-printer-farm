package com.example.farm.task;

import com.example.farm.controller.FarmWebSocketServer;
import com.example.farm.entity.FarmPrinter;
import com.example.farm.entity.dto.MoonrakerStatusDTO;
import com.example.farm.service.FarmPrinterService;
import com.example.farm.common.utils.MoonrakerApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PrinterMonitorTask {

    @Autowired
    private FarmPrinterService printerService;

    @Autowired
    private MoonrakerApiClient moonrakerApiClient; // 注入咱们刚写的通信兵

    // 缩短到 5 秒执行一次，让大屏温度实时跳动！
    @Scheduled(fixedRate = 5000)
    public void checkPrinterStatus() {
        List<FarmPrinter> printers = printerService.list();
        if (printers.isEmpty()) return;

        for (FarmPrinter printer : printers) {
            // 直接向机器索要真实数据！
            MoonrakerStatusDTO status = moonrakerApiClient.getPrinterStatus(printer.getIpAddress());

            if (status != null) {
                // 【机器在线且正常返回数据】
                if ("OFFLINE".equals(printer.getStatus())) {
                    printer.setStatus("IDLE"); // 如果原来是离线，现在活了，恢复为空闲
                    printerService.updateById(printer);
                }

                // 🔥 核心动作：把抓到的温度、进度和当前机器ID打包，通过 WebSocket 推给前端！
                Map<String, Object> payload = new HashMap<>();
                payload.put("printerId", printer.getId());
                payload.put("data", status); // 里面包含了 nozzleTemp, progress 等

                FarmWebSocketServer.broadcastPrinterStatus(payload);

            } else {
                // 【机器失联】
                if (!"OFFLINE".equals(printer.getStatus())) {
                    printer.setStatus("OFFLINE");
                    printerService.updateById(printer);
                    log.warn("❌ 打印机失联: {}", printer.getName());
                }
            }
        }
    }
}