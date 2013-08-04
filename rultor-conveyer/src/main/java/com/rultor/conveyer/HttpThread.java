/**
 * Copyright (c) 2009-2013, rultor.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following
 * disclaimer. 2) Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution. 3) Neither the name of the rultor.com nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.rultor.conveyer;

import com.jcabi.aspects.Loggable;
import com.jcabi.log.Logger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.io.IOUtils;

/**
 * HTTP thread.
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 1.0
 * @checkstyle ClassDataAbstractionCoupling (500 lines)
 */
@ToString
@EqualsAndHashCode(of = { "streams", "sockets" })
@SuppressWarnings("PMD.DoNotUseThreads")
@Loggable(Loggable.DEBUG)
final class HttpThread {

    /**
     * TOP line pattern.
     */
    private static final Pattern TOP =
        Pattern.compile("(?:GET|POST|PUT|OPTIONS) /([^ ]*) HTTP/1\\.(?:0|1)");

    /**
     * Queue of sockets to get from.
     */
    private final transient BlockingQueue<Socket> sockets;

    /**
     * Streams to work with.
     */
    private final transient Streams streams;

    /**
     * Public ctor.
     * @param sckts Sockets to read from
     * @param strms Streams
     */
    protected HttpThread(final BlockingQueue<Socket> sckts,
        final Streams strms) {
        this.sockets = sckts;
        this.streams = strms;
    }

    /**
     * Dispatch one request from the encapsulated queue.
     * @throws InterruptedException If interrupted while waiting for the queue
     */
    @Loggable(value = Loggable.DEBUG, limit = Integer.MAX_VALUE)
    public void dispatch() throws InterruptedException {
        final Socket socket = this.sockets.take();
        try {
            final BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream())
            );
            final Matcher matcher = HttpThread.TOP.matcher(reader.readLine());
            if (matcher.matches()) {
                this.process(matcher.group(1), socket.getOutputStream());
            }
        } catch (IOException ex) {
            Logger.warn(this, "failed to dispatch %s: %s", socket, ex);
        } finally {
            IOUtils.closeQuietly(socket);
        }
    }

    /**
     * Process this auth key into the given output stream.
     * @param query HTTP query string, without a leading slash
     * @param output Output stream
     * @throws IOException If fails
     */
    private void process(final String query, final OutputStream output)
        throws IOException {
        final PrintWriter writer = new PrintWriter(output);
        writer.println("HTTP/1.1 200 OK");
        writer.println("Content-Type: plain/text");
        writer.println("Cache-Control: no-cache");
        writer.println("");
        writer.flush();
        final String key;
        if (query.endsWith("?interrupt")) {
            key = query.substring(0, query.indexOf('?'));
            this.streams.interrupt(key);
        } else {
            key = query;
        }
        IOUtils.copy(this.streams.stream(key), output);
    }

}