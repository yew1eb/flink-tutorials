FLIP6设计文档原文：<https://cwiki.apache.org/confluence/pages/viewpage.action?pageId=65147077>

FLIP6的issue: <https://issues.apache.org/jira/browse/FLINK-4319>

# 背景

目前 Flink 与 YARN 或 Kubernetes 等容器技术的交互存在一系列局限性，导致资源不能被充分利用，主要有以下几点不足:

1. 资源分配是静态的，一个作业需要在启动时获取所需的资源并且在它的生命周期里一直持有这些资源。这导致了作业不能随负载变化而动态调整，在负载下降时无法归还空闲的资源，在负载上升时也无法动态扩展。
2. On-YARN 模式下，所有的 container 都是固定大小的，导致无法根据作业需求来调整 container 的结构。譬如 CPU 密集的作业或许需要更多的核，但不需要太多内存，固定结构的 container 会导致内存被浪费。
3. 与容器管理基础设施的交互比较笨拙，需要两个步骤来启动 Flink 作业: 1.启动 Flink 守护进程；2.提交作业。如果作业被容器化并且将作业部署作为容器部署的一部分，那么将不再需要步骤2。
4. On-YARN 模式下，作业管理页面会在作业完成后消失不可访问。
5. Flink 推荐`per job clusters`的部署方式，但是又支持可以在一个集群上运行多个作业的 session 模式，令人疑惑。

# 核心变化

## 单作业 JobManager

最重要的变化是 JobManager 现在只接受单个作业。JobManager 会随 JobGraph 一起创建，并在作业结束后销毁。这种新的模型会更吻合大多数作业的运行模式。

跨作业的功能会以其他组件（下文的 ResourceManager）封装和创建多个 JobManager 的方式来实现。这样可以更好地分离不同组件的关注点，同时也提升了面对不同集群管理器的模块化兼容性。

随之而来的另外一个好处是，JobManager 的构造器可以接受一个 Savepoint 或 Checkpoint 来初始化作业，这为作业的容器化提供了基础。

## ResourceManager

ResourceManger (自 Flink 1.1 引入) 是为集群管理设计的组件，作为通用的 base class，针对不同的部署模式有特定的实现:

1. YARN
2. Mesos
3. Standalone-multi-job (Standalone 模式)
4. Self-contained-single-job（Docker/Kubernetes 模式）

### ResourceManger 定位

ResourceManager 的主要任务是:

1. 请求新的 TaskManager (或者更细粒度的 slot)，方式是直接将资源池的 TaskManager 分配给作业或启动新的 container （如果资源池没有足够资源）再将其分配给作业。
2. 发送错误提醒消息给 JobManager 和 TaskManager。
3. 缓存 TaskManager (container) 以便重复利用，并将一段时间未使用的空闲资源归还给集群。

ResourceManager 的资源级别或许是 TaskManager 级别，或许是 slot 级别（大概率是 slot 级别），但对于 ResourceManager 来说区别很小: 一个是维护可用 TaskManager 的映射，另外一个是维护可用 slot 的映射。

为了简单，下文谈及的 slot 可以认为等同于 TaskManager，因为普遍情况是单 slot 的 TaskManager。

### ResourceManger 核心设计

以下是 ResourceManager 的核心设计:

1. ResourceManager 不再有一个固定的缓存池大小，而是接受每个单独的资源请求。这种方式下，作业可以请求拥有不同内存和 CPU 核数的 container。（这样有什么好处？）
2. ResourceManager 的生命周期与 Job 和 JobManager 无关，以兼容 session 模式和 Standalone 模式。随之而来的另一个结果是 ResourceManager 是 TaskManger 的第一个联系者，并负责处理 TaskManager 的注册。为了统一 Standalone 模式、session 模式和要求不同资源的批处理作业三者的 container 缓存处理方式，ResourceManager 会维护一个可用 TaskManager 和可用 slot 的资源池。
3. ResourceManager 的崩溃不能够影响当前正在运行的作业，这些作业依旧可以使用已有的 slot 并允许任务失败或重试后继续持有这些 slot，只是在 ResourceManager 不可用期间无法申请新的 slot。实际上 ResourceManager 会在 YARN 或 Mesos 维护时不可用，但是这不该影响实时作业。虽然 JobManager 和 TaskManager 有无缝的错误转移(failover)机制，但是可能有某些时间 ResouceManager 一直不能提供 slot，这是 YARN 和 Mesos 架构所限制的。
4. JobManager 可能会在 ResourceManager 注册，注册后 JobManager 会收到 TaskManager 上它所持有的 slot 的错误通知。

[![ResouceManager 架构](assets/pic1.png)](http://www.whitewood.me/img/flip-6/pic1.png)

在 ResourceManager 已有合适的可用 slot 的情况下，步骤(2)和步骤(3)会被跳过。

## TaskManager

TaskManager 会与 ResouceManager 和 JobManager 两者联系。一方面，它会告诉 ResourceManager 自身的可用资源；另一方面，它需要和与 JobManager 联系以在它的 slot 上执行 task。TaskManager 需要向以上两者报告心跳。

### TaskManger 和 ResourceManager 的交互

1. TaskManager 初始化时需要先在 ResourceManager 注册。如果与 ResourceManager 的连接断开，TaskManager 会重新注册并报告它的可用资源。
2. 对 ResourceManager 的每次心跳会附上自己的可用 slot 信息，这样 ResourceManager 可以保持获得最新的可用资源信息。作为补充，当一个 slot 变为可用时 TaskManager 会直接通知 ResourceManager 以避免心跳带来的延迟。
3. TaskManager 关于哪些 slot 被哪个 JobManager 持有的视图是最为可靠的，ResourceManager 会根据 TaskManager 的心跳和通知来得出自己的资源视图。
4. ResourceManager 会告诉 TaskManager 将一个 slot 交给指定的 JobManager，然后 TaskManager 会按要求将 slot 提供给那个 JobManager。如果 slot 没有被接受，TaskManager 会通知 ResourceManager 这个 slot 实际上是可用的。
5. ResourceManager 会告诉 TaskManager 退出程序。

### TaskManager 和 JobManager 的交互

1. TaskManager 按照 ResourceManager 吩咐将 slot 提供给 JobManager。这个 slot 会和 JobManager 绑定知道 JobManager 释放它。
2. TaskManager 会监视所有持有它的 slot 的 JobManager，与一个 JobManager 的连接断开会导致 master-failure 恢复（当前的方式是取消所有来自这个 JobManager 的 task ）。
3. JobManager 可以且只可以在分配给它的 slot 上部署 task。
4. 当与一个 JobManager 断开连接，TaskManager 会尝试在新的 JobManager (通过 HA leader 查找来获取)上重新注册这些 slot。经过一段过渡时间后，TaskManager 会释放这些 slot 来令它们恢复可用状态。如果备用的 JobManager 没有在这段时间内接手这些 slot，那么它需要重新向 ResourceManager 申请资源。

## JobManager SlotPool

JobManager 有一个 SlotPool 来暂存 TaskManager 提供给它并被接受的 slot。JobManager 的调度器从这个 SlotPool 获取资源，因此即使 ResourceManager 不可用 JobManager 仍然可以使用已经分配给它的 slot。

当 SlotPool 无法满足一个资源请求时，它会向 ResourceManager 请求新的 slot。这时如果 ResourceManager 不可用，或者 ResourceManager 拒绝了请求，或者请求超时，都会导致 slot 请求失败。

SlotPool 会将未使用的 slot 归还给 ResourceManager。一个 slot 被视为未使用的标准是当作业已经完全处于运行状态时它未被使用。

## Dispatcher

Dispathcer 是在新设计里引入的一个新概念。Dispatcher 会从 client 端接受作业提交请求并代表它在集群管理器上启动作业。

引入 Dispatcher 的原因是:

- 一些集群管理器需要一个中心化的作业生成和监控实例。
- 实现 Standalone 模式下 JobManager 的角色，等待作业提交。

在一些案例中，Dispathcer 是可选的(YARN)或者不兼容的(kubernetes)。

[![Dispatcher 架构](assets/pic2.png)](http://www.whitewood.me/img/flip-6/pic2.png)

从长远来说，Dispatcher 还会参与如下方面的功能:

- Dispatcher 是跨作业的服务，并且有一个常驻的 Web 管理页面。
- 未来 Dispathcer 应该只接收 HTTP 调用，因此可以作为不同防火墙集群间的桥梁。
- Dispatcher 从不执行代码所以可以被视为受信任的进程。它可以使用高权限运行(超级用户)，然后代表其他用户(获取他们的认证 token)生成作业。在这个基础上，Dispatcher 可以管理用户认证。

## 容错性

Flink 秉持 “Fail fast” 思想，核心的容错机制是作业重启并从 checkpoint 恢复。以下容错方式根据不同集群管理器而不同，将在下文每个章节描述:

1. 执行 JobManager 和 ResourceManager 的进程的存活检测及重启。
2. 作业的 JobGraph 和依赖文件的恢复。

# 不同部署模式下的架构

## YARN

### YARN 模式的架构总览

相比 Flink 1.1 的状态，新的 Flink-on-YARN 架构提供了如下优势:

1. client 直接在 YARN 上启动作业，而不需要先启动一个集群然后再提交作业到集群。因此 client 再提交作业后可以马上返回。
2. 所有的用户依赖库和配置文件都被直接放在应用的 classpath，而不是用动态的用户代码 classloader 去加载。
3. container 在需要时才请求，不再使用时会被释放。
4. “需要时申请” 的 container 分配方式允许不同算子使用不同 profile (CPU 和内存结构)的 container。

未引入 Dispatcher 的架构:

[![YARN without Dispatcher](assets/pic3.png)](http://www.whitewood.me/img/flip-6/pic3.png)

引入 Dispatcher 的架构:

[![YARN with Dispatcher](assets/pic4.png)](http://www.whitewood.me/img/flip-6/pic4.png)

### YARN 模式的容错机制

ResourceManager 和 JobManager 在 ApplicationMaster 进程内运行，错误检测由 YARN 负责。JobGraph 和依赖库从 ApplicationMaster 启动开始就是其工作目录的一部分，在底层实现上，YARN 会将它们存在一个私有的 HDFS 目录。

## Mesos

### Mesos 模式的架构总览

基于 Mesos 的架构与有 Dispatcher 的 YARN 模式十分相似。Mesos 模式下 Dispatcher 是必须的，因为这是让 Mesos 版本的 ResourceManager 在 Mesos 集群内部运行的唯一方式。

[![Mesos 架构](assets/pic5.png)](http://www.whitewood.me/img/flip-6/pic5.png)

### Mesos 模式的容错机制

ResourceManager 和 JobManager 在一个常规的 Mesos container 内运行。Dispatcher 负责监控这些 container 并在它们异常退出时重启它们。Dispatcher 本身必须通过 Mesos 的 Marathon 等服务实现高可用，如下图所示:

[![Mesos 容错](assets/pic6.png)](http://www.whitewood.me/img/flip-6/pic6.png)

JobGraph 和依赖库需要被 Dispatcher 存放到一个持续化的存储组件上，比如 checkpoint 所在的存储组件。

## Standalone

### Standalone 模式的架构总览

FLIP-6 的 Standalone 架构和当前的 Standalone 架构保持兼容。常驻 JobManager 的角色目前是一个“本地的 Dispatcher”进程，它会在内部生成作业以及 JobManager。而 ResourceManager 会一直存活，处理 TaskManager 的注册。对于高可用配置，会有多个 dispatcher 进程竞争 leader，类似于目前的 JobManager。

[![Standalone 架构](assets/pic7.png)](http://www.whitewood.me/img/flip-6/pic7.png)

### Standalone 模式的容错机制

默认情况下，Flink 并没有提供重启挂掉进程的机制。这需要通过外部工具，或者足够多的可用 Standby 机器(TaskManager 和 Dispatcher)来解决。和 Mesos 模式一样，为了高可用 Dispatcher 必须将 JobGraph 和已提交作业的依赖库存放到持续化的存储组件中。

### Standalone 模式 v2.0

未来版本的 Standalone 模式可以被当作一个“轻量级的 YARN”:

- 所有节点运行一个简单的 “NodeManager” 进程来生成 TaskManager 和 JobManager 进程，这种方式可以为不同作业提供合适的隔离。
- 本地的 Dispatcher 不会在内部生成 JobManager ，而是在轻量级 “NodeManager’ 上。

## Docker/Kubernetes

### Docker/Kubernetes 模式下的架构总览

用户可以单纯基于 docker 化的作业和 TaskManager 来定义一个集群。这中间不需要涉及 client 或者作业提交步骤。

Flink 共提供两类 docker 镜像:

1. 特定作业的镜像，包含作业依赖库，并配置好用于启动一个作业（和一个 JobManager）。
2. 一个 TaskManager 的镜像，只是简单地用于启动 TaskManager。这个镜像可以是通用的（不包含作业依赖），也可以是作业专用的（包含作业依赖，会在启动作业时拉取依赖）。

要启动一个 Flink 作业，用户先需要配置一个服务来启动一个 Job/JobManager container 镜像，和 N 个 TaskManager 镜像。

[![k8s 架构](assets/pic8.png)](http://www.whitewood.me/img/flip-6/pic8.png)

这种部署模式下， ResourceManager 会告诉每个正在注册的 TaskManager 将它们的 slot 马上提供给 JobManager。因此，JobManager 一直会持有集群所有可用的 slot 而不必不断检查和请求新的资源来拓展自己。

### Docker/Kubernetes 模式下的容错机制

ResourceManager 和 JobManager 在 master 进程内运行，错误检测和进程自动重启由容器管理器来负责。作业依赖是 master container 的一部分。作业恢复的方法可以是重新执行程序（对于不确定结果的作业这可能会产生问题）或者从 checkpoint 恢复。

## Sessions

YARN Session 模式目前的运行方法像是启动在 YARN 集群上的 Standalone 集群。这个模式的核心功能是启动一批已经分配好资源的机器，然后可以在上面运行一系列的短作业，类似于数据库的客户端将几个操作合并放到一个连接（会话）中执行。

# 组件设计详解

## 资源分配详解（JobManager, ResourceManager, TaskManager）

首先需要了解一系列概念:

- ResourceID: TaskManager (container) 的唯一 ID。
- RmLeaderID: ResourceManager 的 Leader 的任期识别符，用于识别来自新旧 ResourceManager leader 的请求（旧 leader 可能不知道自己已经不是 leader）。
- JobID: 作业在一个生命周期的唯一 ID。
- JmLeaderID: JobManager 的唯一 ID，也是一个任期识别符，每当 JobManager 接受一个作业这个 ID 就会改变，用于识别来自新旧 JobManager leader 的请求（旧 leader 可能不知道自己已经不是 leader）。
- AllocationID: 一次 slot 分配的 ID，由每次 JobManager 请求资源时创建，不会因为重试请求而变化，用于识别 ResourceManager 的回覆和识别对 TaskManager 发出的部署请求。
- Profile: 申请的资源的简介，包括 CPU 核数、内存、磁盘等资源。

### 请求新的 TaskManager 的 slot 分配流程

[![请求新的 TaskManager 的 slot 分配流程](assets/pic9.png)](http://www.whitewood.me/img/flip-6/pic9.png)

消息丢失的处理方法:

- 消息(4)或消息(6)的丢失会以重试消息(4)的方式处理。ResourceManager 不会请求重复的实例，因为已经有 slot 分配给相应的 AllocationID，或者有处理中的请求是对应那个 AllocationID。如果那个 slot 在重试之前就已经被释放了，那么会导致一个多余的 container，它最终将作为未使用的 container 被释放。
- 消息(10)丢失会以 TaskManager 重新注册的方式处理。ResourseID 会帮助识别重复的注册请求。
- 消息(12)丢失会以简单重试的方式处理。重复的分配资源请求会被 JobManager 识别出来，后者会拒绝请求或者接受注册但随后作为未使用资源释放。
- 消息(13)丢失并不要紧，因为随后的 TaskManager 心跳也会携带相同内容。
- 消息(14)丢失会导致 TaskManager 不断重试，这可以通过`(AllocationID, ResourceID)`二元组来识别。

### 来自缓存 TaskManager 的 slot 分配流程

[![来自缓存 TaskManager 的 slot 分配流程](assets/pic10.png)](http://www.whitewood.me/img/flip-6/pic10.png)

消息丢失的处理方法和上文一样。

上述的 slot 分配流程达到了如下的目的:

- TaskManager 和 JobManager 维护重要的 slot 可用性和保留信息，ResourceManager 只有临时信息，扮演了经纪人的角色。
- ResourceManager 应该总是先将 slot 标记为被占用，然后 TaskManager 上的 slot 才实际被分配出去，因此不会尝试将一个已经被占用的 slot 分配出去。对应的异常是 ResourceManager 端的请求 timeout，这因为 TaskManager 拒绝对一个被占用的 slot 的再次分配。

## 进程崩溃处理

### TaskManager 崩溃

由于 TaskManager 分别向 ResourceManager 和 JobManager 报告心跳，因此两者都可以检测到 TaskManager 崩溃。

在 ResourceManager 端:

1. 通过 TaskManager 心跳超时来检测 TaskManager 崩溃。
2. 在 YARN/Mesos 模式，额外通过集群管理器检测进程崩溃。
3. 将崩溃的 TaskManager 从存活列表清除。
4. 通知持有该 TaskManager 的 slot 的 JobManager。
5. 启动相同配置的 TaskManager 作为替代。

在 JobManager 端:

1. 通过 TaskManager 心跳超时来检测 TaskManager 崩溃。
2. 可能会额外收到来自 ResourceManager 的关于 TaskManager 崩溃的通知。
3. 将该 TaskManager 的 slot 从 SlotPool 移除。
4. 将该 TaskManager 运行的所有 Task 标记为失败，执行常规的 Task 恢复。
5. 如果没有足够多的资源来达到配置的并行度，缩小作业的规模。

TaskManager 上会丢失的数据:

1. 正在执行的算子的状态，需要在 Task 重启时从 checkpoint 恢复。

TaskManager 的恢复操作:

1. 重启的 TaskManager 会寻找 ResourceManager，注册并提供它的 slot。

### ResourceManager 崩溃

TaskManager 需要向 ResourceManager 报告心跳，JobManager 需要向 ResourceManager 申请和归还资源，因此两者都可以检测到 ResourceManager 的崩溃。

在 TaskManager 端:

1. 通过报告心跳超时来检测 ResourceManager 崩溃。
2. 高可用配置下，zookeeper 会通知 TaskManager ResourceManager leader 切换。
3. TaskManager 重新进入注册循环，但是不需要取消 Task。
4. 在注册完成后，TaskManager 向新的 ResourceManager 报告当前可用的 slot 以及已经被占用的 slot 是被哪个 JobManager 占用。

在 JobManager 端:

1. 通过与 ResouceManager 的心跳检测到对方崩溃。
2. 高可用配置下，zookeeper 会通知 JobManager ResourceManager leader 切换。
3. JobManager 等待新的 ResourceManager 变为可用（leader 选举完成会有通知），然后重新请求所有正在处理中的资源分配请求。

ResourceManager 会丢失的数据:

1. 当前活跃的 container。这可以通过集群管理器恢复。
2. 可用的 slot。这可以通过 TaskManager 重新注册恢复。
3. 已经分配给 JobManager 的 slot。这也可以通过 TaskManager 重新注册恢复。
4. 正在处理的 slot 分配。这不会被恢复，JobManager 会重新对这部分 slot 进行资源请求。

### JobManager 崩溃

JobManager 的崩溃同样也可以被另外两者检测到。

在 TaskManager 端:

1. 通过心跳超时检测到 JobManager 崩溃。
2. 高可用配置下，zookeeper 会通知 TaskManager Jobmanager leader 切换。
3. TaskManager 执行 master 崩溃恢复，即清楚所有的来自该 JobManager 的 Task。
4. TaskManager 尝试在新的 JobManager 上注册自己，一段时间后会超时。
5. 如果一段时间后仍没有成功在新 JobManager 上注册，TaskManager 会将这些 slot 释放并告知 ResourceManager。

在 ResourceManager 端:

1. 通过心跳检测到 JobManager 崩溃。
2. 高可用配置下，zookeeper 会通知 ResourceManager JobManager leader 切换。
3. 可选地，告知 TaskManager JobManager 崩溃的消息。

JobManager 会丢失的数据:

1. JobGraph 和作业依赖库。这可以通过持久化存储恢复。
2. 已经完成的 checkpoint。这可以通过 HA checkpoint 存储（zookeeper）恢复。
3. Task 的执行情况。所有的 Task 都被重置为 checkpoint 的时间点。
4. 已经注册的 TaskManager。通过 TaskManager 重新注册和 ResourceManager 重新分配资源恢复。

JobManager 的恢复操作:

1. 获取 leader 状态。
2. 向 ResourceManager 注册自己（以获取 TaskManager 崩溃的通知）。
3. 从最近的 checkpoint 恢复正在运行的作业。

### JobManager 和 ResourceManager 同时崩溃

1. TaskManager 处理常规的 JobManager 崩溃。
2. TaskManager 尝试提供 slot 给恢复的 JobManager，一段时间后超时。
3. TaskManager 不断循环尝试向 ResourceManager 注册自己。

### TaskManager 和 ResourceManager 同时崩溃

1. JobManager 不能通过 ResourceManager 的通知得知 TaskManager 的崩溃，但仍可以通过 TaskManager 的心跳发现。
2. 正在处理的资源分配请求会超时（或者直接被 JobManager 取消，因为 ResourceManager 已经不可用），新的资源分配请求会在 ResourceManager 复活时再发送。
3. JobManager 需要减小作业规模，因为一些 slot 已经随 TaskManager 丢失了。

# 公共接口

这些改变不会影响到任何编程 API，但是的确会影响到 Cluster/Client 的 API （控制脚本），这些 API 并不被视为稳定或者不可变的，因此不管 FLIP-6 是否实现，这些变化都会发生。

用户面临的最大改变是 YARN CLI 的体验。尽管 FLIP-6 不要改变这些参数，但是它会使得请求回覆发生变化。比如，作业不需要等待所有的 TaskManager 都分配好资源并且可用了再被接受。

# 兼容性，过时的设计和迁移计划

- Standalone 模式仍被模仿并保持之前的兼容性。
- YARN per-job 模式会更加好用（基于来自邮件列表、isssus和外部讨论的用户反馈），所以我们直接替换了当前的模式。
- YARN session 模式会依旧像 “启动在 YARN 上的 Standalone 集群” 那样运行。

