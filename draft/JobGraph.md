## 前言
本小节主要介绍Flink是如何将StreamGraph转换成JobGraph的。该转换的关键在于，将多个符合条件的StreamNode节点chain在一起作为一个JobVertex节点，这样可以减少数据在节点之间流动所需要的序列化/反序列化/传输消耗。以 WordCount 为例，转换图如下图所示：
![image.png](https://upload-images.jianshu.io/upload_images/11601528-74b7e87618b74848.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
StreamGraph 和 JobGraph 都是在 Client 端生成的，也就是说我们可以在 IDE 中通过断点调试观察 StreamGraph 和 JobGraph 的生成过程。

JobGraph 的相关数据结构主要在 `org.apache.flink.runtime.jobgraph` 包中。构造 JobGraph 的代码主要集中在 `StreamingJobGraphGenerator` 类中，入口函数是 `StreamingJobGraphGenerator.createJobGraph()`。我们首先来看下`StreamingJobGraphGenerator`的核心源码：

```java
public class StreamingJobGraphGenerator {
  private StreamGraph streamGraph;
  private JobGraph jobGraph;
  // id -> JobVertex
  private Map<Integer, JobVertex> jobVertices;
  // 已经构建的JobVertex的id集合
  private Collection<Integer> builtVertices;
  // 物理边集合（排除了chain内部的边）, 按创建顺序排序
  private List<StreamEdge> physicalEdgesInOrder;
  // 保存chain信息，部署时用来构建 OperatorChain，startNodeId -> (currentNodeId -> StreamConfig)
  private Map<Integer, Map<Integer, StreamConfig>> chainedConfigs;
  // 所有节点的配置信息，id -> StreamConfig
  private Map<Integer, StreamConfig> vertexConfigs;
  // 保存每个节点的名字，id -> chainedName
  private Map<Integer, String> chainedNames;
  
  // 构造函数，入参只有 StreamGraph
  public StreamingJobGraphGenerator(StreamGraph streamGraph) {
    this.streamGraph = streamGraph;
  }
  // 根据 StreamGraph，生成 JobGraph
  public JobGraph createJobGraph() {
    jobGraph = new JobGraph(streamGraph.getJobName());
  
    // streaming 模式下，调度模式是所有节点（vertices）一起启动
    jobGraph.setScheduleMode(ScheduleMode.ALL);
    // 广度优先遍历 StreamGraph 并且为每个SteamNode生成hash id，
    // 保证如果提交的拓扑没有改变，则每次生成的hash都是一样的
    Map<Integer, byte[]> hashes = traverseStreamGraphAndGenerateHashes();
  
    // 最重要的函数，生成JobVertex，JobEdge等，并尽可能地将多个节点chain在一起
setChaining(hashes, legacyHashes, chainedOperatorHashes);
  
    // 将每个JobVertex的入边集合也序列化到该JobVertex的StreamConfig中
    // (出边集合已经在setChaining的时候写入了)
    setPhysicalEdges();
  
    // 根据group name，为每个 JobVertex 指定所属的 SlotSharingGroup 
    // 以及针对 Iteration的头尾设置  CoLocationGroup
    setSlotSharing();
    // 配置checkpoint
    configureCheckpointing();
    // 配置重启策略（不重启，还是固定延迟重启）
    configureRestartStrategy();
  
		// add registered cache file into job configuration
		for (Tuple2<String, DistributedCache.DistributedCacheEntry> e : streamGraph.getEnvironment().getCachedFiles()) {
			DistributedCache.writeFileInfoToConfig(e.f0, e.f1, jobGraph.getJobConfiguration());
		}
    try {
      // 将 StreamGraph 的 ExecutionConfig 序列化到 JobGraph 的配置中
      InstantiationUtil.writeObjectToConfig(this.streamGraph.getExecutionConfig(), this.jobGraph.getJobConfiguration(), ExecutionConfig.CONFIG_KEY);
    } catch (IOException e) {
      throw new RuntimeException("Config object could not be written to Job Configuration: ", e);
    }
    
    return jobGraph;
  }
  ...
}
```

StreamingJobGraphGenerator的成员变量都是为了辅助生成最终的JobGraph。createJobGraph()函数的逻辑也很清晰，首先为所有节点生成一个唯一的hash id，如果节点在多次提交中没有改变（包括并发度、上下游等），那么这个id就不会改变，这主要用于故障恢复。这里我们不能用 StreamNode.id来代替，因为这是一个从1开始的静态计数变量，同样的Job可能会得到不一样的id，如下代码示例的两个job是完全一样的，但是source的id却不一样了。然后就是最关键的chaining处理，和生成JobVetex、JobEdge等。之后就是写入各种配置相关的信息。

下面具体分析下关键函数 setChaining 的实现：

```java
// 从source开始建立 node chains
private void setChaining(Map<Integer, byte[]> hashes, List<Map<Integer, byte[]>> legacyHashes, Map<Integer, List<Tuple2<byte[], byte[]>>> chainedOperatorHashes) {
	for (Integer sourceNodeId : streamGraph.getSourceIDs()) {
		createChain(sourceNodeId, sourceNodeId, hashes, legacyHashes, 0, chainedOperatorHashes);
	}
}

// 构建node chains，返回当前节点的物理出边
// startNodeId != currentNodeId 时,说明currentNode是chain中的子节点
private List<StreamEdge> createChain(
			Integer startNodeId,
			Integer currentNodeId,
			Map<Integer, byte[]> hashes,
			List<Map<Integer, byte[]>> legacyHashes,
			int chainIndex,
			Map<Integer, List<Tuple2<byte[], byte[]>>> chainedOperatorHashes) {

		if (!builtVertices.contains(startNodeId)) {
// 过渡用的出边集合, 用来生成最终的 JobEdge, 注意不包括 chain 内部的边
			List<StreamEdge> transitiveOutEdges = new ArrayList<StreamEdge>();

			List<StreamEdge> chainableOutputs = new ArrayList<StreamEdge>();
			List<StreamEdge> nonChainableOutputs = new ArrayList<StreamEdge>();
// 将当前节点的出边分成 chainable 和 nonChainable 两类
			for (StreamEdge outEdge : streamGraph.getStreamNode(currentNodeId).getOutEdges()) {
				if (isChainable(outEdge, streamGraph)) {
					chainableOutputs.add(outEdge);
				} else {
					nonChainableOutputs.add(outEdge);
				}
			}
//==> 递归调用
			for (StreamEdge chainable : chainableOutputs) {
				transitiveOutEdges.addAll(
						createChain(startNodeId, chainable.getTargetId(), hashes, legacyHashes, chainIndex + 1, chainedOperatorHashes));
			}

			for (StreamEdge nonChainable : nonChainableOutputs) {
				transitiveOutEdges.add(nonChainable);
				createChain(nonChainable.getTargetId(), nonChainable.getTargetId(), hashes, legacyHashes, 0, chainedOperatorHashes);
			}

			List<Tuple2<byte[], byte[]>> operatorHashes =
				chainedOperatorHashes.computeIfAbsent(startNodeId, k -> new ArrayList<>());

			byte[] primaryHashBytes = hashes.get(currentNodeId);

			for (Map<Integer, byte[]> legacyHash : legacyHashes) {
				operatorHashes.add(new Tuple2<>(primaryHashBytes, legacyHash.get(currentNodeId)));
			}
// 生成当前节点的显示名，如："Keyed Aggregation -> Sink: Unnamed"
			chainedNames.put(currentNodeId, createChainedName(currentNodeId, chainableOutputs));
			chainedMinResources.put(currentNodeId, createChainedMinResources(currentNodeId, chainableOutputs));
			chainedPreferredResources.put(currentNodeId, createChainedPreferredResources(currentNodeId, chainableOutputs));
// 如果当前节点是起始节点, 则直接创建 JobVertex 并返回 StreamConfig, 否则先创建一个空的 StreamConfig
    // createJobVertex 函数就是根据 StreamNode 创建对应的 JobVertex, 并返回了空的 StreamConfig

			StreamConfig config = currentNodeId.equals(startNodeId)
					? createJobVertex(startNodeId, hashes, legacyHashes, chainedOperatorHashes)
					: new StreamConfig(new Configuration());
// 设置 JobVertex 的 StreamConfig, 基本上是序列化 StreamNode 中的配置到 StreamConfig 中.
    // 其中包括 序列化器, StreamOperator, Checkpoint 等相关配置
			setVertexConfig(currentNodeId, config, chainableOutputs, nonChainableOutputs);

			if (currentNodeId.equals(startNodeId)) {
      // 如果是chain的起始节点。（不是chain中的节点，也会被标记成 chain start）
				config.setChainStart();
				config.setChainIndex(0);
				config.setOperatorName(streamGraph.getStreamNode(currentNodeId).getOperatorName());
				// 我们也会把物理出边写入配置, 部署时会用到
				config.setOutEdgesInOrder(transitiveOutEdges);
				config.setOutEdges(streamGraph.getStreamNode(currentNodeId).getOutEdges());
// 将当前节点(headOfChain)与所有出边相连
				for (StreamEdge edge : transitiveOutEdges) {
					// 通过StreamEdge构建出JobEdge，创建IntermediateDataSet，用来将JobVertex和JobEdge相连
					connect(startNodeId, edge);
				}
// 将chain中所有子节点的StreamConfig写入到 headOfChain 节点的 CHAINED_TASK_CONFIG 配置中
				config.setTransitiveChainedTaskConfigs(chainedConfigs.get(startNodeId));

			} else {
 // 如果是 chain 中的子节点
				Map<Integer, StreamConfig> chainedConfs = chainedConfigs.get(startNodeId);

				if (chainedConfs == null) {
					chainedConfigs.put(startNodeId, new HashMap<Integer, StreamConfig>());
				}
				config.setChainIndex(chainIndex);
				StreamNode node = streamGraph.getStreamNode(currentNodeId);
				config.setOperatorName(node.getOperatorName());
				// 将当前节点的StreamConfig添加到该chain的config集合中
				chainedConfigs.get(startNodeId).put(currentNodeId, config);
			}

			config.setOperatorID(new OperatorID(primaryHashBytes));

			if (chainableOutputs.isEmpty()) {
				config.setChainEnd();
			}
			// 返回连往chain外部的出边集合
			return transitiveOutEdges;

		} else {
			return new ArrayList<>();
		}
	}
```


每个 JobVertex 都会对应一个可序列化的 StreamConfig, 用来发送给 JobManager 和 TaskManager。最后在 TaskManager 中起 Task 时,需要从这里面反序列化出所需要的配置信息, 其中就包括了含有用户代码的StreamOperator。

setChaining会对source调用createChain方法，该方法会递归调用下游节点，从而构建出node chains。createChain会分析当前节点的出边，根据Operator Chains中的chainable条件，将出边分成chainalbe和noChainable两类，并分别递归调用自身方法。之后会将StreamNode中的配置信息序列化到StreamConfig中。如果当前不是chain中的子节点，则会构建 JobVertex 和 JobEdge相连。如果是chain中的子节点，则会将StreamConfig添加到该chain的config集合中。一个node chains，除了 headOfChain node会生成对应的 JobVertex，其余的nodes都是以序列化的形式写入到StreamConfig中，并保存到headOfChain的 CHAINED_TASK_CONFIG 配置项中。直到部署时，才会取出并生成对应的ChainOperators，具体过程请见理解 Operator Chains。

## SocketWindowWordCount示例
同样地，以`SocketWindowWordCount`为例，我们分析下其创建过程：
![image.png](https://upload-images.jianshu.io/upload_images/11601528-6e71c1c9191a5f2f.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)


如上图所示，我们先给`4`个`StreamNode`节点进行编号，`Source`用`1`表示，`Flat Map`用`2`表示，`Trigger Window`用`3`表示，`Sink`用`4`表示；相应地，`3`条`StreamEdge`则分别用`1->2`，`2->3`，`3->4`表示。

递归调用过程如下：

*   递归始于`Source`，调用`createChain(1, 1)`，当前节点`1`的出边为`1->2`，不可`chain`，将边`1->2`直接加入`transitiveOutEdges`；
*   然后递归调用`createChain(2, 2)`，当前节点`2`的出边为`2->3`，同样的，不可`chain`，将边`2->3`直接加入`transitiveOutEdges`；
*   继续递归调用`createChain(3, 3)`，当前节点`3`的出边为`3->4`，要注意了，可`chain`，等着将下游`createChain()`的返回值加入`transitiveOutEdges`；
*   此处递归调用`createChain(3, 4)`，当前节点`4`没有出边，递归终止。

递归结束条件：

*   当前节点不再有出边集合，即`streamGraph.getStreamNode(currentId).getOutEdges()`为空
*   当前节点已经转换完成，即`builtVertices.contains(startNodeId)`为`false`

递归调用过程中各种操作以及变量情况一览表如下：

| `creatChain()` | `getOutEdges()` | `chainAble` | `nonChainAble` | `transitiveOutEdges` | `JobVertex` | `connect()` |
| --- | --- | --- | --- | --- | --- | --- |
| `(1, 1)` | `1->2` | 无 | `1->2` | `1->2` | `JobVertex` | `Y` |
| `(2, 2)` | `2->3` | 无 | `2->3` | `2->3` | `JobVertex` | `Y` |
| `(3, 3)` | `3->4` | `3->4` | 无 | 无 | `JobVertex` | `Y` |
| `(3, 4)` | 无 | 无 | 无 | 无 | `StreamConfig` | `N` |

**关键方法分析：**

`isChainable()`，用来判断`StreamNode chain`，一共有`9`个条件：

*   下游节点的入边为`1`
*   `StreamEdge`的下游节点对应的算子不为`null`
*   `StreamEdge`的上游节点对应的算子不为`null`
*   `StreamEdge`的上下游节点拥有相同的`slotSharingGroup`，默认都是`default`
*   下游算子的连接策略为`ALWAYS`
*   上游算子的连接策略为`ALWAYS`或者`HEAD`
*   `StreamEdge`的分区类型为`ForwardPartitioner`
*   上下游节点的并行度一致
*   当前`StreamGraph`允许做`chain`

`createJobVertex()`，用来创建`JobVertex`节点，并返回`StreamConfig`。

`createJobVertex()`传入的参数为`StreamNode`。首先会通过`new JobVertex()`构造出`JobVertex`节点，然后通过`JobVertex.setInvokableClass(streamNode.getJobVertexClass())`设置运行时执行类，再通过`jobVertex.setParallelism(parallelism)`设置并行度，最后返回`StreamConfig`。

`connect()`，用来创建`JobEdge`和`IntermediateDataSet`，连接上下游`JobVertex`节点。

遍历`transitiveOutEdges`，并将每一条`StreamEdge`边作为参数传入`connect( )`函数中。接下来就是依据`StreamEdge`得到上下游`JobVertex`节点；然后，通过`StreamEdge.getPartitioner()`方法得到`StreamPartitioner`属性，对于`ForwardPartitioner`和`RescalePartitioner`两种分区方式建立`DistributionPattern.POINTWISE`类型的`JobEdge`和`IntermediateDataSet`，而其他的分区方式则是`DistributionPattern.ALL_TO_ALL`类型。至此已经建立好上下游`JobVertex`节点间的联系。

#### 一句话总结：
首先，通过streamGraph.getSourceIDs()拿到source节点集合，紧接着依次从source节点开始遍历，判断StreamNode Chain，递归创建JobVertex，所以，其真正的处理顺序其实是从sink开始的。然后通过connect()遍历当前节点的物理出边transitiveOutEdges集合，创建JobEdge，建立当前节点与下游节点的联系，即JobVertex与IntermediateDataSet之间。

## 总结
本文主要对 Flink 中将 StreamGraph 转变成 JobGraph 的核心源码进行了分析。思想还是很简单的，StreamNode 转成 JobVertex，StreamEdge 转成 JobEdge，JobEdge 和 JobVertex 之间创建 IntermediateDataSet 来连接。关键点在于将多个 SteamNode chain 成一个 JobVertex的过程，这部分源码比较绕，有兴趣的同学可以结合源码单步调试分析。下一章将会介绍 JobGraph 提交到 JobManager 后是如何转换成分布式化的 ExecutionGraph 的。

## 参考文献
+ [Flink 原理与实现：如何生成 JobGraph](http://wuchong.me/blog/2016/05/10/flink-internals-how-to-build-jobgraph/)


