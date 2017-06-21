/*
 * Copyright (c) 2017. iDoc LLC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     (1) Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     (2) Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *
 *     (3)The name of the author may not be used to
 *     endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package ua.papka24.server.service.events.handler;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Request;
import com.ning.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.service.events.handler.data.HttpMessage;
import ua.papka24.server.service.events.main.data.Notification;
import ua.papka24.server.service.events.main.data.NotifyResult;
import ua.papka24.server.utils.httpclient.AsyncClientHolder;

import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


abstract class HttpPostHandler<T extends Notification> extends Handler<T>{

    private static final Logger log = LoggerFactory.getLogger(HttpPostHandler.class);

    private static final RepeatHolder<HttpMessage> repeatHolder = new RepeatHolder<>();

    HttpPostHandler(){
    }

    public abstract Queue<HttpMessage> getHttpMessages();

    @Override
    public NotifyResult notify(T notification) throws InterruptedException, ExecutionException, IOException {
        try {
            Queue<HttpMessage> httpMessages = getHttpMessages();
            if(httpMessages!=null && !httpMessages.isEmpty()) {
                HttpMessage httpMessage;
                while((httpMessage = httpMessages.poll()) !=null) {
                    AsyncHttpClient.BoundRequestBuilder builder = AsyncClientHolder.getInstance().getAsyncHttpClient(false).preparePost(httpMessage.getUrl());
                    int attemptCount = repeatHolder.getAttemptCount(httpMessage);
                    int requestTimeout = (60 + attemptCount) * 1000;
                    builder.setRequestTimeout(requestTimeout);
                    Map<String, String> headers = httpMessage.getHeaders();
                    for (String header : headers.keySet()) {
                        builder.addHeader(header, headers.get(header));
                    }
                    builder.setBody(httpMessage.getBody());
                    Request req = builder.build();
                    try {
                        ListenableFuture<Response> lr = AsyncClientHolder.getInstance().getAsyncHttpClient(false).executeRequest(req);
                        Response r = lr.get();
                        log.info("notify SEND/RESULT {}:{}:{}", httpMessage, r.getStatusCode(), r.getResponseBody());
                        int statusCode = r.getStatusCode();
                        if (!((statusCode >= 200 && statusCode < 300) || (statusCode >= 400 && statusCode < 500))) {
                            repeatHolder.repeatCheck(httpMessages, httpMessage);
                        }else {
                            repeatHolder.remove(httpMessage);
                        }
                    }catch (ExecutionException tex){
                        log.warn("ERROR TIMEOUT {} : {}",tex.getMessage(), req);
                    }catch (Exception ex) {
                        log.error("error doing request:"+req, ex);
                        repeatHolder.repeatCheck(httpMessages,httpMessage);
                        try {TimeUnit.SECONDS.sleep(10);}catch (Exception e){log.error("sleeping error");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("error send notify request",e);
            throw e;
        }
        return new NotifyResult(NotifyResult.OK,"ok");
    }
}
