# 第一阶段：后端 REST API 适配层说明

## 1. 代码审查结论

### 1.1 opcode 与处理链路

| 原 opcode | 功能 | Socket 处理位置 | 原模块/Service | 新 REST 接口 |
| --- | --- | --- | --- | --- |
| `13` | 患者/医生登录 | `ControlRun case 13` | `Login.login -> UserOpService.loginUser` | `POST /api/auth/login` |
| `14` | 患者/医生注册 | `ControlRun case 14` | `Register.regist -> UserOpService.insertUser` | `POST /api/auth/register` |
| `5` | 查询科室医生 | `ControlRun case 5` | `DataOpService.getDocName` | `GET /api/doctors?department=xxx` |
| `31` | 患者挂号 | `ControlRun case 31` | `Guahao.add -> GuahaoService.add` | `POST /api/appointments` |
| `32` | 医生查询候诊列表 | `ControlRun case 32` | `Guahao.query -> GuahaoService.query` | `GET /api/doctors/me/waiting-list` |
| `33` | 医生接诊患者 | `ControlRun case 33` | `Guahao.accept -> GuahaoService.getPatientInfo` | `POST /api/appointments/{idNumber}/accept` |
| `1` | 获取患者信息 | `ControlRun case 1` | `Guahao.sendPatientInfo -> GuahaoService.sendInfo` | `GET /api/patients/{idNumber}` |
| `12` | 医生写病历 | `ControlRun case 12` | `InsertData.insertInto -> DataOpService.insertInto` | `POST /api/medical-records` |
| `11` | 查询病历/报告 | `ControlRun case 11` | `QueryData.query -> DataOpService.query` | `GET /api/medical-records` |
| `18` | 修改密码 | `ControlRun case 18` | `DoPatientInfo.modifyPassword -> PatientInfoService.modifyPassword` | 本阶段未列入优先接口 |
| `19` | 修改患者信息 | `ControlRun case 19` | `DoPatientInfo.modifyInfo -> PatientInfoService.modifyInfo` | `PUT /api/patients/me` |

### 1.2 核心调用链

| 功能 | 当前调用链 | REST 复用方式 |
| --- | --- | --- |
| 登录 | `ControlRun -> Login -> UserOpService -> UserLogCrypto -> UserLogMapper` | `AuthController -> AuthServiceAdapter -> UserOpService` |
| 注册 | `ControlRun -> Register -> UserOpService -> UserLogCrypto -> UserLogMapper` | `AuthController -> AuthServiceAdapter -> UserOpService` |
| 查询科室医生 | `ControlRun -> DataOpService -> DataOpCrypto -> DataOpMappper` | `DoctorController -> DoctorServiceAdapter -> DataOpService` |
| 挂号 | `ControlRun -> Guahao -> GuahaoService -> GuahaoCrypto -> GuahaoMapper` | `AppointmentController -> AppointmentServiceAdapter -> GuahaoService` |
| 候诊列表 | `ControlRun -> Guahao -> GuahaoService -> GuahaoCrypto -> GuahaoMapper` | `DoctorController -> DoctorServiceAdapter -> GuahaoService` |
| 接诊患者 | `ControlRun -> Guahao -> GuahaoService -> GuahaoCrypto -> GuahaoMapper` | `AppointmentController -> AppointmentServiceAdapter -> GuahaoService` |
| 写病历 | `ControlRun -> InsertData -> DataOpService -> DataOpCrypto -> DataOpMappper` | `MedicalRecordController -> MedicalRecordServiceAdapter -> DataOpService` |
| 查询病历 | `ControlRun -> QueryData -> DataOpService -> DataOpCrypto -> DataOpMappper` | `MedicalRecordController -> MedicalRecordServiceAdapter -> DataOpService` |

### 1.3 可直接复用的方法

| Service 方法 | 复用结论 |
| --- | --- |
| `UserOpService.loginUser(UserLog)` | 可直接复用 |
| `UserOpService.insertUser(UserLog)` | 可直接复用 |
| `GuahaoService.add(GuahaoInfo)` | 可直接复用 |
| `GuahaoService.query(NewPair)` | 可直接复用 |
| `GuahaoService.getPatientInfo(String)` | 可直接复用 |
| `GuahaoService.sendInfo(String)` | 可直接复用 |
| `GuahaoService.delectGuaHao(String)` | 可复用，用于写病历成功后清理挂号 |
| `DataOpService.insertInto(VisitInfo)` | 可直接复用 |
| `DataOpService.query(QueryConditions)` | 可直接复用 |
| `DataOpService.getDocName(String)` | 可直接复用 |
| `DataOpService.sendDocInfo(String)` | 可直接复用 |
| `PatientInfoService.modifyInfo(PatientInfo)` | 可直接复用 |

### 1.4 需要 Adapter 包装的地方

| Adapter | 包装原因 |
| --- | --- |
| `AuthServiceAdapter` | 将 HTTP 登录/注册 DTO 转成原 `UserLog`，生成临时 token |
| `AppointmentServiceAdapter` | 将 HTTP 挂号 DTO 转成 `GuahaoInfo`；接诊时处理空患者异常 |
| `DoctorServiceAdapter` | 将候诊查询参数转成 `NewPair` |
| `MedicalRecordServiceAdapter` | 将 HTTP 病历 DTO 转成 `VisitInfo`；将查询参数转成 `QueryConditions`；写病历成功后复用原逻辑删除挂号 |
| `PatientServiceAdapter` | 包装患者信息查询与修改 |

## 2. 新增 REST 包结构

```text
src/main/java/com/liu/eemrsserver/
├── common/
│   ├── ApiResponse.java
│   ├── BadRequestException.java
│   ├── GlobalExceptionHandler.java
│   └── RequestValidator.java
├── auth/
│   ├── AuthController.java
│   ├── AuthServiceAdapter.java
│   └── dto/
│       ├── LoginRequest.java
│       ├── LoginResponse.java
│       └── RegisterRequest.java
├── appointment/
│   ├── AppointmentController.java
│   ├── AppointmentServiceAdapter.java
│   └── dto/
│       ├── AcceptAppointmentResponse.java
│       └── CreateAppointmentRequest.java
├── doctor/
│   ├── DoctorController.java
│   └── DoctorServiceAdapter.java
├── patient/
│   ├── PatientController.java
│   └── PatientServiceAdapter.java
└── medicalrecord/
    ├── MedicalRecordController.java
    ├── MedicalRecordServiceAdapter.java
    └── dto/
        ├── MedicalRecordQueryRequest.java
        └── MedicalRecordRequest.java
```

## 3. 接口测试样例

### 3.1 登录

| 项 | 内容 |
| --- | --- |
| URL | `POST http://localhost:8080/api/auth/login` |
| 原 opcode | `13` |
| 原业务方法 | `UserOpService.loginUser` |

请求：

```json
{
  "type": "pt",
  "idNumber": "110101199001010001",
  "password": "123456"
}
```

返回示例：

```json
{
  "success": true,
  "message": "login success",
  "data": {
    "type": "pt",
    "idNumber": "110101199001010001",
    "token": "TEMP-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
  }
}
```

医生登录时 `type` 使用原系统值 `dt`，必要时带上 `department`。

### 3.2 注册

| 项 | 内容 |
| --- | --- |
| URL | `POST http://localhost:8080/api/auth/register` |
| 原 opcode | `14` |
| 原业务方法 | `UserOpService.insertUser` |

请求：

```json
{
  "type": "pt",
  "idNumber": "110101199001010001",
  "userName": "张三",
  "password": "123456",
  "department": null
}
```

返回示例：

```json
{
  "success": true,
  "message": "register handled",
  "data": true
}
```

### 3.3 查询科室医生

| 项 | 内容 |
| --- | --- |
| URL | `GET http://localhost:8080/api/doctors?department=内科` |
| 原 opcode | `5` |
| 原业务方法 | `DataOpService.getDocName` |

返回示例：

```json
{
  "success": true,
  "message": "success",
  "data": [
    {
      "idNumber": "doctor-id",
      "userName": "李医生",
      "idHashCode": null,
      "gender": "男",
      "department": "内科"
    }
  ]
}
```

### 3.4 患者挂号

| 项 | 内容 |
| --- | --- |
| URL | `POST http://localhost:8080/api/appointments` |
| 原 opcode | `31` |
| 原业务方法 | `GuahaoService.add` |

请求：

```json
{
  "department": "内科",
  "idNumber": "110101199001010001",
  "userName": "张三",
  "doctorIdNumber": "doctor-id"
}
```

返回示例：

```json
{
  "success": true,
  "message": "appointment handled",
  "data": true
}
```

### 3.5 医生查询候诊列表

| 项 | 内容 |
| --- | --- |
| URL | `GET http://localhost:8080/api/doctors/me/waiting-list?department=内科&doctorIdNumber=doctor-id` |
| 原 opcode | `32` |
| 原业务方法 | `GuahaoService.query` |

返回示例：

```json
{
  "success": true,
  "message": "success",
  "data": [
    {
      "idNumber": "110101199001010001",
      "userName": "张三"
    }
  ]
}
```

### 3.6 医生接诊患者

| 项 | 内容 |
| --- | --- |
| URL | `POST http://localhost:8080/api/appointments/110101199001010001/accept` |
| 原 opcode | `33`，关联 `1` |
| 原业务方法 | `GuahaoService.getPatientInfo` |

返回示例：

```json
{
  "success": true,
  "message": "success",
  "data": {
    "idNumber": "110101199001010001",
    "patientInfo": {
      "userName": "张三",
      "gender": "男",
      "age": "35",
      "birthDay": "1990-01-01",
      "idNumber": "110101199001010001",
      "password": null,
      "medicareCard": "A001",
      "nation": "汉",
      "telephone": "13800000000",
      "address": "北京",
      "mail": "demo@example.com"
    }
  }
}
```

### 3.7 医生写病历

| 项 | 内容 |
| --- | --- |
| URL | `POST http://localhost:8080/api/medical-records` |
| 原 opcode | `12` |
| 原业务方法 | `DataOpService.insertInto`，成功后复用 `GuahaoService.delectGuaHao` |

请求：

```json
{
  "department": "内科",
  "medication": "药品信息",
  "conditionDescription": "患者主诉和病情描述",
  "cost": "100",
  "visitTime": 20260526153000,
  "patientName": "张三",
  "patientIdNumber": "110101199001010001",
  "age": 35,
  "doctorName": "李医生",
  "doctorIdNumber": "doctor-id",
  "dPk": "base64-doctor-public-key",
  "signature": "base64-sm2-signature",
  "gender": "男"
}
```

返回示例：

```json
{
  "success": true,
  "message": "medical record handled",
  "data": true
}
```

注意：该接口仍会走原 `DataOpCrypto.insertInto`，因此 `dPk` 和 `signature` 必须满足原 SM2 验签逻辑。

### 3.8 查询病历/报告

| 项 | 内容 |
| --- | --- |
| URL | `GET http://localhost:8080/api/medical-records?patientIdNumber=110101199001010001&department=内科` |
| 原 opcode | `11` |
| 原业务方法 | `DataOpService.query` |

可选查询参数：

| 参数 | 说明 |
| --- | --- |
| `startTime`、`endTime` | 对应原 `timeInterval` |
| `minAge`、`maxAge` | 对应原 `ageInterval` |
| `patientIdNumber` | 患者身份证号 |
| `doctorIdNumber` | 医生身份证号 |
| `doctorName` | 医生姓名 |
| `department` | 科室 |

返回示例：

```json
{
  "success": true,
  "message": "success",
  "data": [
    {
      "department": "内科",
      "medication": "药品信息",
      "conditionDescription": "患者主诉和病情描述",
      "cost": "100",
      "visitTime": 20260526153000,
      "patientName": "张三",
      "patientIdNumber": "110101199001010001",
      "age": 35,
      "doctorName": "李医生",
      "doctorIdNumber": "doctor-id",
      "dpk": "base64-doctor-public-key",
      "signature": "base64-sm2-signature",
      "gender": "男"
    }
  ]
}
```

## 4. 启动和验证

### 4.1 编译验证

已执行：

```bash
mvn -q -DskipTests compile
```

结果：编译通过。

### 4.2 启动服务

在 `eemrs-server-master` 目录执行：

```bash
mvn spring-boot:run
```

启动后可使用 Postman 访问 `http://localhost:8080` 下的 REST 接口。

说明：原 Socket 服务仍保留，会继续在应用启动后监听 `8887`，本阶段没有删除原兼容逻辑。

## 5. 本阶段未改动的核心文件

未改动：

| 范围 | 说明 |
| --- | --- |
| `crypto/DataOpCrypto.java` 等业务加密类 | 未改动加密算法和密文转换逻辑 |
| `utils/crypto/*` | 未改动 SM2、SM3、SM4、OPE 逻辑 |
| `mapper/*` | 未改动 Mapper 接口主体 |
| `resources/mybatis/mapper/*.xml` | 未改动 SQL 主体 |
| MySQL 表结构 | 未新增或修改数据库表 |
| `ControlRun` | 未删除或替换原 opcode 分发 |
| `EemrsServerApplication` | 未删除原 Socket 启动逻辑 |

为适配当前 JDK 17 编译环境，轻微修改了：

| 文件 | 原因 |
| --- | --- |
| `pom.xml` | 增加 `spring-boot-starter-web`；固定 Lombok 为 `1.18.30` |
| `crypto/SessionKey.java`、`module/session/BuildSession.java` | 将 `javafx.util.Pair` 替换为项目已有 `com.liu.eemrsserver.utils.Pair`，避免服务端在 JDK 17 缺少 JavaFX 包导致无法编译 |

## 6. 下一阶段建议

1. 第二阶段接入 Spring Security/JWT，替换当前临时 token。
2. 为 REST 接口补充集成测试，确保与 Socket opcode 行为一致。
3. 梳理 `UserLog.type` 的枚举值，例如固定 `pt` 和 `dt`。
4. 统一错误码与业务消息，替换乱码历史提示。
5. 在 REST 稳定后再启动 Vue 前端重写。
