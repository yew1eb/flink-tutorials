package org.apache.flink.metrics.api.simple;

import org.apache.flink.metrics.Gauge;

/**
 * @author zhouhai
 * @createTime 2017/12/22
 * @description 统计一个瞬时值，eg. memUsed, cpuRatio，queueSize等这种瞬间的值
 */

public abstract class SmplGauge<T> implements Gauge<T> {

}
