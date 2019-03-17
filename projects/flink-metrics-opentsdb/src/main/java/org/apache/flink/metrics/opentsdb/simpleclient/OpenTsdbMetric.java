
/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.metrics.opentsdb.simpleclient;

import lombok.Builder;


import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Builder
public class OpenTsdbMetric {

    private String metric;

    private Long timestamp;

    private Object value;

    private Map<String, String> tags = new HashMap<String, String>();

    public String getMetric() {
        return metric;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public Object getValue() {
        return value;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    @Override
    public boolean equals(Object o) {

        if (o == this) {
            return true;
        }

        if (!(o instanceof OpenTsdbMetric)) {
            return false;
        }

        final OpenTsdbMetric rhs = (OpenTsdbMetric) o;

        return equals(metric, rhs.metric)
                && equals(timestamp, rhs.timestamp)
                && equals(value, rhs.value)
                && equals(tags, rhs.tags);
    }

    private boolean equals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }


    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{metric, timestamp, value, tags});
    }

    /**
     * Returns a JSON string version of this metric compatible with the HTTP API reporter.
     * <p>
     * Example:
     * <pre><code>
     * {
     *     "metric": "sys.cpu.nice",
     *     "timestamp": 1346846400,
     *     "value": 18,
     *     "tags": {
     *         "host": "web01",
     *         "dc": "lga"
     *     }
     * }
     * </code></pre>
     *
     * @return a JSON string version of this metric compatible with the HTTP API reporter.
     */

    @Override
    public String toString() {
        return "OpenTsdbMetric{" +
                "metric='" + metric + '\'' +
                ", timestamp=" + timestamp +
                ", value=" + value +
                ", tags=" + tags +
                '}';
    }
}