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

package ua.papka24.server.db.scylla.history;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.google.gson.Gson;
import ua.papka24.server.db.dao.DiffDAO;
import ua.papka24.server.db.dto.ResourceDTO;
import ua.papka24.server.db.scylla.ScyllaCluster;
import ua.papka24.server.db.scylla.history.model.ResourceChangeInfo;
import ua.papka24.server.service.events.main.EventType;
import ua.papka24.server.utils.exception.ReceivingDataException;
import ua.papka24.server.utils.exception.ScyllaInteractionException;
import ua.papka24.server.db.scylla.history.model.ResourceDTOFullHolder;
import ua.papka24.server.db.scylla.history.model.ResourceDTOHolder;

import java.util.*;
import java.util.stream.Collectors;


public class HistoryManager extends ScyllaCluster {

    private static final Gson gson = new Gson();

    private HistoryManager(){}

    private static class Singleton {
        private static final HistoryManager HOLDER_INSTANCE = new HistoryManager();
    }

    public static HistoryManager getInstance() {
        return HistoryManager.Singleton.HOLDER_INSTANCE;
    }


    public boolean saveEvent(String userLogin, Date date, long id, String eventType, String extra) throws ScyllaInteractionException {
        try{
            Session session = getSession();
            if(session!=null) {
                BoundStatement bound = preparedStatementMap.get("history.save").bind()
                        .setString(0, userLogin)
                        .setTimestamp(1, date)
                        .setLong(2,id)
                        .setString(3,eventType)
                        .setString(4,extra==null?"":extra);
                session.execute(bound);
                return true;
            }else{
                return false;
            }
        }catch (Exception ex){
            throw new ScyllaInteractionException(ex);
        }
    }

    public boolean saveFullEvent(String userLogin, Date date, long resourceId, String eventType, String extra, String initiator) throws ScyllaInteractionException {
        try{
            Session session = getSession();
            if(session!=null) {
                BoundStatement bound = preparedStatementMap.get("full_history.save").bind()
                        .setString(0, userLogin)
                        .setLong(1, date.getTime())
                        .setLong(2, resourceId)
                        .setString(3, eventType)
                        .setString(4, extra==null?"":extra)
                        .setString(5, initiator)
                        .setString(6, "");
                session.execute(bound);
                return true;
            }else{
                return false;
            }
        }catch (Exception ex){
            throw new ScyllaInteractionException(ex);
        }
    }

    public ResourceDTOHolder getEventsInfo(ua.papka24.server.security.Session userSession, String userLogin, Long dateStart) throws ReceivingDataException {
        return getEventsInfo(userSession, userLogin, dateStart, System.currentTimeMillis());
    }

    /**
     * Количество событий в истории пользователя за период. (события != количеству документов которые будут получены при запросе истории)
     * @param login
     * @param dateStart
     * @param dateEnd
     * @return
     */
    public long getEventsCount(String login, Long dateStart, Long dateEnd) {
        long eventsCount = -1;
        Session session = getSession();
        if(session!=null){
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.setTimeInMillis(dateStart);
            Date from = cal.getTime();
            cal.setTimeInMillis(dateEnd);
            Date to = cal.getTime();
            BoundStatement bound = preparedStatementMap.get("history.count").bind();
            bound.setString(0, login)
                    .setTimestamp(1, from)
                    .setTimestamp(2, to);
            ResultSet rs = session.execute(bound);
            if(!rs.isExhausted()){
                eventsCount = rs.one().getLong(0);
            }
        }
        return eventsCount;
    }

    public long getResourcesCount(ua.papka24.server.security.Session userSession, String userLogin, Long dateStart, Long dateEnd){
        long count = -1;
        try {
            Set<Long> uniqueResorces = new HashSet<>();
            ResourceDTOHolder eventsInfo = getEventsInfo(userSession, userLogin, dateStart, dateEnd);
            if(!eventsInfo.createdResourcesIsEmpty()){
                uniqueResorces.addAll(eventsInfo.createdResources.stream().map(ResourceDTO::getId).collect(Collectors.toSet()));
            }
            if(!eventsInfo.updatedResourcesIsEmpty()){
                uniqueResorces.addAll(eventsInfo.updatedResources.stream().map(ResourceDTO::getId).collect(Collectors.toSet()));
            }
            if(!eventsInfo.deleteResourcesIsEmpty()){
                uniqueResorces.addAll(eventsInfo.deleteResources.stream().map(ResourceDTO::getId).collect(Collectors.toSet()));
            }
            count = uniqueResorces.size();
        } catch (ReceivingDataException e) {
            log.warn("count changed resources in history failed", e.getMessage());
        }
        return count;
    }

    public ResourceDTOFullHolder getFullEventsInfo(ua.papka24.server.security.Session userSession, String userLogin, Long from, Long to) throws ReceivingDataException {
        ResourceDTOFullHolder resourceDTOHolder = new ResourceDTOFullHolder();
        try{
            if(userLogin==null){
                userLogin = userSession.getUser().getLogin();
            }
            //check last success time
            long successSyncTime = DiffDAO.getInstance().getLastSuccessSyncTime(userLogin);
            if(successSyncTime>from){
                Session session = getSession();
                if(session!=null){
                    BoundStatement bound = preparedStatementMap.get("full_history.select").bind();
                    bound.setString(0, userLogin)
                            .setLong(1, from)
                            .setLong(2, to);
                    ResultSet rs = session.execute(bound);

                    if(!rs.isExhausted()){
                        for(Row r : rs.all()){
                            Long resourceId = r.get(0, Long.class);
                            Long timestamp = r.getLong(1);
                            String eventName = r.getString(3);
                            String initiator = r.getString(4);
                            if(EventType.LEAVE_COMPANY.name().equals(eventName) || EventType.JOIN_COMPANY.name().equals(eventName)){
                                throw new ReceivingDataException(eventName);
                            }
                            String resourceJson = r.getString(2);
                            if( !"DELETE".equals(eventName) && (resourceJson==null || resourceJson.isEmpty())){
                                throw new ReceivingDataException(eventName);
                            }
                            ResourceDTO resourceDTO = gson.fromJson(resourceJson, ResourceDTO.class);
                            if(resourceDTO!=null) {
                                switch(eventName){
                                    case "CREATE":{
                                        resourceDTOHolder.createdResources.add(new ResourceChangeInfo(resourceDTO, timestamp, initiator));
                                        break;
                                    }
                                    default:{
                                        resourceDTOHolder.updatedResources.add(new ResourceChangeInfo(resourceDTO, timestamp, initiator));
                                        break;
                                    }
                                }
                            }else{
                                if("DELETE".equals(eventName)){
                                    ResourceDTO emptyResource = new ResourceDTO(resourceId, "", "", null);
                                    resourceDTOHolder.deleteResources.add(new ResourceChangeInfo(emptyResource, timestamp, initiator));
                                }
                            }
                        }
                    }
                }
            }else{
                return null;
            }
        }catch (Exception ex){
            log.error("error", ex);
            throw new ReceivingDataException(ex);
        }
        return resourceDTOHolder;
    }

    /**
     * получение документов, изменненых за время t.
     * @param userSession
     * @param userLogin
     * @param dateStart
     * @return
     */
    public ResourceDTOHolder getEventsInfo(ua.papka24.server.security.Session userSession, String userLogin, Long dateStart, Long dateEnd) throws ReceivingDataException {
        ResourceDTOHolder resourceDTOHolder = new ResourceDTOHolder();
        Map<ResourceDTO,Date> createHolder = new HashMap<>();
        Map<ResourceDTO,Date> updateHolder = new HashMap<>();
        Map<ResourceDTO,Date> deleteHolder = new HashMap<>();
        try{
            if(userLogin==null){
                userLogin = userSession.getUser().getLogin();
            }
            //check last success time
            long successSyncTime = DiffDAO.getInstance().getLastSuccessSyncTime(userLogin);
            if(successSyncTime>dateStart){
                Session session = getSession();
                if(session!=null){
                    BoundStatement bound = preparedStatementMap.get("history.select").bind();
                            bound.setString(0, userLogin)
                                .setTimestamp(1, new Date(dateStart))
                                .setTimestamp(2, new Date(dateEnd));
                    ResultSet rs = session.execute(bound);

                    if(!rs.isExhausted()){
                        for(Row r : rs.all()){
                            Date timestamp = r.getTimestamp(1);
                            String eventName = r.getString(3);
                            Long resourceId = r.get(0, Long.class);
                            if(EventType.LEAVE_COMPANY.name().equals(eventName) || EventType.JOIN_COMPANY.name().equals(eventName)){
                                throw new ReceivingDataException(eventName);
                            }
                            String resourceJson = r.getString(2);
                            if( !"DELETE".equals(eventName) && (resourceJson==null || resourceJson.isEmpty())){
                                throw new ReceivingDataException(eventName);
                            }
                            ResourceDTO resourceDTO = gson.fromJson(resourceJson, ResourceDTO.class);
                            if(resourceDTO!=null) {
                                switch(eventName){
                                    case "CREATE":{
                                        createHolder.put(resourceDTO, timestamp);
                                        break;
                                    }
                                    default:{
                                        if (updateHolder.containsKey(resourceDTO)) {
                                            long oldTime = updateHolder.get(resourceDTO).getTime();
                                            long newTime = timestamp.getTime();
                                            if (newTime > oldTime) {
                                                updateHolder.remove(resourceDTO);
                                                updateHolder.put(resourceDTO, timestamp);
                                            }
                                        } else {
                                            updateHolder.put(resourceDTO, timestamp);
                                        }
                                        break;
                                    }
                                }
                            }else{
                                if("DELETE".equals(eventName)){
                                    ResourceDTO emptyResource = new ResourceDTO(resourceId, "", "", null);
                                    deleteHolder.put(emptyResource, timestamp);
                                }
                            }
                        }
                    }
                }
            }else{
                return null;
            }
        }catch (Exception ex){
            log.error("error", ex);
            throw new ReceivingDataException(ex);
        }
        resourceDTOHolder.createdResources = new ArrayList<>(createHolder.keySet());
        resourceDTOHolder.updatedResources = new ArrayList<>(updateHolder.keySet());
        resourceDTOHolder.deleteResources = new ArrayList<>(deleteHolder.keySet());
        return resourceDTOHolder;
    }

    public boolean saveResourceHistory(long resourceId, String event, String login, String message) throws ScyllaInteractionException {
        try{
            Session session = getSession();
            if(session!=null) {
                BoundStatement bound = preparedStatementMap.get("resource_history.insert").bind()
                        .setLong(0, resourceId)
                        .setLong(1, System.currentTimeMillis())
                        .setString(2, event)
                        .setString(3, login)
                        .setString(4, message);
                session.executeAsync(bound);
                return true;
            }else{
                return false;
            }
        }catch (Exception ex){
            throw new ScyllaInteractionException(ex);
        }
    }
}
