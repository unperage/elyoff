# 离线测试

ElyOff 的判定逻辑（`CancelStateMachine` / `ClientFallback`）刻意不依赖任何 Minecraft 类，
因此这里可以在**不启动游戏**的情况下直接跑验证。

| 文件 | 覆盖内容 |
|---|---|
| `CancelStateMachineTest.java` | 状态机基础行为：上升沿取消、按住抑制、松开解锁、起飞不误伤、地面跳跃不干预（43 项断言） |
| `ClientClickQueueTest.java` | 回归测试：复现 1.0.0 的「一按跳跃起飞就被取消」bug，并用同一段输入脚本对比新旧客户端算法 |
| `ClientFallbackTest.java` | 回归测试：复现 1.0.1 的「服务端不装时按跳跃键完全没反应」（宽限期计数器 off-by-one 把兜底分支永久掐死） |

## 运行

```bash
javac -encoding UTF-8 -d out \
  ../26.2/src/main/java/io/github/unperage/elyoff/logic/CancelStateMachine.java \
  ../26.2/src/main/java/io/github/unperage/elyoff/logic/ClientFallback.java \
  CancelStateMachineTest.java ClientClickQueueTest.java ClientFallbackTest.java

java -cp out CancelStateMachineTest
java -cp out ClientClickQueueTest
java -cp out ClientFallbackTest
```

`ClientFallbackTest` 的结论：

```
场景1：服务端未安装 → 滑翔途中按跳跃必须取消（1.0.1 事故）
  [PASS] 滑翔 500 tick 内不应自行取消            误取消=false
  [PASS] 第 501 tick 按下跳跃 → 客户端落手取消 ★  cancel=true
场景3：服务端已安装 → 客户端绝不落手
  [PASS] 服务端已接管时客户端一次都不落手          落手=0 次
场景5：取消 → 按住 → 松开 → 重新起飞（1.0.0 事故）
  [PASS] 重新起飞后正常飞行，不再误取消 ★         误取消=0 次
```

> 场景1 特意先滑翔 **500 tick** 再按键：任何形态的"宽限期 / 计数器"写错都会被它逮住 ——
> 1.0.1 正是栽在这里。

`ClientClickQueueTest` 的结论：

```
场景A：按跳跃键起飞后按住滑翔
  [PASS] 旧算法会误取消（复现 bug）      取消 15 次
  [PASS] 新算法全程不取消                取消 0 次
场景B：取消一次后重新起飞，不应被再次取消
  [PASS] 旧算法：重新起飞后被误取消      取消 3 次
  [PASS] 新算法：重新起飞后正常飞行      取消 0 次
```

## 为什么不能用 `KeyMapping#consumeClick()`

原版跳跃键只被当作「当前是否按住」使用（`KeyMapping#isDown()`），
**没有任何地方消费它的点击**，`Minecraft#handleKeybinds` 里逐个 `consumeClick()` 的键位也不包含跳跃键。
于是 `clickCount` 会随着每一次按下单调累加且永不清理：

```
地面起跳  → clickCount = 1
空中起飞  → clickCount = 2
（滑翔到第 6 tick）consumeClick() 取到的是第 1 次那个陈旧按键 → 立刻取消
```

而且每飞一次计数又涨，所以症状是「每次都会被取消」，而不是偶发。
正确的取法是状态 + 上升沿，两端共用 `CancelStateMachine`。
