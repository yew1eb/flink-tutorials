[Source](https://yq.aliyun.com/articles/225624?spm=a2c4e.11155435.0.0.2e5e7c3fODmBn2 "Permalink to Flink原理与实现：Window的实现原理-博客-云栖社区-阿里云")

# Flink原理与实现：Window的实现原理-博客-云栖社区-阿里云

_摘要：_ Flink原理与实现系列文章： Flink 原理与实现：架构和拓扑概览Flink 原理与实现：如何生成 StreamGraphFlink 原理与实现：如何生成 JobGraphFlink原理与实现：如何生成ExecutionGraph及物理执行图Flink原理与实现：Operator Chain原理Flink原理与实现：详解Flink中的状态管理 在阅读本文之前，请先阅读Flink 原理与实现：Window机制，这篇文章从用户的角度，对Window做了比较详细的分析，而本文主要是从Flink框架的实现层面，对Window做另一个角度的分析。 

Flink原理与实现系列文章：

[Flink 原理与实现：架构和拓扑概览][1]  
[Flink 原理与实现：如何生成 StreamGraph][1]  
[Flink 原理与实现：如何生成 JobGraph][1]  
[Flink原理与实现：如何生成ExecutionGraph及物理执行图][2]  
[Flink原理与实现：Operator Chain原理][3]  
[Flink原理与实现：详解Flink中的状态管理][4]

在阅读本文之前，请先阅读[Flink 原理与实现：Window机制][1]，这篇文章从用户的角度，对Window做了比较详细的分析，而本文主要是从Flink框架的实现层面，对Window做另一个角度的分析。

首先看一个比较简单的情况，假设我们在一个KeyedStream上做了一个10秒钟的tumbling processing time window，也就是说，每隔10秒钟窗口会触发一次，即：
    
​    
      dataStream.keyBy(0).timeWindow(Time.seconds(10)).sum(1);

在研究源码之前，我们可以在脑子里大概想象一下它应该会怎么处理：

1. 给定一条数据，会给这条数据assign windows，在这个例子中，因为是翻滚窗口，所以只会assign出一个窗口。
2. assign了窗口之后，我们就知道这条消息对应的窗口起始时间，这里比较重要的是窗口的右边界，即窗口过期时间。
3. 我们可以在窗口的过期时间之上，注册一个Scheduled Future，到时间之后让它自动回调窗口的聚合函数，即触发窗口内的数据计算。

上面的第3步，针对KeyedStream，需要再扩展一下，针对一条数据，我们应该注册一个基于Key + Window的Scheduled Future，到时间之后只触发对于这个key的数据计算。当然，这里我们不自觉地会想，当key的数目非常大的时候，可能会创建出大量的Future，这会是一个问题。

脑子中大致有上面的思路之后，我们来看一下Flink的实现。

首先，`KeyedStream.timeWindow`方法会生成一个WindowedStream，`sum`则是我们的aggregator，因此在WindowedStream中，实际调用了`aggregate(new SumAggregator(...))`，然后一层层调下来，目标就是生成一个WindowOperator。

在Flink中，根据是否配置了evictor，会生成两种不同的WindowOperator：

* 有evictor：这种情况下意味着用户需要精确控制如何evict窗口的元素，因此所有的数据都会先缓存起来。此时会生成`EvictingWindowOperator`
* 无evictor：这种情况下，通过指定一个reduce function，来一条数据就会进行reduce，当到达窗口边界之后，直接输出结果就可以了。此时会生成`WindowOperator`。

不管哪一种operator，都需要指定两个function：

* window function：控制如何处理窗口内的元素结果
* reduce function：控制如何对窗口内元素做聚合

当一个窗口被fire的时候，就需要通过window function来控制如何处理窗口的结果了。比如`PassThroughWindowFunction`啥也不做，对每一条结果都直接调用`out.collect`发送到下游；而`ReduceApplyWindowFunction`则在这个时候针对窗口所有元素，调用reduce function进行聚合计算，再将计算的结果发射出去。

在上面的例子中，由于没有指定evictor，因此会生成`WindowOperator`，它的window function为`InternalSingleValueWindowFunction`，它提供了对`PassThroughWindowFunction`的代理。而reduce function则用于构造StateDescriptor：
    
​    
                ReducingStateDescriptor<t> stateDesc = new ReducingStateDescriptor&lt;&gt;("window-contents",
                    reduceFunction,
                    input.getType().createSerializer(getExecutionEnvironment().getConfig()));

也就是说，对于`sum`这种情况，每来一条消息，都会调用reduce function，然后更新reducing state，最后窗口被触发的时候，直接通过`PassThroughWindowFunction`输出reducing state的结果。

接下来看一下`WindowOperator.processElement`方法：
    
​    
         // 给元素分配窗口
            final Collection<w> elementWindows = windowAssigner.assignWindows(
                element.getValue(), element.getTimestamp(), windowAssignerContext);
    
            if (windowAssigner instanceof MergingWindowAssigner) {
                // session window的处理逻辑
                // ...
          } else {
            // 遍历每一个窗口
                for (W window: elementWindows) {
                    // 如果窗口已经过期，直接忽略
                    if (isWindowLate(window)) {
                        continue;
                    }
                    isSkippedElement = false;
    
                    windowState.setCurrentNamespace(window);
                    windowState.add(element.getValue());
    
                    triggerContext.key = key;
                    triggerContext.window = window;
    
                    TriggerResult triggerResult = triggerContext.onElement(element);
    
               // 窗口被触发了
                    if (triggerResult.isFire()) {
                        ACC contents = windowState.get();
                        if (contents == null) {
                            continue;
                        }
                        // 输出窗口结果
                        emitWindowContents(window, contents);
                    }
    
               // 如果窗口需要purge，则清理状态
                    if (triggerResult.isPurge()) {
                        windowState.clear();
                    }
                    registerCleanupTimer(window);
                }

可以看到，大致的逻辑还是非常简单明了的，关键在于这一行：
    
​    
        TriggerResult triggerResult = triggerContext.onElement(element);

这里针对不同的window，会有不同的trigger。其中ProcessingTime的都是`ProcessingTimeTrigger`。看下它的`onElement`方法：
    
​    
            ctx.registerProcessingTimeTimer(window.maxTimestamp());
            return TriggerResult.CONTINUE;

可以看到，`onElement`方法始终返回TriggerResult.CONTINUE这个结果，不会触发窗口的fire操作。那么重点就是第一行了，它实际调用了`WindowOperator.registerProcessingTimeTimer`方法：
    
​    
        internalTimerService.registerProcessingTimeTimer(window, time);

这是一个`InternalTimerService`对象，在`WindowOperator.open`方法中被创建：
    
​    
        internalTimerService =
                    getInternalTimerService("window-timers", windowSerializer, this);

它通过`InternalTimeServiceManager.getInternalTimeService`获取：
    
​    
            HeapInternalTimerService<k, n=""> timerService = timerServices.get(name);
            if (timerService == null) {
                timerService = new HeapInternalTimerService&lt;&gt;(totalKeyGroups,
                    localKeyGroupRange, keyContext, processingTimeService);
                timerServices.put(name, timerService);
            }
            timerService.startTimerService(keySerializer, namespaceSerializer, triggerable);
            return timerService;

即创建了一个`HeapInternalTimerService`实例。

看一下这个类的成员，这里省掉了序列化和反序列化相关的成员变量：
    
​    
      // 目前使用SystemProcessingTimeService，包含了窗口到期回调线程池
        private final ProcessingTimeService processingTimeService;
    
        private final KeyContext keyContext;
    
        /**
         * Processing time相关的timer
         */
        private final Set<internaltimer<k, n="">&gt;[] processingTimeTimersByKeyGroup;
        private final PriorityQueue<internaltimer<k, n="">&gt; processingTimeTimersQueue;
    
        /**
         * Event time相关的timer
         */
        private final Set<internaltimer<k, n="">&gt;[] eventTimeTimersByKeyGroup;
        private final PriorityQueue<internaltimer<k, n="">&gt; eventTimeTimersQueue;
    
        /**
         * 当前task的key-group range信息
         */
        private final KeyGroupsList localKeyGroupRange;
        private final int totalKeyGroups;
        private final int localKeyGroupRangeStartIdx;
    
        /**
         * 当前task的watermark，针对event time
         */
        private long currentWatermark = Long.MIN_VALUE;
    
        /**
         * 最接近的未被触发的窗口的Scheduled Future
         * */
        private ScheduledFuture<!--?--> nextTimer;
    
      // 窗口的回调函数，如果是WindowOperator，则会根据时间类型，回调WindowOperator.onEventTime或onProcessingTime方法
        private Triggerable<k, n=""> triggerTarget;

可以看到，存储窗口timer的数据结构`processingTimeTimersByKeyGroup`或者`eventTimeTimersByKeyGroup`，跟存储state的数据结构很像，也是根据当前task的key group range，创建一个数组。每一个key都会落到数组的一个下标，这个数组元素值是一个`Set<internaltimer<k,n>&gt;`，即Key + Window作为这个集合的key。

此外，还有一个`processingTimeTimersQueue`或者`eventTimeTimersQueue`，这是一个优先队列，会存储所有的 Key + Window的timer，主要作用就是用于快速取出最接近的未被触发的窗口。

接下来看下这个类的`registerProcessingTimeTimer`方法：
    
​    
        public void registerProcessingTimeTimer(N namespace, long time) {
            InternalTimer<k, n=""> timer = new InternalTimer&lt;&gt;(time, (K) keyContext.getCurrentKey(), namespace);
    
            // 获取数组下标下的Timer Set
            Set<internaltimer<k, n="">&gt; timerSet = getProcessingTimeTimerSetForTimer(timer);
            // 判断是否已经添加过这个timer
            if (timerSet.add(timer)) {
                InternalTimer<k, n=""> oldHead = processingTimeTimersQueue.peek();
                long nextTriggerTime = oldHead != null ? oldHead.getTimestamp() : Long.MAX_VALUE;
    
                processingTimeTimersQueue.add(timer);
    
                // 如果新添加的timer的窗口触发时间早于nextTimer，则取消nextTimer的触发，
                if (time &lt; nextTriggerTime) {
                    if (nextTimer != null) {
                        nextTimer.cancel(false);
                    }
                    // 注册新添加的timer回调
                    nextTimer = processingTimeService.registerTimer(time, this);
                }
            }
        }

`processingTimeService.registerTimer(time, this)`注册回调，会调用`SystemProcessingTimeService.registerTimer`，这个方法很简单，会计算出当前时间跟窗口边界的delay，然后通过ScheduledExecutorService注册一个定时的回调，其中target为HeapInternalTimerService本身。

举例来说，当前时间为 2017-06-15 19:00:01，来了一条消息，那么它被assign的窗口为[2017-06-15 19:00:00, 2017-06-15 19:00:10)，计算出来的delay为9秒，因此在9秒之后，会触发`HeapInternalTimerService.onProcessingTime`方法。

看下这个方法的代码：
    
​    
        public void onProcessingTime(long time) throws Exception {
            nextTimer = null;
            InternalTimer<k, n=""> timer;
    
            while ((timer = processingTimeTimersQueue.peek()) != null &amp;&amp; timer.getTimestamp() &lt;= time) {
    
                Set<internaltimer<k, n="">&gt; timerSet = getProcessingTimeTimerSetForTimer(timer);
    
                timerSet.remove(timer);
                processingTimeTimersQueue.remove();
    
                keyContext.setCurrentKey(timer.getKey());
                triggerTarget.onProcessingTime(timer);
            }
    
            if (timer != null) {
                if (nextTimer == null) {
                    nextTimer = processingTimeService.registerTimer(timer.getTimestamp(), this);
                }
            }
        }

这个方法也比较简单，根据当前的窗口边界，从processingTimeTimersQueue这个队列中一个个取timer，只要timer所对应的窗口边界&lt;=当前窗口边界时间，就将timer从timer set中删除，并调用`triggerTarget.onProcessingTime(timer)`触发该窗口。最后设置并注册nextTimer，即下一个最接近的窗口的回调。

对于KeyedStream下的窗口，实际上的情况是，在同一个窗口到达的多个不同的key，实际上窗口的边界都是相同的，所以当一个key的窗口被触发，同时也会触发该Task上所有key group range的窗口。

* * *

看了对Processing Time的处理，接下来看看Event time的情况。

event time跟processing time稍有不同，因为它的窗口触发时间，会依赖于watermark，并不是确定的（会有一个最迟的触发时间）。只要当watermark越过当前窗口的边界，这个窗口就可以被触发。因此它并没有使用nextTimer这个变量来注册和标识一个最接近的未被触发的窗口。

注册event time的timer时也比较简单，只是同时往timerSet和eventTimeTimersQueue中添加timer。

另外，Event Time的trigger使用的是`EventTimeTrigger`，它的`onElement`方法如下：
    
​    
        public TriggerResult onElement(Object element, long timestamp, TimeWindow window, TriggerContext ctx) throws Exception {
            if (window.maxTimestamp() &lt;= ctx.getCurrentWatermark()) {
                return TriggerResult.FIRE;
            } else {
                ctx.registerEventTimeTimer(window.maxTimestamp());
                return TriggerResult.CONTINUE;
            }
        }

可以看到，每处理一个元素，都会拿当前元素所属窗口的max timestamp去跟当前的watermark对比，如果小于watermark，说明已经越过窗口的边界，则fire该窗口。

`ctx.getCurrentWatermark()`方法实际调用的是`WindowOperator.WindowContext.getCurrentWatermark`方法，返回的是`HeapInternalTimerService`的currentWatermark。

那么，watermark是在哪里被更新的呢？在`HeapInternalTimerService.advanceWatermark`方法中。代码如下：
    
​    
            currentWatermark = time;
    
            InternalTimer<k, n=""> timer;
    
            while ((timer = eventTimeTimersQueue.peek()) != null &amp;&amp; timer.getTimestamp() &lt;= time) {
    
                Set<internaltimer<k, n="">&gt; timerSet = getEventTimeTimerSetForTimer(timer);
                timerSet.remove(timer);
                eventTimeTimersQueue.remove();
    
                keyContext.setCurrentKey(timer.getKey());
                triggerTarget.onEventTime(timer);
            }

可以看到，这个方法不仅更新了当前watermark，而且会用触发processing time window很接近的逻辑，来触发event time window。

这个方法在`AbstractStreamOperator.processWatermark`方法中被调用。`processWatermark`则在`StreamInputProcessor`中的`ForwardingValveOutputHandler.handleWatermark`方法中被调用。一个自下往上的反向调用链如下：
    
​    
    HeapInternalTimerService.advanceWatermark
      &lt;-- AbstractStreamOperator.processWatermark
      &lt;-- StreamInputProcessor.StatusWatermarkValve.handleWatermark
      &lt;-- StatusWatermarkValve.inputWatermark
      &lt;-- StreamInputProcessor.processInput

这样，当一个processor收到的消息是一个watermark的时候，就会更新time service中的watermark。这里也可以看到，对于普通的用户消息，是不会主动更新watermark的。因此在Flink中，如果要使用Event Time，就必须实现一个发射watermark的策略：

* 要么数据源自己会发送watermark（实际情况中不大可能，除非用户基于特定数据源自行封装）
* 要么实现`TimestampAssigner`接口，定时往下游发送watermark。这还是有一定的限制的。

还有一个需要考虑的问题是，一个Task可能接受到上游多个channel的输入，每个channel都会有watermark，但是每个channel的进度是不一样的，这个时候该如何选择和计算？举例来说，如果有一个消费TT或kafka的Task，它会同时消费多个partition，每个partition的消费进度不一样，当我们需要获取到当前task的watermark的时候，应该取哪个值？

逻辑上来说，应该是取所有partition中最小的值。因为按照watermark的定义，这个值表示上游的数据中，已经没有比它更小的了。那么看一下Flink是如何做的，在`StatusWatermarkValve.inputWatermark`方法中：
    
​    
            if (lastOutputStreamStatus.isActive() &amp;&amp; channelStatuses[channelIndex].streamStatus.isActive()) {
                long watermarkMillis = watermark.getTimestamp();
    
                // 如果当前输入的watermark小于该channel的watermark，直接忽略
                if (watermarkMillis &gt; channelStatuses[channelIndex].watermark) {
                    channelStatuses[channelIndex].watermark = watermarkMillis;
    
                    // 标识当前channel的watermark已经检查过
                    if (!channelStatuses[channelIndex].isWatermarkAligned &amp;&amp; watermarkMillis &gt;= lastOutputWatermark) {
                        channelStatuses[channelIndex].isWatermarkAligned = true;
                    }
    
                    // 取所有channel的watermark最小值并调用handleWatermark方法
                    findAndOutputNewMinWatermarkAcrossAlignedChannels();
                }
            }

的确也是这么做的。

最后总结一下，Flink的window，很灵活很强大，不过在有的时候还是会有一些问题：

1. 当key的规模很大，而任务的并发不高的时候，会存大大量的timer对象，会消耗掉不少的内存。
2. watermark需要用户来定义实现，实现得不好很容易会得出错误的窗口计算结果，这点不太方便。
3. watermark的计算策略过于单一，目前只能取各channel的最小值，用户无法自定义这一块的逻辑。

[1]: https://yq.aliyun.com#
[2]: https://yq.aliyun.com/articles/225618
[3]: https://yq.aliyun.com/articles/225621
[4]: https://yq.aliyun.com/articles/225623

  </internaltimer<k,></k,></internaltimer<k,></k,></k,></internaltimer<k,></k,></internaltimer<k,n></k,></internaltimer<k,></internaltimer<k,></internaltimer<k,></internaltimer<k,></k,></w></t>