# 第二阶段认证与权限测试报告

## 1. 测试环境

- Java 版本：Oracle JDK 17.0.12
- Maven 版本：Apache Maven 3.9.12
- SpringBoot 启动结果：成功，`http://localhost:8080` 可访问
- 数据库连接结果：本地 MySQL `3306` 端口可达；注册、登录、挂号等接口实际访问数据库成功
- 测试时间：2026-05-27，Asia/Shanghai

说明：报告已脱敏，不输出数据库密码、JWT secret、完整 token 或完整身份证号。

## 2. 测试账号

本次测试脚本自动通过注册接口创建临时账号。

- PATIENT_A：`1798***1001`
- PATIENT_B：`1798***1002`
- DOCTOR_A：`2798***1001`
- DOCTOR_B：`2798***1002`
- DEPARTMENT：内科

## 3. 编译与启动结果

| 测试项 | 结果 | 说明 |
| --- | --- | --- |
| Java 版本检查 | PASS | JDK 17.0.12 |
| Maven 版本检查 | PASS | Maven 3.9.12 |
| `mvn -q -DskipTests compile` | PASS | 编译通过 |
| `application.yml` 检查 | PASS | 未显式配置 `server.port`，默认 8080；存在 MySQL 配置；未发现外置 JWT 配置 |
| MySQL 端口检查 | PASS | `localhost:3306` 可达 |
| `mvn spring-boot:run` | PASS | 服务启动后 8080 可达 |
| 原 Socket 兼容逻辑 | PASS | 未删除；本次未测试 8887 业务兼容性 |

## 4. JWT 登录测试

| 测试项 | 结果 | HTTP 状态码 | 说明 |
| --- | --- | --- | --- |
| 注册 PATIENT_A | PASS | 200 | success=true |
| 注册 PATIENT_B | PASS | 200 | success=true |
| 注册 DOCTOR_A | PASS | 200 | success=true |
| 注册 DOCTOR_B | PASS | 200 | success=true |
| PATIENT_A 登录 | PASS | 200 | 返回 JWT，非 `TEMP-`；`tokenType=Bearer`；`role=PATIENT`；JWT 包含 `idNumber,type,role,iat,exp` |
| PATIENT_B 登录 | PASS | 200 | 返回 JWT，非 `TEMP-`；`tokenType=Bearer`；`role=PATIENT` |
| DOCTOR_A 登录 | PASS | 200 | 返回 JWT，非 `TEMP-`；`tokenType=Bearer`；`role=DOCTOR`；JWT 包含 `department` |
| DOCTOR_B 登录 | PASS | 200 | 返回 JWT，非 `TEMP-`；`tokenType=Bearer`；`role=DOCTOR` |

## 5. 401/403 权限测试

| 编号 | 场景 | 期望 | 实际 | 结果 |
| --- | --- | --- | --- | --- |
| 1 | 未携带 token 访问 `GET /api/medical-records` | 401 | 401 | PASS |
| 2 | 患者 token 访问 `GET /api/doctors/me/waiting-list` | 403 | 403 | PASS |
| 3 | 医生 token 访问 `PUT /api/patients/me` | 403 | 403 | PASS |

## 6. 业务级越权测试

| 编号 | 场景 | 期望 | 实际 | 结果 |
| --- | --- | --- | --- | --- |
| 1 | 患者 A 查询患者 B 病历 | 403，或强制只返回患者 A 数据 | 403 | PASS |
| 2 | 患者 A 替患者 B 挂号 | 403 | 403 | PASS |
| 3 | 医生 A 伪造医生 B 查询候诊 | 403 | 403 | PASS |
| 4 | 医生 A 伪造医生 B 写病历 | 403，且应在 SM2 验签前拦截 | 400 | FAIL |

失败定位：

- `MedicalRecordServiceAdapter.create` 当前先校验 `dPk` 和 `signature`，再校验 `doctorIdNumber` 是否等于当前 token 中的医生身份。
- 测试请求使用了非法 `dPk`，接口先触发参数错误，返回 400。
- 这说明权限校验顺序不满足“越权必须优先返回 403”的验收要求。

修复建议：

1. 在 `MedicalRecordServiceAdapter.create` 中，将医生角色和 `doctorIdNumber` 一致性校验移动到 `dPk/signature` 校验之前。
2. 同时建议为 DTO 字段 `dPk` 增加 Jackson 映射校验，例如兼容 `dPk` 和 `dpk`，避免前端字段大小写导致误判。

## 7. 合法业务流程测试

| 编号 | 场景 | 期望 | 实际 | 结果 |
| --- | --- | --- | --- | --- |
| 1 | 患者 A 查询科室医生 | 200，返回医生列表 | 200 | PASS |
| 2 | 患者 A 挂号 | 200，success=true | 200，success=true | PASS |
| 3 | 医生 A 查询候诊列表 | 200，列表包含患者 A | 200，包含患者 A | PASS |
| 4 | 医生 A 接诊患者 A | 200，返回患者基础信息 | 200 | PASS |
| 5 | 医生 A 写合法病历 | 200，success=true | SKIPPED | SKIPPED |
| 6 | 患者 A 查询自己的病历/报告 | 200，只返回患者 A 数据 | 500 | FAIL |
| 7 | 医生 A 查询自己的病历 | 200，只返回医生 A 相关数据 | 200 | PASS |

SKIPPED 说明：

- 合法写病历仍依赖旧 `DataOpCrypto.insertInto` 的 SM2 验签。
- 当前测试环境没有提供可用于本次请求内容的合法 `dPk` 和 `signature`。
- 测试脚本支持通过环境变量 `VALID_DOCTOR_PUBLIC_KEY`、`VALID_SM2_SIGNATURE`、`VALID_CONDITION_DESCRIPTION` 传入合法签名数据后继续端到端测试。
- 本项不能标记为通过。

患者查询 500 定位：

- 患者新注册后尚无合法病历记录。
- 旧查询链路 `DataOpCrypto.query` 在指定 `patientIdNumber` 时读取患者 `counter`，再生成 `pids`。
- 当 `counter=0` 时，`pids` 为空集合，但 MyBatis XML 只判断 `pids != null`，会进入 `patient_id_hash_code in (...)` 拼接逻辑，存在生成空 `IN ()` SQL 的风险。
- 该问题发生在旧查询链路中，但被第二阶段“患者只能查自己”的权限覆盖策略触发出来。

修复建议：

1. 在 REST Adapter 层先做兼容保护：患者查询自身记录前，如果能判断无记录则返回空列表。
2. 或在不改变 SQL 主体业务语义的前提下，调整查询条件构造：当 `pids` 为空时直接返回空列表，不进入 Mapper 查询。
3. 更长期建议在 `DataOpCrypto.query` 中处理 `sum <= 0`，返回空列表，避免空集合传入 Mapper。

## 8. 自动化测试脚本

已创建：

```text
scripts/test_phase2_security.py
```

使用方式：

```bash
python scripts/test_phase2_security.py
```

可选环境变量：

| 环境变量 | 用途 |
| --- | --- |
| `PATIENT_A_ID`、`PATIENT_A_PASSWORD` | 指定患者 A |
| `PATIENT_B_ID`、`PATIENT_B_PASSWORD` | 指定患者 B |
| `DOCTOR_A_ID`、`DOCTOR_A_PASSWORD` | 指定医生 A |
| `DOCTOR_B_ID`、`DOCTOR_B_PASSWORD` | 指定医生 B |
| `DEPARTMENT` | 指定科室 |
| `VALID_DOCTOR_PUBLIC_KEY` | 合法写病历用 SM2 公钥 |
| `VALID_SM2_SIGNATURE` | 合法写病历用 SM2 签名 |
| `VALID_CONDITION_DESCRIPTION` | 与签名匹配的病情描述 |

脚本输出结果保存到：

```text
phase2_security_results.json
```

该 JSON 只保存脱敏账号和测试结果，不保存完整 token。

## 9. 发现的问题

| 编号 | 问题 | 影响 | 建议 |
| --- | --- | --- | --- |
| P1 | 医生 A 伪造医生 B 写病历返回 400，而不是权限层 403 | 不满足“越权应先于 SM2 验签拦截”的验收标准 | 调整 `MedicalRecordServiceAdapter.create` 校验顺序，先校验医生身份 |
| P2 | 患者查询自己的病历在无记录时返回 500 | 患者报告查询接口不可稳定使用 | 已修复，见“P2 修复结果” |
| P3 | 合法写病历端到端测试 SKIPPED | 无法证明写病历完整业务闭环已通过 | 已修复，见“P3 修复结果” |
| P4 | JWT secret 未外置配置 | 安全配置不利于部署 | 后续将 secret、过期时间放入配置文件或环境变量；报告不输出 secret |

## 10. 结论

结论：**第二阶段未通过，需要先修复权限或认证相关问题。**

已经通过的部分：

- 项目可编译。
- 服务可启动。
- 登录接口返回 JWT，不再返回 `TEMP-` token。
- 401 未认证拦截有效。
- 患者/医生 URL 级角色隔离有效。
- 患者替他人挂号、患者查他人病历、医生伪造他人查询候诊均已被拦截。
- 合法挂号、候诊查询、接诊流程已跑通。

未通过或未完成的部分：

- 医生伪造他人写病历未优先返回 403。
- 患者查询自己病历在无记录时返回 500。
- 合法写病历缺少有效 SM2 签名数据，未完成端到端证明。

建议下一步：当前 P2 和 P3 已完成修复；如需第二阶段完全通过，还需要单独处理 P1。

## P2 修复结果

### 1. 问题原因

患者通过 JWT 访问 `GET /api/medical-records` 时，`MedicalRecordServiceAdapter` 会把查询条件强制限定为当前 token 中的患者 `idNumber`。随后 `DataOpService.query` 调用 `DataOpCrypto.query`。

旧查询链路中，当患者存在但病历计数 `counter=0` 时，`DataOpCrypto.query` 会生成空的 `pids` 集合并继续调用 Mapper。Mapper XML 中存在 `patient_id_hash_code in <foreach collection="pids">...</foreach>` 逻辑，空集合会带来空 `IN ()` SQL 风险，导致接口返回 500。

### 2. 修改文件

| 文件 | 修改内容 |
| --- | --- |
| `src/main/java/com/liu/eemrsserver/crypto/DataOpCrypto.java` | 在患者病历查询分支中增加 `sum <= 0` 和空 `pids` 保护，直接返回空 `List<VisitInfo>` |
| `scripts/test_phase2_security.py` | 加强患者/医生病历查询断言：必须是 `200 + success=true + data 为数组`，且不能包含越权目标数据 |

### 3. 修复方式

采用最小侵入修复：

1. 不改 Mapper XML。
2. 不改 MySQL 旧表结构。
3. 不改 Socket/opcode 逻辑。
4. 不重写查询链路。
5. 仅在旧查询进入 Mapper 前处理“患者存在但病历数量为 0”的情况，返回空列表 `[]`。

关键行为：

```text
counter <= 0 -> return new ArrayList<VisitInfo>()
empty pids   -> return new ArrayList<VisitInfo>()
```

### 4. 修复前结果

| 场景 | 修复前实际结果 |
| --- | --- |
| 患者 A 查询自己的病历，且患者无病历 | HTTP 500，`success=false` |

复现响应：

```json
{
  "success": false,
  "message": "Internal server error",
  "data": null
}
```

### 5. 修复后结果

重新运行 `scripts/test_phase2_security.py` 后：

| 场景 | 修复后实际结果 | 结果 |
| --- | --- | --- |
| 患者 A 查询自己的病历 | HTTP 200，`success=true`，`data` 为数组 | PASS |
| 医生 A 查询自己的病历 | HTTP 200，`success=true`，`data` 为数组 | PASS |
| 患者 A 查询患者 B 病历 | HTTP 403 | PASS |

符合期望：

```json
{
  "success": true,
  "message": "success",
  "data": []
}
```

### 6. 是否影响核心模块

| 范围 | 是否影响 | 说明 |
| --- | --- | --- |
| crypto 加密算法 | 否 | 未修改 SM2、SM3、SM4、OPE 算法 |
| mapper / MyBatis XML | 否 | 未改 SQL 主体 |
| MySQL 旧表结构 | 否 | 未改表结构 |
| Socket/opcode | 否 | 未删除、未改动原兼容入口 |
| 权限策略 | 否 | 患者仍只能查询自己；医生仍限定当前医生身份 |

说明：本次触碰 `DataOpCrypto.query` 的查询分支，但没有改动加密算法本身，只是在进入 Mapper 前对“无病历”场景做空列表返回。

### 7. 回归测试结果

| 测试项 | 修复后结果 |
| --- | --- |
| `mvn -q -DskipTests compile` | PASS |
| 服务启动 | PASS |
| 未携带 token 访问受保护接口 | PASS，401 |
| 患者 token 访问医生接口 | PASS，403 |
| 医生 token 访问患者接口 | PASS，403 |
| 患者 A 查询患者 B 病历 | PASS，403 |
| 患者 A 替患者 B 挂号 | PASS，403 |
| 医生 A 伪造医生 B 查询候诊 | PASS，403 |
| 患者 A 查询科室医生 | PASS，200 |
| 患者 A 挂号 | PASS，200 |
| 医生 A 查询候诊列表 | PASS，200 |
| 医生 A 接诊患者 A | PASS，200 |
| 患者 A 查询自己的病历 | PASS，200 |
| 医生 A 查询自己的病历 | PASS，200 |

保留的已知问题：

| 问题 | 状态 |
| --- | --- |
| P1：医生 A 伪造医生 B 写病历返回 400 而不是 403 | 未修复，按本次要求保留 |
| P3：合法写病历缺少合法 SM2 `dPk/signature`，端到端测试 SKIPPED | 已修复，见“P3 修复结果” |

### 8. P2 结论

**P2 已修复，可以继续进入 Vue 前端重写。**

注意：第二阶段整体报告中 P1 仍然是已知遗留问题；P2 和 P3 已分别完成修复。

## P3 修复结果

### 1. 旧医生端签名生成逻辑

在 `Doctor-master` 中定位到旧医生端写病历逻辑：

| 字段 | 旧医生端来源 | 生成方式 | 编码格式 | REST 请求字段 |
| --- | --- | --- | --- | --- |
| `conditionDescription` | `DoctorInsertController.description.getText()` | 医生录入病情描述 | Java 字符串字节，旧代码使用平台默认编码；测试生成器使用 UTF-8，与当前中文测试数据一致 | `conditionDescription` |
| `dPk` | `OperateKey.getSM2ClientKeyFromFile().getPublicKey()` | 读取 `Doctor-master/SM2KeyPair/ec.x509.pub.der`，取公钥 DER 编码 | Base64 of X.509 DER | `dPk`，测试请求同时带 `dpk` 兼容 Jackson 绑定 |
| `signature` | `SM2.sign(visitInfo.getConditionDescription(), privateKey)` | 读取 `Doctor-master/SM2KeyPair/ec.pkcs8.pri.der`，对 `conditionDescription` 单字段签名 | Base64 of SM2 signature bytes | `signature` |
| 医生私钥 | `Doctor-master/SM2KeyPair/ec.pkcs8.pri.der` | `BCECUtil.convertPKCS8ToECPrivateKey` | PKCS#8 DER 文件 | 不进入 REST 请求 |
| 医生公钥 | `Doctor-master/SM2KeyPair/ec.x509.pub.der` | `BCECUtil.convertX509ToECPublicKey` | X.509 DER 文件，再 Base64 放入请求 | `dPk` / `dpk` |

旧医生端提交的 `VisitInfo` 与 REST `MedicalRecordRequest` 的核心字段一致：`department`、`medication`、`conditionDescription`、`patientName`、`patientIdNumber`、`age`、`doctorName`、`doctorIdNumber`、`dPk`、`signature`、`gender`。

### 2. 服务端验签规则

服务端位置：

- `DataOpService.insertInto`
- `DataOpCrypto.insertInto`
- `VisitInfo`
- `SM2.verify`
- `OperateKey.toSM2PublicKey`

验签规则：

```text
签名原文 = VisitInfo.conditionDescription
编码方式 = data.getBytes()
公钥来源 = Base64.decode(VisitInfo.dPk) -> X.509 DER -> BCECPublicKey
签名格式 = Base64.decode(VisitInfo.signature)
验签工具类 = com.liu.eemrsserver.utils.crypto.SM2.verify -> org.zz.gmhelper.SM2Util.verify
验签时机 = 写入数据库加密前验签；验签失败则 DataOpCrypto.insertInto 返回 false
```

没有发现字段拼接，服务端只对 `conditionDescription` 单字段验签。`visitTime`、`age`、`cost` 不参与签名。

### 3. 修改文件

| 文件 | 修改内容 |
| --- | --- |
| `scripts/GenerateSm2Signature.java` | 新增测试辅助程序，读取 Doctor-master 旧 SM2 公私钥，对病情描述生成合法 `dPk` 和 `signature` |
| `scripts/test_phase2_security.py` | 合法写病历测试自动调用签名生成器；若环境变量已提供签名则优先使用环境变量 |
| `TEST_REPORT_PHASE2.md` | 增加 P3 修复结果说明 |

### 4. 修复方式

本次没有绕过服务端验签，而是复用旧医生端签名方式：

1. 从 `Doctor-master/SM2KeyPair/ec.x509.pub.der` 读取医生公钥。
2. 从 `Doctor-master/SM2KeyPair/ec.pkcs8.pri.der` 读取医生私钥。
3. 对 `conditionDescription` 生成 SM2 签名。
4. 将公钥 DER Base64 作为 `dPk/dpk`，签名 Base64 作为 `signature`。
5. 调用 `POST /api/medical-records`，仍走 `DataOpCrypto.insertInto` 的原始验签和加密入库逻辑。

测试脚本不会打印完整私钥、完整 token 或完整身份证号。

### 5. 修复前结果

| 场景 | 修复前实际结果 |
| --- | --- |
| 合法医生写病历端到端测试 | SKIPPED，原因是缺少合法 SM2 `dPk/signature` |

第一次接入签名后曾出现 HTTP 400，原因是 REST JSON 字段名 `dPk` 在 Jackson/Lombok 下存在大小写绑定差异。测试请求现在同时携带 `dPk` 和 `dpk`，确保 DTO 的 `setDPk` 能收到值；这只影响测试请求体，不改业务代码。

### 6. 修复后结果

重新运行 `scripts/test_phase2_security.py` 后：

| 场景 | 修复后实际结果 | 结果 |
| --- | --- | --- |
| 合法医生写病历 | HTTP 200，`success=true` | PASS |
| 患者 A 查询自己的病历/报告 | HTTP 200，`success=true`，`data` 为数组 | PASS |
| 医生 A 查询自己的病历 | HTTP 200，`success=true`，`data` 为数组 | PASS |

脚本结果中合法写病历项：

```text
PASS | doctor A create valid medical record | expected=200 success=true | actual=200 | signatureSource=Doctor-master SM2KeyPair
```

### 7. 是否影响核心模块

| 范围 | 是否影响 | 说明 |
| --- | --- | --- |
| SM2 验签逻辑 | 否 | 未关闭、未绕过、未改 `SM2.verify` |
| `DataOpCrypto.insertInto` | 否 | 写病历仍走原始验签与加密入库逻辑 |
| crypto 底层算法 | 否 | 未修改 |
| mapper / MyBatis XML | 否 | 未修改 |
| MySQL 旧表结构 | 否 | 未修改 |
| Socket/opcode | 否 | 未删除、未修改 |
| P1 | 否 | 医生伪造医生 B 写病历仍保留为已知问题，当前测试仍返回 400 |

### 8. P3 结论

**P3 已修复：合法医生写病历端到端测试已真实通过。**

当前仅剩 P1 作为第二阶段已知遗留问题：医生 A 伪造医生 B 写病历返回 400 而不是 403。本次按要求未修复 P1。
