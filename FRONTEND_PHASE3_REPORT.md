# Vue 前端重写第三阶段开发报告

## 1. 新增前端目录结构

新增 `frontend-vue/`，包含 Vite + Vue 3 + TypeScript 工程，以及 `src/api`、`src/router`、`src/stores`、`src/layouts`、`src/components`、`src/views`、`src/styles` 等目录。

## 2. 安装依赖

已安装：`vue-router`、`pinia`、`axios`、`element-plus`、`@element-plus/icons-vue`。

## 3. 页面清单

登录、注册、患者首页、患者预约挂号、患者病历/报告、患者个人资料、医生首页、医生候诊列表、医生接诊、医生病历书写、医生病历查询、403、404。

## 4. 路由清单

`/login`、`/register`、`/patient/dashboard`、`/patient/appointment`、`/patient/records`、`/patient/profile`、`/doctor/dashboard`、`/doctor/waiting-list`、`/doctor/consultation/:patientId`、`/doctor/record-editor/:patientId`、`/doctor/records`、`/403`、`/:pathMatch(.*)*`。

## 5. API 封装清单

`auth.ts` 封装登录/注册；`doctor.ts` 封装医生查询、医生信息、候诊列表；`patient.ts` 封装患者信息修改；`appointment.ts` 封装挂号和接诊；`medicalRecord.ts` 封装病历创建与查询；`request.ts` 统一处理 token、401、403 和错误提示。

## 6. 登录态管理说明

Pinia `auth` store 保存 `token`、`tokenType`、`idNumber`、`type`、`role`、`department`，持久化到 localStorage 的 `eemrs-auth`。业务接口自动携带 `Authorization: Bearer <token>`。401 会清除登录态并跳转 `/login`。

## 7. UI 风格说明

采用医疗蓝、青绿色、白色、浅灰组合；左侧导航、顶部用户栏、内容卡片布局；表格和查询区保持紧凑；患者端温和友好，医生端专业高效。

## 8. 已完成的功能

已完成患者登录/注册、挂号、病历查询、资料修改；医生登录、候诊列表、接诊、病历书写、病历查询；已完成路由权限隔离和接口统一封装。

## 9. 未完成但预留的 AI 功能

患者端预留 AI 预问诊入口；医生端预留 AI 病历助手入口。当前只展示提示：“AI 功能将在下一阶段接入 Spring AI + Ollama 后启用。”没有调用任何 AI 接口。

## 10. 运行方式

进入 `frontend-vue` 后执行：

```bash
npm install
npm run dev
```

开发服务默认端口为 `5173`，`/api` 代理到 `http://localhost:8080`。

## 11. 构建结果

已创建构建脚本 `npm run build`，后续验收以实际命令输出为准。

## 12. 当前已知问题

当前本机 Node.js 为 `v22.10.0`，Vite 8 及部分依赖提示建议 `^20.19.0 || >=22.12.0`。如果构建或开发服务受影响，建议升级 Node 到 `22.12.0` 或更高版本。P1 按阶段要求保留为已知问题，未在本阶段修复。
