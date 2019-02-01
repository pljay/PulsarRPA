/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.platon.pulsar.protocol.httpclient;

// JDK imports

import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Commons Codec imports
// Commons Logging imports
// Hadoop imports

/**
 * Implementation of RFC 2617 Basic Authentication. Usernames and passwords are
 * stored in standard configuration files using the following properties:
 * http.auth.basic.<realm>.user http.auth.basic.<realm>.pass
 */
public class HttpBasicAuthentication implements HttpAuthentication,
        Configurable {

    public static final Logger LOG = LoggerFactory
            .getLogger(HttpBasicAuthentication.class);

    private static Pattern basic = Pattern
            .compile("[bB][aA][sS][iI][cC] [rR][eE][aA][lL][mM]=\"(\\w*)\"");

    private static Map<String, HttpBasicAuthentication> authMap = new TreeMap<>();

    private Configuration conf = null;
    private String challenge = null;
    private ArrayList<String> credentials = null;
    private String realm = null;

    /**
     * Construct an HttpBasicAuthentication for the given challenge parameters.
     * The challenge parameters are returned by the web server using a
     * WWW-Authenticate header. This will typically be represented by single line
     * of the form <code>WWW-Authenticate: Basic realm="myrealm"</code>
     *
     * @param challenge
     *          WWW-Authenticate header from web server
     */
    protected HttpBasicAuthentication(String challenge, Configuration conf)
            throws HttpAuthenticationException {

        setConf(conf);
        this.challenge = challenge;
        credentials = new ArrayList<String>();

        String username = this.conf.get("http.auth.basic." + challenge + ".user");
        String password = this.conf.get("http.auth.basic." + challenge
                + ".password");

        if (LOG.isTraceEnabled()) {
            LOG.trace("BasicAuthentication challenge is " + challenge);
            LOG.trace("BasicAuthentication username=" + username);
            LOG.trace("BasicAuthentication password=" + password);
        }

        if (username == null) {
            throw new HttpAuthenticationException("Username for " + challenge
                    + " is null");
        }

        if (password == null) {
            throw new HttpAuthenticationException("Password for " + challenge
                    + " is null");
        }

        byte[] credBytes = (username + ":" + password).getBytes();
        credentials.add("Authorization: Basic "
                + new String(Base64.encodeBase64(credBytes)));
        if (LOG.isTraceEnabled()) {
            LOG.trace("Basic credentials: " + credentials);
        }
    }

  /*
   * ---------------------------------- * <implementation:Configurable> *
   * ----------------------------------
   */

    /**
     * This method is responsible for providing Basic authentication information.
     * The method caches authentication information for each realm so that the
     * required authentication information does not need to be regenerated for
     * every request.
     *
     * @param challenge
     *          The challenge string provided by the webserver. This is the text
     *          which follows the WWW-Authenticate header, including the Basic
     *          tag.
     * @return An HttpBasicAuthentication object or null if unable to generate
     *         appropriate credentials.
     */
    public static HttpBasicAuthentication getAuthentication(String challenge,
                                                            Configuration conf) {
        if (challenge == null)
            return null;
        Matcher basicMatcher = basic.matcher(challenge);
        if (basicMatcher.matches()) {
            String realm = basicMatcher.group(1);
            Object auth = authMap.get(realm);
            if (auth == null) {
                HttpBasicAuthentication newAuth = null;
                try {
                    newAuth = new HttpBasicAuthentication(realm, conf);
                } catch (HttpAuthenticationException hae) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("HttpBasicAuthentication failed for " + challenge);
                    }
                }
                authMap.put(realm, newAuth);
                return newAuth;
            } else {
                return (HttpBasicAuthentication) auth;
            }
        }
        return null;
    }

    /**
     * Provides a pattern which can be used by an outside resource to determine if
     * this class can provide credentials based on simple header information. It
     * does not calculate any information regarding realms or challenges.
     *
     * @return Returns a Pattern which will match a Basic WWW-Authenticate header.
     */
    public static final Pattern getBasicPattern() {
        return basic;
    }

  /*
   * ---------------------------------- * <implementation:Configurable> *
   * ----------------------------------
   */

    public Configuration getConf() {
        return this.conf;
    }

    public void setConf(Configuration conf) {
        this.conf = conf;
        // if (conf.getBoolean("http.auth.verbose", false)) {
        // log.setLevel(Level.FINE);
        // } else {
        // log.setLevel(Level.WARNING);
        // }
    }

    /**
     * Gets the Basic credentials generated by this HttpBasicAuthentication object
     *
     * @return Credentials in the form of
     *         <code>Authorization: Basic &lt;Base64 encoded userid:password&gt;
     *
     */
    public List<String> getCredentials() {
        return credentials;
    }

    /**
     * Gets the realm attribute of the HttpBasicAuthentication object. This should
     * have been supplied to the {@link #getAuthentication(String, Configuration)}
     * static method
     *
     * @return The realm
     */
    public String getRealm() {
        return realm;
    }
}
