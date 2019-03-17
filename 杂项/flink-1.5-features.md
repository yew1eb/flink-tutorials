These release notes discuss important aspects, such as configuration, behavior, or dependencies, that changed between Flink 1.4 and Flink 1.5\. Please read these notes carefully if you are planning to upgrade your Flink version to 1.5.

### [](https://github.com/apache/flink/blob/release-1.5/docs/release-notes/flink-1.5.md#update-configuration-for-reworked-job-deployment)Update Configuration for Reworked Job Deployment

Flink’s reworked cluster and job deployment component improves the integration with resource managers and enables dynamic resource allocation. One result of these changes is, that you no longer have to specify the number of containers when submitting applications to YARN and Mesos. Flink will automatically determine the number of containers from the parallelism of the application.

Although the deployment logic was completely reworked, we aimed to not unnecessarily change the previous behavior to enable a smooth transition. Nonetheless, there are a few options that you should update in your `conf/flink-conf.yaml`or know about.

*   The allocation of TaskManagers with multiple slots is not fully supported yet. Therefore, we recommend to configure TaskManagers with a single slot, i.e., set `taskmanager.numberOfTaskSlots: 1`
*   If you observed any problems with the new deployment mode, you can always switch back to the pre-1.5 behavior by configuring `mode: legacy`.

Please report any problems or possible improvements that you notice to the Flink community, either by posting to a mailing list or by opening a JIRA issue.

*Note*: We plan to remove the legacy mode in the next release.

### [](https://github.com/apache/flink/blob/release-1.5/docs/release-notes/flink-1.5.md#update-configuration-for-reworked-network-stack)Update Configuration for Reworked Network Stack

The changes on the networking stack for credit-based flow control and improved latency affect the configuration of network buffers. In a nutshell, the networking stack can require more memory to run applications. Hence, you might need to adjust the network configuration of your Flink setup.

There are two ways to address problems of job submissions that fail due to lack of network buffers.

*   Reduce the number of buffers per channel, i.e., `taskmanager.network.memory.buffers-per-channel` or
*   Increase the amount of TaskManager memory that is used by the network stack, i.e., increase `taskmanager.network.memory.fraction` and/or `taskmanager.network.memory.max`.

Please consult the section about [network buffer configuration]({{ site.baseurl }}/ops/config.html#configuring-the-network-buffers) in the Flink documentation for details. In case you experience issues with the new credit-based flow control mode, you can disable flow control by setting `taskmanager.network.credit-model: false`.

*Note*: We plan to remove the old model and this configuration in the next release.

### [](https://github.com/apache/flink/blob/release-1.5/docs/release-notes/flink-1.5.md#hadoop-classpath-discovery)Hadoop Classpath Discovery

We removed the automatic Hadoop classpath discovery via the Hadoop binary. If you want Flink to pick up the Hadoop classpath you have to export `HADOOP_CLASSPATH`. On cloud environments and most Hadoop distributions you would do

```text-roff
export HADOOP_CLASSPATH=`hadoop classpath`.
```

### [](https://github.com/apache/flink/blob/release-1.5/docs/release-notes/flink-1.5.md#breaking-changes-of-the-rest-api)Breaking Changes of the REST API

In an effort to harmonize, extend, and improve the REST API, a few handlers and return values were changed.

*   The jobs overview handler is now registered under `/jobs/overview` (before `/joboverview`) and returns a list of job details instead of the pre-grouped view of running, finished, cancelled and failed jobs.
*   The REST API to cancel a job was changed.
*   The REST API to cancel a job with savepoint was changed.

Please check the [REST API documentation]({{ site.baseurl }}/monitoring/rest_api.html#available-requests) for details.

### [](https://github.com/apache/flink/blob/release-1.5/docs/release-notes/flink-1.5.md#kafka-producer-flushes-on-checkpoint-by-default)Kafka Producer Flushes on Checkpoint by Default

The Flink Kafka Producer now flushes on checkpoints by default. Prior to version 1.5, the behaviour was disabled by default and users had to explicitly call `setFlushOnCheckpoints(true)` on the producer to enable it.

### [](https://github.com/apache/flink/blob/release-1.5/docs/release-notes/flink-1.5.md#updated-kinesis-dependency)Updated Kinesis Dependency

The Kinesis dependencies of Flink’s Kinesis connector have been updated to the following versions.

```text-roff
<aws.sdk.version>1.11.319</aws.sdk.version>
<aws.kinesis-kcl.version>1.9.0</aws.kinesis-kcl.version>
<aws.kinesis-kpl.version>0.12.9</aws.kinesis-kcl.version>
```


Flink 1.5.0 is out! This release includes many significant features, such as a completely reworked deployment model supporting dynamic resource allocations, improved latency, faster recovery, broadcast state, more SQL joins. Check out the announcement: [http://flink.apache.org/news/2018/05/25/release-1.5.0.html …](https://t.co/TjnjXqSE4U "http://flink.apache.org/news/2018/05/25/release-1.5.0.html")

1.5.0 版終於釋出囉！
這版本是還蠻重大的里程碑的，為了更長遠的 roadmap 舖了許多功能，
層面包含 resource elasticity，recovery speed，SQL client，broadcast state，network stack 等:

1. Process model rework:

這部分重點在於 enabling dynamic resource allocation，為之後支援 dynamic rescaling for stateful streaming jobs 做準備。
同時，這個 runtime 重造也讓 Flink on Kubernetes 變得更簡單了。

2. Network stack improvements / credit based flow-control:

解決的問題是之前會因為單一個 stream partition channel 堵塞而讓所有 partition 都被 backpressure，主要原因是所有的 stream partition channel 都會 multiplex 到單一個 TM 之間的 TCP connection。
解法就是在 TCP connection 之上多加了一層 Flink-managed flow control，細節可以再翻一下之前的設計文件。

3. Task local recovery:

這個 feature 讓 Flink 在做 checkpoint recovery 時可以不用都到 DFS 去 read state。
這個再搭配之前已經有的 fine-grained recovery，讓 Flink 的 recovery speed 再次提升了。

4. SQL client:

不用寫 code 了! 1.5.0 版開始提供一個純 SQL client，可以同時下 batch / streaming SQL。
目前這個功能仍算是 beta，但對於 unified batch / streaming interface 和 easier accessibility 來看都是一個大進展。