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

package ua.papka24.server.db.scylla;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import ua.papka24.server.api.DTO.RatioDTO;
import ua.papka24.server.db.dto.AnalyticsInfoDTO;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class Analytics extends ScyllaCluster {

    public enum Event{
        login,
        sign,
        upload
    }

    private Analytics(){}

    private static class Singleton {
        private static final Analytics HOLDER_INSTANCE = new Analytics();
    }

    public static Analytics getInstance() {
        return Analytics.Singleton.HOLDER_INSTANCE;
    }

    public void saveEvent(Event type, Date time, String login, String version, String info){
        try {
            Session session = getSession();
            BoundStatement bound = preparedStatementMap.get("analytics.save").bind()
                    .setString(0, type.name())
                    .setTimestamp(1, time)
                    .setString(2, login)
                    .setString(3, version)
                    .setString(4, info);
            session.executeAsync(bound);
        }catch (Exception ex){
            log.error("error analytics",ex);
        }
    }

    public RatioDTO getEventRatio(Event type, long dateStart, long dateStop){
        try{
            List<AnalyticsInfoDTO> ids = getEvent(type,dateStart,dateStop);

            long apiCount = ids.stream().filter(i -> i.getVersion().equals("API")).count();
            return new RatioDTO(apiCount,ids.size()-apiCount);

        }catch (Exception ex){
            log.error("error get events ration",ex);
        }
        return null;
    }

    public List<AnalyticsInfoDTO> getEvent(Event type, long dateStart, long dateStop){
        List<AnalyticsInfoDTO> ids = new ArrayList<>();
        try{
            Session session = getSession();
            if(session!=null){
                BoundStatement bound = preparedStatementMap.get("analytics.select").bind();
                bound.setString(0,type.name())
                        .setTimestamp(1,new Date(dateStart))
                        .setTimestamp(2,new Date(dateStop));
                ResultSet rs = session.execute(bound);
                if(!rs.isExhausted()){
                    rs.forEach(r->ids.add(new AnalyticsInfoDTO(r.getString(0),r.getString(2), r.getTimestamp(1).getTime(), r.getString(3), r.getString(4))));
                }
            }
        }catch (Exception ex){
            log.error("error get events info",ex);
        }
        return ids;
    }
}
