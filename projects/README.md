

## Dhalion
Autopiloting #realtime Stream Processing in Heron<https://www.youtube.com/watch?v=jCswlLsE3No>
Dhalion: Self-Regulating Stream Processing in Heron <https://www.youtube.com/watch?v=Ld_qWfRHbXc>

<https://blog.acolyer.org/2017/06/30/dhalion-self-regulating-stream-processing-in-heron/>

应近年来大规模实时分析的需求，很多流处理系统被开发出来。Twitter Heron开源系统就是其中的代表项目之一。这类系统要求在软件或者硬件失败的极端情况下能有较好的服务水平。为了达到这种要求，Twitter Heron系统添加了Dhalion异常检测和恢复框架来保障Heron系统的服务水平。

Dhalion异常检测和恢复框架使用polocy（策略）来整合detector（检测器）和resolver（执行器）模块。整个系统非常灵活。通过替换policy或者detector或者resolver能进行各种检测和恢复任务，包括检测back pressure（反压）指标并进行扩容，和检测负载指标并重新调度容器等等。Dhalion框架的应用给Heron系统带来了初步的自行规范调整机制。

Twitter 是如何实现对实时系统的异常检测？
据吴惠君介绍：Heron 项目就是 Twitter 提供的流处理或者叫做大数据实时计算的典型。

一个稳定可靠的系统离不开监控，我们不仅监控服务是否存活，还要监控系统的运行状况。运行状况主要是对这些组件的核心 metrics 采集、抓取、分析和报警。

吴惠君描述：

在异常检测方面，Heron 采用的 Dahlion 框架，在 Dhalion 框架基础上开发了 Health manager 模块来检测和处理系统异常。其中，Dhalion 框架是由 Microsoft 和 Twitter 联合发明专用于实时流处理系统的异常检测和响应框架，它主要应对三个在流处理系统中的常见场景：

self tuning

self stabilizing

self healing

Dhalion 由一个 policy 调度器和若干个 detector 和 resolver 来合作完成异常检测和响应。

policy 管理 detector 和 resolver：

detector 检测系统各种指标的异常；

resolver 根据 detector 的结果进行对应的处理。

另外，还有些辅助的模块来配合这个主过程，比如 metrics provider 喂给 detecor 检测数据；action 等完成 resolver 和 detector 之间的协同。

在 Dhalion 基础上的 Health manager 实现了几个 Heron 常用的 detector 和 resolver。比如检测 slow instance 的 detector。
有些时候，某些容器云调度的容器由于各种原因会比较慢，那么根据这个容器的各种指标，比如队列长度，响应时间，反压 backpressure 标志等，判断比较然后得出这个容器结点相比其他容器是不是异常。

其他一些 detector 还包括检测是不是整体上资源不够，是不是可以压缩资源池等等。在 resolver 方面，从最简单的重启容器，到扩容 / 缩容，特殊操作等等都可以用 resolver 的形式实现应用。

## DS2: fast, accurate, automatic scaling decisions for distributed streaming dataflows.

Three steps is all you need: fast, accurate, automatic scaling decisions for distributed streaming dataflows
