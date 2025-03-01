package com.ontologycentral.ldspider.http.internal;

import java.io.IOException;
import javax.net.ssl.SSLHandshakeException;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.NoHttpResponseException;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;

public class HttpRequestRetryHandler implements org.apache.http.client.HttpRequestRetryHandler {

    int _retries;

    public HttpRequestRetryHandler(int retries) {
        _retries = retries;
    }

    public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
        if (executionCount >= _retries) {
            // Do not retry if over max retry count
            return false;
        }
        if (exception instanceof NoHttpResponseException) {
            // Retry if the server dropped connection on us
            return true;
        }
        if (exception instanceof SSLHandshakeException) {
            // Do not retry on SSL handshake exception
            return false;
        }
        HttpRequest request = HttpCoreContext.adapt(context).getRequest();
        // Retry if the request is considered idempotent
        return !(request instanceof HttpEntityEnclosingRequest);
    }
}
