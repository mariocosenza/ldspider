package com.ontologycentral.ldspider.http.internal;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.apache.http.conn.HttpClientConnectionManager;

public class CloseIdleConnectionThread {
	private final static Logger log = Logger.getLogger(CloseIdleConnectionThread.class.getSimpleName());

	private final HttpClientConnectionManager _cm;
	private final long _st;
	private final ScheduledExecutorService scheduler;

	public CloseIdleConnectionThread(HttpClientConnectionManager cm, long sleepTime) {
		_cm = cm;
		_st = sleepTime;
		scheduler = Executors.newSingleThreadScheduledExecutor();
		log.info("Initialised " + CloseIdleConnectionThread.class.getSimpleName() + " with sleepTime " + _st + " ms");
	}

	public void start() {
		log.info("Starting " + CloseIdleConnectionThread.class.getSimpleName());
		// Schedule the task to run at fixed intervals (_st milliseconds)
		scheduler.scheduleAtFixedRate(() -> {
			log.info("Closing expired and idle connections");
			_cm.closeExpiredConnections();
			_cm.closeIdleConnections(0L, TimeUnit.SECONDS);
		}, 0, _st, TimeUnit.MILLISECONDS);
	}

	public void shutdown() {
		log.info("Stopping " + CloseIdleConnectionThread.class.getSimpleName());
		scheduler.shutdownNow();
	}
}
