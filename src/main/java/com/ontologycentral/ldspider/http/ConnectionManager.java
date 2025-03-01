package com.ontologycentral.ldspider.http;

import java.io.IOException;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import com.ontologycentral.ldspider.CrawlerConstants;
import com.ontologycentral.ldspider.http.internal.CloseIdleConnectionThread;
import com.ontologycentral.ldspider.http.internal.HttpRequestRetryHandler;
import com.ontologycentral.ldspider.http.internal.ResponseGzipUncompress;

public class ConnectionManager {

	private CloseableHttpClient _client;
	private final PoolingHttpClientConnectionManager cm;
	private final RequestConfig requestConfig;
	private final CredentialsProvider credsProvider;
	private final CloseIdleConnectionThread _ciThread;

	public ConnectionManager(String proxyHost, int proxyPort, String puser, String ppassword, int connections) {
		// Create a pooling connection manager
		cm = new PoolingHttpClientConnectionManager();
		cm.setMaxTotal(connections);
		cm.setDefaultMaxPerRoute(connections);

		// Build request configuration
		RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
		requestConfigBuilder.setSocketTimeout(CrawlerConstants.SOCKET_TIMEOUT);
		requestConfigBuilder.setConnectTimeout(CrawlerConstants.CONNECTION_TIMEOUT);
		requestConfigBuilder.setRedirectsEnabled(false); // handle redirects manually

		if (proxyHost != null) {
			HttpHost proxy = new HttpHost(proxyHost, proxyPort, "http");
			requestConfigBuilder.setProxy(proxy);
		}
		requestConfig = requestConfigBuilder.build();

		// Set up credentials if a proxy user is provided
		if (proxyHost != null && puser != null) {
			credsProvider = new BasicCredentialsProvider();
			credsProvider.setCredentials(new AuthScope(proxyHost, proxyPort),
					new UsernamePasswordCredentials(puser, ppassword));
		} else {
			credsProvider = null;
		}

		// Build the HTTP client with gzip response interceptor
		_client = HttpClients.custom()
				.setConnectionManager(cm)
				.setDefaultRequestConfig(requestConfig)
				.setDefaultCredentialsProvider(credsProvider)
				.addInterceptorFirst(new ResponseGzipUncompress())
				.build();

		// Start the idle connection closing thread
		_ciThread = new CloseIdleConnectionThread(cm, CrawlerConstants.CLOSE_IDLE);
		_ciThread.start();
	}

	public void setRetries(int no) {
		// Rebuild the client with a retry handler if needed.
		if (no > 0) {
			HttpRequestRetryHandler retryHandler = new HttpRequestRetryHandler(no);
			CloseableHttpClient newClient = HttpClients.custom()
					.setConnectionManager(cm)
					.setDefaultRequestConfig(requestConfig)
					.setDefaultCredentialsProvider(credsProvider)
					.setRetryHandler(retryHandler)
					.addInterceptorFirst(new ResponseGzipUncompress())
					.build();
			try {
				_client.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			_client = newClient;
		}
	}

	public void shutdown() {
		_ciThread.shutdown();
		try {
			_client.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public HttpResponse connect(HttpGet get) throws IOException {
		return _client.execute(get);
	}
}
