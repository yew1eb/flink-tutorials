
[Source](https://mp.weixin.qq.com/s/K_nOHimSzP2RtUu2PIy2nQ "Permalink to Apache Flink中的状态管理")

## Apache Flink中的状态管理

**摘要**  

&nbsp; &nbsp; 流处理引擎近年来在工业界正在兴起，Apache Flink将状态管理视为主要的设计准则，同时是一个开源的、可扩展的、提供高性能(兼具低延迟、高吞吐)与精确处理的分布式流计算引擎。

&nbsp; &nbsp; Flink的状态管理，得益于其轻量级异步快照的机制，简称ABS。其发生快照时不会影响到正常的流计算，可用于一致性的失败恢复、版本管理、系统扩容等场景，这对于持续的流处理变得非常重要而高效。

**1\. 引言**

&nbsp; &nbsp; 当实现数据驱动的应用程序时，通常来说程序的逻辑和数据的状态是相互独立的，要么程序是无状态的，要么状态数据存储到外部的数据库系统中。简单来讲就是状态的保存并不属于计算框架本身，而是依赖外部系统。这种传统的方式所面临的问题主要是数据的一致性无法得到保证。

&nbsp; &nbsp; 而Flink本身就是一个有状态的流计算引擎，其将应用程序中的状态作为一等公民看待，且在快照期间，不会对正常的流处理程序产生较大的影响。其原理就是全局分布式快照，借鉴自Chandy-Lamport算法，不但可以保证exactly-once语义的数据处理，同时失败恢复时也可以保证一致性。

&nbsp; &nbsp; 这篇文章主要讲述以下几点：

1\. 为有状态的处理提供端到端的设计

2\. 一致性快照机制是如何做到不影响系统正常的运行

3\. 各种失败恢复时，如何利用快照做到了exactly-once的处理

4\. operator以及sink operator，快照做了不同级别的封装，根据需要作出选择

5\. 包含状态的flink job在生产中的应用

**2\. 篇首**

**2.1 Apache Flink系统**

&nbsp; &nbsp; Flink是一个开源的纯流式计算引擎，编程模型分为用于处理有界数据集的批处理模型DataSet以及用于处理无界数据集的流处理模型DataStream，认为批是流的一种特殊形式。其编成模型分别受Google的Flume JAVA以及Dataflow模型的启发，提供了high level的API以及声明式的SQL API，自身对于状态的管理可以保证一致性；同时其部署方式以及状态数据保存有多种不同的选择。

&nbsp; &nbsp; Flink的运行时包含了client、jobmanager、taskmanager，同时其高可用依赖于Zookeeper,状态数据的存储依赖于状态后端。首先，client提交应用程序，以JobGraph(DAG模型)的形式提交到到JobManager，JobManager负责程序部署、触发检查点以及失败恢复等。JM与TM之间以异步RPC的形式(Akka)进行通信，TM定时向JM发送心跳；TaskManager收到task后，负责task的执行，以及检查点的执行，这些状态数据最终会发送到状态后端进行存储。在高可用环境中，zookeeper负责存储JobManager的元数据信息，例如jobGraph、快照id、JobaManager的地址等，当leader JobManager失败时负责master选举。根据需要进行系统扩容时，Flink也可以很好的依赖状态数据以及保存点机制进行灵活的扩容调整。可参考系统架构图：

**2.2 全局快照面临的问题**

&nbsp; &nbsp; 在失败恢复时，通常的做法是将系统回滚到过去某一个一致性的时间点，程序从那个点开始恢复，重新计算时，每个算子的数据状态也要恢复到那个点时的状态。一致性的快照就要保证在某一时刻的状态是一致的，分布式的DAG图首先从source端的顶点开始，将数据记录发送到下游算子直到sink端的顶点上，数据是不断的在算子间流转。为了得到一个一致性的快照结果，某些系统会暂时停止流的执行，直到快照完成。但这其实没有必要，下面我们将介绍Flink是如何做到一致性的快照且不影响到正常系统运行的。

**3\. 核心概念与机制**

**3.1 系统模型**

&nbsp; &nbsp; 在Flink中，根据用户编写的算子，将算子首先表式为一个StreamGraph，在对StreamGraph进行优化后(chain、slot共享资源组等)表式为JobGraph，此DAG图可看作由多个顶点与多个边组成的集合，顶点代表具体的算子，边则代表中间数据的传输，到了物理执行阶段，JobGraph被表式为多个实例并行的ExecutionGraph，如下图：

&nbsp; &nbsp; 上图中共5个operator，优化后t3与t4被chain到一起，物理执行时则根据各自的并行度并行执行。

&nbsp; &nbsp; 接下来我们将介绍Flink中被管理的状态以及状态具体的分配。

**3.1.1 可管理的状态**

&nbsp; &nbsp; 每个算子操作都可以包含状态，具体而言，状态既可以是每个分组后的sum值，也可以是个类似于每个并行task的offset的标记值，Flink中将状态分为两类：

1\. Keyed-State

2\. Operator-state

&nbsp; &nbsp; Keyed-State顾名思义，对数据进行分组，每个组都维护着一个值。这个值既可以是一个单一的数值ValueState，其特点是根据数据的变化，值可以不断的被更新；也可以是一系列的值ListState，不断的将新值插入列表中；还可以是个不断聚合的ReducingState；最后还有一个MapState，维护着具体数值的key值。简单举个MapState的例子：在keyBy(code)操作后的map函数中，每个code内部，根据其name列的不同，要得到每个name的最大value，此时就可以在RichMapFunction中，自定义一个MapState<uk,uv>，uk代表name，uv代表max(value)，此MapState状态在流中不断的被维护，当然也是被Flink所管理的。

&nbsp; &nbsp; Operator-State是每个并行实例级别的状态或者不能被keyBy的算子上的状态。例如kafka的例子。Flink在source端接收kafka的数据，其内部要为每个并行实例维护kafka中每个partition的offset信息，这就是无关乎具体数据的key，而是每个并行实例级别的状态。

&nbsp; &nbsp; 此外，窗口数据算是一种隐士的状态数据，自动被Flink所维护。

**3.1.2 状态的分片与分配**

&nbsp; &nbsp; 简单起见，根据状态的不同类型，Flink有不同的分片与管理策略。Flink在keyed state中引入了Key-Groups的概念，这对于扩容时的状态重新分配有很高的效率提升。而对于operator state，其在分配或扩容重分配时，将原来每个task实例的状态存储从每个task的List转向了全局的ListState。这样做的目的也大大的提高了扩容时的效率。

**3.2 管道式的一致性快照**

&nbsp; &nbsp; 有些系统在快照期间会暂停流，但是Flink不会。Flink在快照时，会在每个并行的source端插入一个自增的标记，这里称为epoch。插入的标记是由JobManger定期触发的(或者由用户手动执行保存点时触发)。每个标记被当作事件，同数据元素一样，在流中流向下游。当流过一个算子时，会根据这个算子的输入端的数量以及当前已经到达的同一个epoch数量，来决定是否进行检查点操作还是等待，等待在这里称为align对齐操作。具体解释来结合下图来看：

&nbsp; &nbsp; 算子t1的并行度是2，我们这里以t1(1)和t1(2)来表示每个并行实例。当t1(2)实例中的epoch为n的标记流入到t3时，还不能立刻进行检查点操作，需要对齐，即等待，因为t3有2个输入端，且此时另一个输入端t1(1)中epoch为n的标记还没有到达。当t1(1)中标记为n的事件到达t3时，此时对齐结束，t3开始执行检查点操作，保存t3算子上的状态数据。

&nbsp; &nbsp; 算子t5同样涉及对齐，因为t4的epoch(n)到达时，先等待，直到t3的epoch(n)的标记到达t5算子时，才开始执行t5的检查点操作。

&nbsp; &nbsp; 算子t1、t2和t4就不涉及对齐操作，原因有两点：第一，算子本身可能就是无状态的，例如某些filter、map等操作(用户没有明确定义状态且不是window算子)；第二，即使算子是有状态的，但是其只有1个输入流，所以不需要对齐，epoch为n的标记(barrier或marker)到达时直接进行检查点操作。

&nbsp; &nbsp; 当所有的算子的检查点都结束后(sink算子结束)，此时epoch为n的完整检查点才算结束，所有算子的状态数据发送到用户配置的状态后端(state backend)。

&nbsp; &nbsp; 当发生task失败或扩容恢复时，系统会从最后完成的一致性快照开始，重新恢复数据。这就相当于让时间回拨到之前的某一个时间点，且那个时间点是一个一致性的数据快照(快照也可能失败，所以必须是成功的快照)。从上边的过程看，Flink实现时是有几个假设前提的：

1\. 当flink从之前的快照恢复时，source的数据也要具备重发的能力，例如Apache Kafka这种可重发的数据源

2\. exactly-once的处理依赖于align对齐，而率先到达的input流就要被block，且此流在被block期间到达的数据，要被buffer起来

3\. 每个被buffer的task，在被unblock后，要继续发送buffer中的数据到下游，不能丢弃

&nbsp; &nbsp; 后两点Flink内部已经实现，第一点可重发的数据源，也是需要source支持的。可以看到，在快照期间，Flink并没有停止所有的流，而是有选择性的buffer一部分数据，这可能会稍微增加了系统的延迟。但是为了追求极致的低延迟，用户可以选择性的放弃exactly-once的正确性处理，降级为at-least-once，这样做在内部就没必要阻塞并buffer数据了，当然代价就是状态中的数据无法保证正确性。之所以有了buffer的策略，使得每个算子都可以buffer数据，这也使得Flink在低延迟和高吞吐之间具备的更大的灵活性，其既可以做到低延迟，又具备高吞吐的特性，就是依赖于buffer的自定义设置。

&nbsp; &nbsp; 这里只讨论有向无环图的实现，对于有环的状态处理不做介绍。

**3.3 用法与一致性回滚**

&nbsp; &nbsp; 快照是用来失败时恢复的，Flink根据周期性的检查点机制来触发。当前系统更多的部署到云端，这就要求流处理系统根据需要，具备调整集群规模的能力。扩容后，由于DAG图可能发生变化，因此对于这种情况的恢复操作，会面临很多的问题。集群规模重新配置的原因，无外乎以下几种情况：

1\. 程序更新，增加新功能以及bug修复、测试

2\. job并行度改变，系统扩容

&nbsp; &nbsp; 为了适应以上几种情况，Flink的状态管理更加精细化，针对keyed state以及operator state，分别引入了key-groups以及全局ListState。依赖于状态管理以及可重发的数据源，Flink也可以做到扩容时恢复到一致性的状态，通常的步骤是：手动执行检查点(保存点)，停止job，更改应用程序，恢复。下图是一个快照用法的例子：

&nbsp; &nbsp; 可以看到，当fail时，可以利用快照进行recover恢复；当进行rescale扩容操作时，即使并行度改变，导致Execution Graph改变时，依然可以利用快照进行一致性恢复。

**4\. 实施与使用**

**4.1 状态后端的支持**

&nbsp; &nbsp; Flink有2种类型的状态后端，第一种是本地的状态后端，状态的访问是由本地物理节点来控制；另一种是外部状态后端，协调外部数据库系统来完成。

&nbsp; &nbsp; 本地状态后端可以将状态数据保存到内存中，也可以保存到HDFS或RocksDB中。推荐生产使用RocksDB，因为状态数据的大小仅仅受磁盘大小的影响，同时其可以实现异步增量快照。

&nbsp; &nbsp; 外部状态后端可以通过WAL预写日志来或多数据库版本机制来实现增量快照。

**4.2 异步增量快照**

&nbsp; &nbsp; 当每个task调用triggerSnapshot()方法进行检查点时，会创建一个当前状态数据的拷贝，但这时没必要立刻将状态写入本地状态后端中，而是以懒加载的方式，只有在copy-on-write时才真正写数据，这也就允许了异步快照。对于RocksDB而言，其根据LSM树算法，实现了异步快照；同时，基于LSM树，其也可实现增量快照，仅写入上次快照变化的数据。

**4.3 可查询的状态**

&nbsp; &nbsp; Flink新增加的状态特性是将管理的状态数据，以服务的形式提供给外部系统直接进行查询，即Queryable State。对于Flink管理的基于keyed-state而言，外部系统可以通过一个kv库来访问。这很适合对于key值不断更新的value的访问。外部客户端向JobManager发起keyed-state的请求以获得key-value存在的TaskManager信息，然后client获取后继续向所在的TaskManager发起请求，检索位于state backend上的数据。

**4.4 Sink端的eaxctly-once发送**

&nbsp; &nbsp; 之前一直在强调Flink内部的一致性快照保证，但是对接外部系统的sink算子操作(例如数据库、文件系统、MQ系统等)，提供exactly-once的保障也很重要。由于在失败恢复时，会从最近完成的检查点进行恢复，因此sink端就有可能存在部分数据sink2次的情况。针对这种情况，Flink配备了2种保障exactly-once的sink机制：幂等与事务sink。

&nbsp; &nbsp; 幂等性Sink只适合于Cassandra等KV数据库，因为Cassandra维护了一个WAL日志，作为其状态的一部分，一旦epoch的标记到达Cassandra时，就会将所有处于pending状态的数据commit。而且KV库本身支持同一个key的多次输出，因此用于KV库是幂等性的一个实现。

&nbsp; &nbsp; 事务性Sink是当幂等性无法实现时的一种替代方案，例如sink端是MySQL等关系性数据库或者分布式文件系统等。对于RDBMS的SQL执行，可以在sink中维护一个状态，作为WAL预写日志的一个实现。另一种事务性的sink实现是针对分布式文件系统sink的。Flink提供了一个BucketingSink功能，将数据写到HDFS上，而在bucket未完成时，文件的状态为pending，只有当收到快照完成的标志时，才会真正提交(也要根据用户设置的hdfs文件大小来决定)，此时文件状态变为commited，如下图：

**4.5 高可用与重配置**

&nbsp; &nbsp; Flink依赖zookeeper做高可用，利用其ZAB协议，实现master选举。这就要求zookeeper保存JobManager的元数据信息、JobGraph以及快照id等，一旦leader失败，standby的JobManager争得leader权限，负责集群的运行。当然，由于JobManager失败进行恢复的情况，将会产生一些延迟。

**5\. 大规模部署**

&nbsp; &nbsp; 当前，阿里巴巴使用了1000台以上的节点部署了Flink集群，用于搜索服务；Uber使用Flink的Stream SQL构建了AthenaX流处理分析平台；Netflix公司依靠Flink引擎处理每天3PB以上规模的数据并构建内部使用的服务平台；英国的游戏公司King很早时就构建了Flink集群并推出RBEA平台；除此之外，来自荷兰的ING集团、法国的Bouygues公司等也在大规模使用Flink集群进行流处理。

**6\. 相关的工作**

&nbsp; &nbsp; Apache Beam是Google的一个基于Dataflow模型的开源实现，其是一个SDK，而Flink作为runner，可以完全支持Beam中涉及的特性，包括乱序的处理、多种窗口触发机制、exactly-once语义的数据处理、对迟到数据处理、session window的支持以及兼具低延迟与高吞吐等特性。

**7\. 结论与未来的工作**

&nbsp; &nbsp; Flink是一个灵活的、可重新配置的流计算系统，其全局一致性状态保障了失败时的恢复以及exactly-once的处理语义。未来会对状态管理进一步提升，优化增量快照，根据吞吐量重新配置规模，并实现自动扩容，同时支持高效的迭代处理。

本文整理自VLDB 2017的论文：State Management in Apache Flink

&gt; http://www.vldb.org/pvldb/vol10/p1718-carbone.pdf

  </uk,uv>