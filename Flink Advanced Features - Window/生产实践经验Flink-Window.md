# Event time and watermark

这两个货是用来干啥的?处理 out-of-order events.

## when i use event time window, it's just doesn't trigger?

这最可能是watermark的问题，可能的cases:

1、no data from the source 

2、one of the source parallelisms doesn't have data send to upstream operator

3、the time field extracted from the record should be millisecond instead of second.

4、Data should cover a longer time spam than the window size to advance the event time. 啥意思？

## how monitoring current event time ?

flink有一个task级别的指标叫做currentLowWatermark,用来表示这个task当前接收到的最小的watermark. 在Flink UI里面可以找到这个task指标。

## Handling Event Time Stragglers

(针对不同的场景，watermark的生产方式建议)

Approach 1: Watermark stays late (indicated completeness), windows fire early

Approach 2: Watermark heuristic with maximum lateness, windows accept late data

