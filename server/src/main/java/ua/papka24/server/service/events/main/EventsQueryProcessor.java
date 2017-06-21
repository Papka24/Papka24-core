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

package ua.papka24.server.service.events.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.service.events.handler.Handler;
import ua.papka24.server.service.events.handler.RepeatHolder;
import ua.papka24.server.service.events.main.data.Notification;
import ua.papka24.server.utils.exception.NotSatisfiedException;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class EventsQueryProcessor extends Thread{

    private static Logger log = LoggerFactory.getLogger(EventsQueryProcessor.class);
    private static Queue<Notification> notifyQueue;
    private static Map<Class<? extends Notification>, List<Class<? extends Handler>>> handlersClass;
    private static final long TIMEOUT = 100L;
    private static final RepeatHolder<Notification> repeatHolder = new RepeatHolder<>();
    private static final ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(10,50,2,TimeUnit.MINUTES, new LinkedBlockingQueue<>());
    private static final Map<Class,Handler> handlers = new WeakHashMap<>();

    private EventsQueryProcessor(){
        notifyQueue = new ConcurrentLinkedQueue<>();
        handlersClass = new HashMap<>();
        start();
    }

    private static class Singleton {
        private static final EventsQueryProcessor HOLDER_INSTANCE = new EventsQueryProcessor();
    }

    public static EventsQueryProcessor getInstance() {
        return EventsQueryProcessor.Singleton.HOLDER_INSTANCE;
    }

    public boolean addNotification(Notification notification){
        return notifyQueue.add(notification);
    }

    public boolean registerEventHandler(Class<? extends Notification> notificationClass, Class<? extends Handler> handlerClass){
        List<Class<? extends Handler>> handlersClasses = handlersClass.computeIfAbsent(notificationClass, k -> new ArrayList<>());
        return handlersClasses.add(handlerClass);
    }

    public long getCompletedTaskCount(){
        if(poolExecutor!=null){
            return poolExecutor.getCompletedTaskCount();
        }else{
            return -1;
        }
    }

    public long getNotifyQueueSize(){
        if(notifyQueue!=null){
            return notifyQueue.size();
        }else{
            return -1;
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                Notification notification;
                while ((notification = notifyQueue.poll()) != null) {
                    poolExecutor.submit(new Task(notification));
                }
                TimeUnit.MILLISECONDS.sleep(TIMEOUT);
            } catch (Exception e) {
                log.error("Error start queue", e);
            }
        }
    }

    private static class Task implements Runnable{

        final Notification notification;

        Task(Notification notification){
            this.notification = notification;
        }

        @Override
        public void run() {
            try {
                List<Class<? extends Handler>> classes = handlersClass.get(notification.getClass());
                if (classes != null) {
                    for (Class cls : classes) {
                        poolExecutor.submit(()->{
                            try {
                                Handler handlerObj = handlers.get(cls);
                                if(handlerObj==null){
                                    handlerObj = (Handler) cls.newInstance();
                                    handlers.put(cls,handlerObj);
                                }
                                handlerObj.notify(notification);
                                repeatHolder.remove(notification);
                            } catch (NotSatisfiedException ex) {
                                log.error("some error while doing doNotify", ex);
                                repeatHolder.repeatCheck(notifyQueue, notification);
                            } catch (Exception ex) {
                                log.error("error doing notification", ex);
                            }
                            }
                        );
                    }
                }
            }catch (Exception ex){
                log.error("notify error",ex);
            }
        }
    }
}