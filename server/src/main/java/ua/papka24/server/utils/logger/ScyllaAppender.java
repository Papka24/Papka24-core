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

package ua.papka24.server.utils.logger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.MDC;
import ua.papka24.server.db.scylla.ScyllaCluster;
import ua.papka24.server.db.scylla.logger.AppenderManager;

import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;



public class ScyllaAppender extends AppenderBase<ILoggingEvent> {

    private String scyllaPath;
    private String scyllaPort;
    private String scyllaKeyspace;
    private String scyllaEnable;
    private volatile AppenderManager manager;

    private void init(String scyllaPath, String scyllaPort, String scyllaKeyspace) {
        if (manager == null) {
            synchronized (AppenderManager.class) {
                if (manager == null) {
                    manager = new AppenderManager(scyllaPath, scyllaPort, scyllaKeyspace);
                }
            }
        }
    }

    @Override
    public void start() {
        try {
            init(scyllaPath, scyllaPort, scyllaKeyspace);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        try {
            if (scyllaEnable == null || "true".equals(scyllaEnable)) {
                if (manager == null || !ScyllaCluster.isActive()) {
                    init(scyllaPath, scyllaPort, scyllaKeyspace);
                } else {
                    try {
                        Map<String, String> mdcPropertyMap = eventObject.getMDCPropertyMap();
                        String sessionId = mdcPropertyMap.getOrDefault("session", "SYSTEM");
                        String userLogin = mdcPropertyMap.getOrDefault("user_login", "SYSTEM");
                        String level = eventObject.getLevel().levelStr;
                        long eventTime = eventObject.getTimeStamp();

                        TimeZone tz = TimeZone.getTimeZone("Europe/Kiev");
                        Calendar eventTimeCal = Calendar.getInstance(tz);
                        eventTimeCal.setTimeInMillis(eventTime);
                        StringBuilder dayStrBuild = new StringBuilder(8);
                        dayStrBuild.append(eventTimeCal.get(Calendar.YEAR));
                        int month = eventTimeCal.get(Calendar.MONTH) + 1;
                        String monthStr = ((month < 10) ? "0" : "") + month;
                        dayStrBuild.append(monthStr);
                        int day = eventTimeCal.get(Calendar.DAY_OF_MONTH);
                        String daySrt = ((day < 10) ? "0" : "") + day;
                        dayStrBuild.append(daySrt);
                        String mdcMessage = MDC.get("message");
                        String realIp = mdcPropertyMap.get("real_ip");
                        if (mdcMessage == null) {
                            mdcMessage = "";
                        }
                        if(realIp!=null) {
                            mdcMessage = mdcMessage + ":" + realIp;
                        }

                        String request = MDC.get("request");
                        if (request == null) {
                            request = "N/A";
                        }

                        String dayStr = dayStrBuild.toString();
                        //String message = nodeName+"->"+eventObject.getFormattedMessage();
                        String message = eventObject.getFormattedMessage();

                        Object[] argumentArray = eventObject.getArgumentArray();
                        Event event = null;
                        if (argumentArray != null && argumentArray.length > 0) {
                            try {
                                event = Event.valueOf(argumentArray[argumentArray.length - 1]);
                                manager.saveEventDay(dayStr, event, eventTimeCal.getTime(), userLogin, sessionId, message);
                            } catch (IllegalArgumentException ex) {
                                // nothing to do/no event found
                            }
                        }

                        if (event != null && (event == Event.ADD_NEW_SIGN_TO_CASH || event == Event.ADD_CLOUD_SIGN || event == Event.ADD_SIGN)) {
                            mdcMessage = event.name();
                        }

                        switch (eventObject.getLevel().levelInt) {
                            case Level.INFO_INT: {
                                manager.saveUserLog(userLogin, dayStr, sessionId, eventTimeCal.getTime(), message + "[" + mdcMessage + "]", level, request);
                                manager.saveUserDo(userLogin, eventTimeCal.getTime(), event==null?"":event.name(), sessionId, message,request);
                                break;
                            }
                            case Level.WARN_INT:
                            case Level.ERROR_INT: {
                                IThrowableProxy throwableProxy = eventObject.getThrowableProxy();
                                if (throwableProxy != null) {
                                    message = ThrowableProxyUtil.asString(throwableProxy);
                                }
                                manager.saveErr(dayStr, eventTimeCal.getTime(), sessionId, message);
                                break;
                            }
                        }
                        manager.saveSessionLog(dayStr, sessionId, eventTimeCal.getTime(), message + "[" + mdcMessage + "]", level);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public String getScyllaPath() {
        return scyllaPath;
    }

    public void setScyllaPath(String scyllaPath) {
        this.scyllaPath = scyllaPath;
    }

    public String getScyllaPort() {
        return scyllaPort;
    }

    public void setScyllaPort(String scyllaPort) {
        this.scyllaPort = scyllaPort;
    }

    public String getScyllaKeyspace() {
        return scyllaKeyspace;
    }

    public void setScyllaKeyspace(String scyllaKeyspace) {
        this.scyllaKeyspace = scyllaKeyspace;
    }

    public String getScyllaEnable() {
        return scyllaEnable;
    }

    public void setScyllaEnable(String scyllaEnable) {
        this.scyllaEnable = scyllaEnable;
    }
}