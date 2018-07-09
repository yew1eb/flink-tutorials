http://vishnuviswanath.com/flink_eventtime.html
http://vishnuviswanath.com/flink-session-windows.html
http://vishnuviswanath.com/flink_trigger_evictor.html
http://vishnuviswanath.com/flink_streaming.html
https://blog.codecentric.de/en/2017/03/distributed-stream-processing-frameworks-fast-big-data/

Flink原理与实现：Window的实现原理
<https://yq.aliyun.com/articles/225624?spm=a2c4e.11155435.0.0.2e5e7c3fODmBn2>

https://ci.apache.org/projects/flink/flink-docs-master/dev/event_time.html
windows的时间窗口的划分是从00:00:00开始的，与作业启动时的时间无关。


http://chenyuzhao.me/2017/10/18/Flink-WIndowOperator-%E7%9A%84%E8%AE%BE%E8%AE%A1/

window的触发要符合以下几个条件：

1、watermark时间 >= window_end_time

2、在[window_start_time,window_end_time)中有数据存在



watermark是一个全局的值，Event Time < watermark时间，所以来一条就触发一个window。

Flink应该如何设置最大乱序时间？

这个要结合自己的业务以及数据情况去设置。如果maxOutOfOrderness设置的太小，而自身数据发送时由于网络等原因导致乱序或者late太多，那么最终的结果就是会有很多单条的数据在window中被触发，数据的正确性影响太大。


### 参考文献

* [Introducing Stream Windows in Apache Flink](https://flink.apache.org/news/2015/12/04/Introducing-windows.html)
* Aljoscha Krettek – Notions of Time


https://ci.apache.org/projects/flink/flink-docs-release-1.3/dev/windows.html

http://www.jianshu.com/p/a883262241ef

Apache Flink：流处理中Window的概念

Apache Flink：Session Window

Tumbling Windows vs Sliding Windows区别与联系

Flink 原理与实现：Window 机制
Flink 原理与实现：Session Window
<https://yq.aliyun.com/articles/64818>

Flink如何用窗格来优化窗口
Flink流处理之窗口算子分析
Flink – WindowedStream
Flink – window operator
Flink – Trigger，Evictor
Flink流计算编程–watermark（水位线）简介
Flink流计算编程--Session Window实战
Flink流计算编程–流处理引擎的选型
Flink流处理与Kafka流
Flink流计算编程--Flink sink to Oracle
Flink流计算编程--如何实现基于KEY/VALUE的List State
Flink流计算编程：双流中实现Inner Join、Left Join与Right Join

flink watermark and checkpoint

http://www.jianshu.com/p/00bb57459ef4

http://www.jianshu.com/p/8c4a1861e49f

http://www.jianshu.com/p/68ab40c7f347

Flink流计算编程--在WindowedStream中体会EventTime与ProcessingTime 

 http://aitozi.com/2017/09/10/flink-watermark/

Flink - Generating Timestamps / Watermarks
Flink - watermark

https://blog.codecentric.de/en/2017/03/distributed-stream-processing-frameworks-fast-big-data/
http://aitozi.com/2018/01/07/Flink%20Window%E5%AE%9E%E7%8E%B0%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90/