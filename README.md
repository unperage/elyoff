# ElyOff

> **滑翔途中按一下跳跃键，立刻取消鞘翅飞行。**
> 服务端权威 + 客户端自动兜底，两端自动协商，无需手动配置。

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-green)]()
[![Loader](https://img.shields.io/badge/Loader-Fabric-blue)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)]()

---

## ✨ 实现的功能

| 功能 | 说明 |
|---|---|
| **跳跃取消飞行** | 滑翔过程中**按下跳跃键**（默认空格）立即退出滑翔 |
| **服务端权威分支** | 装在服务端即可生效：取消动作由服务端执行并通过实体同步标志位下发，**连原版客户端也能用**，且不存在两端状态不同步 |
| **客户端兜底分支** | 服务端**没装**本模组时，由客户端本地取消滑翔，保证单人/小服也能用 |
| **双端自动协商** | 通过自定义握手包 + `canSend` 探测：**服务端一旦生效，客户端兜底逻辑自动停跑**，绝不重复处理 |
| **防误伤起飞** | 起飞那一次按键不会被误判为"取消"（滑翔需持续 > 5 tick 才响应） |
| **防按住一直飞** | 取消后只要跳跃键仍按住，再次进入滑翔会被立刻取消 |
| **松开即可重新起飞** | 松开跳跃键即解除抑制——**任何新按键都必然 preceded by 一次松开**，所以松开再按一定能重新滑翔 |
| **无第三方库依赖** | 只依赖 **Fabric API**，不需要任何额外前置库 |

### 行为示例

```
滑翔中  →  按空格        →  立即掉出滑翔 ✅
（按住空格不放）          →  不会自己重新起飞（设计如此）
松开空格 →  再按空格      →  正常重新滑翔 ✅
用跳跃键起飞              →  不会被误取消 ✅
```

---

## 🧠 工作原理

Minecraft 的滑翔状态保存在实体的**同步标志位（shared flag 7）**里，服务端改动会自动同步给所有客户端。

- **服务端分支**：每 tick 读取玩家最近的客户端输入（`ServerPlayer#getLastClientInput()`），
  在检测到「滑翔途中跳跃键上升沿」时调用 `stopFallFlying()`，
  由服务端同步给客户端 —— 因此**原版客户端也被支持**。
- **客户端分支**：在 `ClientTickEvents.END_CLIENT_TICK` 中读取跳跃键点击并本地取消，
  仅在服务端未生效时启用。

判定逻辑被抽成纯状态机 `CancelStateMachine`（不依赖任何 Minecraft 类），
因此可以**脱离游戏离线单元测试**（见 `CancelStateMachine` 的注释与断言场景）。

---

## 📦 安装

1. 安装 **Fabric Loader**（≥ 0.19.3，Minecraft **26.2**）
2. 安装 **Fabric API**
3. 把 `elyoff-x.y.z+26.2.jar` 放进：
   - **服务端** `mods/` —— 推荐，所有玩家生效（含基岩版通过 Geyser 进入的玩家）
   - **客户端** `mods/` —— 可选，用于服务端未安装时兜底

> 两侧都装也没问题：客户端检测到服务端已生效后会自动让出处理权。

---

## 🔨 构建

```bash
./gradlew :26.2:build
```

产物：`26.2/build/libs/elyoff-<version>+26.2.jar`

---

## 📄 许可

[MIT](LICENSE)

---

## 👤 作者与致谢

- **作者**：unperage
- **代码编写**：**deepseek-flash**（本项目的全部实现代码由 deepseek-flash 编写）
- **灵感来源**：[ElytraControl](https://github.com/Smootheez/Elytra-Control) 的"空中取消飞行"功能，
  本项目为其**独立重写版**：去掉对 SmoothiezApi 的依赖，并新增服务端权威分支与双端协商机制。
