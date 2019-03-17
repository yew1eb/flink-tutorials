package org.apache.flink.metrics.opentsdb.simpleclient;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.metrics.HistogramStatistics;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @Description TODO
 * @Date 2017/09/30 14:55
 * @Created by yew1eb
 */

@Slf4j
public final class MetricUtil {

    public static final char REPLACE_CHAR = '-';
    public static final String REPLACE_STRING = String.valueOf(REPLACE_CHAR);
    public static final String JOIN_STRING = ".";
    public static final String TAG_SCOPE = "scope";
    public static final String TAG_METRIC_TYPE = "metric_type";
    public static final String TAG_JOB_NAME = "job_name";


    public static final String KAFKA_CONSUMER_METRICS_GROUP = "KafkaConsumer";

    /**
     * opentsdb的metric和tags的命名约束
     * 1）大小写敏感，例如：Sys.Cpu.Usage和sys.cpu.usage表示不同的metric。
     * 2）不允许出现空格。
     * 3）只能包含如下字符: a-z，A-Z，0-9，-，_，.，或者这些字符对应的unicode字符。
     */
    public static String filterText(String input) {
        if (StringUtils.isEmpty(input)) {
            return REPLACE_STRING;
        }

        char[] chars = input.toCharArray();

        for (int i = 0; i < chars.length; ++i) {
            final char ch = chars[i];
            if (('a' <= ch && ch <= 'z') ||
                    ('A' <= ch && ch <= 'Z') ||
                    ('0' <= ch && ch <= '9') ||
                    (ch == '-') ||
                    (ch == '_') ||
                    (ch == '.')) {
                // is valid
            } else {
                chars[i] = REPLACE_CHAR;
            }
        }

        return new String(chars);
    }

    /**
     * If it is true, it is a legitimate character.
     *
     * @param c char
     * @return
     */
    private static boolean checkChar(char c) {
        return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || ('0' <= c && c <= '9') ||
                c == '-' || c == '_' ||
                c == '.' || c == ' ' || c == ',' || c == '=' || c == '/' || c == ':' ||
                c == '(' || c == ')' || c == '[' || c == ']' || c == '\'' || c == '/' || c == '#' ||
                Character.isLetter(c);
    }


    public static MetricWrapper completeMetricWrapper(MetricWrapper.MetricWrapperBuilder builder, String name, String delimiter) {
        Map<String, String> tags = new HashMap<>();
        String metricName = "";
        try {
            String[] splits = name.split(Pattern.quote(delimiter));
            for (int i = 0; i < splits.length; ++i) {
                splits[i] = filterText(splits[i]);
            }

            MetricScopeEnum metricScope = MetricScopeEnum.of(splits[0]);
            tags.put(TAG_SCOPE, metricScope.getName());

            String[] scopeFormats = metricScope.getScopeFormats();
            String[] scopeValues = Arrays.copyOfRange(splits, 1, scopeFormats.length + 1);
            String[] otherValues = Arrays.copyOfRange(splits, scopeValues.length + 1, splits.length);
            for (int i = 0; i < scopeValues.length; ++i) {
                tags.put(scopeFormats[i], scopeValues[i]);
            }

            if (otherValues.length == 0) {
                return null;
            }

            //处理scopeformat之后的部分
            if (MetricScopeEnum.OPERATOR.equals(metricScope) && MetricScopeEnum.UDF.getName().equals(otherValues[0])) {
                //用户通过MetricClient注册的自定义Metric, scope 修改为 MetricScopeEnum.UDF
                tags.put(TAG_SCOPE, MetricScopeEnum.UDF.getName());
            } else if (StringUtils.equals(otherValues[0], KAFKA_CONSUMER_METRICS_GROUP)) {//KafkaConsumer offset metrics
                tags.put(otherValues[1], otherValues[2]); // <topic-group-name, topic-name>
                tags.put(otherValues[3], otherValues[4]); // <partition-group-name, partition-number>
                otherValues = Arrays.copyOfRange(otherValues, 5, otherValues.length);
            }

            if (otherValues.length > 0) {
                metricName = StringUtils.join(otherValues, JOIN_STRING);
            } else {
                return null;
            }

        } catch (Exception e) {
            log.error("Invalid Metric {} !", name);
            return null;
        }

        return builder.name(metricName).tags(tags).build();
    }


    public static OpenTsdbMetric generateOpenTsdbMetric(String name, Number value, long timestamp, Map<String, String> tags) {
        return OpenTsdbMetric.builder()
                .metric(name)
                .value(value)
                .timestamp(timestamp)
                .tags(tags)
                .build();
    }


    public static Set<OpenTsdbMetric> generateHistogramMetircs(String name, Histogram histogram, long timestamp, Map<String, String> tags) {

        final MetricsCollector collector = MetricsCollector.createNew(name, tags, timestamp);
        final HistogramStatistics statistics = histogram.getStatistics();

        return collector.addMetric("count", histogram.getCount())
                .addMetric("max", statistics.getMax())
                .addMetric("min", statistics.getMin())
                .addMetric("mean", statistics.getMean())
                .addMetric("stddev", statistics.getStdDev())
                .addMetric("p75", statistics.getQuantile(0.75))
                .addMetric("p95", statistics.getQuantile(0.95))
                .addMetric("p98", statistics.getQuantile(0.98))
                .addMetric("p99", statistics.getQuantile(0.99))
                .addMetric("p999", statistics.getQuantile(0.999))
                .build();
    }


    private static class MetricsCollector {
        private final String prefix;
        private final Map<String, String> tags;
        private final long timestamp;
        private final Set<OpenTsdbMetric> metrics = new HashSet<OpenTsdbMetric>();

        private MetricsCollector(String prefix, Map<String, String> tags, long timestamp) {
            this.prefix = prefix;
            this.tags = tags;
            this.timestamp = timestamp;
        }

        public static MetricsCollector createNew(String prefix, Map<String, String> tags, long timestamp) {
            return new MetricsCollector(prefix, tags, timestamp);
        }

        public MetricsCollector addMetric(String metricName, Number value) {
            this.metrics.add(OpenTsdbMetric.builder()
                    .metric(prefix + "_" + metricName)
                    .value(value)
                    .timestamp(timestamp)
                    .tags(tags)
                    .build());
            return this;
        }

        public Set<OpenTsdbMetric> build() {
            return metrics;
        }
    }


    /**
     * Check the point format
     *
     * @param metric metric
     */
    public static boolean validOpenTsdbMetric(OpenTsdbMetric metric) {
        if (metric.getMetric() == null || metric.getMetric().length() == 0) {
            log.warn("The metric can't be empty, {}", metric);
            return false;
        }

        if (metric.getTimestamp() == null) {
            log.warn("The timestamp can't be null, {}", metric);
            return false;
        }

        if (metric.getTimestamp() <= 0) {
            log.warn("The timestamp can't be less than or equal to 0, {}", metric);
            return false;
        }

        if (metric.getValue() == null) {
            log.warn("The value can't be all null, {}", metric);
            return false;
        }

        if (metric.getValue() instanceof String && ((String) metric.getValue()).isEmpty()) {
            log.warn("The value can't be empty, {}", metric);
            return false;
        }

        Double value = ((Number) metric.getValue()).doubleValue();
        if (metric.getValue() instanceof Number && Double.isNaN(value)) {
            log.warn("The value can't be NaN, {}", metric);
            return false;
        }

        if (metric.getValue() instanceof Number && Double.isInfinite(value)) {
            log.warn("The value can't be INFINITY, {}", metric);
            return false;
        }

        if (metric.getTags() == null || metric.getTags().size() == 0) {
            log.warn("At least one tag is needed, {}", metric);
            return false;
        }

        for (int i = 0; i < metric.getMetric().length(); i++) {
            final char c = metric.getMetric().charAt(i);
            if (!checkChar(c)) {
                log.warn("There is an invalid character in metric. the char is '" + c + "', {}", metric);
                return false;
            }
        }

        for (Map.Entry<String, String> entry : metric.getTags().entrySet()) {
            String tagkey = entry.getKey();
            String tagvalue = entry.getValue();

            if (tagkey == null || tagkey.length() == 0) {
                log.warn("the tag key is null or empty, {}", metric);
                return false;
            }

            if (tagvalue == null || tagvalue.length() == 0) {
                log.warn("the tag value is null or empty, {}", metric);
                return false;
            }

            for (int i = 0; i < tagkey.length(); i++) {
                final char c = tagkey.charAt(i);
                if (!checkChar(c)) {
                    log.warn("There is an invalid character in tagkey. the tagkey is + "
                            + tagkey + ", the char is '" + c + "', {}", metric);
                    return false;
                }
            }

            for (int i = 0; i < tagvalue.length(); i++) {
                final char c = tagvalue.charAt(i);
                if (!checkChar(c)) {
                    log.warn("There is an invalid character in tagvalue. the tag is + <"
                            + tagkey + ":" + tagvalue + "> , the char is '" + c + "', {}", metric);
                    return false;
                }
            }
        }

        return true;
    }

}
