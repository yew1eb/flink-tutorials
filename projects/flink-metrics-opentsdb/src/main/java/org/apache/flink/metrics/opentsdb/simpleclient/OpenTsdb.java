package org.apache.flink.metrics.opentsdb.simpleclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * @Description TODO
 * @Date 2018/10/30 15:11
 * @Created by yew1eb
 */

@Slf4j
public class OpenTsdb {
    public static final String PUT_POST_API = "/api/put";
    public static final int DEFAULT_BATCH_SIZE_LIMIT = 0;
    public static final Gson gson = new GsonBuilder().create();

    private enum ExpectResponse {
        STATUS_CODE, SUMMARY, DETAIL
    }

    private String serviceUrl;
    private PoolingHttpClient httpClient;
    private int batchSizeLimit;

    public OpenTsdb(String url) {
        this.serviceUrl = buildUrl(url, PUT_POST_API, ExpectResponse.DETAIL);
        this.batchSizeLimit = DEFAULT_BATCH_SIZE_LIMIT;
        this.httpClient = new PoolingHttpClient();
    }

    public OpenTsdb(String url, int batchSize) {
        this.serviceUrl = buildUrl(url, PUT_POST_API, ExpectResponse.DETAIL);
        this.batchSizeLimit = batchSize;
        this.httpClient = new PoolingHttpClient();
    }

    /**
     * @param serviceUrl
     * @param postApiEndPoint
     * @param expectResponse
     * @return
     */
    private String buildUrl(String serviceUrl, String postApiEndPoint,
                            ExpectResponse expectResponse) {
        String url = serviceUrl + postApiEndPoint;

        switch (expectResponse) {
            case SUMMARY:
                url += "?summary";
                break;
            case DETAIL:
                url += "?details";
                break;
            default:
                break;
        }
        log.info("serviceUrl:{}", url);
        return url;
    }

    /**
     * Send a metric to opentsdb
     *
     * @param metric
     */
    public void send(OpenTsdbMetric metric) {
        send(Collections.singletonList(metric));
    }

    /**
     * send a set of metrics to opentsdb
     *
     * @param metrics
     */
    public void send(List<OpenTsdbMetric> metrics) {
        // we set the patch size because of existing issue in opentsdb where large batch of metrics failed
        // see at https://groups.google.com/forum/#!topic/opentsdb/U-0ak_v8qu0
        // we recommend batch size of 5 - 10 will be safer
        // alternatively you can enable chunked request
        if (batchSizeLimit > 0 && metrics.size() > batchSizeLimit) {
            final List<OpenTsdbMetric> smallMetrics = new ArrayList<>();
            for (final OpenTsdbMetric metric : metrics) {
                smallMetrics.add(metric);
                if (smallMetrics.size() >= batchSizeLimit) {
                    sendHelper(smallMetrics);
                    smallMetrics.clear();
                }
            }
            sendHelper(smallMetrics);
        } else {
            sendHelper(metrics);
        }
    }

    private void sendHelper(List<OpenTsdbMetric> metrics) {
        /*
         * might want to bind to a specific version of the API.
         * according to: http://opentsdb.net/docs/build/html/api_http/index.html#api-versioning
         * "if you do not supply an explicit version, ... the latest version will be used."
         * circle back on this if it's a problem.
         */
        if (!metrics.isEmpty()) {
            try {
                Response response = httpClient.doPost(serviceUrl, gson.toJson(metrics));
                if (!response.isSuccess()) {
                    log.info("send to opentsdb endpoint failed, response = {}", response);
                }
            } catch (Exception ex) {
                log.error("send to opentsdb endpoint failed, metrics = {}", ex, gson.toJson(metrics));
            }
        }
    }
}
