
## 一、可用性

容错-异常退出-failover

1. yarn的container自动退出（未知原因）
2. OOM导致某些container退出
3. 程序异常导致某个container退出

部分task失败会导致整个job停止

异常自动恢复

## 二、可调试性

查看日志



## 三、监控

metric展示，历史

程序异常告警

# Flink 远程调试

`flink-conf.yaml`中添加如下参数：
```
env.java.opts.jobmanager: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
env.java.opts.taskmanager: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006"
```

![](http://wiki.jikexueyuan.com/project/intellij-idea-tutorial/images/remote-debugging-2.jpg)

+ [cwiki - Remote Debugging of Flink Clusters](https://cwiki.apache.org/confluence/display/FLINK/Remote+Debugging+of+Flink+Clusters)


