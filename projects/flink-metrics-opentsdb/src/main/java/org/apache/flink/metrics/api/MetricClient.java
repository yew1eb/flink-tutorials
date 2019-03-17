package org.apache.flink.metrics.api;

import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.metrics.*;

/**
 * @author zhouhai
 * @createTime 2017/12/10
 * @description
 */
public class MetricClient {
    private static final String UDF_GROUP = "UDF";
    private MetricGroup metricGroup;

    public MetricClient(RuntimeContext context) {
        metricGroup = context.getMetricGroup().addGroup(UDF_GROUP);
    }

    public Counter registerCounter(String name, Counter counter) {
        return metricGroup.counter(name, counter);
    }

    public Gauge registerGauge(String name, Gauge gauge) {
        return metricGroup.gauge(name, gauge);
    }

    public Histogram registerHistogram(String name, Histogram histogram) {
        return metricGroup.histogram(name, histogram);
    }

    public Meter registerMeter(String name, Meter meter) {
        return metricGroup.meter(name, meter);
    }
}
