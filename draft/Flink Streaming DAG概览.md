Flink构建一个streaming作业的DAG结构以及最终的物理执行的计划，分成了四层抽象： `StreamGraph -> JobGraph -> ExecutionGraph -> 物理执行图` 每一层都会做不同的事情，下面我们来大概了解一下。 

+ **StreamGraph**: 是用于表示`streaming topology`的数据结构，它包含了生成JobGraph的必要信息。
+ **JobGraph**: StreamGraph经过算子chaining等优化后生成的graph，它是提交给JobManager的数据结构。JobGraph是连接`vertices`和`intermediate results`的形成一个DAG的graph。 请注意，` iterations (feedback edges) `目前不在JobGraph内进行编码,而是在某些特定的顶点内部建立它们之间的`feedback channel`。JobGraph包含了作业范围内的相关配置信息，而`vertex`和`intermediate result`定义了具体操作和中间数据的特征。JobGraph 的责任就是统一 Batch 和 Stream 的图，用来描述清楚一个拓扑图的结构，并且做了 chaining 的优化，chaining 是普适于 Batch 和 Stream 的，所以在这一层做掉。
+ **ExecutionGraph**：JobManager根据JobGraph生成的分布式并行化的执行图，是调度层最核心的数据结构, ExecutionGraph 的责任是方便调度和各个 tasks 状态的监控和跟踪。
+ **物理执行图**：JobManager 根据 ExecutionGraph 对 Job 进行调度后，在各个TaskManager 上部署 Task 后形成的“图”，并不是一个具体的数据结构。


以SocketWindowWordCount为例，其执行图的演变过程如下图所示：
![image.png](https://upload-images.jianshu.io/upload_images/11601528-d47d96ea1992fa3b.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
上面图片来源于：<http://wuchong.me/blog/2016/05/03/flink-internals-overview/>
下面对每一层抽象图中的概念进行简单的说明：
#### StreamGraph

+ **StreamNode**：用来代表 operator 的类，并具有所有相关的属性，如并发度、入边和出边等。
+ **StreamEdge**：表示连接两个StreamNode的边, 注意：`One edge like this does not necessarily
  gets converted to a connection between two job vertices (due to chaining/optimization)`。

####  JobGraph 

+ **JobVertex**：经过优化后符合条件的多个StreamNode可能会chain在一起生成一个JobVertex，即一个JobVertex包含一个或多个operator，JobVertex的输入是JobEdge，输出是`IntermediateDataSet`。
+ **IntermediateDataSet**：表示JobVertex的输出，即经过operator处理产生的数据集。producer是JobVertex，consumer是JobEdge。
+ **JobEdge**：代表了job graph中的一条数据传输通道。source 是 `IntermediateDataSet`，target 是 JobVertex。即数据通过JobEdge由`IntermediateDataSet`传递给目标JobVertex。

#### ExecutionGraph 

+ **ExecutionJobVertex**：和JobGraph中的JobVertex一一对应。ExecutionJobVertex对应于一个并行操作。它包含该操作的每个并行实例的ExecutionVertex。
+ **ExecutionVertex**：表示ExecutionJobVertex的其中一个并行subtask，输入是ExecutionEdge，输出是`IntermediateResultPartition`。
+ **IntermediateResult**：和JobGraph中的`IntermediateDataSet`一一对应。一个`IntermediateResult`包含多个`IntermediateResultPartition`，其个数等于该operator的并发度。
+ **IntermediateResultPartition**：表示ExecutionVertex的一个输出分区，producer是ExecutionVertex，consumer是若干个ExecutionEdge。
+ **ExecutionEdge**：表示ExecutionVertex的输入，source是`IntermediateResultPartition`，target是ExecutionVertex。source和target都只能有一个。
+ **Execution**：是执行一个 ExecutionVertex 的一次尝试。当`(for recovery, re-computation, re-configuration)`的情况下 ExecutionVertex 可能会有多个 ExecutionAttemptID。一个 Execution 通过 ExecutionAttemptID 来唯一标识。JM和TM之间关于 task 的部署和 task status 的更新都是通过 ExecutionAttemptID 来确定消息接受者。

#### 物理执行图 

+ **Task**：Execution被调度后在分配的 TaskManager 中启动对应的 subTask。subTask 包裹了具有用户执行逻辑的 operator。
+ **ResultPartition**：代表由一个Task的生成的数据，和ExecutionGraph中的IntermediateResultPartition一一对应。
+ **ResultSubpartition**：是ResultPartition的一个子分区。每个ResultPartition包含多个ResultSubpartition，其数目要由下游消费 Task 数和 DistributionPattern 来决定。
+ **InputGate**：代表Task的输入封装，和JobGraph中JobEdge一一对应。每个InputGate消费了一个或多个的ResultPartition。
+ **InputChannel**：每个InputGate会包含一个以上的InputChannel，和ExecutionGraph中的ExecutionEdge一一对应，也和ResultSubpartition一对一地相连，即一个InputChannel接收一个ResultSubpartition的输出。


flink streaming作业与batch作业的data flow拓扑结构统一抽象见下图：
![image.png](https://upload-images.jianshu.io/upload_images/11601528-82c48ae6e5753347.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
上面图片来源于：<http://wuchong.me/blog/2016/05/03/flink-internals-overview/>







