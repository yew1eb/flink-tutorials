Flink容错机制


https://zhuanlan.zhihu.com/p/34358365

（基于FLIP6的YARN架构）

YARN application master启动：YarnFlinkApplicationMasterRunner
runApplicationMaster(flinkConfig) 
启动RPC服务
初始化resource manager
初始化job master参数
启动resource manager
启动job manager runner
JobManagerRunner流程： 
从jar包加载作业图：jobGraph = loadJobGraph(config);
BlobLibraryCacheManager注册作业，库和class loader
设置ha服务
启动JobManager
JobManager流程： 
初始化作业（name, jobid)
初始化重启策略
初始化任务槽池
构建执行图ExecutionGraph
为ExecutionGraph注册状态监听器
通过作业图构建执行图buildGraph： 
初始化具有initializeOnMaster钩子的vertex（输出格式创建目录，输入格式创建splits）
将 job vertices 拓扑排序，并附加到执行图
配置state checkpointing
创建执行图的metrics
executionGraph.enableCheckpointing：创建CheckpointCoordinator，触发和提交检查点，保存状态。
执行图的监听器监听作业状态，到达JobStatus.RUNNING时启动检查点调度器startCheckpointScheduler()，定期触发triggerCheckpoint（）。
triggerCheckpoint（）:发送消息给任务管理器，触发检查点：execution.triggerCheckpoint(checkpointID, timestamp, checkpointOptions); 
taskManagerGateway.triggerCheckpoint(attemptId, getVertex().getJobId(), checkpointId, timestamp, checkpointOptions);
任务管理器接收到TriggerCheckpoint消息：从消息里解出3个参数并传给task触发：task.triggerCheckpointBarrier(checkpointId, timestamp, checkpointOptions)
task.triggerCheckpointBarrier(checkpointId, timestamp, checkpointOptions)：异步执行快照动作。

CheckpointMetaData(checkpointID, checkpointTimestamp)

StreamTask.performCheckpoint(checkpointMetaData, checkpointOptions, checkpointMetrics);：

向下游operatorChain广播: CheckpointBarrier(id, timestamp, checkpointOptions)

checkpointingOperation.executeCheckpointing();

提交一个AsyncCheckpointRunnable线程异步执行
State Backend执行snapshot()操作。