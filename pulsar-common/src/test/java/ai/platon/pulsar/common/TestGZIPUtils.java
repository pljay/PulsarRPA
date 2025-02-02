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

package ai.platon.pulsar.common;

import org.junit.Test;;

import java.io.IOException;

import static org.junit.Assert.*;

/** Unit tests for GZIPUtils methods. */
public class TestGZIPUtils {

    /* a short, highly compressable, string */
    String SHORT_TEST_STRING = "aaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbcccccccccccccccc";

    /* a short, highly compressable, string */
    String LONGER_TEST_STRING = SHORT_TEST_STRING + SHORT_TEST_STRING
            + SHORT_TEST_STRING + SHORT_TEST_STRING + SHORT_TEST_STRING
            + SHORT_TEST_STRING + SHORT_TEST_STRING + SHORT_TEST_STRING
            + SHORT_TEST_STRING + SHORT_TEST_STRING + SHORT_TEST_STRING
            + SHORT_TEST_STRING;

    /* a snapshot of the pulsar webpage */
    String WEBPAGE = "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">\n"
            + "<html>\n"
            + "<head>\n"
            + "  <meta http-equiv=\"content-type\"\n"
            + " content=\"text/html; charset=ISO-8859-1\">\n"
            + "  <title>Pulsar</title>\n"
            + "</head>\n"
            + "<body>\n"
            + "<h1\n"
            + " style=\"font-family: helvetica,arial,sans-serif; text-align: center; color: rgb(255, 153, 0);\"><a\n"
            + " href=\"http://www.pulsar.org/\"><font style=\"color: rgb(255, 153, 0);\">Pulsar</font></a><br>\n"
            + "<small>an open source web-search engine</small></h1>\n"
            + "<hr style=\"width: 100%; height: 1px;\" noshade=\"noshade\">\n"
            + "<table\n"
            + " style=\"width: 100%; text-align: left; margin-left: auto; margin-right: auto;\"\n"
            + " border=\"0\" cellspacing=\"0\" cellpadding=\"0\">\n"
            + "  <tbody>\n"
            + "    <tr>\n"
            + "      <td style=\"vertical-align: top; text-align: center;\"><a\n"
            + " href=\"http://sourceforge.net/project/showfiles.php?group_id=59548\">Download</a><br>\n"
            + "      </td>\n"
            + "      <td style=\"vertical-align: top; text-align: center;\"><a\n"
            + " href=\"tutorial.html\">Tutorial</a><br>\n"
            + "      </td>\n"
            + "      <td style=\"vertical-align: top; text-align: center;\"><a\n"
            + " href=\"http://cvs.sourceforge.net/cgi-bin/viewcvs.cgi/pulsar/pulsar/\">CVS</a><br>\n"
            + "      </td>\n"
            + "      <td style=\"vertical-align: top; text-align: center;\"><a\n"
            + " href=\"service/index.html\">Javadoc</a><br>\n"
            + "      </td>\n"
            + "      <td style=\"vertical-align: top; text-align: center;\"><a\n"
            + " href=\"http://sourceforge.net/tracker/?atid=491356&amp;group_id=59548&amp;func=browse\">Bugs</a><br>\n"
            + "      </td>\n"
            + "      <td style=\"vertical-align: top; text-align: center;\"><a\n"
            + " href=\"http://sourceforge.net/mail/?group_id=59548\">Lists</a></td>\n"
            + "      <td style=\"vertical-align: top; text-align: center;\"><a\n"
            + " href=\"policies.html\">Policies</a><br>\n"
            + "      </td>\n"
            + "    </tr>\n"
            + "  </tbody>\n"
            + "</table>\n"
            + "<hr style=\"width: 100%; height: 1px;\" noshade=\"noshade\">\n"
            + "<h2>Introduction</h2>\n"
            + "Pulsar is a nascent effort to implement an open-source web search\n"
            + "engine. Web search is a basic requirement for internet navigation, yet\n"
            + "the number of web search engines is decreasing. Today's oligopoly could\n"
            + "soon be a monopoly, with a single company controlling nearly all web\n"
            + "search for its commercial gain. &nbsp;That would not be good for the\n"
            + "users of internet. &nbsp;Pulsar aims to enable anyone to easily and\n"
            + "cost-effectively deploy a world-class web search engine.<br>\n"
            + "<br>\n"
            + "To succeed, the Pulsar software must be able to:<br>\n"
            + "<ul>\n"
            + "  <li> crawl several billion pages per month</li>\n"
            + "  <li>maintain an index of these pages</li>\n"
            + "  <li>search that index up to 1000 times per second</li>\n"
            + "  <li>provide very high quality search results</li>\n"
            + "  <li>operate at minimal cost</li>\n"
            + "</ul>\n"
            + "<h2>Status</h2>\n"
            + "Currently we're just a handful of developers working part-time to put\n"
            + "together a demo. &nbsp;The demo is coded entirely in Java. &nbsp;However\n"
            + "persistent data is written in well-documented formats so that modules\n"
            + "may eventually be re-written in other languages (e.g., Perl, C++) as the\n"
            + "project progresses.<br>\n"
            + "<br>\n"
            + "<hr style=\"width: 100%; height: 1px;\" noshade=\"noshade\"> <a\n"
            + " href=\"http://sourceforge.net\"> </a>\n"
            + "<div style=\"text-align: center;\"><a href=\"http://sourceforge.net\"><img\n"
            + " src=\"http://sourceforge.net/sflogo.php?group_id=59548&amp;type=1\"\n"
            + " style=\"border: 0px solid ; width: 88px; height: 31px;\"\n"
            + " alt=\"SourceForge.net Logo\" title=\"\"></a></div>\n"
            + "</body>\n"
            + "</html>\n";

    // tests

    @Test
    public void testZipUnzip() {
        byte[] testBytes = SHORT_TEST_STRING.getBytes();
        testZipUnzip(testBytes);
        testBytes = LONGER_TEST_STRING.getBytes();
        testZipUnzip(testBytes);
        testBytes = WEBPAGE.getBytes();
        testZipUnzip(testBytes);
    }

    @Test
    public void testZipUnzipBestEffort() {
        byte[] testBytes = SHORT_TEST_STRING.getBytes();
        testZipUnzipBestEffort(testBytes);
        testBytes = LONGER_TEST_STRING.getBytes();
        testZipUnzipBestEffort(testBytes);
        testBytes = WEBPAGE.getBytes();
        testZipUnzipBestEffort(testBytes);
    }

    @Test
    public void testTruncation() {
        byte[] testBytes = SHORT_TEST_STRING.getBytes();
        testTruncation(testBytes);
        testBytes = LONGER_TEST_STRING.getBytes();
        testTruncation(testBytes);
        testBytes = WEBPAGE.getBytes();
        testTruncation(testBytes);
    }

    @Test
    public void testLimit() {
        byte[] testBytes = SHORT_TEST_STRING.getBytes();
        testLimit(testBytes);
        testBytes = LONGER_TEST_STRING.getBytes();
        testLimit(testBytes);
        testBytes = WEBPAGE.getBytes();
        testLimit(testBytes);
    }

    // helpers

    public void testZipUnzip(byte[] origBytes) {
        byte[] compressedBytes = GZIPUtils.zip(origBytes);

        assert compressedBytes != null;
        assertTrue("compressed array is not smaller!", compressedBytes.length < origBytes.length);

        byte[] uncompressedBytes = null;
        try {
            uncompressedBytes = GZIPUtils.unzip(compressedBytes);
        } catch (IOException e) {
            fail("caught exception '" + e + "' during unzip()");
        }
        assertEquals("uncompressedBytes is wrong size", uncompressedBytes.length, origBytes.length);

        for (int i = 0; i < origBytes.length; i++)
            if (origBytes[i] != uncompressedBytes[i])
                fail("uncompressedBytes does not match origBytes");
    }

    public void testZipUnzipBestEffort(byte[] origBytes) {
        byte[] compressedBytes = GZIPUtils.zip(origBytes);

        assert compressedBytes != null;
        assertTrue("compressed array is not smaller!",
                compressedBytes.length < origBytes.length);

        byte[] uncompressedBytes = GZIPUtils.unzipBestEffort(compressedBytes);
        assertEquals("uncompressedBytes is wrong size", uncompressedBytes.length, origBytes.length);

        for (int i = 0; i < origBytes.length; i++)
            if (origBytes[i] != uncompressedBytes[i])
                fail("uncompressedBytes does not match origBytes");
    }

    public void testTruncation(byte[] origBytes) {
        byte[] compressedBytes = GZIPUtils.zip(origBytes);

        System.out.println("original data has len " + origBytes.length);
        assert compressedBytes != null;
        System.out.println("compressed data has len " + compressedBytes.length);

        for (int i = compressedBytes.length; i >= 0; i--) {
            byte[] truncCompressed = new byte[i];

            System.arraycopy(compressedBytes, 0, truncCompressed, 0, i);

            byte[] trunc = GZIPUtils.unzipBestEffort(truncCompressed);

            if (trunc == null) {
                // System.out.println("truncated to len " + i + ", trunc is null");
            } else {
//                System.out.println("truncated to len " + i + ", trunc.length=  "
//                        + trunc.length);

                for (int j = 0; j < trunc.length; j++)
                    if (trunc[j] != origBytes[j])
                        fail("truncated/uncompressed array differs at pos " + j
                                + " (compressed data had been truncated to len " + i + ")");
            }
        }
    }

    public void testLimit(byte[] origBytes) {
        byte[] compressedBytes = GZIPUtils.zip(origBytes);

        assertTrue("compressed array is not smaller!", compressedBytes.length < origBytes.length);

        for (int i = 0; i < origBytes.length; i++) {
            byte[] uncompressedBytes = GZIPUtils.unzipBestEffort(compressedBytes, i);
            assertEquals("uncompressedBytes is wrong size", uncompressedBytes.length, i);
            for (int j = 0; j < i; j++)
                if (origBytes[j] != uncompressedBytes[j]) {
                    fail("uncompressedBytes does not match origBytes");
                }
        }
    }

}
