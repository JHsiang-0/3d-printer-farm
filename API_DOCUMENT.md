# 3D 农场管理系统 API 文档

## 基础信息
- Base URL: `http://localhost:8080`
- API 前缀: `/api/v1`
- 认证方式: `Authorization: Bearer <token>`

## 统一响应格式
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

## 1. 认证与用户（`/api/v1/auth`）

### 1.1 登录
- `POST /api/v1/auth/login`
- 免认证

请求体：
```json
{
  "username": "admin",
  "password": "Admin123"
}
```

响应 `data`：
```json
{
  "token": "eyJ...",
  "expiresIn": 604800,
  "userId": 1,
  "username": "admin",
  "role": "ADMIN",
  "email": "admin@example.com",
  "phone": "13800138000"
}
```

### 1.2 注册
- `POST /api/v1/auth/register`
- 免认证

请求体：
```json
{
  "username": "operator01",
  "password": "Pass1234",
  "confirmPassword": "Pass1234",
  "email": "operator@example.com",
  "phone": "13800138001"
}
```

校验规则：
- `username`: 3-20 位，仅字母/数字/下划线
- `password`: 6-20 位，必须包含大写字母+小写字母+数字
- `confirmPassword`: 必填，且与 `password` 一致
- `phone`: 中国大陆手机号格式

### 1.3 修改密码
- `POST /api/v1/auth/{userId}/change-password`
- 需认证，且 `userId` 必须是当前登录用户

请求体：
```json
{
  "oldPassword": "OldPass123",
  "newPassword": "NewPass123",
  "confirmPassword": "NewPass123"
}
```

### 1.4 获取个人信息
- `GET /api/v1/auth/{userId}/profile`
- 需认证，且 `userId` 必须是当前登录用户

### 1.5 更新个人信息
- `PUT /api/v1/auth/{userId}/profile`
- 需认证，且 `userId` 必须是当前登录用户
- 普通用户接口不允许修改 `role`

请求体：
```json
{
  "email": "newemail@example.com",
  "phone": "13900139000"
}
```

### 1.6 检查用户名是否可用
- `GET /api/v1/auth/check-username?username=operator01`
- 免认证

### 1.7 检查邮箱是否可用
- `GET /api/v1/auth/check-email?email=test@example.com`
- 免认证

---

## 2. 管理员用户接口（`/api/v1/auth/admin`）
> 需要 `ADMIN` 角色

### 2.1 分页查询用户
- `GET /api/v1/auth/admin/users?pageNum=1&pageSize=10`

### 2.2 更新用户
- `PUT /api/v1/auth/admin/users/{userId}`

请求体示例：
```json
{
  "email": "u@example.com",
  "phone": "13800138000",
  "role": "OPERATOR"
}
```

### 2.3 禁用用户
- `POST /api/v1/auth/admin/users/{userId}/disable`
- 不需要传 `adminId`，后端从登录态获取

### 2.4 启用用户
- `POST /api/v1/auth/admin/users/{userId}/enable`
- 不需要传 `adminId`

### 2.5 批量迁移明文密码
- `POST /api/v1/auth/admin/migrate-passwords?adminSecret=...`

### 2.6 查询密码存储状态
- `GET /api/v1/auth/admin/password-status?adminSecret=...`

---

## 3. 打印任务（`/api/v1/print-jobs`）

### 3.1 获取队列任务
- `GET /api/v1/print-jobs/queue`

### 3.2 创建任务
- 推荐：`POST /api/v1/print-jobs/create`
- 兼容：`POST /api/v1/print-jobs`（同义）

请求体：
```json
{
  "fileId": 5,
  "materialType": "PLA",
  "nozzleSize": 0.4,
  "priority": 5,
  "autoAssign": true
}
```

### 3.3 取消任务
- `DELETE /api/v1/print-jobs/{id}`

### 3.4 手动派发任务
- `POST /api/v1/print-jobs/{jobId}/assign?printerId=1`

---

## 4. 打印文件（`/api/v1/print-files`）

### 4.1 上传并解析 G-code
- `POST /api/v1/print-files/upload`
- `Content-Type: multipart/form-data`
- 参数：`file`

### 4.2 分页查询文件
- `POST /api/v1/print-files/page`

请求体：
```json
{
  "pageNum": 1,
  "pageSize": 10
}
```

### 4.3 删除文件
- `DELETE /api/v1/print-files/{id}`

---

## 5. 打印机（`/api/v1/printers`）

### 5.1 分页查询
- `GET /api/v1/printers/page?pageNum=1&pageSize=10`
- 支持按名称和状态筛选

### 5.2 新增打印机（基于 MAC Upsert）
- `POST /api/v1/printers/add`

**核心逻辑**：基于 MAC 地址的 Upsert 机制
- MAC 已存在 → 更新该设备的 IP 和状态为 ONLINE（设备换了 IP 重新上线）
- MAC 不存在 → 插入新记录（真正的新设备）

请求体：
```json
{
  "name": "Printer_3456",
  "ipAddress": "192.168.1.100",
  "macAddress": "b8:27:eb:12:34:56",
  "firmwareType": "Klipper",
  "apiKey": "optional_api_key"
}
```

字段说明：
| 字段 | 必填 | 说明 |
|------|------|------|
| `name` | 否 | 打印机名称，不传则自动生成（如 Printer_3456） |
| `ipAddress` | **是** | 局域网 IP 地址 |
| `macAddress` | 否 | MAC 地址，不传则后端自动获取 |
| `firmwareType` | 否 | 固件类型，默认 Klipper |
| `apiKey` | 否 | Moonraker API 密钥 |

### 5.3 更新打印机
- `PUT /api/v1/printers/update`

请求体：
```json
{
  "id": 1,
  "name": "Updated Name",
  "ipAddress": "192.168.1.101",
  "macAddress": "b8:27:eb:12:34:56",
  "firmwareType": "Klipper",
  "apiKey": "new_api_key",
  "currentMaterial": "PLA",
  "nozzleSize": 0.4,
  "machineNumber": "P001"
}
```

> **注意**：修改 IP 时会自动处理 IP 冲突（将占用该 IP 的旧设备下线）

### 5.4 删除打印机
- `DELETE /api/v1/printers/delete/{id}`
- **限制**：正在打印中的设备无法删除

### 5.5 局域网扫描（带 MAC 识别）
- `GET /api/v1/printers/scan?subnet=192.168.1`

**功能说明**：
- 并发扫描网段内所有 IP 的 7125 端口（Klipper/Moonraker 默认端口）
- 对响应的设备尝试获取 MAC 地址（ARP 表或 Moonraker API）
- 与数据库对比，标记新旧设备状态

响应 `data`：
```json
[
  {
    "ipAddress": "192.168.1.100",
    "macAddress": "b8:27:eb:12:34:56",
    "firmwareType": "Klipper",
    "isNewDevice": true,
    "status": "NEW",
    "suggestedName": "Printer_3456"
  },
  {
    "ipAddress": "192.168.1.101",
    "macAddress": "b8:27:eb:78:9a:bc",
    "firmwareType": "Klipper",
    "isNewDevice": false,
    "status": "EXISTING",
    "suggestedName": "Printer_9abc"
  }
]
```

状态说明：
| status | 含义 |
|--------|------|
| `NEW` | 新设备（MAC 不在数据库中） |
| `EXISTING` | 已知设备（MAC 已存在） |
| `UNKNOWN_MAC` | 无法获取 MAC 地址，需手动处理 |

### 5.6 批量新增/更新（基于 MAC Upsert）⭐
- `POST /api/v1/printers/batch-add`

**核心功能**：解决 DHCP 动态分配导致的设备重复录入问题

请求体（扫描结果数组）：
```json
[
  {
    "ipAddress": "192.168.1.100",
    "macAddress": "b8:27:eb:12:34:56",
    "firmwareType": "Klipper",
    "isNewDevice": true,
    "suggestedName": "Printer_3456"
  },
  {
    "ipAddress": "192.168.1.101",
    "macAddress": "b8:27:eb:78:9a:bc",
    "firmwareType": "Klipper",
    "isNewDevice": false,
    "suggestedName": "Printer_9abc"
  }
]
```

响应 `data`：
```json
{
  "totalCount": 2,
  "insertedCount": 1,
  "updatedCount": 1,
  "failedCount": 0,
  "message": "批量处理完成：新增 1 台，更新 1 台，失败 0 台"
}
```

处理逻辑：
1. **防 IP 冲突**：若 IP 被其他 MAC 设备占用，先将旧设备 IP 设为 NULL，状态设为 OFFLINE
2. **基于 MAC 的 Upsert**：使用 MySQL `ON DUPLICATE KEY UPDATE` 实现原子性操作
3. **降级处理**：无 MAC 地址的设备仍可通过普通插入添加

### 5.7 批量更新物理位置（数字孪生看板）⭐
- `POST /api/v1/printers/batch-update-positions`

**功能**：更新打印机在数字孪生看板上的物理位置坐标

请求体：
```json
[
  {
    "id": 1,
    "gridRow": 2,
    "gridCol": 3
  },
  {
    "id": 2,
    "gridRow": 1,
    "gridCol": 5
  }
]
```

字段说明：
| 字段 | 必填 | 范围 | 说明 |
|------|------|------|------|
| `id` | **是** | - | 打印机 ID |
| `gridRow` | 否 | 1-4 | 网格行号（null 表示移回待分配区） |
| `gridCol` | 否 | 1-12 | 网格列号（null 表示移回待分配区） |

> **业务规则**：传入 null 可将设备移回"待分配区"

---

## 5.x 打印机数据结构

### FarmPrinter 实体字段
```json
{
  "id": 1,
  "name": "Printer_3456",
  "ipAddress": "192.168.1.100",
  "macAddress": "b8:27:eb:12:34:56",
  "firmwareType": "Klipper",
  "apiKey": "optional_key",
  "status": "ONLINE",
  "currentMaterial": "PLA",
  "nozzleSize": 0.4,
  "machineNumber": "P001",
  "gridRow": 2,
  "gridCol": 3,
  "createdAt": "2026-03-05T10:30:00",
  "updatedAt": "2026-03-05T14:20:00"
}
```

状态枚举：
| status | 说明 |
|--------|------|
| `ONLINE` | 在线 |
| `OFFLINE` | 离线 |
| `PRINTING` | 打印中 |
| `PAUSED` | 暂停 |
| `ERROR` | 错误 |

---

## 6. 打印控制（`/api/v1/control`）
- `POST /api/v1/control/{id}/emergency-stop`
- `POST /api/v1/control/{id}/pause`

---

## 7. WebSocket
- URL: `ws://localhost:8080/ws/farm-status`
- 通道无需 token（按当前后端配置）

---

## 常见错误码说明
- 参数校验失败：`code != 200`，`message` 含字段级错误（例如 `password:密码必须包含大小写字母和数字`）
- 方法不支持：`message` 类似 `请求方法不支持，当前方法=POST，支持方法=DELETE`
- 未登录/Token 失效：401
- 用户被禁用：403