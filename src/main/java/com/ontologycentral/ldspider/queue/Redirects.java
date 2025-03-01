package com.ontologycentral.ldspider.queue;

import java.io.Serializable;
import java.net.URI;

public interface Redirects extends Serializable{

	void put(URI from, URI to);
	
	URI getRedirect(URI from);
}