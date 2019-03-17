package org.apache.flink.metrics;

import org.apache.flink.metrics.opentsdb.simpleclient.MetricUtil;
import org.apache.flink.metrics.opentsdb.simpleclient.MetricWrapper;
import org.apache.flink.metrics.opentsdb.simpleclient.OpenTsdbMetric;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description TODO
 * @Date 2018/11/6 12:28
 * @Created by yew1eb
 */
public class MetricParseTest {
    private MetricWrapper.MetricWrapperBuilder builder = MetricWrapper.builder();
    private String delimiter = "$";

    @Test
    public void testParse1() {
        String name = "OPERATOR$gh-data-hdp-dn-rtyarn0017$container_e03_1535179038827_48514538_01_000003$Flink-Streaming-Job$time-attribute---event_time-$1$numRecordsIn";
        Assert.assertNotNull(MetricUtil.completeMetricWrapper(builder, name, delimiter));
    }

    /**
     * Test filter legacy Committed Offsets Metric
     */
    @Test
    public void testParse2() {
        String name = "OPERATOR$gh-data-hdp-dn-rtyarn0010$container_e03_1535182620333_2137272_01_000003$Flink-Streaming-Job$Source--Kafka08TableSource-event_time--order_key--user_id--proc_time--region_id-$2$KafkaConsumer$committed-offsets$app.hbdata_hotel_order_pay-0";
        Assert.assertNull(MetricUtil.completeMetricWrapper(builder, name, delimiter));
    }


    @Test
    public void testValidOpenTsdbMetric() {
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "val1");

        OpenTsdbMetric metric = OpenTsdbMetric.builder().metric("KafkaProducer.io-wait-time-ns-avg").timestamp(1541480077L)
                .tags(tags)
                .value(Float.POSITIVE_INFINITY)
                .build();

        Assert.assertTrue(!MetricUtil.validOpenTsdbMetric(metric));
    }

}
