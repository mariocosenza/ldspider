/*
 * Copyright (c) 2003-2005, Henri Yandell
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the
 * following conditions are met:
 *
 * + Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * + Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * + Neither the name of OSJava nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.osjava.norbert;

import java.io.IOException;
import java.io.StringReader;
import java.io.BufferedReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;

public class NoRobotClient {

    private final String userAgent;
    private RulesEngine rules;
    private RulesEngine wildcardRules;
    private URL baseUrl;

    /**
     * Create a Client for a particular user-agent name.
     *
     * @param userAgent name for the robot
     */
    public NoRobotClient(String userAgent) {
        this.userAgent = userAgent;
    }

    public void parse(String txt, URL baseUrl) throws NoRobotException {
        this.baseUrl = baseUrl;
        parseText(txt);
    }

    public void parseText(String txt) throws NoRobotException {
        this.rules = parseTextForUserAgent(txt, this.userAgent);
        this.wildcardRules = parseTextForUserAgent(txt, "*");
    }

    private RulesEngine parseTextForUserAgent(String txt, String userAgent) throws NoRobotException {

        RulesEngine engine = new RulesEngine();

        // Classic basic parser style, read an element at a time,
        // changing a state variable [parsingAllowBlock]

        // take each line, one at a time
        BufferedReader rdr = new BufferedReader(new StringReader(txt));
        String line;
        String value;
        boolean parsingAllowBlock = false;
        try {
            while ((line = rdr.readLine()) != null) {
                // trim whitespace from either side
                line = line.trim();

                // ignore lines starting with '#'
                if (line.startsWith("#")) {
                    continue;
                }

                // if User-agent == userAgent
                // record the rest up until end or next User-agent
                if (line.toLowerCase().startsWith("user-agent:")) {

                    if (parsingAllowBlock) {
                        // we've just finished reading allows/disallows
                        if (engine.isEmpty()) {
                            // multiple user agents in a line, let's
                            // wait until we get rules
                            continue;
                        } else {
                            break;
                        }
                    }

                    value = line.toLowerCase().substring("user-agent:".length()).trim();
                    if (value.equalsIgnoreCase(userAgent)) {
                        parsingAllowBlock = true;
                    }
                } else {
                    // if not, then store if we're currently the user agent
                    if (parsingAllowBlock) {
                        if (line.startsWith("Allow:")) {
                            value = line.substring("Allow:".length()).trim();
                            value = URLDecoder.decode(value, "UTF-8");
                            engine.allowPath(value);
                        } else if (line.startsWith("Disallow:")) {
                            value = line.substring("Disallow:".length()).trim();
                            value = URLDecoder.decode(value, "UTF-8");
                            engine.disallowPath(value);
                        } // else ignore other lines
                    }
                }
            }
        } catch (IOException ioe) {
            // As this is parsing a String, it should not have an IOException
            throw new NoRobotException("Problem while parsing text.", ioe);
        }

        return engine;
    }

    /**
     * Decide if the parsed website will allow this URL to be
     * seen. Note that parse(String, URL) must be called before this method.
     *
     * @param url in question
     * @return is the URL allowed?
     *
     * @throws IllegalStateException if parse has not been called.
     * @throws IllegalArgumentException if the URL does not match the baseUrl.
     */
    public boolean isUrlAllowed(URL url) throws IllegalStateException, IllegalArgumentException {
        if (rules == null) {
            throw new IllegalStateException("You must call parse before calling this method.");
        }

        if (!baseUrl.getHost().equals(url.getHost()) ||
                baseUrl.getPort() != url.getPort() ||
                !baseUrl.getProtocol().equals(url.getProtocol())) {
            throw new IllegalArgumentException("Illegal to use a different URL (" + url.toExternalForm() +
                    ") for this robots.txt: " + this.baseUrl.toExternalForm());
        }
        String urlStr = url.toExternalForm().substring(this.baseUrl.toExternalForm().length() - 1);
        if ("/robots.txt".equals(urlStr)) {
            return true;
        }
        // Replace deprecated single-argument decode with one specifying charset.
        try {
            urlStr = URLDecoder.decode(urlStr, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        Boolean allowed = this.rules.isAllowed(urlStr);
        if (allowed == null) {
            allowed = this.wildcardRules.isAllowed(urlStr);
        }
        if (allowed == null) {
            allowed = Boolean.TRUE;
        }

        return allowed;
    }

    public String toString() {
        return this.rules.toString() + " " + this.wildcardRules.toString();
    }
}
