

原文链接：<https://ci.apache.org/projects/flink/flink-docs-release-1.4/internals/task_lifecycle.html>
[TOC]

# 任务的生命周期
task是Flink中执行的基本单位，也是operator的每个并行实例被执行的地方。例如，并行度为5的operator ，其每个实例都由一个单独task执行。

StreamTask是Flink流引擎中所有不同task子类型的基础，本文将介绍StreamTask生命周期的不同阶段，并描述了代表每个阶段的主要方法。

## Operator Lifecycle in a nutshell(Operator生命周期)
由于task是执行并行operator实例的实体，所以task的生命周期与operator的生命周期密切相关。因此，我们会先简要介绍一下表示operator生命周期的基本方法，然后才能深入StreamTask。下面的列表按照每个方法的调用顺序排布，给定一个operator可以有一个用户定义的函数（UDF），在每个operator方法之下，我们还给出了它所调用的UDF生命周期（缩进项中）。如果operator extends AbstractUdfStreamOperator，那么这些方法都将生效，这是执行UDF operators的基类。

```
 // initialization phase
    OPERATOR::setup
        UDF::setRuntimeContext
    OPERATOR::initializeState
    OPERATOR::open
        UDF::open
    
    // processing phase (called on every element/watermark)
    OPERATOR::processElement
        UDF::run
    OPERATOR::processWatermark
    
    // checkpointing phase (called asynchronously on every checkpoint)
    OPERATOR::snapshotState
            
    // termination phase
    OPERATOR::close
        UDF::close
    OPERATOR::dispose
```
  
简而言之，它setup()是初始化一些operator-specific的方法，例如RuntimeContext和metric collection数据结构。之后，initializeState()给出一个用来初始state的operator，然后 open()方法执行所有operator-specific的初始化，例如打开用户自定义的函数AbstractUdfStreamOperator。

注意 ：initializeState()包含operator state的初始化（例如register keyed state），也包含任务失败后从checkpoint中恢复state的逻辑。关于这些下面有更详细的解释。

现在一切都设置好了，operator就可以处理输入的数据了。输入的elements可以是以下之一：input element，watermark和checkpoint barriers。他们中的每一个都有一个特殊的单元来处理。element由processElement()方法处理，watermark由processWatermark()处理，checkpoint barriers由异步调用的snapshotState()方法处理，此方法会触发一次checkpoint ，我们在下面描述。对于每个输入element，根据其类型，调用对应的方法。但要注意的是，processElement()方法也是UDF的逻辑被调用的地方，例如MapFunction里的map()方法。

Finally, in the case of a normal, fault-free termination of the operator (*e.g.* if the stream is finite and its end is reached), the `close()` method is called to perform any final bookkeeping action required by the operator’s logic (*e.g.* close any connections or I/O streams opened during the operator’s execution), and the `dispose()` is called after that to free any resources held by the operator (*e.g.* native memory held by the operator’s data).

上面这一段原文有点绕口，有些不知道怎么翻译，简单的说就是stream如果正常结束，close()方法会被调用，可以close()方法里做一些关闭连接、关闭io流的操作，然后再调用dispose()方法来释放operator占用的系统资源。（这是通常能想到的处理方式，读者自行理解。）

在由故障或手动取消引起的任务终止的情况下，将直接跳转到dispose() 方法，并跳过operator在故障发生时的阶段的任何中间过程和dispose()。(注：前一个dispose指当前operator的方法，后一个dispose指被跳过的operator的dispose方法)

Checkpoints: 当接收到一个checkpoint barrier时，operator的snapshotState()被称为与上述其余方法异步的方法。Checkpoints 在processing阶段执行，即在operator的open()和close()之间执行。该方法的职责是将当前state存储到指定的state backend， 当任务从故障中恢复时，state也会被恢复。关于介绍Flink的Checkpoint机制，以及有关Flink Checkpoint原理的更详细讨论，请阅读相应的文档： Fault Tolerance for Data Streaming。

## Task Lifecycle(Task生命周期)
上节简要介绍了operator的主要阶段，本节将更详细地介绍一个task在集群中执行期间如何调用对应的方法。这里描述的阶段的顺序主要包括在StreamTask类的invoke()方法中。下面的文档分为两个部分，一部分描述了任务的正常执行过程，或者说叫无故障执行过程（见Normal Execution），以及（一个较短的）描述在任务由于手动或某些其他原因（例如执行期间抛出的异常）被取消时所遵循的不同过程，此阶段请参阅（Interrupted Execution）。

## Normal Execution(正常执行)
task在没有中断且正常执行完成的情况下，执行步骤如下所示：

```
    TASK::setInitialState
    TASK::invoke
	    create basic utils (config, etc) and load the chain of operators
	    setup-operators
	    task-specific-init
	    initialize-operator-states
   	    open-operators
	    run
	    close-operators
	    dispose-operators
	    task-specific-cleanup
	    common-cleanup
```
如上所示，在恢复任务配置和初始化一些重要的运行参数之后，任务的第一步是在setInitialState()方法中重新初始化task的state，并且在如下两种情况下尤为重要：

1. 当任务从故障中恢复并从最后一个成功的checkpoint点重新启动时
2. 从一个保存点恢复时。

如果任务是第一次执行，则初始task state为空。

在恢复state后，task进入其invoke()方法，在那里，它首先通过调用每一个operator的setup()方法来初始化涉及本地计算的operator，然后通过调用本地init()方法执行task特有的初始化，task特有的初始的意思是根据任务的类型（比如SourceTask，OneInputStreamTask或TwoInputStreamTask等），这个步骤可能会有所不同，但在任何情况下，从这些类型可以得知task所需要的资源。例如，OneInputStreamTask表示具有单个输入流的任务，初始化方法将连接到与本地任务相关的输入流的不同分区的位置。

获得必要的资源后，operaator和用户自定义的Function从task的state中获取自己state，这个过程在task的initializeState()方法中完成，该方法还会调用每个operator的initializeState()`方法。每个stateful operator都应该覆盖initializeState()，并且包含state初始化逻辑，既可以在任务启动时执行，也可以让任务在故障后使用savepoint恢复的时候执行。

现在task中的所有operator都已初始化，每个operator的open()方法都被StreamTask的openAllOperators()方法调用。该方法(指openAllOperators)执行所有的operational的初始化，例如使用定时器服务注册定时器。单个task可能正在执行多个operator，消耗其前驱的输出，在这种情况下，该open()方法在最后一个operator中调用，即这个operator的输出也是task本身的输出。这样做使得当第一个operator开始处理任务的输入时，它的所有下游operator都准备好接收其输出。

注意： task中的连续operator是从最后到第一个依次open

现在，任务可以(从故障中)恢复执行，operator也可以开始处理新的输入数据。这时task的run() 方法被调用。run()方法将一直运行，直到没有更多的输入数据（在finite stream的情况下），或任务被取消（手动或非手动的方式），这个方法也是operator的processElement()和processWatermark()方法被调用的地方。

在运行到完成的情况下(即没有更多的输入数据要处理)，task从run() 方法退出后，并进入shutdown 过程。首先，定时器服务停止注册任何新的定时器（例如从正在执行的点fired timers），清除所有尚未启动的定时器，并等待当前执行的定时器的完成。然后closeAllOperators()通过尝试调用每个operator的close()方法来优雅地关闭涉及计算的operator。然后，buffered里的所有的输出数据被flushed，以便它们可以被下游的task处理，最后任务尝试通过调用所有operator的 dispose()方法来清除其持有的资源。之前我们提到，open不同的operator时是从最后到第一个，但这里的closing是用相反的方式发生：是从第一个到最后一个。

注意：task中的连续operator是第一个到最后一个依次close

最后，当所有operator都被关闭并且所有资源都被释放时，task会关闭其定时器服务，执行其自己清理逻辑，例如清除其所有内部缓冲区，然后执行其通用任务清理，其中包括关闭所有的输出通道和清理任何输出缓冲区。

Checkpoints: 之前的章节里，我们看到在initializeState()期间，或者说在故障恢复的情况下，task及其所有operator和function会从故障前的最后一个被成功持久存储的checkpoint来恢复state。Flink中的checkpoint基于用户指定的时间间隔定期执行，并且由与主任务线程不同的线程执行，这就是为什么checkpoint不包括在task生命周期的主要阶段的原因。简而言之，CheckpointBarriers作为特殊元素周期性地输入到job的数据流中，并且是从source到sink流经整个数据流。source task在运行模式下输入这此barriers，并假设CheckpointCoordinator也在运行，每当一个task收到这样一个barrier时，就会调度一个checkpoint线程任务去执行，在这个线程里调用 operator里的snapshotState()方法。当checkpoint正在执行时，task仍然可以从输入流中接收数据，但是接收的数据会被缓存在buffer里，直到checkpoint完成后才被process和emit。

## Interrupted Execution(执行中断的情况)
在前面的章节里，我们描述了task从运行到完成的生命周期。如果task在任何时候被cancelled，则正常执行被中断，那么从这个时刻开始，以下operator会被执行：关闭定时器服务，清理task，回收operator资源，以及一些基础的task清理（指关闭连接、关闭io流），这些过程上面都有描述。

