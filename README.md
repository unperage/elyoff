# ElyOff

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-green)]()
[![Loader](https://img.shields.io/badge/Loader-Fabric-blue)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)]()

---

## 功能

滑翔中按下跳跃取消
 
## 工作原理

Minecraft 的滑翔状态保存在实体的**同步标志位（shared flag 7）**
服务端自动同步于客户端

- **服务端**：每 tick 读取玩家最近的客户端输入（`ServerPlayer#getLastClientInput()`）
  在检测到「滑翔途中跳跃键上升沿」时调用 `stopFallFlying()`
  由服务端同步给客户端
- **客户端**：在 `ClientTickEvents.END_CLIENT_TICK` 中读取跳跃取消滑翔
  仅服务端未生效时启用

判定逻辑纯状态机 `CancelStateMachine`（不依赖任何 Minecraft 类）
可**离线单元测试**（见 `CancelStateMachine` 的注释与断言场景）

---


## 构建

```bash
./gradlew :26.2:build
```

产物：`26.2/build/libs/elyoff-<version>+26.2.jar`

---

## 许可

[MIT](LICENSE)

---

## 作者与致谢

- **作者**：unperage
- **代码编写**：**deepseek-flash**（本项目的全部实现代码由 deepseek-flash 编写）
- **灵感**：[ElytraControl](https://github.com/Smootheez/Elytra-Control) 的"空中取消飞行"功能
  本项目为其**独立重写版**：去除 SmoothiezApi 依赖，新增服务端权威分支与双端协商机制
