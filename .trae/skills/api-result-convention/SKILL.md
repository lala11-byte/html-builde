---
name: "api-result-convention"
description: "统一返回封装规范：所有 API 必须返回 Result<T>（code/message/data）。触发词：写接口、Controller 返回值、Result.success、Result.fail、错误码、前端拦截器解包、request.js、Axios 响应处理、API 返回格式。不触发：纯前端样式调整、非 API 文件。"
---

# 统一返回封装规范（api-result-convention）

**硬性要求：前后端所有接口的返回类型必须经过 Result 封装**，任何接口不得返回裸数据。

## Result 结构

```java
public class Result<T> {
    private int code;       // 200 = 成功，其他 = 失败
    private String message; // 提示信息（成功为 "success"，失败为可读原因）
    private T data;         // 业务数据；失败时为 null
}
```

成功响应示例：

```json
{ "code": 200, "message": "success", "data": { "id": 1, "name": "我的页面" } }
```

失败响应示例：

```json
{ "code": 1001, "message": "页面名称不能为空", "data": null }
```

## 错误码约定

- 200 成功
- 400 参数校验失败；401 未登录；403 无权限；404 资源不存在；500 服务器内部异常
- 1xxx 业务错误码按模块分段（10xx 项目、11xx 页面、12xx 模板…），新增错误码必须登记在 `ResultCode` 枚举中
- 所有响应 HTTP 状态码固定 200，成功与否由 `body.code` 区分，前端拦截器据此统一处理

## 后端规则

- Controller 方法返回值一律 `Result<T>`，使用静态工厂 `Result.success(data)` / `Result.fail(code, message)`
- 业务异常抛自定义 `BusinessException(code, message)`，由全局异常处理器 `@RestControllerAdvice` 统一转换为 Result；禁止 catch 后自行拼裸响应
- 参数校验用 `@Valid` + DTO 注解，校验失败统一映射为 400 + 首个错误信息
- 分页数据封装为 `Result<PageVO<T>>`（含 `list / total / page / size`）
- **微服务场景**：gateway-service 对下游异常、超时、熔断统一捕获并转 `Result`，HTTP 状态码仍固定 200，不向调用方暴露堆栈
- **服务间调用**：OpenFeign 接口约定返回 `Result<T>`，调用方按 `code` 判断成功；若下游不可用由熔断器返回降级 `Result.fail(5003, "服务暂不可用")`

## 前端规则

- `api/request.js` 的 Axios **响应拦截器**统一解包：`code === 200` → 直接返回 `data`；否则弹出统一错误提示（ElMessage）并 reject
- api 层函数返回的即业务数据，调用方**不得再判断 code**
- 文件下载/导出等二进制响应走独立 responseType 配置，不经过 Result 解包

## 触发时机

- 编写任何后端 Controller/接口时
- 新增前端 API 调用或修改 request.js 时
- 新增业务错误码、处理接口错误时
