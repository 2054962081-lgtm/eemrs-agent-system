# 第二阶段：认证与权限改造说明

## 1. 认证模型

现有系统使用 `UserLog.type` 区分用户类型：

| type | 角色 |
| --- | --- |
| `pt` | `PATIENT` |
| `dt` | `DOCTOR` |

JWT 中包含字段：

| 字段 | 说明 |
| --- | --- |
| `sub` | 当前用户身份证号 |
| `idNumber` | 当前用户身份证号 |
| `type` | 原系统类型，`pt` 或 `dt` |
| `role` | 新 REST 权限角色，`PATIENT` 或 `DOCTOR` |
| `department` | 医生科室，可为空 |
| `iat` | 签发时间 |
| `exp` | 过期时间，当前为 24 小时 |

登录返回示例：

```json
{
  "success": true,
  "message": "login success",
  "data": {
    "token": "jwt-token",
    "tokenType": "Bearer",
    "idNumber": "110101199001010001",
    "type": "pt",
    "role": "PATIENT",
    "department": null,
    "expiresIn": 86400
  }
}
```

后续请求头：

```http
Authorization: Bearer <jwt-token>
```

## 2. 新增安全代码

```text
security/
├── CurrentUser.java
├── CurrentUserHolder.java
├── ForbiddenException.java
├── JwtAuthenticationFilter.java
├── JwtTokenProvider.java
├── Role.java
├── SecurityConfig.java
├── UnauthorizedException.java
└── UserPrincipal.java
```

新增依赖：

| 依赖 | 作用 |
| --- | --- |
| `spring-boot-starter-security` | URL 鉴权、过滤器链、401/403 |
| `io.jsonwebtoken:jjwt:0.9.1` | JWT 生成与解析 |

## 3. 权限矩阵

| 接口 | 访问权限 | 业务级限制 |
| --- | --- | --- |
| `POST /api/auth/login` | 公开 | 登录成功后签发 JWT |
| `POST /api/auth/register` | 公开 | 保留原注册逻辑 |
| `GET /api/doctors?department=xxx` | 患者、医生 | 无敏感身份参数 |
| `PUT /api/patients/me` | 患者 | 只能修改 token 对应患者 |
| `POST /api/appointments` | 患者 | `idNumber` 必须等于 token 中 `idNumber`，实际写入使用 token 身份 |
| `GET /api/doctors/me` | 医生 | 医生信息从 token 的 `idNumber` 查询 |
| `GET /api/doctors/me/waiting-list` | 医生 | `doctorIdNumber` 必须等于 token 中 `idNumber`，实际查询使用 token 身份 |
| `POST /api/appointments/{idNumber}/accept` | 医生 | 只允许医生接诊，按患者 id 查询 |
| `POST /api/medical-records` | 医生 | `doctorIdNumber` 必须等于 token；患者必须存在或在可接诊范围内 |
| `GET /api/medical-records` | 患者、医生 | 患者只能查自己；医生查询强制限定为当前医生 |
| `POST /api/ai/pre-consultations` | 患者预留 | 当前未实现 |
| `POST /api/ai/report-interpretations` | 患者预留 | 当前未实现 |
| `POST /api/ai/record-drafts` | 医生预留 | 当前未实现 |

## 4. 关键业务级权限策略

### 4.1 患者查询病历

患者访问 `GET /api/medical-records` 时：

1. 如果传入其他 `patientIdNumber`，返回 `403`。
2. 如果不传 `patientIdNumber`，后端自动覆盖为 token 中的 `idNumber`。
3. 后端会清空患者请求中的 `doctorIdNumber`，避免伪造医生条件。

### 4.2 患者挂号

患者访问 `POST /api/appointments` 时：

1. 如果请求体传入 `idNumber` 且不等于 token 中 `idNumber`，返回 `403`。
2. 实际写入挂号信息时使用 token 中 `idNumber`。

### 4.3 医生候诊列表

医生访问 `GET /api/doctors/me/waiting-list` 时：

1. 如果传入 `doctorIdNumber` 且不等于 token 中 `idNumber`，返回 `403`。
2. 实际查询使用 token 中 `idNumber`。

### 4.4 医生写病历

医生访问 `POST /api/medical-records` 时：

1. 如果传入 `doctorIdNumber` 且不等于 token 中 `idNumber`，返回 `403`。
2. 实际写入使用 token 中 `idNumber`。
3. 当前旧表没有预约状态字段，因此先做最小校验：医生身份一致，患者可通过现有 `GuahaoService.getPatientInfo` 查询到。

### 4.5 医生查询病历

医生访问 `GET /api/medical-records` 时：

1. 如果传入 `doctorIdNumber` 且不等于 token 中 `idNumber`，返回 `403`。
2. 实际查询强制限定当前医生。

## 5. 测试样例

### 5.1 未携带 token 访问病历

```http
GET http://localhost:8080/api/medical-records
```

期望：

```json
{
  "success": false,
  "message": "Unauthorized",
  "data": null
}
```

HTTP 状态码：`401`

### 5.2 患者 token 访问医生候诊列表

```http
GET http://localhost:8080/api/doctors/me/waiting-list?department=内科
Authorization: Bearer <patient-token>
```

期望：`403 Forbidden`

### 5.3 医生 token 访问患者个人信息修改

```http
PUT http://localhost:8080/api/patients/me
Authorization: Bearer <doctor-token>
Content-Type: application/json

{
  "idNumber": "110101199001010001",
  "telephone": "13800000000"
}
```

期望：`403 Forbidden`

### 5.4 患者 A 查询患者 B 病历

```http
GET http://localhost:8080/api/medical-records?patientIdNumber=patient-b-id
Authorization: Bearer <patient-a-token>
```

期望：`403 Forbidden`

### 5.5 医生 A 写病历时伪造医生 B

```http
POST http://localhost:8080/api/medical-records
Authorization: Bearer <doctor-a-token>
Content-Type: application/json

{
  "department": "内科",
  "conditionDescription": "患者主诉",
  "patientIdNumber": "patient-id",
  "doctorIdNumber": "doctor-b-id",
  "dPk": "base64-doctor-public-key",
  "signature": "base64-sm2-signature"
}
```

期望：`403 Forbidden`

### 5.6 合法患者挂号

```http
POST http://localhost:8080/api/appointments
Authorization: Bearer <patient-token>
Content-Type: application/json

{
  "department": "内科",
  "idNumber": "same-as-token-id",
  "userName": "张三",
  "doctorIdNumber": "doctor-id"
}
```

期望：

```json
{
  "success": true,
  "message": "appointment handled",
  "data": true
}
```

### 5.7 合法医生查询候诊列表

```http
GET http://localhost:8080/api/doctors/me/waiting-list?department=内科
Authorization: Bearer <doctor-token>
```

期望：

```json
{
  "success": true,
  "message": "success",
  "data": []
}
```

### 5.8 合法医生写病历

```http
POST http://localhost:8080/api/medical-records
Authorization: Bearer <doctor-token>
Content-Type: application/json

{
  "department": "内科",
  "medication": "药品信息",
  "conditionDescription": "患者主诉和病情描述",
  "cost": "100",
  "visitTime": 20260526153000,
  "patientName": "张三",
  "patientIdNumber": "patient-id",
  "age": 35,
  "doctorName": "李医生",
  "doctorIdNumber": "same-as-token-id",
  "dPk": "base64-doctor-public-key",
  "signature": "base64-sm2-signature",
  "gender": "男"
}
```

期望：

```json
{
  "success": true,
  "message": "medical record handled",
  "data": true
}
```

说明：该接口仍会走原 `DataOpCrypto.insertInto` 的 SM2 验签逻辑，测试数据需要满足旧系统验签要求。

## 6. 启动与验证

编译：

```bash
mvn -q -DskipTests compile
```

启动：

```bash
mvn spring-boot:run
```

服务启动后：

1. 先调用 `POST /api/auth/login` 获取 JWT。
2. 在 Postman 的 Authorization 里选择 `Bearer Token`，填入返回的 `token`。
3. 访问受保护 REST 接口。

## 7. 本阶段未改动的核心内容

未改动：

| 范围 | 说明 |
| --- | --- |
| `crypto/*` 业务加密类 | 未重构、未替换 |
| `utils/crypto/*` | 未改动 SM2/SM3/SM4/OPE |
| `mapper/*` | 未改动 Mapper 主体 |
| `resources/mybatis/mapper/*.xml` | 未改动 SQL 主体 |
| MySQL 旧表结构 | 未新增、未修改旧表 |
| `ControlRun` / Socket opcode | 未删除，继续保留兼容 |

## 8. 下一阶段建议

第二阶段已经具备前后端分离所需的基础认证与角色隔离，可以进入 Vue 前端重写。进入前端前建议先确认两类真实账号：

1. 一个 `pt` 患者账号。
2. 一个 `dt` 医生账号，并确认登录时是否必须传 `department`。

随后 Vue 前端统一使用 `Authorization: Bearer <token>` 调用 REST API。
