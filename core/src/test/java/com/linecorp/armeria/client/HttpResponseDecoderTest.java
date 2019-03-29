/*
 * Copyright 2019 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linecorp.armeria.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.channels.Channel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linecorp.armeria.client.HttpResponseDecoder.HttpResponseWrapper;
import com.linecorp.armeria.client.retry.Backoff;
import com.linecorp.armeria.client.retry.RetryStrategy;
import com.linecorp.armeria.client.retry.RetryingHttpClientBuilder;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.logging.RequestLogAvailability;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.server.ServerRule;

public class HttpResponseDecoderTest {
    private static final Logger logger = LoggerFactory.getLogger(HttpResponseDecoderTest.class);

    @ClassRule
    public static ServerRule rule = new ServerRule() {
        @Override
        protected void configure(ServerBuilder sb) throws Exception {
            sb.service("/", (ctx, req) -> HttpResponse.of("Hello, Armeria!"));
        }
    };

    /**
     * This test would be passed because the {@code cancelAction} method of the {@link HttpResponseWrapper} is
     * invoked in the event loop of the {@link Channel}.
     */
    @Test
    public void confirmResponseStartAndEndInTheSameThread() throws InterruptedException {
        final AtomicBoolean failed = new AtomicBoolean();
        final Backoff backoff = Backoff.exponential(200, 1000).withJitter(0.2);
        final RetryStrategy strategy = (ctx, cause) -> CompletableFuture.completedFuture(backoff);
        final HttpClient client = new HttpClientBuilder("h1c://127.0.0.1:" + rule.httpPort())
                // This increases the execution duration of 'endResponse0' of the DefaultRequestLog,
                // which means that we have more chance to reproduce the bug if two threads are racing
                // for notifying RESPONSE_END to listeners.
                .contentPreview(100)
                // In order to use a different thread to send a request.
                .decorator(new RetryingHttpClientBuilder(strategy).maxTotalAttempts(2).newDecorator())
                .decorator((delegate, ctx, req) -> {
                    final AtomicReference<Thread> responseStartedThread = new AtomicReference<>();
                    ctx.log().addListener(log -> {
                        responseStartedThread.set(Thread.currentThread());
                        if (!ctx.eventLoop().inEventLoop()) {
                            // Actually we don't expect this situation, but it is prepared for the case.
                            logger.error("{} Response started in another thread: {} != {}",
                                         ctx, ctx.eventLoop(), Thread.currentThread());
                            failed.set(true);
                        }
                    }, RequestLogAvailability.RESPONSE_START);
                    ctx.log().addListener(log -> {
                        final Thread thread = responseStartedThread.get();
                        if (thread != null && thread != Thread.currentThread()) {
                            logger.error("{} Response ended in another thread: {} != {}",
                                         ctx, thread, Thread.currentThread());
                            failed.set(true);
                        }
                    }, RequestLogAvailability.RESPONSE_END);
                    return delegate.execute(ctx, req);
                })
                .build();

        // Execute it as much as we can in order to confirm that there's no problem.
        final int n = 1000;
        final CountDownLatch latch = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            client.execute(HttpRequest.of(HttpMethod.GET, "/")).aggregate()
                  .handle((unused1, unused2) -> {
                      latch.countDown();
                      return null;
                  });
        }

        latch.await(System.getenv("CI") != null ? 60 : 10, TimeUnit.SECONDS);
        assertThat(failed.get()).isFalse();
    }
}
