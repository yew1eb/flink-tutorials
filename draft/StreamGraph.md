## 概念

+ StreamGraph: Class representing the streaming topology. It contains all the information necessary to build the jobgraph for the execution.


#### DataStream
DataStream内部包含一个`StreamExecutionEnvironment`和`StreamTransformation`, 通过transformation可以把一个DataStream转换成另一个DataStream. transformatio内部会包装一个StreamOperator，StreamOperator是运行时的具体实现，会决定UDF(User-Defined Funtion)的调用方式。

#### eg. DataStream.map

```java
	public <R> SingleOutputStreamOperator<R> map(MapFunction<T, R> mapper) {
// 通过java reflection抽出mapper的返回值类型
		TypeInformation<R> outType = TypeExtractor.getMapReturnTypes(clean(mapper), getType(),
				Utils.getCallLocationName(), true);
 // 返回一个新的DataStream，SteramMap 为 StreamOperator 的实现类
		return transform("Map", outType, new StreamMap<>(clean(mapper)));
	}

	public <R> SingleOutputStreamOperator<R> transform(String operatorName, TypeInformation<R> outTypeInfo, OneInputStreamOperator<T, R> operator) {

		// read the output type of the input Transform to coax out errors about MissingTypeInfo
		transformation.getOutputType();
 // 新的transformation会连接上当前DataStream中的transformation
		OneInputTransformation<T, R> resultTransform = new OneInputTransformation<>(
				this.transformation,
				operatorName,
				operator,
				outTypeInfo,
				environment.getParallelism());

		@SuppressWarnings({ "unchecked", "rawtypes" })
		SingleOutputStreamOperator<R> returnStream = new SingleOutputStreamOperator(environment, resultTransform);
    
                 // 所有的transformation都会存到 env 中，调用execute时遍历该list生成StreamGraph
		getExecutionEnvironment().addOperator(resultTransform);

		return returnStream;
	}
```
从上方代码可以了解到，map转换将用户自定义的函数MapFunction包装到StreamMap这个Operator中，再将StreamMap包装到OneInputTransformation，最后该transformation存到env中，当调用env.execute时，遍历其中的transformation集合构造出StreamGraph。其分层实现如下图所示：

![image.png](https://upload-images.jianshu.io/upload_images/11601528-530fdd16ba562e9e.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

#### StreamTransformation
![image.png](https://upload-images.jianshu.io/upload_images/11601528-97cfb2c2074d5d16.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

A `StreamTransformation` represents the operation that creates a [`DataStream`](https://ci.apache.org/projects/flink/flink-docs-release-1.4/api/java/org/apache/flink/streaming/api/datastream/DataStream.html "class in org.apache.flink.streaming.api.datastream"). Every [`DataStream`](https://ci.apache.org/projects/flink/flink-docs-release-1.4/api/java/org/apache/flink/streaming/api/datastream/DataStream.html "class in org.apache.flink.streaming.api.datastream") has an underlying `StreamTransformation` that is the origin of said DataStream.

API operations such as [`DataStream.map(org.apache.flink.api.common.functions.MapFunction<T, R>)`](https://ci.apache.org/projects/flink/flink-docs-release-1.4/api/java/org/apache/flink/streaming/api/datastream/DataStream.html#map-org.apache.flink.api.common.functions.MapFunction-) create a tree of `StreamTransformation`s underneath. When the stream program is to be executed this graph is translated to a [`StreamGraph`](https://ci.apache.org/projects/flink/flink-docs-release-1.4/api/java/org/apache/flink/streaming/api/graph/StreamGraph.html "class in org.apache.flink.streaming.api.graph") using [`StreamGraphGenerator`](https://ci.apache.org/projects/flink/flink-docs-release-1.4/api/java/org/apache/flink/streaming/api/graph/StreamGraphGenerator.html "class in org.apache.flink.streaming.api.graph").

A `StreamTransformation` does not necessarily correspond to a physical operation at runtime. Some operations are only logical concepts. Examples of this are union, split/select data stream, partitioning.

The following graph of `StreamTransformations`:

```

   Source              Source
      +                   +
      |                   |
      v                   v
  Rebalance          HashPartition
      +                   +
      |                   |
      |                   |
      +------>Union<------+
                +
                |
                v
              Split
                +
                |
                v
              Select
                +
                v
               Map
                +
                |
                v
              Sink

```

Would result in this graph of operations at runtime:

```

  Source              Source
    +                   +
    |                   |
    |                   |
    +------->Map<-------+
              +
              |
              v
             Sink

```

The information about partitioning, union, split/select end up being encoded in the edges that connect the sources to the map operation.


#### StreamOperator
DataStream 上的每一个 Transformation 都对应了一个 StreamOperator，StreamOperator是运行时的具体实现，会决定UDF(User-Defined Funtion)的调用方式。下图所示为 StreamOperator 的类图:
![image.png](https://upload-images.jianshu.io/upload_images/11601528-93bcae23e760830d.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
可以发现，所有实现类都继承了AbstractStreamOperator。另外除了 project 操作，其他所有可以执行UDF代码的实现类都继承自AbstractUdfStreamOperator，该类是封装了UDF的StreamOperator。UDF就是实现了Function接口的类，如MapFunction,FilterFunction。


## 生成 StreamGraph 
StreamGraph的相关代码主要在org.apache.flink.streaming.api.graph包中。主要逻辑集中在StreamGraphGenerator类，入口函数是`StreamGraphGenerator.generate(env, transformations)`，该函数由触发程序执行的StreamExecutionEnvironment.execute()调用到。
#### StreamGraphGenerator
StreamGraphGenerator通过`StreamTransformation`列表生成一个StreamGraph.
 This traverses the tree of {@code StreamTransformations} starting from the sinks. At each transformation we recursively transform the inputs, then create a node in the {@code StreamGraph} and add edges from the input Nodes to our newly created node. The transformation methods return the IDs of the nodes in the StreamGraph that represent the input transformation. Several IDs can be returned to be able to deal with feedback transformations and unions.
Partitioning, split/select and union don't create actual nodes in the {@code StreamGraph}. For these, we create a virtual node in the {@code StreamGraph} that holds the specific property, i.e.
partitioning, selector and so on. When an edge is created from a virtual node to a downstream node the {@code StreamGraph} resolved the id of the original node and creates an edge in the graph with the desired property. For example, if you have this graph:
 <pre>
   Map-1 -&gt; HashPartition-2 -&gt; Map-3
 </pre>

<p>where the numbers represent transformation IDs. We first recurse all the way down. {@code Map-1} is transformed, i.e. we create a {@code StreamNode} with ID 1. Then we transform the {@code HashPartition}, for this, we create virtual node of ID 4 that holds the property {@code HashPartition}. This transformation returns the ID 4. Then we transform the {@code Map-3}. We add the edge {@code 4 -> 3}. The {@code StreamGraph} resolved the actual node with ID 1 and creates and edge {@code 1 -> 3} with the property HashPartition.

#### StreamGraphGenerator.transform
最终都会调用 transformXXX 来对具体的StreamTransformation进行转换。我们可以看下transformOnInputTransform(transform)的实现：

```java
private <IN, OUT> Collection<Integer> transformOneInputTransform(OneInputTransformation<IN, OUT> transform) {
// 递归对该transform的直接上游transform进行转换，获得直接上游id集合
		Collection<Integer> inputIds = transform(transform.getInput());

		// the recursive call might have already transformed this
		if (alreadyTransformed.containsKey(transform)) {
			return alreadyTransformed.get(transform);
		}

		String slotSharingGroup = determineSlotSharingGroup(transform.getSlotSharingGroup(), inputIds);
// 添加 StreamNode
		streamGraph.addOperator(transform.getId(),
				slotSharingGroup,
				transform.getOperator(),
				transform.getInputType(),
				transform.getOutputType(),
				transform.getName());

		if (transform.getStateKeySelector() != null) {
			TypeSerializer<?> keySerializer = transform.getStateKeyType().createSerializer(env.getConfig());
			streamGraph.setOneInputStateKey(transform.getId(), transform.getStateKeySelector(), keySerializer);
		}

		streamGraph.setParallelism(transform.getId(), transform.getParallelism());
		streamGraph.setMaxParallelism(transform.getId(), transform.getMaxParallelism());
// 添加 StreamEdge
		for (Integer inputId: inputIds) {
			streamGraph.addEdge(inputId, transform.getId(), 0);
		}

		return Collections.singleton(transform.getId());
	}
```
该函数首先会对该transform的上游transform进行递归转换，确保上游的都已经完成了转化。然后通过transform构造出StreamNode，最后与上游的transform进行连接，构造出StreamNode。
最后再来看下对逻辑转换（partition、union等）的处理，如下是transformPartition函数的源码：

```java
	private <T> Collection<Integer> transformPartition(PartitionTransformation<T> partition) {
		StreamTransformation<T> input = partition.getInput();
		List<Integer> resultIds = new ArrayList<>();
// 直接上游的id
		Collection<Integer> transformedIds = transform(input);
		for (Integer transformedId: transformedIds) {
// 生成一个新的虚拟id
			int virtualId = StreamTransformation.getNewNodeId();
// 添加一个虚拟分区节点，不会生成 StreamNode
			streamGraph.addVirtualPartitionNode(transformedId, virtualId, partition.getPartitioner());
			resultIds.add(virtualId);
		}

		return resultIds;
	}
```
对partition的转换没有生成具体的StreamNode和StreamEdge，而是添加一个虚节点。当partition的下游transform（如map）添加edge时（调用StreamGraph.addEdge），会把partition信息写入到edge中。如StreamGraph.addEdgeInternal所示：

```java
	public void addEdge(Integer upStreamVertexID, Integer downStreamVertexID, int typeNumber) {
		addEdgeInternal(upStreamVertexID,
				downStreamVertexID,
				typeNumber,
				null,
				new ArrayList<String>(),
				null);

	}

	private void addEdgeInternal(Integer upStreamVertexID,
			Integer downStreamVertexID,
			int typeNumber,
			StreamPartitioner<?> partitioner,
			List<String> outputNames,
			OutputTag outputTag) {

		if (virtualSideOutputNodes.containsKey(upStreamVertexID)) {
			int virtualId = upStreamVertexID;
			upStreamVertexID = virtualSideOutputNodes.get(virtualId).f0;
			if (outputTag == null) {
				outputTag = virtualSideOutputNodes.get(virtualId).f1;
			}
			addEdgeInternal(upStreamVertexID, downStreamVertexID, typeNumber, partitioner, null, outputTag);
 // 当上游是select时，递归调用，并传入select信息
		} else if (virtualSelectNodes.containsKey(upStreamVertexID)) {
			int virtualId = upStreamVertexID;
// select上游的节点id
			upStreamVertexID = virtualSelectNodes.get(virtualId).f0;
			if (outputNames.isEmpty()) {
				// selections that happen downstream override earlier selections
				outputNames = virtualSelectNodes.get(virtualId).f1;
			}
			addEdgeInternal(upStreamVertexID, downStreamVertexID, typeNumber, partitioner, outputNames, outputTag);
		} else if (virtualPartitionNodes.containsKey(upStreamVertexID)) {
			int virtualId = upStreamVertexID;
			upStreamVertexID = virtualPartitionNodes.get(virtualId).f0;
			if (partitioner == null) {
				partitioner = virtualPartitionNodes.get(virtualId).f1;
			}
			addEdgeInternal(upStreamVertexID, downStreamVertexID, typeNumber, partitioner, outputNames, outputTag);
		} else {
// 真正构建StreamEdge
			StreamNode upstreamNode = getStreamNode(upStreamVertexID);
			StreamNode downstreamNode = getStreamNode(downStreamVertexID);

			// If no partitioner was specified and the parallelism of upstream and downstream
			// operator matches use forward partitioning, use rebalance otherwise.
			if (partitioner == null && upstreamNode.getParallelism() == downstreamNode.getParallelism()) {
				partitioner = new ForwardPartitioner<Object>();
			} else if (partitioner == null) {
				partitioner = new RebalancePartitioner<Object>();
			}
// 安全检查， forward partitioner必须要上下游的并发度一致
			if (partitioner instanceof ForwardPartitioner) {
				if (upstreamNode.getParallelism() != downstreamNode.getParallelism()) {
					throw new UnsupportedOperationException("Forward partitioning does not allow " +
							"change of parallelism. Upstream operation: " + upstreamNode + " parallelism: " + upstreamNode.getParallelism() +
							", downstream operation: " + downstreamNode + " parallelism: " + downstreamNode.getParallelism() +
							" You must use another partitioning strategy, such as broadcast, rebalance, shuffle or global.");
				}
			}
// 创建 StreamEdge
			StreamEdge edge = new StreamEdge(upstreamNode, downstreamNode, typeNumber, outputNames, partitioner, outputTag);
// 将该 StreamEdge 添加到上游的输出，下游的输入
			getStreamNode(edge.getSourceId()).addOutEdge(edge);
			getStreamNode(edge.getTargetId()).addInEdge(edge);
		}
	}
```

### SocketWindowWordCount示例分析

```java
DataStream<String> text = env.socketTextStream(hostName, port);
text.flatMap(new LineSplitter()).shuffle().filter(new HelloFilter()).print();
```

首先会在env中生成一棵transformation树，用List<StreamTransformation<?>>保存。其结构图如下：
![image.png](https://upload-images.jianshu.io/upload_images/11601528-8348e434dd17ed80.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
其中符号*为input指针，指向上游的transformation，从而形成了一棵transformation树。然后，通过调用StreamGraphGenerator.generate(env, transformations)来生成StreamGraph。自底向上递归调用每一个transformation，也就是说处理顺序是Source->FlatMap->Shuffle->Filter->Sink。
![image.png](https://upload-images.jianshu.io/upload_images/11601528-6bdf100f3e044d76.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
如上图所示：

首先处理的Source，生成了Source的StreamNode。
然后处理的FlatMap，生成了FlatMap的StreamNode，并生成StreamEdge连接上游Source和FlatMap。由于上下游的并发度不一样（1:4），所以此处是Rebalance分区。
然后处理的Shuffle，由于是逻辑转换，并不会生成实际的节点。将partitioner信息暂存在virtuaPartitionNodes中。
在处理Filter时，生成了Filter的StreamNode。发现上游是shuffle，找到shuffle的上游FlatMap，创建StreamEdge与Filter相连。并把ShufflePartitioner的信息写到StreamEdge中。
最后处理Sink，创建Sink的StreamNode，并生成StreamEdge与上游Filter相连。由于上下游并发度一样（4:4），所以此处选择 Forward 分区。

#### StreamGraph结构示例：
```java
{
            "id":2,
            "type":"Flat Map",
            "pact":"Operator",
            "contents":"Flat Map",
            "parallelism":8,
            "predecessors":[
                {
                    "id":1,
                    "ship_strategy":"REBALANCE",
                    "side":"second"
                }
            ]
        },
        {
            "id":3,
            "type":"Map",
            "pact":"Operator",
            "contents":"Map",
            "parallelism":8,
            "predecessors":[
                {
                    "id":2,
                    "ship_strategy":"FORWARD",
                    "side":"second"
                }
            ]
        }
```

## 参考文献
+ [Flink 原理与实现：如何生成 StreamGraph](http://wuchong.me/blog/2016/05/04/flink-internal-how-to-build-streamgraph/)
+ [Apache Flink Client生成StreamGraph](http://vinoyang.com/2016/07/23/flink-streaming-job-generate-stream-graph/)

