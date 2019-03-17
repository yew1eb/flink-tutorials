本小节主要介绍`Flink`是如何将`JobGraph`转换成`ExecutionGraph`的。简单来说，就是并行化`JobGraph`，为调度做好准备。

##### 找突破口

`ExecutionGraph`的相关数据结构主要在`org.apache.flink.runtime.executiongraph`包中，构造`ExecutionGraph`的代码集中在`ExecutionGraphBuilder`类和`ExecutionGraph`类中，入口函数是`ExecutionGraphBuilder.buildGraph(executionGraph, jobGraph, ...)`。

##### 理关键点

*   客户端提交`JobGraph`给`JobManager`
*   构建ExecutionGraph对象
    *   将`JobGraph`进行拓扑排序，得到`sortedTopology`顶点集合
    *   将`JobVertex`封装成`ExecutionJobVertex`
    *   把`ExecutionVertex`节点通过`ExecutionEdge`连接起来

##### 构建过程

**`1、JobClient`提交`JobGraph`给`JobManager`**

一个程序的`JobGraph`真正被提交始于对`JobClient`的`submitJobAndWait()`方法的调用，而且`submitJobAndWait()`方法会触发基于`Akka`的`Actor`之间的消息通信。`JobClient`在这其中起到了“桥接”的作用，它连接了同步的方法调用和异步的消息通信。

在`submitJobAndWait()`方法中，首先会创建一个`JobClientActor`的`ActorRef`，并向其发送一个包含`JobGraph`实例的`SubmitJobAndWait`消息。该`SubmitJobAndWait`消息被`JobClientActor`接收后，调用`trySubmitJob()`方法触发真正的提交动作，即通过`jobManager.tell( )`的方式给`JobManager Actor`发送封装`JobGraph`的`SubmitJob`消息。随后，`JobManager Actor`会接收到来自`JobClientActor`的该`SubmitJob`消息，进而触发`submitJob()`方法。

由此可见，一个`JobGraph`从提交开始会经过多个对象层层递交，各个对象之间的交互关系如下图所示：

![image.png](https://upload-images.jianshu.io/upload_images/11601528-8ecacacd87186db9.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)


**`2、`构建`ExecutionGraph`对象**

入口：
`JobManager`作为`Actor`，在`handleMessage()`方法中，针对`SubmitJob`消息调用`submitJob()`方法。

```
private def submitJob(jobGraph: JobGraph, jobInfo: JobInfo, isRecovery: Boolean = false): Unit = {
    ...
    executionGraph = ExecutionGraphBuilder.buildGraph(executionGraph, jobGraph, ...)
    ...
}

```

真正的构建过程：

```
public static ExecutionGraph buildGraph(){
    ...
    //对JobGraph中的JobVertex节点进行拓扑排序，得到List<JobVertex>
    List<JobVertex> sortedTopology = jobGraph.getVerticesSortedTopologicallyFromSources();
    executionGraph.attachJobGraph(sortedTopology);  //构建ExecutionGraph的核心方法
    ...
}

```

由上可知，`attachJobGraph()`方法是构建`ExecutionGraph`图结构的核心方法。

```
public void attachJobGraph(List<JobVertex> topologiallySorted){
    ...
    for (JobVertex jobVertex : topologiallySorted) {
        ...
        // create the execution job vertex and attach it to the graph
        ExecutionJobVertex ejv = new ExecutionJobVertex(this, jobVertex, 1, rpcCallTimeout, globalModVersion, createTimestamp);
        ejv.connectToPredecessors(this.intermediateResults);
        ...
    }
    ...
}

```

下面详细分析下，`attachJobGraph()`方法主要完成的两件事情：

*   将`JobVertex`封装成`ExecutionJobVertex`
*   把节点通过`ExecutionEdge`连接

**关键方法分析：**

`new ExecutionJobVertex()`方法，用来将一个个`JobVertex`封装成`ExecutionJobVertex`，并依次创建`ExecutionVertex`、`Execution`、`IntermediateResult`、`IntermediateResultPartition`，用于丰富`ExecutionGraph`。

在`ExecutionJobVertex`的构造函数中，首先是依据对应的`JobVertex`的并发度，生成对应个数的`ExecutionVertex`。其中，一个`ExecutionVertex`代表着一个`ExecutionJobVertex`的并发子`task`。然后是将原来`JobVertex`的中间结果`IntermediateDataSet`转化为`ExecutionGraph`中的`IntermediateResult`。

类似的，`ExecutionVertex`的构造函数中，首先会创建`IntermediateResultPartition`，并通过`IntermediateResult.setPartition( )`建立`IntermediateResult`和`IntermediateResultPartition`之间的关系；然后生成`Execution`，并配置资源相关。

新创建的`ExecutionJobVertex`调用`ejv.connectToPredecessor()`方法，按照不同的分发策略连接上游，其参数为上游生成的`IntermediateResult`集合。其中，根据`JobEdge`中两种不同的`DistributionPattern`属性，分别调用`connectPointWise()`或者`connectAllToAll( )`方法，创建`ExecutionEdge`，将`ExecutionVertex`和上游的`IntermediateResultPartition`连接起来。

其中，`SocketWindowWordCount`示例中，就是采用了`connectAllToAll()`的方式建立与上游的关系。

接下来，我们详细介绍下`connectPointWise()`方法的实现，即`DistributionPattern.POINTWISE`策略，该策略用来连接当前`ExecutionVertex`与上游的`IntermediateResultPartition`。首先，获取上游`IntermediateResult`的`partition`数，用`numSources`表示，以及此`ExecutionJobVertex`的并发度，用`parallelism`表示；然后，根据其并行度的不同，分别创建`ExecutionEdge`。共分`3`种情况：

(1) 如果并发数等于`partition`数，则一对一进行连接。如下图所示：
即`numSources == parallelism`

![image.png](https://upload-images.jianshu.io/upload_images/11601528-21e276e69ce8bdaf.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)


(2) 如果并发数大于`partition`数，则一对多进行连接。如下图所示：
即`numSources < parallelism`，且`parallelism % numSources == 0`

![image.png](https://upload-images.jianshu.io/upload_images/11601528-61de9f61704f92b8.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)


即`numSources < parallelism`，且`parallelism % numSources != 0`

![image.png](https://upload-images.jianshu.io/upload_images/11601528-1a5380969629ff26.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

(3) 如果并发数小于`partition`数，则多对一进行连接。如下图所示：
即`numSources > parallelism`，且`numSources % parallelism == 0`

![image.png](https://upload-images.jianshu.io/upload_images/11601528-8e5bde1f6c3f7844.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)


即`numSources > parallelism`，且`numSources % parallelism != 0`

![image.png](https://upload-images.jianshu.io/upload_images/11601528-02b834d238e3f6f6.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)


**一句话总结：**

将`JobGraph`按照拓扑排序后得到一个`JobVertex`集合，遍历该`JobVertex`集合，即从`source`开始，将`JobVertex`封装成`ExecutionJobVertex`，并依次创建`ExecutionVertex`、`Execution`、`IntermediateResult`和`IntermediateResultPartition`。然后通过`ejv.connectToPredecessor()`方法，创建`ExecutionEdge`，建立当前节点与其上游节点之间的联系，即连接`ExecutionVertex`和`IntermediateResultPartition`。

#### 终章

构建好`ExecutionGraph`，接下来会基于`ExecutionGraph`触发`Job`的调度，申请`Slot`，真正的部署任务，这是`Task`被执行的前提：

```
if (leaderElectionService.hasLeadership) {
    log.info(s"Scheduling job $jobId ($jobName).")
    executionGraph.scheduleForExecution()  // 将生成好的ExecutionGraph进行调度
} else {
    self ! decorateMessage(RemoveJob(jobId, removeJobFromStateBackend = false))
    log.warn(s"Submitted job $jobId, but not leader. The other leader needs to recover " +
              "this. I am not scheduling the job for execution.")
}

```


## 参考文献

+ [Flink原理与实现：如何生成ExecutionGraph及物理执行图](https://yq.aliyun.com/articles/225618)
+ [Flink 物理计划生成](http://chenyuzhao.me/2017/02/06/flink%E7%89%A9%E7%90%86%E8%AE%A1%E5%88%92%E7%94%9F%E6%88%90/)