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
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import ua.papka24.server.db.scylla.ScyllaCluster;
import ua.papka24.server.utils.logger.Event;

import java.util.List;


public class LoggerInfoManager extends ScyllaCluster {

    private LoggerInfoManager(){}


    private static class Singleton {
        private static final LoggerInfoManager HOLDER_INSTANCE = new LoggerInfoManager();
    }

    public static LoggerInfoManager getInstance() {
        return LoggerInfoManager.Singleton.HOLDER_INSTANCE;
    }

    public String getUserDayLog(String userLogin, String date){
        Session session = getSession();
        BoundStatement bound = preparedStatementMap.get("log.user_day.select").bind()
                .setString(0,userLogin)
                .setString(1,date);
        ResultSet rs = session.execute(bound);
        return process(rs);
    }

    public String getLogPerUser(String login, Long dateStart, Long dateEnd){
        Session session = getSession();
        BoundStatement bound = preparedStatementMap.get("users_do.select").bind()
                .setString(0, login)
                .setTimestamp(1, new java.util.Date(dateStart))
                .setTimestamp(2, new java.util.Date(dateEnd));
        ResultSet rs = session.execute(bound);
        return process(rs);
    }

    private String process(ResultSet rs){
        if(rs.isExhausted()) {
            return "no info found";
        }else {
            List<Row> all = rs.all();
            StringBuilder sb = new StringBuilder();
            all.forEach((row) ->
                    sb.append(row.getTimestamp("time").getTime()).append('|')
                            .append(row.getString("session_id")).append('|')
                            .append(row.getString("message")).append('|')
                            .append("\n\n\n"));
            return sb.toString();
        }
    }

    public String getSessionDay(String date){
        Session session = getSession();
        BoundStatement bound = preparedStatementMap.get("log.session_day.select").bind()
                .setString(0,date);
        ResultSet rs = session.execute(bound);
        return process(rs);
    }

    public String getErrorLog(String day) {
        Session session = getSession();
        BoundStatement bound = preparedStatementMap.get("log.error.select").bind()
                .setString(0,day);
        ResultSet rs = session.execute(bound);
        return process(rs);
    }

    public String getEventLog(String day, Event event) {
        Session session = getSession();
        BoundStatement bound = preparedStatementMap.get("log.event.select").bind()
                .setString(0,day);
        ResultSet rs = session.execute(bound);
        if(rs.isExhausted()) {
            return "no info found";
        }else {
            List<Row> all = rs.all();
            StringBuilder sb = new StringBuilder();
            all.forEach((row) -> {
                if(event==null || row.getString("event").equals(event.name())){
                    sb.append(row.getString("day")).append("|\t")
                            .append(row.getTimestamp("time")).append("|\t")
                            .append(row.getString("event")).append("|\t")
                            .append(row.getString("message")).append("|\t")
                            .append(row.getString("session_id")).append("|\t")
                            .append(row.getString("user_login"))
                            .append('\n');
                    }
                }
            );
            if(sb.length()==0){
                sb.append("no info found");
            }
            return sb.toString();
        }
    }
}
