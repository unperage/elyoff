# 离线测试

判定逻辑（`CancelStateMachine` / `ClientFallback` / `ForceMode`）不依赖任何 Minecraft 类，可以直接用 `javac` + `java` 跑，不用启动游戏。

| 文件 | 覆盖 |
|---|---|
| `CancelStateMachineTest.java` | 上升沿取消、按住抑制、松开解锁、起飞不误伤、地面跳跃不干预（43 项） |
| `ClientClickQueueTest.java` | 1.0.0 的「一按跳跃起飞就被取消」 |
| `ClientFallbackTest.java` | 1.0.1 的「服务端不装时按跳跃完全没反应」、1.1.0 的 force 场景（16 项） |
| `ForceModeTest.java` | 开/关/智能，阈值边界与解析容错（34 项） |

```bash
javac -encoding UTF-8 -d out \
  ../26.2/src/main/java/io/github/unperage/elyoff/logic/CancelStateMachine.java \
  ../26.2/src/main/java/io/github/unperage/elyoff/logic/ClientFallback.java \
  ../26.2/src/main/java/io/github/unperage/elyoff/logic/ForceMode.java \
  CancelStateMachineTest.java ClientClickQueueTest.java ClientFallbackTest.java ForceModeTest.java

java -cp out CancelStateMachineTest
java -cp out ClientClickQueueTest
java -cp out ClientFallbackTest
java -cp out ForceModeTest
```

合计 100 项断言，全部通过。
