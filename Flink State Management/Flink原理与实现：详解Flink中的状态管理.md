
[Source](https://yq.aliyun.com/articles/225623?spm=a2c4e.11155435.0.0.2e5e7c3fODmBn2 "Permalink to Flink原理与实现：详解Flink中的状态管理-博客-云栖社区-阿里云")

# Flink原理与实现：详解Flink中的状态管理-博客-云栖社区-阿里云

_摘要：_ Flink原理与实现系列文章 ： Flink 原理与实现：架构和拓扑概览Flink 原理与实现：如何生成 StreamGraphFlink 原理与实现：如何生成 JobGraphFlink原理与实现：如何生成ExecutionGraph及物理执行图Flink原理与实现：Operator Chain原理 上面Flink原理与实现的文章中，有引用word count的例子，但是都没有包含状态管理。 

Flink原理与实现系列文章 ：

[Flink 原理与实现：架构和拓扑概览][1]  
[Flink 原理与实现：如何生成 StreamGraph][1]  
[Flink 原理与实现：如何生成 JobGraph][1]  
[Flink原理与实现：如何生成ExecutionGraph及物理执行图][2]  
[Flink原理与实现：Operator Chain原理][3]

上面Flink原理与实现的文章中，有引用word count的例子，但是都没有包含状态管理。也就是说，如果一个task在处理过程中挂掉了，那么它在内存中的状态都会丢失，所有的数据都需要重新计算。从容错和消息处理的语义上(at least once, exactly once)，Flink引入了state和checkpoint。

首先区分一下两个概念，state一般指一个具体的task/operator的状态。而checkpoint则表示了一个Flink Job，在一个特定时刻的一份全局状态快照，即包含了所有task/operator的状态。

Flink通过定期地做checkpoint来实现容错和恢复。

## State

### Keyed State和Operator State

Flink中包含两种基础的状态：Keyed State和Operator State。

#### Keyed State

顾名思义，就是基于KeyedStream上的状态。这个状态是跟特定的key绑定的，对KeyedStream流上的每一个key，可能都对应一个state。

#### Operator State

与Keyed State不同，Operator State跟一个特定operator的一个并发实例绑定，整个operator只对应一个state。相比较而言，在一个operator上，可能会有很多个key，从而对应多个keyed state。

举例来说，Flink中的Kafka Connector，就使用了operator state。它会在每个connector实例中，保存该实例中消费topic的所有(partition, offset)映射。

#### 原始状态和Flink托管状态 (Raw and Managed State)

Keyed State和Operator State，可以以两种形式存在：原始状态和托管状态。

托管状态是由Flink框架管理的状态，如ValueState, ListState, MapState等。

下面是Flink整个状态框架的类图，还是比较复杂的，可以先扫一眼，看到后面再回过来看：

![image.png][4]

通过框架提供的接口，我们来更新和管理状态的值。

而raw state即原始状态，由用户自行管理状态具体的数据结构，框架在做checkpoint的时候，使用byte[]来读写状态内容，对其内部数据结构一无所知。

通常在DataStream上的状态推荐使用托管的状态，当实现一个用户自定义的operator时，会使用到原始状态。

下文中所提到的状态，如果没有特殊说明，均为托管状态。

### 使用Keyed State

首先看一下Keyed State下，我们可以用哪些原子状态：

* ValueState：即类型为T的单值状态。这个状态与对应的key绑定，是最简单的状态了。它可以通过`update`方法更新状态值，通过`value()`方法获取状态值。
* ListState：即key上的状态值为一个列表。可以通过`add`方法往列表中附加值；也可以通过`get()`方法返回一个`Iterable`来遍历状态值。
* ReducingState：这种状态通过用户传入的reduceFunction，每次调用`add`方法添加值的时候，会调用reduceFunction，最后合并到一个单一的状态值。
* FoldingState：跟ReducingState有点类似，不过它的状态值类型可以与`add`方法中传入的元素类型不同（这种状态将会在Flink未来版本中被删除）。
* MapState：即状态值为一个map。用户通过`put`或`putAll`方法添加元素。

以上所有的状态类型，都有一个`clear`方法，可以清除当前key对应的状态。

需要注意的是，以上所述的State对象，仅仅用于与状态进行交互（更新、删除、清空等），而真正的状态值，有可能是存在内存、磁盘、或者其他分布式存储系统中。相当于我们只是持有了这个状态的句柄(state handle)。

接下来看下，我们如何得到这个状态句柄。Flink通过`StateDescriptor`来定义一个状态。这是一个抽象类，内部定义了状态名称、类型、序列化器等基础信息。与上面的状态对应，从`StateDescriptor`派生了`ValueStateDescriptor`, `ListStateDescriptor`等descriptor。

具体如下：

* ValueState getState(ValueStateDescriptor)
* ReducingState getReducingState(ReducingStateDescriptor)
* ListState getListState(ListStateDescriptor)
* FoldingState getFoldingState(FoldingStateDescriptor)
* MapState getMapState(MapStateDescriptor)

接下来我们看一下创建和使用ValueState的例子：
    
    
    public class CountWindowAverage extends RichFlatMapFunction, Tuple2> {
    
        /**
         * ValueState状态句柄. 第一个值为count，第二个值为sum。
         */
        private transient ValueState> sum;
    
        @Override
        public void flatMap(Tuple2 input, Collector> out) throws Exception {
            // 获取当前状态值
            Tuple2 currentSum = sum.value();
    
            // 更新
            currentSum.f0 += 1;
            currentSum.f1 += input.f1;
    
            // 更新状态值
            sum.update(currentSum);
            
            // 如果count >=2 清空状态值，重新计算
            if (currentSum.f0 >= 2) {
                out.collect(new Tuple2<>(input.f0, currentSum.f1 / currentSum.f0));
                sum.clear();
            }
        }
    
        @Override
        public void open(Configuration config) {
            ValueStateDescriptor> descriptor =
                    new ValueStateDescriptor<>(
                            "average", // 状态名称
                            TypeInformation.of(new TypeHint>() {}), // 状态类型
                            Tuple2.of(0L, 0L)); // 状态默认值
            sum = getRuntimeContext().getState(descriptor);
        }
    }
    
    // ...
    env.fromElements(Tuple2.of(1L, 3L), Tuple2.of(1L, 5L), Tuple2.of(1L, 7L), Tuple2.of(1L, 4L), Tuple2.of(1L, 2L))
            .keyBy(0)
            .flatMap(new CountWindowAverage())
            .print();
    
    // the printed output will be (1,4) and (1,5)

由于状态需要从`RuntimeContext`中创建和获取，因此如果要使用状态，必须使用RichFunction。普通的Function是无状态的。

KeyedStream上的scala api则提供了一些语法糖，让创建和使用状态更加方便：
    
    
    val stream: DataStream[(String, Int)] = ...
    
    val counts: DataStream[(String, Int)] = stream
      .keyBy(_._1)
      .mapWithState((in: (String, Int), count: Option[Int]) =>
        count match {
          case Some(c) => ( (in._1, c), Some(c + in._2) )
          case None => ( (in._1, 0), Some(in._2) )
        })

### Inside Keyed State

上面以Keyed State为例讲了如何使用状态，接下来我们从代码层面分析一下，框架在内部做了什么事情。

先看下上面例子中`open`方法中获取状态句柄的代码：
    
    
        sum = getRuntimeContext().getState(descriptor);

它调用了`RichFlatMapFunction.getRuntimeContext().getState`方法，最终会调用`StreamingRuntimeContext.getState`方法：
    
    
        public  ValueState getState(ValueStateDescriptor stateProperties) {
            KeyedStateStore keyedStateStore = checkPreconditionsAndGetKeyedStateStore(stateProperties);
            stateProperties.initializeSerializerUnlessSet(getExecutionConfig());
            return keyedStateStore.getState(stateProperties);
        }

`checkPreconditionsAndGetKeyedStateStore`方法中：
    
    
        KeyedStateStore keyedStateStore = operator.getKeyedStateStore();
        return keyedStateStore;

即返回了`AbstractStreamOperator.keyedStateStore`变量。这个变量的初始化在`AbstractStreamOperator.initState`方法中：
    
    
        private void initKeyedState() {
            try {
                TypeSerializer keySerializer = config.getStateKeySerializer(getUserCodeClassloader());
                // create a keyed state backend if there is keyed state, as indicated by the presence of a key serializer
                if (null != keySerializer) {
                    KeyGroupRange subTaskKeyGroupRange = KeyGroupRangeAssignment.computeKeyGroupRangeForOperatorIndex(
                            container.getEnvironment().getTaskInfo().getMaxNumberOfParallelSubtasks(),
                            container.getEnvironment().getTaskInfo().getNumberOfParallelSubtasks(),
                            container.getEnvironment().getTaskInfo().getIndexOfThisSubtask());
    
                    long estimatedStateSizeInMB = config.getStateSize();
    
                    this.keyedStateBackend = container.createKeyedStateBackend(
                            keySerializer,
                            // The maximum parallelism == number of key group
                            container.getEnvironment().getTaskInfo().getMaxNumberOfParallelSubtasks(),
                            subTaskKeyGroupRange,
                            estimatedStateSizeInMB);
    
                    this.keyedStateStore = new DefaultKeyedStateStore(keyedStateBackend, getExecutionConfig());
                }
    
            // ...
        }
    

它先调用`StreamTask.createKeyedStateBackend`方法创建stateBackend，然后将stateBackend传入DefaultKeyedStateStore。

`StreamTask.createKeyedStateBackend`方法通过它内部的stateBackend来创建keyed statebackend：
    
    
        backend = stateBackend.createKeyedStateBackend(
                getEnvironment(),
                getEnvironment().getJobID(),
                operatorIdentifier,
                keySerializer,
                numberOfKeyGroups,
                keyGroupRange,
                estimatedStateSizeInMB,
                getEnvironment().getTaskKvStateRegistry());

看一下statebackend的初始化，在`StreamTask.createStateBackend`方法中，这个方法会根据配置项`state.backend`的值创建backend，其中内置的backend有`jobmanager`, `filesystem`, `rocksdb`。

`jobmanager`的state backend会把状态存储在job manager的内存中。  
`filesystem`会把状态存在文件系统中，有可能是本地文件系统，也有可能是HDFS、S3等分布式文件系统。  
`rocksdb`会把状态存在rocksdb中。

所以可以看到，创建了state backend之后，创建keyed stated backend，实际上就是调用具体的state backend来创建。我们以filesystem为例，实际就是`FsStateBackend.createKeyedStateBackend`方法，这个方法也很简单，直接返回了`HeapKeyedStateBackend`对象。

先不展开说`HeapKeyedStateBackend`类，我们返回去看创建keyed state，最终返回的是`DefaultKeyedStateStore`对象，它的`getState`, `getListState`, `getReducingState`等方法，都是对底层keyed state backend的一层封装，`keyedStateBackend.getPartitionedState`来返回具体的state handle（`DefaultKeyedStateStore.getPartitionedState`方法）。

这个方法实际调用了`AbstractKeyedStateBackend.getPartitionedState`方法，`HeapKeyedStateBackend`和`RocksDBKeyedStateBackend`都从这个基类派生。

这个类有一个成员变量：
    
    
        private final HashMap> keyValueStatesByName;

它保存了的一个映射。map value中的InternalKvState，实际为创建的`HeapValueState`, `HeapListState`, `RocksDBValueState`, `RocksDBListStat`等实现。

回到上面`AbstractKeyedStateBackend.getPartitionedState`，正常的代码路径下，它会调用`AbstractKeyedStateBackend.getOrCreateKeyedState`方法来创建这个InternalKvState，其方法如下：
    
    
            S state = stateDescriptor.bind(new StateBackend() {
                @Override
                public  ValueState createValueState(ValueStateDescriptor stateDesc) throws Exception {
                    return AbstractKeyedStateBackend.this.createValueState(namespaceSerializer, stateDesc);
                }
    
                @Override
                public  ListState createListState(ListStateDescriptor stateDesc) throws Exception {
                    return AbstractKeyedStateBackend.this.createListState(namespaceSerializer, stateDesc);
                }
            // ...

`AbstractKeyedStateBackend.createValueState`，`AbstractKeyedStateBackend.createListState`等方法是AbstractKeyedStateBackend的抽象方法，具体还是在HeapKeyedStateBackend、RocksDBKeyedStateBackend等类中实现的，所以这里创建的state只是一个代理，它proxy了具体的上层实现。在我们的例子中，最后绕了一个圈，调用的仍然是`HeapKeyedStateBackend.createValueState`方法，并将state name对应的state handle放入到keyValueStatesByName这个map中，保证在一个task中只有一个同名的state handle。

回来看`HeapKeyedStateBackend`，这个类有一个成员变量：
    
    
        private final Map> stateTables = new HashMap<>();

它的key为state name, value为StateTable，用来存储这个state name下的状态值。它会将所有的状态值存储在内存中。

它的`createValueState`方法：
    
    
            StateTable stateTable = tryRegisterStateTable(namespaceSerializer, stateDesc);
            return new HeapValueState<>(this, stateDesc, stateTable, keySerializer, namespaceSerializer);

即先注册StateTable，然后返回一个HeapValueState。

这里整理一下从应用层面创建一个ValueState的state handle的过程：
    
    
    sum = getRuntimeContext().getState(descriptor) (app code)
      --> RichFlatMapFunction.getRuntimeContext().getState
      --> StreamingRuntimeContext.getState
        --> KeyedStateStore.getState(stateProperties)
        --> AbstractStreamOperator.keyedStateStore.getState
          --> DefaultKeyedStateStore.getState
          --> DefaultKeyedStateStore.getPartitionedState
          --> AbstractKeyedStateBackend.getPartitionedState
          --> AbstractKeyedStateBackend.getOrCreateKeyedState
            --> HeapKeyedStateBackend.createValueState
            --> HeapKeyedStateBackend.tryRegisterStateTable
            --> return new HeapValueState        

而从框架层面看，整个调用流程如下：
    
    
    Task.run
      --> StreamTask.invoke
      --> StreamTask.initializeState
      --> StreamTask.initializeOperators
        --> AbstractStreamOperator.initializeState
        --> AbstractStreamOperator.initKeyedState
          --> StreamTask.createKeyedStateBackend
            --> MemoryStateBackend.createKeyedStateBackend
              --> HeapKeyedStateBackend.

整体来看，创建一个state handle还是挺绕的，中间经过了多层封装和代理。

* * *

创建完了state handle，接下来看看如何获取和更新状态值。

首先需要讲一下HeapState在内存中是如何组织的，还是以最简单的HeapValueState为例，  
具体的数据结构，是在其基类`AbstractHeapState`中，以`StateTable stateTable`的形式存在的，其中K代表Key的类型，N代表state的namespace（这样属于不同namespace的state可以重名），SV代表state value的类型。

`StateTable`类内部数据结构如下：
    
    
        protected final KeyGroupRange keyGroupRange;
        /** Map for holding the actual state objects. */
        private final List>> state;
        /** Combined meta information such as name and serializers for this state */
        protected RegisteredBackendStateMetaInfo metaInfo;

最核心的数据结构是`state`成员变量，它保存了一个list，其值类型为`Map>`，即按namespace和key分组的两级map。那么它为什么是一个list呢，这里就要提到`keyGroupRange`成员变量了，它代表了当前state所包含的key的一个范围，这个范围根据当前的sub task id以及最大并发进行计算，在`AbstractStreamOperator.initKeyedState`方法中：
    
    
                    KeyGroupRange subTaskKeyGroupRange = KeyGroupRangeAssignment.computeKeyGroupRangeForOperatorIndex(
                            container.getEnvironment().getTaskInfo().getMaxNumberOfParallelSubtasks(),
                            container.getEnvironment().getTaskInfo().getNumberOfParallelSubtasks(),
                            container.getEnvironment().getTaskInfo().getIndexOfThisSubtask());

举例来说，如果当前task的并发是2，最大并发是128，那么task-1所属的state backend的keyGroupRange为[0,63]，而task-2所属的state backend的keyGroupRange为[64,127]。

这样，task-1中的StateTable.state这个list，最大size即为64。获取特定key的state value时，会先计算key的hash值，然后用hash值 % 最大并发，这样会得到一个[0,127]之间的keyGroup，到这个list中get到这个下标的`Map>`值，然后根据 namespace + key二级获取到真正的state value。

看到这里，有人可能会问，对于一个key，如何保证在task-1中，它计算出来的keyGroup一定是在[0,63]之间，在task-2中一定是在[64,127]之间呢？

原因是，在KeyedStream中，使用了`KeyGroupStreamPartitioner`这种partitioner来向下游task分发keys，而这个类重载的`selectChannels`方法如下：
    
    
            K key;
            try {
                key = keySelector.getKey(record.getInstance().getValue());
            } catch (Exception e) {
                throw new RuntimeException("Could not extract key from " + record.getInstance().getValue(), e);
            }
            returnArray[0] = KeyGroupRangeAssignment.assignKeyToParallelOperator(key, maxParallelism, numberOfOutputChannels);
            return returnArray;

这里关键是`KeyGroupRangeAssignment.assignKeyToParallelOperator`方法，它中间调用了`KeyGroupRangeAssignment.assignToKeyGroup`方法来确定一个key所属的keyGroup，这个跟state backend计算keyGroup是同一个方法。然后根据这个keyGroup，它会计算出拥有这个keyGroup的task，并将这个key发送到此task。所以能够保证，从KeyedStream上emit到下游task的数据，它的state所属的keyGroup一定是在当前task的keyGroupRange中的。

上面已经提到了获取ValueState的值，这里贴一下代码，结合一下就很容易理解了：
    
    
            Map> namespaceMap = stateTable.get(backend.getCurrentKeyGroupIndex());
            if (namespaceMap == null) {
                return stateDesc.getDefaultValue();
            }
    
            Map keyedMap = namespaceMap.get(currentNamespace);
            if (keyedMap == null) {
                return stateDesc.getDefaultValue();
            }
    
            V result = keyedMap.get(backend.getCurrentKey());
            if (result == null) {
                return stateDesc.getDefaultValue();
            }
    
            return result;

而更新值则通过`ValueState.update`方法进行更新，这里就不贴代码了。

上面讲了最简单的ValueState，其他类型的state，其实也是基本一样的，只不过stateTable中状态值的类型不同而已。如HeapListState，它的状态值类型为ArrayList；HeapMapState，它的状态值类型为HashMap。而值类型的不同，导致了在State上的接口也有所不同，如ListState会有`add`方法，MapState有`put`和`get`方法。在这里就不展开说了。

* * *

## Checkpoint

到上面为止，都是简单的关于状态的读写，而且状态都还是只在Task本地，接下来就会涉及到checkpoint。  
所谓checkpoint，就是在某一时刻，将所有task的状态做一个快照(snapshot)，然后存储到memory/file system/rocksdb等。

关于Flink的分布式快照，请参考 [分布式Snapshot和Flink Checkpointing简介][1] 及相关论文，这里不详述了。

Flink的checkpoint，是由`CheckpointCoordinator`来协调的，它位于JobMaster中。但是其实在ExecutionGraph中已经创建了，见`ExecutionGraph.enableSnapshotCheckpointing`方法。

当Job状态切换到RUNNING时，`CheckpointCoordinatorDeActivator`（从JobStatusListener派生）会触发回调`coordinator.startCheckpointScheduler();`，根据配置的checkpoint interval来定期触发checkpoint。

每个checkpoint由checkpoint ID和timestamp来唯一标识，其中checkpoint ID可以是standalone（基于内存）的，也可能是基于ZK的。  
已经完成的checkpoint，保存在CompletedCheckpointStore中，可以是StandaloneCompletedCheckpointStore（保存在JobMaster内存中），也可以是ZooKeeperCompletedCheckpointStore（保存在ZK中），甚至是自己实现的store，比如基于HDFS的。

触发checkpoint的方法在CheckpointCoordinator.ScheduledTrigger中，只有一行：
    
    
        triggerCheckpoint(System.currentTimeMillis(), true);

这个方法比较长，它会先做一系列检查，如检查coordinator自身的状态（是否被shutdown），还会检查与上次checkpoint的时间间隔、当前的并发checkpoint数是否超过限制，如果都没问题，再检查所有task的状态是否都为RUNNING，都没问题之后，触发每个Execution的checkpoint：
    
    
        for (Execution execution: executions) {
            execution.triggerCheckpoint(checkpointID, timestamp, checkpointOptions);
        }

看下`Execution.triggerCheckpoint`方法：
    
    
        public void triggerCheckpoint(long checkpointId, long timestamp, CheckpointOptions checkpointOptions) {
            final SimpleSlot slot = assignedResource;
    
            if (slot != null) {
                final TaskManagerGateway taskManagerGateway = slot.getTaskManagerGateway();
    
                taskManagerGateway.triggerCheckpoint(attemptId, getVertex().getJobId(), checkpointId, timestamp, checkpointOptions);
            } else {
                LOG.debug("The execution has no slot assigned. This indicates that the execution is " +
                    "no longer running.");
            }
        }

很简单，通过RPC调用向TaskManager触发当前JOB的checkpoint，然后一路调用下去：
    
    
    RpcTaskManagerGateway.triggerCheckpoint
      --> TaskExecutorGateway.triggerCheckpoint
      --> TaskExecutor.triggerCheckpoint
        --> task.triggerCheckpointBarrier
        --> StatefulTask.triggerCheckpoint
        --> StreamTask.triggerCheckpoint
        --> StreamTask.performCheckpoint

具体做checkpoint的时候，会先向下游广播checkpoint barrier，然后调用`StreamTask.checkpointState`方法做具体的checkpoint，实际会调用到`StreamTask.executeCheckpointing`方法。

checkpoint里，具体操作为，遍历每个StreamTask中的所有operator：

1. 调用operator的`snapshotState(FSDataOutputStream out, long checkpointId, long timestamp)`方法，存储operator state，这个结果会返回operator state handle，存储于`nonPartitionedStates`中。这里实际处理的时候，只有当user function实现了`Checkpointed`接口，才会做snapshot。需要注意的是，此接口已经deprecated，被`CheckpointedFunction`代替，而对`CheckpointedFunction`的snapshot会在下面的第2步中来做，因此这两个接口一般来说是2选1的。
2. 调用operator的`snapshotState(long checkpointId, long timestamp, CheckpointOptions checkpointOptions)`方法，返回`OperatorSnapshotResult`对象。注意虽然每个snapshot方法返回的都是一个RunnableFuture，不过目前实际上还是同步做的checkpoint（可以比较容易改成异步）。

    1. 这里会先调用`AbstractStreamOperator.snapshotState`方法，为rich function做state snapshot
    2. 调用`operatorStateBackend.snapshot`方法，对operator state做snapshot。
    3. 调用`keyedStateBackend.snapshot`方法，对keyed state做snapshot。
    4. 调用`timerServiceBackend.snapshot`方法，对processing time/event time window中注册的timer回调做snapshot（恢复状态的时候必须也要恢复timer回调）
3. 调用`StreamTask.runAsyncCheckpointingAndAcknowledge`方法确认上面的snapshot是否都成功，如果成功，则会向CheckpointCoordinator发送ack消息。
4. CheckpointCoordinator收到ack消息后，会检查本地是否存在这个pending的checkpoint，并且这个checkpoint是否超时，如果都OK，则判断是否收到所有task的ack消息，如果是，则表示已经完成checkpoint，会得到一个CompletedCheckpoint并加入到completedCheckpointStore中。

在上面的checkpoint过程中，如果state backend选择的是jobmanager，那么最终返回的state handle为ByteStreamStateHandle，这个state handle中包含了snapshot后的所有状态数据。而如果是filesystem，则state handle只会包含数据的文件句柄，数据则在filesystem中，这个下面会再细说。

* * *

### Filesystem State Backend

上面提到的都是比较简单的基于内存的state backend，在实际生产中是不太可行的。因此一般会使用filesystem或者rocksdb的state backend。我们先讲一下基于filesystem的state backend。

基于内存的state backend实现为`MemoryStateBackend`，基于文件系统的state backend的实现为`FsStateBackend`。FsStateBackend有一个策略，当状态的大小小于1MB（可配置，最大1MB）时，会把状态数据直接存储在meta data file中，避免出现很小的状态文件。

FsStateBackend另外一个成员变量就是`basePath`，即checkpoint的路径。实际做checkpoint时，生成的路径为：`//chk-/`。

而且filesystem推荐使用分布式文件系统，如HDFS等，这样在fail over时可以恢复，如果是本地的filesystem，那恢复的时候是会有问题的。

回到StreamTask，在做checkpoint的时候，是通过`CheckpointStateOutputStream`写状态的，FsStateBack会使用`FsCheckpointStreamFactory`，然后通过`FsCheckpointStateOutputStream`去写具体的状态，这个实现也比较简单，就是一个带buffer的写文件系统操作。最后向上层返回的StreamStateHandle，视状态的大小，如果状态特别小，则会直接返回带状态数据的`ByteStreamStateHandle`，否则会返回`FileStateHandle`，这个state handle包含了状态文件名和大小。

需要注意的是，虽然checkpoint是写入到文件系统中，但是基于FsStateBackend创建的keyed state backend，仍然是`HeapKeyedStateBackend`，也就是说，keyed state的读写仍然是会在内存中的，只有在做checkpoint的时候才会持久化到文件系统中。

### RocksDB State Backend

RocksDB跟上面的都略有不同，它会在本地文件系统中维护状态，KeyedStateBackend等会直接写入本地rocksdb中。同时它需要配置一个远端的filesystem uri（一般是HDFS），在做checkpoint的时候，会把本地的数据直接复制到filesystem中。fail over的时候从filesystem中恢复到本地。

从RocksDBStateBackend创建出来的RocksDBKeyedStateBackend，更新的时候会直接以key + namespace作为key，然后把具体的值更新到rocksdb中。

如果是ReducingState，则在`add`的时候，会先从rocksdb中读取已有的值，然后根据用户的reduce function进行reduce，再把新值写入rocksdb。

做checkpoint的时候，会首先在本地对rockdb做checkpoint（rocksdb自带的checkpoint功能），这一步是同步的。然后将checkpoint异步复制到远程文件系统中。最后返回`RocksDBStateHandle`。

RocksDB克服了HeapKeyedStateBackend受内存限制的缺点，同时又能够持久化到远端文件系统中，比较适合在生产中使用。

* * *

## Queryable State

Queryable State，顾名思义，就是可查询的状态，表示这个状态，在流计算的过程中就可以被查询，而不像其他流计算框架，需要存储到外部系统中才能被查询。目前可查询的state主要针对partitionable state，如keyed state等。

简单来说，当用户在job中定义了queryable state之后，就可以在外部，通过`QueryableStateClient`，通过job id, state name, key来查询所对应的状态的实时的值。

queryable state目前支持两种方法来定义：

* 通过`KeyedStream.asQueryableState`方法，生成一个QueryableStream，需要注意的是，这个stream类似于一个sink，是不能再做transform的。 实现上，生成QueryableStream就是为当前stream加上一个operator：`QueryableAppendingStateOperator`，它的`processElement`方法，每来一个元素，就会调用`state.add`去更新状态。因此这种方式有一个限制，只能使用ValueDescriptor, FoldingStateDescriptor或者ReducingStateDescriptor，而不能是ListStateDescriptor，因为它可能会无限增长导致OOM。此外，由于不能在stream后面再做transform，也是有一些限制。
* 通过managed keyed state。
    
          ValueStateDescriptor> descriptor =
    new ValueStateDescriptor<>(
          "average", // the state name
          TypeInformation.of(new TypeHint>() {}),
          Tuple2.of(0L, 0L)); 
      descriptor.setQueryable("query-name"); // queryable state name

这个只需要将具体的state descriptor标识为queryable即可，这意味着可以将一个pipeline中间的operator的state标识为可查询的。

首先根据state descriptor的配置，会在具体的TaskManager中创建一个KvStateServer，用于state查询，它就是一个简单的netty server，通过`KvStateServerHandler`来处理请求，查询state value并返回。

但是一个partitionable state，可能存在于多个TaskManager中，因此需要有一个路由机制，当QueryableStateClient给定一个query name和key时，要能够知道具体去哪个TaskManager中查询。

为了做到这点，在Job的ExecutionGraph（JobMaster）上会有一个用于定位KvStateServer的KvStateLocationRegistry，当在TaskManager中注册了一个queryable KvStateServer时，就会调用`JobMaster.notifyKvStateRegistered`，通知JobMaster。

具体流程如下图：

![image.png][5]

这个设计看起来很美好，通过向流计算实时查询状态数据，免去了传统的存储等的开销。但实际上，除了上面提到的状态类型的限制之外，也会受netty server以及state backend本身的性能限制，因此并不适用于高并发的查询。

* * *

参考资料：

1. [Dynamic Scaling: Key Groups][6]
2. [Stateful Stream Processing][7]
3. [Working with State][8]
4. [Scaling to large state][9]
5. [Queryable state design doc][10]

[1]: https://yq.aliyun.com#
[2]: https://yq.aliyun.com/articles/225618
[3]: https://yq.aliyun.com/articles/225621
[4]: http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/73e4b0c06aa9ee0535cf00384a106638.png "image.png"
[5]: http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/13e467bef1282a1474ec625a8da11c9e.png "image.png"
[6]: https://docs.google.com/document/d/1G1OS1z3xEBOrYD4wSu-LuBCyPUWyFd9l3T9WyssQ63w/edit#
[7]: https://cwiki.apache.org/confluence/display/FLINK/Stateful+Stream+Processing
[8]: https://ci.apache.org/projects/flink/flink-docs-release-1.3/dev/stream/state.html
[9]: https://www.slideshare.net/FlinkForward/stephan-ewen-scaling-to-large-state
[10]: https://docs.google.com/document/d/1NkQuhIKYmcprIU5Vjp04db1HgmYSsZtCMxgDi_iTN-g/edit#

  



http://www.google-analytics.com/ga.js"> src="js/ZeroClipboard.js">







">http://yandex.st/highlightjs/8.0/highlight.min.js">

