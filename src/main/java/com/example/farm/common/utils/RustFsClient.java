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
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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
        log.info("正在初始化 RustFS(S3) 客户端: endpoint={}", endpoint);
        try {
            this.s3Client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    // RustFS 不校验 region，写固定值即可。
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)
                    ))
                    // 适配 RustFS 的 Path-Style 地址。
                    .forcePathStyle(true)
                    .build();

            // 启动时自动检查并创建 Bucket。
            createBucketIfNotExists();
            log.info("RustFS(S3) 客户端初始化成功");
        } catch (Exception e) {
            log.error("RustFS(S3) 客户端初始化失败，请检查配置或网络", e);
        }
    }

    private void createBucketIfNotExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            log.info("对象存储桶不存在，开始自动创建: bucket={}", bucket);
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    /**
     * 流式上传文件到 RustFS。
     *
     * @return 文件最终可访问 URL
     */
    public String uploadFile(String filename, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(filename)
                    // G-code 本质为纯文本。
                    .contentType("text/plain")
                    .build();

            // 通过流式方式上传，避免临时文件和高内存占用。
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, file.getSize()));

            // 组装 Path-Style URL，供 Klipper 下载。
            String fileUrl = String.format("%s/%s/%s", endpoint, bucket, filename);
            log.info("文件上传成功: {}", fileUrl);
            return fileUrl;

        } catch (Exception e) {
            log.error("上传切片文件到对象存储失败: 文件名={}", filename, e);
            throw new RuntimeException("对象存储服务异常");
        }
    }

    /**
     * 读取文件头部片段（8KB），用于快速元数据探测。
     */
    public String readHeader(String filename) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(filename)
                    .range("bytes=0-8191")
                    .build();

            byte[] bytes = s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
            return new String(bytes);
        } catch (Exception e) {
            log.warn("读取文件头失败: 文件名={}，原因={}", filename, e.getMessage());
            return "";
        }
    }

    /**
     * 从 RustFS 获取文件流。
     */
    public org.springframework.core.io.InputStreamResource getFileStream(String filename) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(filename)
                    .build();

            software.amazon.awssdk.core.ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getObjectRequest);

            // 包装成 Spring 可识别的资源流，供后续转发上传等场景使用。
            return new org.springframework.core.io.InputStreamResource(s3Stream) {
                @Override
                public String getFilename() {
                    return filename;
                }

                @Override
                public long contentLength() {
                    return s3Stream.response().contentLength();
                }
            };
        } catch (Exception e) {
            log.error("读取对象存储文件流失败: 文件名={}", filename, e);
            throw new RuntimeException("读取对象存储文件失败");
        }
    }

    /**
     * 按对象 Key 删除 RustFS 文件。
     */
    public void deleteFile(String filename) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(filename)
                    .build();
            s3Client.deleteObject(request);
            log.info("RustFS 对象删除成功: bucket={}, key={}", bucket, filename);
        } catch (Exception e) {
            log.error("RustFS 对象删除失败: bucket={}, key={}", bucket, filename, e);
            throw new RuntimeException("对象存储删除失败");
        }
    }
}