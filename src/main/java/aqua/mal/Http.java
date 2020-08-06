package aqua.mal;

import clojure.lang.IFn;

import com.google.common.collect.ImmutableMap;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;

import java.util.concurrent.FutureTask;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;

public class Http {
    private static final DateTimeFormatter RFC_850_DATE_TIME = new DateTimeFormatterBuilder()
            .appendPattern("EEEE, dd-MMM-").appendValueReduced(ChronoField.YEAR, 2, 2, 1992)
            .appendPattern(" HH:mm:ss zzz").toFormatter(Locale.US);
    private static final DateTimeFormatter ASCTIME_DATE_TIME = DateTimeFormatter.ofPattern("EEE MMM ppd HH:mm:ss yyyy",
            Locale.US);
    private static final ZoneId UTC = ZoneId.of("UTC");

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
            return callback.invoke(null, statusCode, body, response);
        }

        @Override
        public void onThrowable(Throwable t) {
            callback.invoke(t, null, null);
        }
    }

    private static final DefaultAsyncHttpClientConfig CONFIG = new DefaultAsyncHttpClientConfig.Builder()
            .setCompressionEnforced(true).setConnectTimeout(2500).setReadTimeout(5000).setRequestTimeout(10000).build();
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
        RequestBuilder builder = new RequestBuilder().setMethod("GET").setUrl(url).setRequestTimeout(timeout);

        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            builder.addQueryParam(entry.getKey(), entry.getValue());
        }
        Request request = builder.build();

        return executeRequest(request, callback);
    }

    public static Future<Object> getCDN(String url, String etag, int timeout, IFn callback) {
        RequestBuilder builder = new RequestBuilder().setMethod("GET").setUrl(url).setRequestTimeout(timeout);

        if (etag != null) {
            builder = builder.setHeader("If-None-Match", etag);
        }
        Request request = builder.build();

        return executeRequest(request, callback);
    }

    public static long parseDate(String httpDate) {
        ZonedDateTime date = null;

        try {
            date = ZonedDateTime.parse(httpDate, DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (DateTimeParseException e) {
            // ignore
        }

        try {
            if (date == null) {
                date = ZonedDateTime.parse(httpDate, RFC_850_DATE_TIME);
            }
        } catch (DateTimeParseException e) {
            // ignore
        }

        try {
            if (date == null) {
                date = LocalDateTime.parse(httpDate, ASCTIME_DATE_TIME).atZone(UTC);
            }
        } catch (DateTimeParseException e) {
            // ignore
        }

        if (date == null) {
            return 0;
        }
        return date.toEpochSecond();
    }

    private static Future<Object> executeRequest(Request request, IFn callback) {
        try {
            return CLIENT.executeRequest(request, new HttpHandler(callback));
        } catch (Throwable t) {
            return new FutureTask<>(() -> callback.invoke(t, null, null));
        }
    }
}
