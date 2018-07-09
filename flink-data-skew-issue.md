

## 数据倾斜

## 问题提出
用户发现作业处理性能很差，增加资源，增大并行度也没有发现性能有很大的提升！
根据经验出现这种case，应该是数据出现了热点、出现了数据倾斜导致性能处理的很差！
总结一下数据倾斜的现象：
1、数据倾斜直接会导致一种情况：OOM。
2、运行速度慢,特别慢，非常慢，极端的慢，不可接受的慢。
3、某一些task处理数据量明显高于其他task
怎么来解决这样的问题呢？
分情况来看这个问题。
##  为skew的key增加随机前缀/后缀
解决的问题是按key分组，部分key组的数据量特别大，而keyby之后，同一个key组的数据会发往同一个task处理，而倾斜的key组落到的task会明显的处理不过。
我们要做的就是将倾斜的数据分散到不同的task中。
预聚合 + 二次（最终）聚合
预聚合： 给key加随机前缀/后缀，原来相同的key编程不同的key，这样就将原本会在同一个task上处理的数据分散到多个task上去做局部聚合，
进而解决单个task处理数据量过多的问题。
接着去除掉随机前缀，再次进行全局聚合，就可以得到最终的结果。


（相当于将其数据增到到原来的N倍，N即为随机前缀的总个数）


Keys are distributed to worker threads by hash partitioning. 
This means that the key values are hashed and the thread is determined by modulo #workers. 
With two keys and two threads there is a good chance that both keys are assigned to the same thread.
You can try to use different key values whose hash values distribute across both threads.

HashPartitioner, keys分组根据key的哈希与output channel数取模。

keyby的时候可以不用HashPartitioner吗？ 用随机Shuffle可以吗？为什么？

下图就是一个很清晰的例子：hello这个key，在三个节点上对应了总共7条数据，这些数据都会被拉取到同一个task中进行处理;而world和you这两个key分别才对应1条数据，
所以另外两个task只要分别处理1条数据即可。此时第一个task的运行时间可能是另外两个task的7倍，而整个stage的运行速度也由运行最慢的那个task所决定。
![](http://s2.51cto.com/wyfs02/M01/8A/16/wKiom1gluEGChZwmAABvm6amm0k523.jpg)



## 参考文献

+ Petra关于数据倾斜的解决方案<https://123.sankuai.com/km/page/28185761>
+ 四种解决Spark数据倾斜（Data Skew）的方法<https://www.iteblog.com/archives/2061.html>
