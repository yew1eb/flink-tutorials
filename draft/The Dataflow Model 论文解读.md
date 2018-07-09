

The Dataflow Model：一种平衡准确性、延迟程度、处理成本的大规模无边界无序数据处理实践方法

就像Hadoop是基于Google的开源论文实现出来的
flink是在Stratospere的基础上结合The Dataflow Model这边论文提出的模型方法实现的。

这篇文章主要是在讲如果精确地调整延时程度和准确性的能力，
具体处理成本的在工程实现上进行优化，比如阿里对flink的改进。
众所周知，Apache Flink最早来源于[Stratosphere]项目，DataFlow则来源于MillWheel项目，
且DataFlow的实现是基于FlumeJava和MillWheel。Flink的实现也是借鉴自Millwheel，
因此，在流处理的实现上，两者的实现引擎可以说都来自MillWheel，进而有很多相似之处，
尽管Apache Flink在概念上有些也是基于DataFlow的论文。

### 参考文献

流计算精品翻译: The Dataflow Model - 阿里云流计算
<https://yq.aliyun.com/articles/64911>
The Dataflow Model 论文 - fxjwind的解读
<http://www.cnblogs.com/fxjwind/p/5124174.html>
Flink流计算编程–watermark（水位线）简介
<http://blog.csdn.net/lmalds/article/details/52704170>
通过Time、Window与Trigger比较Google Cloud DataFlow与Apache Flink的区别
<http://blog.csdn.net/lmalds/article/details/54291527>
分布式计算框架:Google Cloud Dataflow
<http://liweithu.com/dataflow>

<https://www.oreilly.com/ideas/the-world-beyond-batch-streaming-101>
<https://www.oreilly.com/ideas/the-world-beyond-batch-streaming-102>
<http://www.longda.us/2016/10/26/dataflow/>
http://yangxiaowei.cn/wordpress/?p=562
