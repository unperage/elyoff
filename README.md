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

> 客户端只看跳跃键的**按住状态**（`isDown()`）取上升沿，**不使用** `KeyMapping#consumeClick()`。
> 原版跳跃键只被当作 `isDown()` 使用、无人消费它的点击，`clickCount` 会随每次按下单调累加且永不清理；
> 滑翔刚过 5 tick 门槛就会取到地面起跳 / 空中起飞遗留的**陈旧按键**，
> 这正是 1.0.0「一按跳跃起飞就被取消、而且每次都是」的根因，1.0.1 已修复。

判定逻辑纯状态机 `CancelStateMachine`（不依赖任何 Minecraft 类）
客户端与服务端共用同一份实现，可**离线单元测试**（见 [`tests/`](tests/README.md)）：

```bash
cd tests
javac -encoding UTF-8 -d out \
  ../26.2/src/main/java/io/github/unperage/elyoff/logic/CancelStateMachine.java \
  CancelStateMachineTest.java ClientClickQueueTest.java
java -cp out CancelStateMachineTest
java -cp out ClientClickQueueTest
```

---


## 构建

```bash
./gradlew :26.2:build
```

产物：`26.2/build/libs/elyoff-<version>+26.2.jar`

---

## 更新日志

### 1.0.1
- 修复客户端兜底分支的**误取消**：1.0.0 客户端用了 `KeyMapping#consumeClick()`，
  而原版从不消费跳跃键的点击，`clickCount` 随每次按下单调累加。
  结果滑翔刚过 5 tick 就取到地面起跳 / 空中起飞遗留的陈旧按键，把玩家立刻踢出滑翔，
  且每飞一次计数又涨，所以**每次都会发生**。
  现在客户端改为与服务端**共用同一个 `CancelStateMachine`**，只看 `isDown()` 的上升沿。
- `CancelStateMachine` 移入 `io.github.unperage.elyoff.logic`，成为客户端 / 服务端共用的唯一实现。
- 加入服务器后增加 40 tick 握手宽限期，进一步避免两端同时落手。
- 新增离线回归测试 `ClientClickQueueTest`：同一段输入脚本对比新旧算法，旧算法误取消 15 次，新算法 0 次。

### 1.0.0
- 首个版本：跳跃取消滑翔、服务端权威分支、客户端兜底、双端握手协商、零第三方依赖。

---

## 许可

[MIT](LICENSE)

---

## 作者与致谢

- **作者**：unperage
- **代码编写**：**deepseek-flash**（本项目的全部实现代码由 deepseek-flash 编写）
- **灵感**：[ElytraControl](https://github.com/Smootheez/Elytra-Control) 的"空中取消飞行"功能
  本项目为其**独立重写版**：去除 SmoothiezApi 依赖，新增服务端权威分支与双端协商机制
