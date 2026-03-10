# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

3D打印农场管理系统 - 一个基于 Spring Boot 的 Web 应用，用于管理 3D 打印机集群、打印任务调度和文件管理。

## Build & Development Commands

```bash
# 编译项目
mvn compile

# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=ClassName

# 运行单个测试方法
mvn test -Dtest=ClassName#methodName

# 打包
mvn package

# 跳过测试打包
mvn package -DskipTests

# 运行应用
mvn spring-boot:run

# 清理构建
mvn clean
```

## Technology Stack

- **Framework**: Spring Boot 4.0.3, Java 25
- **ORM**: MyBatis-Plus 3.5.15
- **Database**: MySQL 8.x
- **Cache**: Redis
- **Security**: Spring Security + JWT (auth0/java-jwt)
- **API Docs**: SpringDoc OpenAPI 3.0.2
- **File Storage**: RustFS (S3-compatible, via AWS SDK)
- **Build Tool**: Maven

## Architecture Overview

### Layer Structure

```
Controller -> Service -> Mapper -> Entity
                |
           common/utils (工具类)
```

### Core Modules

1. **用户管理** (`FarmUserController`, `FarmUserService`)
   - JWT 认证，支持 ADMIN/OPERATOR/CUSTOMER 角色
   - 密码使用 BCrypt 加密

2. **打印机管理** (`FarmPrinterController`, `FarmPrinterService`)
   - 基于 MAC 地址的 Upsert 机制（解决 DHCP 动态 IP 问题）
   - 支持局域网扫描发现 Klipper 设备
   - 数字孪生看板：grid_row/grid_col 物理位置管理

3. **打印任务调度** (`JobSchedulerTask`)
   - 分布式锁（Redis）确保集群单实例调度
   - 每 10 秒扫描队列，自动匹配空闲打印机
   - 任务状态：QUEUED -> ASSIGNED -> PRINTING -> COMPLETED/FAILED/CANCELLED

4. **打印机监控** (`PrinterMonitorTask`)
   - 每 5 秒轮询打印机状态
   - 通过 Moonraker API 获取 Klipper 实时数据
   - WebSocket 推送状态到前端

5. **文件管理** (`FarmPrintFileController`)
   - G-code 文件上传解析（层高等参数提取）
   - RustFS 对象存储

### Key Components

- **MoonrakerApiClient**: 与 Klipper/Moonraker 通信的 HTTP 客户端，3秒超时
- **MoonrakerStatusDTO**: 统一状态机，融合 webhooks.state（系统级）和 print_stats.state（任务级）
- **PrinterCacheService**: Redis 缓存打印机状态和列表，减少数据库压力
- **LogUtil**: 结构化日志工具，支持慢操作检测和数据变更追踪

## Database Schema

- `farm_user`: 用户表
- `farm_printer`: 打印机设备表（含 MAC 地址、物理位置 grid_row/grid_col）
- `farm_print_file`: 打印文件表
- `farm_print_job`: 打印任务表（含 user_id, error_reason, file_url, est_time 等）

Flyway 迁移脚本位于 `src/main/resources/db/migration/`。

## Configuration

主要配置项在 `application.yaml`:

- `spring.datasource.*`: MySQL 连接
- `spring.data.redis.*`: Redis 连接
- `jwt.secret-key`: JWT 签名密钥
- `admin.secret-key`: 管理员敏感操作密钥
- `rustfs.*`: 对象存储配置

## API Documentation

启动后访问: `http://localhost:8080/swagger-ui.html`

Controller 使用 `@Tag` 和 `@Operation` 注解进行中文文档标注。

## Testing

- 单元测试使用 Spring Boot Test Starter
- 测试配置文件可放在 `src/test/resources/application-test.yaml`

## Important Notes

- 数据库字段使用下划线命名（如 `created_at`），Java 实体使用驼峰（`createdAt`），MyBatis-Plus 自动转换
- 调度任务使用 Redis 分布式锁，确保多实例部署时只有一个实例执行
- 打印机状态统一：IDLE, PRINTING, OFFLINE, ERROR, MAINTENANCE
- 统一状态机常量定义在 `MoonrakerStatusDTO` 中
