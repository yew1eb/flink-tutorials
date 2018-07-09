## 前言
Flink中提供了两种主要的kafka consumer实现：
1、对于kafka0.8版本使用的是低级的SimpleConsumer来实现的
2、对于kafka0.9以上版本使用的是新版的kafka consumer来实现的(详见KAFKA-1326: https://issues.apache.org/jira/browse/KAFKA-1326)

## FlinkKafkaConsumer08 extends FlinkKafkaConsumerBase
FlinkKafkaConsumerBase实现了所有的kafka版本中常见的行为。FlinkKafkaConsumerBase继承RichParallelSourceFunction,并实现了CheckpointListener，CheckpointedFunction接口。
### parallelsourcefunction如何分配partition?

### 数据如何拉取？
FlinkKafkaConsumerBase 是核心类，它继承自 SourceFunction 接口，通过运行 run() 方法获取数据，背后又通过维护一个 AbstractFetcher 成员获取数据，AbstractFetcher 维护了一个消费线程 KafkaConsumerThread 来不断与 Kafka 交互；同时我们观察到 AbstractFetcher 为每个消费的 kafka partition 维护了一个状态成员 KafkaTopicPartitionState，用于保存 partition 当前消费到的 offset 以及已经 commit 到 kafka 的 offset 信息
### 消息数据以及watermark事件如何分发？
### checkpoint以及失败恢复是怎么一个流程？

### AbstractFetcher
 * Base class for all fetchers, which implement the connections to Kafka brokers and
 * pull records from Kafka partitions.
 *
 * <p>This fetcher base class implements the logic around emitting records and tracking offsets,
 * as well as around the optional timestamp assignment and watermark generation.

### Kafka08Fetcher extends AbstractFetcher
 * A fetcher that fetches data from Kafka brokers via the Kafka 0.8 low-level consumer API.
 * The fetcher also handles the explicit communication with ZooKeeper to fetch initial offsets
 * and to write offsets to ZooKeeper.

## 参考文献
[1] [Connecting Apache Flink to the World - Reviewing the streaming connectors, 2016, Robert Metzger](https://www.slideshare.net/FlinkForward/robert-metzger-connecting-apache-flink-to-the-world-reviewing-the-streaming-connectors)


## Flink Kafka Consumer09+
## [Flink - FlinkKafkaConsumer010](http://www.cnblogs.com/fxjwind/p/6957844.html)
http://chenyuzhao.me/2017/03/29/flink-kafka-connector/

## Flink Kafka Producer 0.11
“HIT ME, BABY, JUST ONE TIME” – BUILDING END-TO-END EXACTLY-ONCE APPLICATIONS WITH FLINK, 
http://www.cnblogs.com/huxi2b/p/7717775.html (关于Kafka幂等producer的讨论 )
http://flink.apache.org/features/2018/03/01/end-to-end-exactly-once-apache-flink.html

