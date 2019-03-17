**Flink核心技术内幕 -- 大纲**

作者：yew1eb

创建时间： 2018/7/24

更新时间：

## 开篇-流计算领域发展历史

**Streaming处理的重要几个方面：**

1.  Delivery Guarantees

2.  Fault Tolerance

3.  State Management

4.  Performance

5.  Advanced Features(Event Time Processing, Watermark, Windowing, streaming SQL, )

6.  Maturity

**两种类型的流处理：**

Native Streaming \ Micro batching

storm \ spark streaming \ flink \ kafka streams \ samza



### 前言

本系列的文章主要介绍Flink的通信机制、作业执行原理、调度算法、容错和监控管理等进行深入分析，在分析原理和代码的同事结合实例进行演示。



## Flink 概述与架构



## What is Apache Flink?

<https://flink.apache.org/flink-architecture.html>

<https://flink.apache.org/flink-applications.html>

<https://flink.apache.org/flink-operations.html>

https://ci.apache.org/projects/flink/flink-docs-release-1.6/concepts/runtime.html#job-managers-task-managers-clients



## Flink拓扑构建与作业提交

DAG构建整体流程概览 DONE

StreamGraph深入源码分析  DONE

JobGraph深入源码分析  DONE

ExecutionGraph深入源码分析  DOING



## Flink通信机制

分布式通信方式

通信框架AKKA

client、master和worker之间的通信 Flink communication部分



## Flink调度机制

Scheduler类解读

物理执行计划生成

task的调度

task执行

task的生命周期管理

上下游operator数据分发/接收机制，task之间的数据交换



## Flink 状态管理与一致性快照

Checkpoint(检查点)

savepoint机制



## Flink 容错机制

节点HA机制



## Flink内存管理

进度：资料收集完成，TODO



## Flink SQL

进度：资料收集完成，TODO

# Flink 问题排查与性能调优

搭建flink standalone集群，开启debug

Flink 重要配置说明

Flink重要监控指标说明

FAQ

代码性能优化

内存配置调优

"Execution"配置

# 编程模型&高级特性

## Side Outputs



## The Broadcast State Pattern



## Async I/O



## Window

进度：资料收集完成，TODO

# 经验参考

在学习中和工作很重要的一点是遇到任何问题一定要刨根问底，不要停留在现象和一些浅层次的直观的原因上，一定要找到本质。

一个比较好的判定标志是你能不能一句话给别人讲清楚。

要做到这个可能会让你一开始花更多的时间，甚至觉得自己学得比别人慢很多，但是你学过的每个东西都是完全吃透的，而很多东西的原理是相通的，在一段时间的积累后你会发现学任何新东西就像看说明书一样了。



\* 最主要的是刚开始一定要抓主干，不要纠结于源码的细节实现！！！

\* 欲速则不达，贪多嚼不烂！

\* 横向对比类似的框架storm, spark, kafka, hadoop的设计的异同，优劣

\* 为何如此设计架构（整体的架构，核心机制）

\* 为何如此拆分模块（模块划分的粒度，模块的归类）

\* 为何如此抽象方法（方法的粒度，哪些方法应该被抽象）

\* 有趣的代码，为何用此种写法（性能，兼容性，安全，可维护性，设计模式，炫技）

# 参考文献

- Streaming Systems: The What, Where, When, and How of Large-Scale Data Processing 1st Edition
- (January 4, 2019)Stream Processing with Apache Flink: Fundamentals, Implementation, and Operation of Streaming Applications 1st Edition
- Designing Distributed Systems: Patterns and Paradigms for Scalable, Reliable Services 1st Edition, Kindle Edition
- 
- Spark内核设计的艺术架构设计与实现
- 循序渐进学Spark [[http://yuedu.163.com/source/b766363446c544fc9b487803baf1a8e7\_4]{.underline}](http://yuedu.163.com/source/b766363446c544fc9b487803baf1a8e7_4)
- Storm源码分析
- Presto技术内幕
- Flink Internals <https://cwiki.apache.org/confluence/display/FLINK/Flink+Internals> 
-  Flink Improvement Proposals <https://cwiki.apache.org/confluence/display/FLINK/Flink+Improvement+Proposals>
- Design Documents <https://cwiki.apache.org/confluence/display/FLINK/Design+Documents>
- Old Planning/Design Documents <https://cwiki.apache.org/confluence/pages/viewpage.action?pageId=65146055>

- 容错和高性能如何兼得：Flink创始人谈流计算核心架构掩护和现状
