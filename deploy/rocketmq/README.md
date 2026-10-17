# RocketMQ 本地部署

本目录用于本地验证统计消息队列。集群只启动**单节点 NameServer + Broker**，
不具备高可用能力；生产环境需要至少 2 节点主从或 DLedger。

## 一、环境

| 组件 | 本机路径 |
| --- | --- |
| RocketMQ | `F:\DevTools\tools\rocketmq\rocketmq-4.9.5` |
| JDK | `F:\DevTools\environment\jdk-8`（Broker 与命令行工具用它） |

应用侧使用 JDK 17 + `rocketmq-client 4.9.7`，与 4.9.5 Broker 协议兼容。

## 二、启动

打开两个命令行窗口，分别执行：

```bat
:: 窗口 1：NameServer（9876）
set ROCKETMQ_HOME=F:\DevTools\tools\rocketmq\rocketmq-4.9.5
%ROCKETMQ_HOME%\bin\mqnamesrv.cmd
```

```bat
:: 窗口 2：Broker（10911）
set ROCKETMQ_HOME=F:\DevTools\tools\rocketmq\rocketmq-4.9.5
%ROCKETMQ_HOME%\bin\mqbroker.cmd -n 127.0.0.1:9876 -c F:\project\clearlink\deploy\rocketmq\broker.conf
```

说明：

- 消息落盘到 `deploy/rocketmq/store`（已在 `.gitignore` 中忽略）；
- RocketMQ 自身的运行日志默认写到 `%USERPROFILE%\logs\rocketmqlogs`，
  GC 日志写到 `%USERPROFILE%`，这是官方脚本的默认行为；
- 停止：直接 `Ctrl+C`，或使用 `mqshutdown.cmd namesrv` / `mqshutdown.cmd broker`
  （`mqshutdown` 需要 `JAVA_HOME` 指向 JDK 8）。

## 三、建 Topic 与消费组

本地开启了自动创建，正常情况下应用启动后会自动建 Topic 与消费组。
生产环境建议关闭自动创建并显式执行：

```bat
set ROCKETMQ_HOME=F:\DevTools\tools\rocketmq\rocketmq-4.9.5
set JAVA_HOME=F:\DevTools\environment\jdk-8

%ROCKETMQ_HOME%\bin\mqadmin.cmd updateTopic ^
  -n 127.0.0.1:9876 -c DefaultCluster -t clearlink-stats-topic
```

## 四、常用排查命令

```bat
set ROCKETMQ_HOME=F:\DevTools\tools\rocketmq\rocketmq-4.9.5
set JAVA_HOME=F:\DevTools\environment\jdk-8
set NAMESRV_ADDR=127.0.0.1:9876

:: 集群状态
%ROCKETMQ_HOME%\bin\mqadmin.cmd clusterList

:: Topic 列表
%ROCKETMQ_HOME%\bin\mqadmin.cmd topicList

:: 消费进度（堆积、消费位点）
%ROCKETMQ_HOME%\bin\mqadmin.cmd consumerProgress -g clearlink-stats-consumer-group

:: 按 Key 查消息（生产者在消息上写了 eventId）
%ROCKETMQ_HOME%\bin\mqadmin.cmd queryMsgByKey -t clearlink-stats-topic -k <eventId>
```

**死信**：消费重试超过 `max-reconsume-times`（默认 16）后，消息进入
`%DLQ%clearlink-stats-consumer-group`。可用 `topicList` 看到该 Topic，
用 `mqadmin queryMsgById` / `queryMsgByKey` 排查，必要时人工重放到原 Topic。

## 五、Linux 服务器

```bash
export ROCKETMQ_HOME=/opt/rocketmq
nohup sh $ROCKETMQ_HOME/bin/mqnamesrv > /var/log/rocketmq-namesrv.log 2>&1 &
nohup sh $ROCKETMQ_HOME/bin/mqbroker -n 127.0.0.1:9876 \
  -c /opt/clearlink/conf/broker.conf > /var/log/rocketmq-broker.log 2>&1 &
```

生产环境要点：

1. Broker 磁盘与 `storePathRootDir` 分区要留足空间，并配置磁盘使用率告警；
2. `brokerIP1` 必须是客户端可达的地址（不要留 127.0.0.1）；
3. 至少主从 2 节点，`brokerRole` 按可靠性要求选择 `SYNC_MASTER` / `ASYNC_MASTER`；
4. 关闭 `autoCreateTopicEnable`，Topic 与消费组通过发布流程创建；
5. 为 `%DLQ%` 配置告警与人工处理 SOP。
