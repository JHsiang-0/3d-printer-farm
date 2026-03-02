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

    // 只保留这一个核心客户端和解析器
    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public MoonrakerApiClient() {
        // 核心修复点：强制指定使用 HTTP/1.1 协议，并设置 3 秒超时时间！
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1) // 降级为 HTTP/1.1
                .connectTimeout(Duration.ofSeconds(3)) // 3秒连不上就算了
                .build();

        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    /**
     * 1. 获取打印机实时温度、进度和状态
     * 对应 Moonraker API: /printer/objects/query
     */
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
            // 核心修复点 2：把一大堆红色的异常堆栈变成一行优雅的警告
            // 因为在物联网中，设备断网、超时太正常了，这属于业务常态，不需要打印完整堆栈
            log.warn("设备 [{}] 探测失败 (连接被拒绝或超时)", ipAddress);
        }
        return null; // 返回 null，外面的巡逻员就会自动把它标为 OFFLINE
    }

    /**
     * 2. 发送紧急停止指令 (Emergency Stop)
     * 对应 Moonraker API: /printer/emergency_stop
     */
    public boolean emergencyStop(String ipAddress) {
        String url = String.format("http://%s:7125/printer/emergency_stop", ipAddress);
        try {
            restClient.post()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity(); // 我们只关心请求是否发送成功，不关心返回值
            log.info("🚨 已向打印机 [{}] 发送急停指令！", ipAddress);
            return true;
        } catch (Exception e) {
            log.error("急停指令发送失败 [{}]: {}", ipAddress, e.getMessage());
            return false;
        }
    }

    /**
     * 3. 暂停当前打印任务
     * 对应 Moonraker API: /printer/print/pause
     */
    public boolean pausePrint(String ipAddress) {
        String url = String.format("http://%s:7125/printer/print/pause", ipAddress);
        try {
            restClient.post().uri(url).retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    /**
     * 4. 终极指令：将现成的文件流发送给 Moonraker，上传完毕后立即开始打印！
     */
    public boolean uploadAndPrint(String ipAddress, org.springframework.core.io.Resource gcodeResource, String filename) {
        String targetUrl = String.format("http://%s:7125/server/files/upload", ipAddress);

        try {
            // 1. 构造发给打印机的 multipart/form-data 表单
            org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("file", gcodeResource); // 直接塞入流
            body.add("filename", filename != null ? filename : "farm_print.gcode");
            body.add("print", "true"); // 告诉 Moonraker 上传完直接开打！

            log.info("🚀 正在向打印机 [{}] 投递切片文件并发送点火指令...", ipAddress);

            // 2. 投递给打印机
            String response = restClient.post()
                    .uri(targetUrl)
                    .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            log.info("✅ 打印机 [{}] 接收成功！响应: {}", ipAddress, response);
            return true;
        } catch (Exception e) {
            log.error("❌ 给打印机 [{}] 下发任务失败: {}", ipAddress, e.getMessage());
            return false;
        }
    }
}