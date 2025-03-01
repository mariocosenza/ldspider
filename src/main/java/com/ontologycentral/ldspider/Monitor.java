package com.ontologycentral.ldspider;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Monitor {
	private final ScheduledExecutorService scheduler;

    public Monitor(List<Thread> threads, PrintStream pw, int intervalMillis) {
        scheduler = Executors.newSingleThreadScheduledExecutor();
		// The monitoring task is scheduled immediately.
		scheduler.scheduleAtFixedRate(() -> {
			for (Thread t : threads) {
				pw.println(t.getName());
			}
		}, 0, intervalMillis, TimeUnit.MILLISECONDS);
	}

	// Added start() method for compatibility with existing code.
	public void start() {
		// No action needed since the scheduler starts in the constructor.
	}

	public void shutdown() {
		scheduler.shutdownNow();
	}
}
