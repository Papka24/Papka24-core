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

package ua.papka24.server.db.scylla.logger;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Session;
import ua.papka24.server.db.scylla.ScyllaCluster;
import ua.papka24.server.Main;
import ua.papka24.server.utils.logger.Event;

import java.util.Date;

public class AppenderManager extends ScyllaCluster {

    public AppenderManager(String scyllaPath, String scyllaPort, String scyllaKeyspace) {
        super(scyllaPath, scyllaPort, scyllaKeyspace);
    }

    public boolean saveUserLog(String userLogin, String dayStr, String sessionId, Date eventTime, String message, String level, String request) {
        boolean res = false;
        try {
            Session session = getSession();
            if (session != null) {
                BoundStatement bound = preparedStatementMap.get("log.user_day.save").bind()
                        .setString(0, userLogin)
                        .setString(1, dayStr)
                        .setString(2, sessionId +":"+ Main.nodeName)
                        .setTimestamp(3, eventTime)
                        .setString(4, message)
                        .setString(5, level)
                        .setString(6, request);
                session.executeAsync(bound);
                res = true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return res;
    }

    public boolean saveUserDo(String userLogin, Date time, String event, String sessionId, String message, String request) {
        boolean res = false;
        try {
            Session session = getSession();
            if (session != null) {
                BoundStatement bound = preparedStatementMap.get("users_do.save").bind()
                        .setString(0, userLogin)
                        .setTimestamp(1, time)
                        .setString(2, event)
                        .setString(3, sessionId +":"+ Main.nodeName)
                        .setString(4, message)
                        .setString(5, request);
                session.executeAsync(bound);
                res = true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return res;
    }

    public boolean saveSessionLog(String dayStr, String sessionId, Date eventTime, String message, String level) {
        boolean res = false;
        try {
            Session session = getSession();
            if (session != null) {
                BoundStatement bound = preparedStatementMap.get("log.session_day.save").bind()
                        .setString(0, dayStr)
                        .setString(1, sessionId +":"+ Main.nodeName)
                        .setTimestamp(2, eventTime)
                        .setString(3, message)
                        .setString(4, level);
                session.executeAsync(bound);
                res = true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return res;
    }

    public boolean saveEventDay(String day, Event event, Date time, String userLogin, String sessionId, String message) {
        boolean res = false;
        try {
            Session session = getSession();
            BoundStatement bound = preparedStatementMap.get("log.event.save").bind()
                    .setString(0, day)
                    .setString(1, event.name())
                    .setTimestamp(2, time)
                    .setString(3, userLogin)
                    .setString(4, sessionId +":"+ Main.nodeName)
                    .setString(5, message);
            session.executeAsync(bound);
            res = true;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return res;
    }

    public boolean saveErr(String day, Date time, String sessionId, String message) {
        boolean res = false;
        try {
            Session session = getSession();
            BoundStatement bound = preparedStatementMap.get("log.error.save").bind()
                    .setString(0, day)
                    .setTimestamp(1, time)
                    .setString(2, sessionId +":"+ Main.nodeName)
                    .setString(3, message);
            session.executeAsync(bound);
            res = true;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return res;
    }
}
