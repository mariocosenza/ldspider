package com.ontologycentral.ldspider.queue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import org.semanticweb.yars.tld.TldManager;

import com.ontologycentral.ldspider.CrawlerConstants;
import com.ontologycentral.ldspider.frontier.DiskFrontier;
import com.ontologycentral.ldspider.frontier.Frontier;
import com.ontologycentral.ldspider.frontier.RankedFrontier;
import com.ontologycentral.ldspider.frontier.SortingDiskFrontier;
import com.ontologycentral.ldspider.seen.Seen;

public class BreadthFirstQueue extends RedirectsFavouringSpiderQueue {
	private static final long serialVersionUID = 1L;
	private static final Logger _log = Logger.getLogger(BreadthFirstQueue.class.getName());

	Map<String, Queue<URI>> _queues;
	Queue<String> _current;

	/**
	 * Point in time of the last schedule or the last queue turnaround.
	 */
	long _time;

	/**
	 * Maximum URIs per pay-level-domain.
	 */
	int _maxuris;

	/**
	 * Maximum number of plds to keep.
	 */
	int _maxplds;

	/**
	 * Minimum active plds; when reached, the hop should finish to avoid starvation.
	 */
	int _minActPlds;

	/**
	 * Scheduled frontiers should equal hops+1.
	 */
	int _scheduledFrontiers;

	/**
	 * Indicates if the minimum active plds have been reached.
	 */
	boolean _minReached;

	/**
	 * Whether the minimum plds limit should already apply for the seedlist.
	 */
	boolean _minActPldsAlready4Seedlist;

	public BreadthFirstQueue(TldManager tldm, Redirects redirs, Seen seen, int maxuris, int maxplds, int minActPlds, boolean minActPldsAlready4Seedlist) {
		super(tldm, redirs, seen);
		_maxuris = (maxuris == -1) ? Integer.MAX_VALUE - 1 : maxuris;
		_maxplds = (maxplds == -1) ? Integer.MAX_VALUE - 1 : maxplds;
		_minActPlds = minActPlds;
		_current = new ConcurrentLinkedQueue<>();
		_queues = Collections.synchronizedMap(new HashMap<>());
		_minReached = false;
		_scheduledFrontiers = 0;
		_minActPldsAlready4Seedlist = minActPldsAlready4Seedlist;
	}

	/**
	 * Schedules URIs from the frontier into the queue.
	 */
	public synchronized void schedule(Frontier f) {
		_log.info("start scheduling...");
		_minReached = false;
		long time = System.currentTimeMillis();

		_queues.clear();
		for (URI u : f) {
			if (!checkSeen(u)) {
				add(u, true);
			}
		}

		// For cases where _minActPlds is disabled (<0)
		if (_minActPlds < 0) {
			for (String pld : _queues.keySet()) {
				Queue<URI> q = _queues.get(pld);
				int maxuris = _maxuris;
				for (String s : CrawlerConstants.SITES_SLOW) {
					if (s.equals(pld)) {
						maxuris = maxuris / CrawlerConstants.SLOW_DIV;
					}
				}
				if (q.size() > maxuris) {
					int n = 0;
					ConcurrentLinkedQueue<URI> nq = new ConcurrentLinkedQueue<>();
					for (URI u : q) {
						nq.add(u);
						n++;
						if (n >= maxuris) {
							break;
						}
					}
					q = nq;
					_queues.put(pld, q);
				}
			}
		}

		List<String> lipld = getQueuePlds(_minActPlds < 0);
		_log.info("sorted pld list " + lipld.toString());

		if (_maxplds < Integer.MAX_VALUE - 1) {
			for (int i = _maxplds; i < lipld.size(); i++) {
				String pld = lipld.get(i);
				_queues.remove(pld);
				_log.fine("removing " + pld);
			}
		}

		_current.addAll(_queues.keySet());

		// Reset the frontier if it is a DiskFrontier, RankedFrontier, or SortingDiskFrontier.
		if (f instanceof DiskFrontier || f instanceof RankedFrontier || f instanceof SortingDiskFrontier) {
			f.reset();
		}

		++_scheduledFrontiers;
		_time = System.currentTimeMillis();

		_log.info("scheduling " + _current.size() + " plds done (" + size()
				+ " URIs) in " + (_time - time) + " ms. This was schedule No. "
				+ _scheduledFrontiers);
		_log.info(toString());

		// Notify waiting threads that new items are available.
		notifyAll();
	}

	/**
	 * Polls a URI in a round-robin fashion, waiting if necessary rather than busy-waiting.
	 */
	protected synchronized URI pollInternal() {
		if (_current == null) {
			return null;
		}

		URI next = null;
		long startTime = System.currentTimeMillis();

		while (true) {
			long now = System.currentTimeMillis();
			// If _current is empty or maximum delay exceeded, refresh the queue.
			if (_current.isEmpty() || (_minActPlds < 0 && (now - _time) > CrawlerConstants.MAX_DELAY)) {
				if (size() == 0) {
					return null;
				}
				long elapsed = now - _time;
				if (elapsed < CrawlerConstants.MIN_DELAY) {
					long waitTime = CrawlerConstants.MIN_DELAY - elapsed;
					try {
						_log.info("Waiting for " + waitTime + " ms...");
						wait(waitTime);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				_log.info("Queue turnaround in " + (System.currentTimeMillis() - _time) + " ms");
				_time = System.currentTimeMillis();
				List<String> lipld = getQueuePlds(_minActPlds < 0);
				_current.addAll(lipld);
				if (_minActPlds > -1 && _current.size() < _minActPlds && (_minActPldsAlready4Seedlist || _scheduledFrontiers > 1)) {
					_log.info("The minimum number of active PLDs has been reached. Finishing this round...");
					_minReached = true;
				}
			}
			if (_minReached) {
				return null;
			}
			String pld = _current.poll();
			if (pld == null) {
				// Nothing available, so wait briefly for new items.
				try {
					wait(CrawlerConstants.MIN_DELAY);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				continue;
			}
			Queue<URI> q = _queues.get(pld);
			if (q != null && !q.isEmpty()) {
				next = q.poll();
				if (checkSeen(next)) {
					next = null;
				} else {
					setSeen(next);
					break;
				}
			}
		}

		long endTime = System.currentTimeMillis();
		_log.fine("Poll for " + next + " done in " + (endTime - startTime) + " ms");
		return next;
	}

	List<String> getSortedQueuePlds() {
		return getQueuePlds(true);
	}

	List<String> getQueuePlds(boolean sorted) {
		List<String> li = new ArrayList<>();
		for (String pld : _queues.keySet()) {
			if (!_queues.get(pld).isEmpty()) {
				li.add(pld);
			}
		}
		if (sorted)
			li.sort(new PldCountComparator(_queues));
		return li;
	}

	public synchronized void add(URI u, boolean uriHasAlreadyBeenProcessed) {
		if (!uriHasAlreadyBeenProcessed) {
			try {
				u = Frontier.normalise(u);
			} catch (URISyntaxException e) {
				_log.info(u + " not parsable, skipping " + u);
				return;
			}
		}
		String pld = _tldm.getPLD(u);
		if (pld != null) {
			Queue<URI> q = _queues.computeIfAbsent(pld, k -> new ConcurrentLinkedQueue<>());
			q.add(u);
			// Notify waiting threads that a new URI is available.
			notifyAll();
		}
	}

	public int size() {
		int size = super.size();
		for (Queue<URI> q : _queues.values()) {
			size += q.size();
		}
		return size;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (String pld : _queues.keySet()) {
			Queue<URI> q = _queues.get(pld);
			sb.append(pld).append(": ").append(q.size()).append("\n");
		}
		sb.append("Plus ").append(_redirectsQueue.size()).append(" redirects.\n");
		return sb.toString();
	}
}
