package com.example.farm.common.utils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.net.URI;

@Slf4j
@Component
public class RustFsClient {

    @Value("${rustfs.endpoint}")
    private String endpoint;

    @Value("${rustfs.access-key}")
    private String accessKey;

    @Value("${rustfs.secret-key}")
    private String secretKey;

    @Value("${rustfs.bucket}")
    private String bucket;

    private S3Client s3Client;

    @PostConstruct
    public void init() {
        log.info("🔍 正在初始化 RustFS (S3) 客户端, 地址: {}", endpoint);
        try {
            this.s3Client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.US_EAST_1) // RustFS 不校验 region，写死即可
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)
                    ))
                    .forcePathStyle(true) // 核心！适配 RustFS 的路径风格
                    .build();

            // 启动时自动检查并创建 Bucket
            createBucketIfNotExists();
            log.info("✅ RustFS 客户端初始化成功！");
        } catch (Exception e) {
            log.error("❌ RustFS 初始化失败，请检查配置或网络！", e);
        }
    }

    private void createBucketIfNotExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            log.info("📦 Bucket [{}] 不存在，正在自动创建...", bucket);
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    /**
     * 流式上传文件到 RustFS
     * @return 文件的最终下载 URL
     */
    public String uploadFile(String filename, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(filename)
                    .contentType("text/plain") // G-code 本质上是纯文本
                    .build();

            // 直接通过流式传输，零临时文件，超低内存消耗
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, file.getSize()));

            // 拼装用于 Klipper 下载的 Path-Style URL
            String fileUrl = String.format("%s/%s/%s", endpoint, bucket, filename);
            log.info("⬆️ 文件已成功上传至: {}", fileUrl);
            return fileUrl;

        } catch (Exception e) {
            log.error("❌ 上传 G-code 到 RustFS 失败: {}", filename, e);
            throw new RuntimeException("云存储服务异常");
        }
    }

    /**
     * 极速读取文件头 (利用 S3 的 Range 特性，只下前 8KB)
     */
    public String readHeader(String filename) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(filename)
                    .range("bytes=0-8191") // S3 协议原生支持 Range 获取
                    .build();

            byte[] bytes = s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
            return new String(bytes);
        } catch (Exception e) {
            log.warn("⚠️ 无法抓取文件头 [{}]: {}", filename, e.getMessage());
            return "";
        }
    }
    /**
     * 带着 S3 密钥从 RustFS 获取文件流
     */
    public org.springframework.core.io.InputStreamResource getFileStream(String filename) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(filename)
                    .build();

            software.amazon.awssdk.core.ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getObjectRequest);

            // 包装成 Spring 认识的资源流，供后续表单上传
            return new org.springframework.core.io.InputStreamResource(s3Stream) {
                @Override
                public String getFilename() {
                    return filename; // 必须重写，否则 Spring 找不到文件名
                }
                @Override
                public long contentLength() {
                    return s3Stream.response().contentLength();
                }
            };
        } catch (Exception e) {
            log.error("❌ 从 RustFS 拉取文件流失败: {}", filename, e);
            throw new RuntimeException("读取云存储文件失败");
        }
    }
}