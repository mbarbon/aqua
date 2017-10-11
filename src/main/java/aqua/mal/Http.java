package aqua.mal;

import clojure.lang.IFn;

import com.google.common.collect.ImmutableMap;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;

import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Response;

public class Http {
    private static class HttpHandler extends AsyncCompletionHandler<Object> {
        private final IFn callback;

        public HttpHandler(IFn callback) {
            this.callback = callback;
        }

        @Override
        public Object onCompleted(Response response) throws Exception {
            InputStream body = response.getResponseBodyAsStream();
            int statusCode = response.getStatusCode();
            // compression is handled by Netty, no need to do it here
            return callback.invoke(null, statusCode, body);
        }

        @Override
        public void onThrowable(Throwable t){
            callback.invoke(t, null, null);
        }
    }

    private static final DefaultAsyncHttpClientConfig CONFIG =
        new DefaultAsyncHttpClientConfig.Builder()
            .setCompressionEnforced(true)
            .build();
    private static AsyncHttpClient CLIENT;

    public static synchronized void init() {
        if (CLIENT == null) {
            CLIENT = new DefaultAsyncHttpClient(CONFIG);
        }
    }

    public static Future<Object> get(String url, int timeout, IFn callback) {
        return get(url, ImmutableMap.of(), timeout, callback);
    }

    public static Future<Object> get(String url, Map<String, String> queryParams, int timeout, IFn callback) {
        BoundRequestBuilder builder =
            CLIENT
                .prepareGet(url)
                .setRequestTimeout(timeout);

        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            builder.addQueryParam(entry.getKey(), entry.getValue());
        }

        return builder.execute(new HttpHandler(callback));
    }
}
