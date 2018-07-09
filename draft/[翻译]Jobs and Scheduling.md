
[TOC]

原文链接：<https://ci.apache.org/projects/flink/flink-docs-master/internals/job_scheduling.html>

# 任务和调度
该文档简要描述了Flink是如何调度Job的，以及flink如何表示和跟踪作业的状态在JobManager上。


## 调度（Scheduling)
Flink使用任务槽（Task Slot）来定义Execution resources，每个TaskManager都有一或多个任务槽，每个任务槽都可以运行one pipeline of parallel tasks，一个pipeline可以由多个连续的task组成，例如一个MapFunction的第n个并行实例(parallel instance)与一个ReduceFunction的第n个并行实例的串联在一起组成一个pipeline。注意，Flink通常会并发执行连续的任务，对于Streaming 程序来说，任何情况都如此执行；而对于batch 程序，多数情况也如此执行。

下图举例说明。由一个data source、一个MapFunction和一个ReduceFunction组成的程序，data source和MapFunction的并发度都为4，而ReduceFunction的并发度为3。一个数据流由Source-Map-Reduce的顺序组成，在具有2个TaskManager，每个TaskManager都有3个Task Slot的集群上运行，则程序执行情况如图所述。
![Assigning Pipelines of Tasks to Slots](https://ci.apache.org/projects/flink/flink-docs-master/fig/slots.svg)

Flink内通过[SlotSharingGroup](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/runtime/jobmanager/scheduler/SlotSharingGroup.java)和[CoLocationGroup](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/runtime/jobmanager/scheduler/CoLocationGroup.java)来定义哪些task共享一个task slot，也可以自定义某些task部署到同一个task slot中。

## JobManager数据结构(JobManager Data Structures)
在job执行期间，JobManager会持续跟踪分布式任务，来决定什么时候调度下一个task(或者tasks)，并且对完成的或执行失败的任务进行响应。

### JobGraph -> ExecutionGraph
JobManager接收JobGraph(代码地址：<https://github.com/apache/flink/blob/master//flink-runtime/src/main/java/org/apache/flink/runtime/jobgraph/>)，JobGraph是数据流的表现形式，包括Operator（JobVertex,代码地址：<https://github.com/apache/flink/blob/master//flink-runtime/src/main/java/org/apache/flink/runtime/jobgraph/JobVertex.java>）和中间结果（intermediateDataSet,代码地址：<https://github.com/apache/flink/blob/master//flink-runtime/src/main/java/org/apache/flink/runtime/jobgraph/IntermediateDataSet.java>）。每个Operator都有诸如并行度和执行代码等属性。此外，JobGraph还拥有一些在Operator执行代码时所需要的附加libraries。

JobManager将JobGraph转换为ExecutionGraph(代码地址：<https://github.com/apache/flink/blob/master//flink-runtime/src/main/java/org/apache/flink/runtime/executiongraph/>)，ExecutionGraph是JobGraph的并行版本：对每个JobVertex，它的每个并行子任务都有一个ExecutionVertex。一个并行度为100的Operator将拥有一个JobVertex和100个ExecutionVertex。ExecutionVertex会跟踪其特定子任务的执行状态。来自一个JobVertex的所有ExecutionVertex都由一个ExecutionJobVertex(代码地址：<https://github.com/apache/flink/blob/master//flink-runtime/src/main/java/org/apache/flink/runtime/executiongraph/ExecutionJobVertex.java>)管理，ExecutionJobVertex跟踪整个Operator的状态。除了这些节点之外，ExecutionGraph同样包括了IntermediateResult（代码地址：<https://github.com/apache/flink/blob/master//flink-runtime/src/main/java/org/apache/flink/runtime/executiongraph/IntermediateResult.java>）和IntermediateResultPartition(代码地址：<https://github.com/apache/flink/blob/master//flink-runtime/src/main/java/org/apache/flink/runtime/executiongraph/IntermediateResultPartition.java>)，前者跟踪IntermediateDataSet的状态，后者跟踪每个它的partition的状态。

![JobGraph and ExecutionGraph](https://ci.apache.org/projects/flink/flink-docs-release-1.4/fig/job_and_execution_graph.svg)
每个ExecutionGraph具有与其相关联的作业状态。此作业状态指示作业执行的当前状态。

### Job状态机
Flink job首先处于创建状态，然后切换到运行中状态，并且在完成所有工作后，它将切换到完成状态。在失败的情况下，job切换到第一个失败点，即取消所有正在运行任务的地方。如果所有job节点都已达到最终(或者说不可更改)状态，并且job不可重新启动，则job将转换为失败。如果作业可以重新启动，那么它将进入重新启动状态。一旦完成重新启动，它将变成创建状态。

在用户取消作业的情况下，将进入取消状态 ，这需要取消所有当前正在运行的任务。一旦所有运行的任务已经达到最终(或者说不可更改)状态，该作业将转换到已取消状态。

与完成状态不同，取消状态和失败状态表示一个全局的终端状态，并且触发清理工作，而暂停(suspended)状态仅处于本地终端上。本地终端意味着job的执行已在相应的JobManager上终止，但Flink集群的另一个JobManager可以从持久的HA存储中恢复这个job并重新启动。因此，处于暂停状态的job将不会被完全清理。

![States and Transitions of Flink job](https://ci.apache.org/projects/flink/flink-docs-release-1.4/fig/job_status.svg)

在执行ExecutionGraph期间，每个parallel task经过多个stages，从创建到完成或失败 ，下图说明了它们之间的状态和可能的转换。task可能会被执行多次（例如故障恢复）。所以，一个Execution（代码地址：<https://github.com/apache/flink/blob/master//flink-runtime/src/main/java/org/apache/flink/runtime/executiongraph/Execution.java>）跟踪一个ExecutionVertex的执行，每个ExecutionVertex都有一个当前Execution（current execution）和一个前驱Execution（prior execution）。

![States and Transitions of Task Executions](https://ci.apache.org/projects/flink/flink-docs-release-1.4/fig/state_machine.svg)

