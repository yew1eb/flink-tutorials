## What the difference between checkpoint and savepoint ?



checkpoint周期性在flink内部自动制作的，被用于运行时作业failure了重新重启恢复。

savepoint是手动触发制作的，和checkpoint在物理上都是snapshot. 被用于启动一个“new"作业保持内部的状态在以下场景中使用：bug fixing / flink version upgrade / A/B testing / Auto scaling



## Introduce a friendly way to resume the job from externalized checkpoints automatically
<https://issues.apache.org/jira/browse/FLINK-9043>


## Checkpointing only works if all operators/tasks are still running
<https://issues.apache.org/jira/browse/FLINK-2491>


