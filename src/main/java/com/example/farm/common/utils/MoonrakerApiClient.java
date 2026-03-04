package com.example.farm.common.utils;

import com.example.farm.entity.dto.MoonrakerStatusDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 与 Klipper(Moonraker) 通信的 HTTP 客户端工具类
 */
@Slf4j
@Component
public class MoonrakerApiClient {

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public MoonrakerApiClient() {
        // 核心修复点 1：设置 3 秒连接超时
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        // 核心修复点 2：设置 3 秒读取超时 (防止设备连上后不回传数据导致线程假死)
        factory.setReadTimeout(Duration.ofSeconds(3));

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    // ... 下面的所有业务方法 (getPrinterStatus, emergencyStop 等) 完全保持不变 ...

    public MoonrakerStatusDTO getPrinterStatus(String ipAddress) {
        String url = String.format("http://%s:7125/printer/objects/query?extruder=temperature,target&heater_bed=temperature,target&display_status=progress&print_stats=state", ipAddress);

        try {
            String jsonResponse = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            if (jsonResponse != null) {
                JsonNode rootNode = mapper.readTree(jsonResponse);
                if (rootNode.has("result")) {
                    JsonNode statusNode = rootNode.path("result").path("status");
                    MoonrakerStatusDTO dto = new MoonrakerStatusDTO();

                    JsonNode extruder = statusNode.path("extruder");
                    dto.setNozzleTemp(extruder.path("temperature").asDouble(0.0));
                    dto.setNozzleTarget(extruder.path("target").asDouble(0.0));

                    JsonNode bed = statusNode.path("heater_bed");
                    dto.setBedTemp(bed.path("temperature").asDouble(0.0));
                    dto.setBedTarget(bed.path("target").asDouble(0.0));

                    double rawProgress = statusNode.path("display_status").path("progress").asDouble(0.0);
                    dto.setProgress(Math.round(rawProgress * 10000.0) / 100.0);
                    dto.setState(statusNode.path("print_stats").path("state").asText("offline"));

                    return dto;
                }
            }
        } catch (Exception e) {
            log.debug("打印机状态探测失败: 设备IP={}，可能是连接拒绝或超时", ipAddress);
        }
        return null;
    }

    // ... emergencyStop, pausePrint, uploadAndPrint 保持你原有的写法 ...
    public boolean emergencyStop(String ipAddress) {
        // 你的原有逻辑
        String url = String.format("http://%s:7125/printer/emergency_stop", ipAddress);
        try {
            restClient.post().uri(url).retrieve().toBodilessEntity();
            log.info("已发送急停指令: 打印机IP={}", ipAddress);
            return true;
        } catch (Exception e) {
            log.error("发送急停指令失败: 打印机IP={}，原因={}", ipAddress, e.getMessage());
            return false;
        }
    }

    public boolean pausePrint(String ipAddress) {
        // 你的原有逻辑
        String url = String.format("http://%s:7125/printer/print/pause", ipAddress);
        try {
            restClient.post().uri(url).retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("发送暂停指令失败: 打印机IP={}，原因={}", ipAddress, e.getMessage());
            return false;
        }
    }

    public boolean uploadAndPrint(String ipAddress, org.springframework.core.io.Resource gcodeResource, String filename) {
        // 你的原有逻辑
        String targetUrl = String.format("http://%s:7125/server/files/upload", ipAddress);
        try {
            org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("file", gcodeResource);
            body.add("filename", filename != null ? filename : "farm_print.gcode");
            body.add("print", "true");

            log.info("开始下发打印任务: 打印机IP={}，文件名={}", ipAddress, filename);
            String response = restClient.post()
                    .uri(targetUrl)
                    .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            log.info("打印任务下发成功: 打印机IP={}，响应={}", ipAddress, response);
            return true;
        } catch (Exception e) {
            log.error("打印任务下发失败: 打印机IP={}，原因={}", ipAddress, e.getMessage());
            return false;
        }
    }
}
