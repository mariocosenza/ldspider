package com.ontologycentral.ldspider.hooks.error;

import java.net.URI;
import java.util.Iterator;

import org.apache.http.Header;
import org.semanticweb.yars.nx.Node;

public interface ErrorHandler {
	 void handleError(URI u, Throwable e);
	//public void handleStatus(URI u, int status, String type, long duration, long contentLength);
	 void handleStatus(URI u, int status, Header[] headers, long duration, long contentLength);
	 void handleRedirect(URI from, URI to, int status);
	 void handleLink(Node from, Node to);
	 void handleNextRound();
	 long lookups();
	 void close();
	 Iterator<ObjectThrowable> iterator();
}
