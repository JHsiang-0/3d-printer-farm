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
- `GET /api/v1/printers/page`

### 5.2 新增
- `POST /api/v1/printers/add`

### 5.3 更新
- `PUT /api/v1/printers/update`

### 5.4 删除
- `DELETE /api/v1/printers/delete/{id}`

### 5.5 局域网扫描
- `GET /api/v1/printers/scan?subnet=192.168.1`

### 5.6 批量新增
- `POST /api/v1/printers/batch-add`

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