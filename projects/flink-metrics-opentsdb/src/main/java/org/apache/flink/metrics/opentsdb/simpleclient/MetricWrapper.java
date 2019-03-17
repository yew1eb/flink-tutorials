package org.apache.flink.metrics.opentsdb.simpleclient;

import lombok.Builder;
import lombok.Data;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Metric;

import java.util.Map;

/**
 * @Description TODO
 * @Date 2018/10/29 12:18
 * @Created by yew1eb
 */

@Builder
@Data
public class MetricWrapper {

    private final Metric metricRef;
    private final String name;
    private final Map<String, String> tags;
}
