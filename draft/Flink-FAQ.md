

http://flink.apache.org/gettinghelp.html

* 数据在两个算子传输的方式有哪几种？
one-to-one (or forwarding)
Redistributing： hashshuffle(), broadcast(), or rebalance() 

* 算子subtask链接在一起形成task
在分布式环境中执行，Flink会将operator的subtask链接在一起形成task，每个task在一个线程中执行。将operators链成task是一个有效的优化：它减少了线程到线程切换和缓冲的开销，并且在减少延迟的同时增加了总体吞吐量。 链接行为可以配置;
BTW, 在某些场景下可能“任务链”优化反而起反作用，比如，上下两个算子内部都有很耗时的io请求。

* Flink中Job Managers, Task Managers, Clients分别是干啥的？

    + The JobManagers (also called masters) coordinate the distributed execution. They schedule tasks, coordinate checkpoints, coordinate recovery on failures, etc.
    
        There is always at least one Job Manager. A high-availability setup will have multiple JobManagers, one of which one is always the leader, and the others are standby.

    + The TaskManagers (also called workers) execute the tasks (or more specifically, the subtasks) of a dataflow, and buffer and exchange the data streams.
        
        There must always be at least one TaskManager.

    + The JobManagers and TaskManagers can be started in various ways: directly on the machines as a standalone cluster, in containers, or managed by resource frameworks like YARN or Mesos. TaskManagers connect to JobManagers, announcing themselves as available, and are assigned work.

    + The client is not part of the runtime and program execution, but is used to prepare and send a dataflow to the JobManager. After that, the client can disconnect, or stay connected to receive progress reports. 

* Task Slots and Resources是个什么鬼?
一个TaskManager（worker)就是一个JVM进程，在独立的线程里面执行一个或多个subtask。为了控制worker接受task的数量，将worker抽象成task slots（任务槽集合），每个task slot（任务槽位）代表TaskManager的固定资源子集。eg. 具有三个slot的TaskManager会将其管理内存的1/3用于每个slot。资源slot化意味着subtask将不需要跟来自其他jobs的subtask竞争被管理的内存，而是具有拥有一定大小的托管内存。
需要注意的是，这里没有CPU隔离;
目前slot仅仅用来隔离task的被托管内存。
拥有多个slot意味着更多的subtask共享同一个JVM。
而在同一个JVM中的task将共享TCP连接（基于多路复用）和心跳消息。他们还可以共享数据集和数据结构，从而减少每个task的开销。 
一个slot可以运行job的一个pipeline.
It is easier to get better resource utilization. Without slot sharing, the non-intensive source/map() subtasks would block as many resources as the resource intensive window subtasks. With slot sharing, increasing the base parallelism in our example from two to six yields full utilization of the slotted resources, while making sure that the heavy subtasks are fairly distributed among the TaskManagers.
As a rule-of-thumb, a good default number of task slots would be the number of CPU cores. With hyper-threading, each slot then takes 2 or more hardware thread contexts.

* 什么是保存点？
用户手动触发的checkpoint，

* 时间窗口是如何实现的？

* 计数窗口是如何实现的？

* 基于事件时间的窗口是如何实现的？



* Flink是个啥？
Flink是一个流处理与批处理结合的实时计算引擎。
* flink的最优应用场景是什么？	
高吞吐，低延迟，复杂的流计算场景
	 
* flink在公司内部的使用情况是什么样的？	 	 
使用flink后，相较于storm能来哪些好处？	
更大的灵活性，更高的开发效率，流和批共享一套代码和更高的性能
flink同时支持batch+streaming和iteration，而且算子和语义更丰富，更灵活，并内置state支持
storm的API更底层一些，没有高层的DataStream API和SQL层。也没有exactly once语义的支持。另外storm只支持流处理，不支持批处理

* flink实时计算性能比spark高多少？	
flink实时处理的latency可以达到ms级别，在大规模稳定的高性能场景下，可以维持在几十ms级别。spark streaming在大规模下，几乎很难实现亚秒级

 
* 在流转换成表的计算中，多次的撤销之前的计算结果会有什么隐患/问题？	撤销需要算子的支持才能work，有些算子要实现撤销的语义是成本比较高的	 
* Streaming SQL	
    Flink流处理和批处理都支持SQL和TableAPI	 
* 双流怎样join?	 	 
	 
* flink对状态的copy（从本机到HDFS）是异步的，怎样保证exactly once了？	
state采取了多版本控制(MVCC)的方式进行并发控制。在barrier到达时，Blink对state进行了快照。尽管在copy时，state仍然会被更新，但是快照所看到的数据是不会改变的。由于所有operator在同一个barrier下对state进行了快照，所以得到的状态是一致的，都是处理完同一批输入之后的状态。以这个状态恢复的计算自然也就是exactly once的了。
snapshot是同步，非常快，持久化是异步，不影响状态的一致性。

* checkpoint容错机制	
flink采取了checkpoint + replay的方式来从错误中恢复。如果有一个节点挂了，那么就会将这个节点和这个节点依赖的所有节点的状态都恢复到上一次成功的checkpoint上，并通过回放从上次checkpoint开始的输入来重新计算。

blink也正对这个方式进行优化。如果在从节点到输入的这条路径中，如果已经有结果被持久化了，那么直接利用这个持久化结果即可，产生这个持久化结果的节点和这个节点之前的节点就不需要进行状态回放了。

 
* FLINK是如何做到heap内存管理？	
如果是batch job需要sort这种排序需求的，内存排序会使用框架的内存管理，这样效率更高，不会OOM, 网络shuffle过程上下游之间的数据传输buffer也是基于内存管理实现的，这样一是保证内存可控，同时也实现了流控反压机制，这里的内存管理可以使用heap也可以使用non-heap都是可配置的	 

### blink添加的监控指标具体有哪些？	

进程级别的资源使用情况cpu load，内存使用情况（heap, non-heap, rss）,gc stat
task相关的包括输入输出队列的数据堆积情况、tps、消息的处理延时和transfer延时，以及rockdb的相关性能指标
checkpoint的不同阶段的耗时情况

 
* 做checkpoint的时候使用barrier如何保证不影响window的语义？	
window operator收到 barrier的时候会把数据、 timer（如果有的话）等持久化，不会影响window语义的。

可以理解为，window中未计算的数据和已计算的状态都保存了，即使恢复，也能保证是原来的window？

 
* flink是否有类似shuffle的过程	
 flink的数据是通过网络+内存队列流式shuffle的，出现failover后source会从上次checkpoint重新拉数据，其他节点也会从上次checkpoint恢复

flink采取了checkpoint + replay的方式来从错误中恢复。如果有一个节点挂了，那么就会将这个节点和这个节点依赖的所有节点的状态都恢复到上一次成功的checkpoint上，并通过回放从上次checkpoint开始的输入来重新计算。

blink也正对这个方式进行优化。如果在从节点到输入的这条路径中，如果已经有结果被持久化了，那么直接利用这个持久化结果即可，产生这个持久化结果的节点和这个节点之前的节点就不需要进行状态回放了。

shuffle过程类似hadoop和map-reduce和spark，基于netty的网络数据传输，基于checkpoint的failover机制保证了下游失败后上游会重新回放checkpoint最后一次成功后的未处理数据，数据重新transfer到下游。


----------------------------
[实时计算 Flink SQL 核心功能解密](https://yq.aliyun.com/articles/457438?spm=a2c4e.11153959.teamhomeleft.30.4e2c723bU4lCxX)

[Flink SQL 功能解密系列 —— 流式 TopN 挑战与实现](https://yq.aliyun.com/articles/457445?spm=a2c4e.11153959.teamhomeleft.20.4e2c723bU4lCxX)

[Flink SQL 功能解密系列 —— 解决热点问题的大杀器MiniBatch](https://yq.aliyun.com/articles/448853?spm=5176.10695662.1996646101.searchclickresult.21235006scWVtI)

[蒋晓伟：Blink计算引擎](https://yq.aliyun.com/articles/57828?spm=5176.10695662.1996646101.searchclickresult.bf495006B5X8q3)

[阿里云-流计算](https://help.aliyun.com/product/45029.html?spm=a2c4g.11186623.3.1.2GJbEE)

TOP_N
<https://help.aliyun.com/document_detail/62508.html?spm=5176.10695662.1996646101.searchclickresult.630d5006l18vVr>

topk  Flink Stream SQL order by 
https://stackoverflow.com/questions/49191326/flink-stream-sql-order-by



How to handle large lookup tables that update rarely in Apache Flink
<https://stackoverflow.com/questions/37448847/how-to-handle-large-lookup-tables-that-update-rarely-in-apache-flink>




http://flink.apache.org/faq.html



[Flink SQL 功能解密系列 —— 阿里云流计算/Blink支持的connectors](https://yq.aliyun.com/articles/457396?spm=a2c4e.11154873.tagmain.38.1515788dG4FJRP)

云栖社区 - #flink#
https://yq.aliyun.com/tags/type_blog-tagid_13652-page_3?spm=a2c4e.11154873.tagmain.205.1515788dG4FJRP

阿里云流计算中维表join VS 流join

https://yq.aliyun.com/articles/540778?spm=a2c4e.11154873.tagmain.6.1515788dG4FJRP

### Side Outputs & join & connect
https://ci.apache.org/projects/flink/flink-docs-release-1.3/dev/stream/side_output.html

http://www.jianshu.com/p/0350cd9a38b5

[Flink流计算编程--在双流中体会joinedStream与coGroupedStream](http://blog.csdn.net/lmalds/article/details/51743038)

## [流计算技术实战 - 超大维表问题](http://www.cnblogs.com/fxjwind/p/7771338.html)
