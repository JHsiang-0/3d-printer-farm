# 3D 打印机农场管理系统 - API 接口文档

## 基础信息

- **基础 URL**: `http://localhost:8080`
- **API 前缀**: `/api/v1`
- **WebSocket**: `ws://localhost:8080/ws/farm-status`
- **认证方式**: JWT Token (Header: `Authorization: Bearer {token}`)

## 统一响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1712345678901
}
```

---

## 一、用户认证接口

**Base URL**: `/api/v1/auth`

### 1.1 用户登录
- **URL**: `POST /api/v1/auth/login`
- **无需认证**: ✅
- **请求体**:
```json
{
  "username": "admin",
  "password": "Admin123"
}
```
- **响应**:
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 604800,
    "userId": 1,
    "username": "admin",
    "role": "ADMIN",
    "email": "admin@example.com",
    "phone": "13800138000"
  }
}
```

### 1.2 用户注册
- **URL**: `POST /api/v1/auth/register`
- **无需认证**: ✅
- **请求体**:
```json
{
  "username": "operator01",
  "password": "Pass1234",
  "confirmPassword": "Pass1234",
  "email": "operator@example.com",
  "phone": "13800138001"
}
```

### 1.3 修改密码
- **URL**: `POST /api/v1/auth/{userId}/change-password`
- **请求体**:
```json
{
  "oldPassword": "OldPass123",
  "newPassword": "NewPass123",
  "confirmPassword": "NewPass123"
}
```

### 1.4 获取当前用户信息
- **URL**: `GET /api/v1/auth/{userId}/profile`

### 1.5 更新用户信息
- **URL**: `PUT /api/v1/auth/{userId}/profile`
- **请求体**:
```json
{
  "id": 1,
  "email": "newemail@example.com",
  "phone": "13900139000",
  "role": "OPERATOR"
}
```

### 1.6 检查用户名是否可用
- **URL**: `GET /api/v1/auth/check-username?username=operator01`
- **无需认证**: ✅

### 1.7 检查邮箱是否可用
- **URL**: `GET /api/v1/auth/check-email?email=test@example.com`
- **无需认证**: ✅

---

## 二、打印机管理接口

**Base URL**: `/api/v1/printers`

### 2.1 获取打印机分页列表
- **URL**: `GET /api/v1/printers/page`
- **参数**:
  - `pageNum` (可选): 页码，默认 1
  - `pageSize` (可选): 每页条数，默认 10
  - `name` (可选): 打印机名称模糊查询
  - `status` (可选): 状态筛选 (IDLE/PRINTING/OFFLINE/ERROR)
- **响应**:
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "name": "Voron-2.4-01",
        "ipAddress": "192.168.1.101",
        "macAddress": "AA:BB:CC:DD:EE:FF",
        "firmwareType": "Klipper",
        "status": "PRINTING",
        "currentMaterial": "PLA",
        "nozzleSize": 0.40,
        "currentJobId": 5
      }
    ],
    "total": 10,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

### 2.2 添加打印机
- **URL**: `POST /api/v1/printers/add`
- **请求体**:
```json
{
  "name": "Voron-2.4-02",
  "ipAddress": "192.168.1.102",
  "macAddress": "AA:BB:CC:DD:EE:00",
  "firmwareType": "Klipper",
  "apiKey": "optional_api_key"
}
```

### 2.3 修改打印机信息
- **URL**: `PUT /api/v1/printers/update`
- **请求体**:
```json
{
  "id": 1,
  "name": "Voron-2.4-01-New",
  "ipAddress": "192.168.1.101",
  "macAddress": "AA:BB:CC:DD:EE:FF",
  "firmwareType": "Klipper",
  "apiKey": "new_api_key"
}
```

### 2.4 删除打印机
- **URL**: `DELETE /api/v1/printers/delete/{id}`

### 2.5 扫描局域网打印机
- **URL**: `GET /api/v1/printers/scan?subnet=192.168.1`
- **响应**: `["192.168.1.101", "192.168.1.102"]`

### 2.6 批量添加打印机
- **URL**: `POST /api/v1/printers/batch-add`
- **请求体**: `["192.168.1.101", "192.168.1.102"]`

---

## 三、打印任务接口

**Base URL**: `/api/v1/print-jobs`

### 3.1 获取排队中的任务
- **URL**: `GET /api/v1/print-jobs/queue`
- **响应**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "fileId": 5,
      "printerId": null,
      "userId": 1,
      "priority": 10,
      "status": "QUEUED",
      "progress": 0.00,
      "materialType": "PLA",
      "nozzleSize": 0.40
    }
  ]
}
```

### 3.2 提交打印任务（简化版）
- **URL**: `POST /api/v1/print-jobs/submit`
- **参数**:
  - `fileId` (必填): 文件ID
  - `priority` (可选): 优先级，默认 0

### 3.3 创建打印任务（完整版）
- **URL**: `POST /api/v1/print-jobs`
- **请求体**:
```json
{
  "fileId": 5,
  "materialType": "PLA",
  "nozzleSize": 0.40,
  "priority": 5
}
```

### 3.4 取消任务
- **URL**: `DELETE /api/v1/print-jobs/{id}`

### 3.5 手动指派任务给打印机
- **URL**: `POST /api/v1/print-jobs/{jobId}/assign?printerId=1`

---

## 四、打印文件接口

**Base URL**: `/api/v1/print-files`

### 4.1 上传切片文件
- **URL**: `POST /api/v1/print-files/upload`
- **Content-Type**: `multipart/form-data`
- **参数**:
  - `file` (必填): G-code 文件
- **响应**:
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "originalName": "cube.gcode",
    "safeName": "1712345678901_cube.gcode",
    "fileUrl": "http://127.0.0.1:9000/farm/1712345678901_cube.gcode",
    "fileSize": 1024567,
    "estTime": 3600,
    "materialType": "PLA",
    "nozzleSize": 0.40
  }
}
```

### 4.2 创建打印任务（从文件）
- **URL**: `POST /api/v1/print-jobs/create`
- **请求体**:
```json
{
  "fileId": 1,
  "materialType": "PLA",
  "nozzleSize": 0.40,
  "priority": 0
}
```

---

## 五、打印机控制接口

**Base URL**: `/api/v1/control`

### 5.1 紧急停止
- **URL**: `POST /api/v1/control/{id}/emergency-stop`

### 5.2 暂停打印
- **URL**: `POST /api/v1/control/{id}/pause`

---

## 六、WebSocket 实时数据

### 6.1 连接地址
- **URL**: `ws://localhost:8080/ws/farm-status`
- **无需认证**: ✅

### 6.2 接收数据格式
```json
{
  "printerId": 1,
  "data": {
    "nozzleTemp": 215.5,
    "nozzleTarget": 210.0,
    "bedTemp": 60.0,
    "bedTarget": 60.0,
    "progress": 45.67,
    "state": "printing"
  },
  "timestamp": 1712345678901
}
```

### 6.3 JavaScript 连接示例
```javascript
const ws = new WebSocket('ws://localhost:8080/ws/farm-status');

ws.onopen = () => console.log('WebSocket 连接成功');

ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log('打印机状态:', data);
};

ws.onerror = (error) => console.error('WebSocket 错误:', error);
ws.onclose = () => console.log('WebSocket 连接关闭');
```

---

## 七、管理员接口

**Base URL**: `/api/v1/auth/admin`

### 7.1 查询用户列表
- **URL**: `GET /api/v1/auth/admin/users?pageNum=1&pageSize=10`

### 7.2 更新用户信息
- **URL**: `PUT /api/v1/auth/admin/users/{userId}`

### 7.3 禁用用户
- **URL**: `POST /api/v1/auth/admin/users/{userId}/disable?adminId=1`

### 7.4 启用用户
- **URL**: `POST /api/v1/auth/admin/users/{userId}/enable?adminId=1`

### 7.5 批量迁移明文密码
- **URL**: `POST /api/v1/auth/admin/migrate-passwords?adminSecret=FarmAdmin2024`

### 7.6 检查密码存储状态
- **URL**: `GET /api/v1/auth/admin/password-status?adminSecret=FarmAdmin2024`

---

## 附录：状态枚举

### 打印机状态
| 状态 | 说明 |
|------|------|
| IDLE | 空闲 |
| PRINTING | 打印中 |
| OFFLINE | 离线 |
| ERROR | 错误 |
| MAINTENANCE | 维护中 |

### 打印任务状态
| 状态 | 说明 |
|------|------|
| QUEUED | 排队中 |
| ASSIGNED | 已分配 |
| PRINTING | 打印中 |
| COMPLETED | 已完成 |
| FAILED | 失败 |
| CANCELED | 已取消 |

### 用户角色
| 角色 | 说明 |
|------|------|
| ADMIN | 管理员 |
| OPERATOR | 操作员 |
| CUSTOMER | 客户 |
