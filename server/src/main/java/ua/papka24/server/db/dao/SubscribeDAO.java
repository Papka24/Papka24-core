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

package ua.papka24.server.db.dao;

import org.apache.commons.lang3.math.NumberUtils;
import ua.papka24.server.db.dto.EmployeeDTO;
import ua.papka24.server.db.dto.SubscribeFullInfoDTO;
import ua.papka24.server.api.DTO.CompanyDTO;
import ua.papka24.server.db.dto.SubscribeInfoDTO;
import ua.papka24.server.db.dto.SubscribeInfoDTO.SubscribeType;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;


public class SubscribeDAO extends DAO {

    private SubscribeDAO(){}

    private static class Singleton {
        private static final SubscribeDAO HOLDER_INSTANCE = new SubscribeDAO();
    }

    public static SubscribeDAO getInstance() {
        return SubscribeDAO.Singleton.HOLDER_INSTANCE;
    }

    public static class SubscribeResult{
        Long subId;
        SubscribeInfoDTO subRequest;
        String errMsg;

        SubscribeResult(Long subId, SubscribeInfoDTO subRequest) {
            this.subId = subId;
            this.subRequest = subRequest;
        }

        SubscribeResult(Long subId, SubscribeInfoDTO subRequest, String errorMsg) {
            this.subId = subId;
            this.subRequest = subRequest;
            this.errMsg = errorMsg;
        }

        public Long getSubId() {
            return subId;
        }

        public SubscribeInfoDTO getSubRequest() {
            return subRequest;
        }

        public String getErrMsg() {
            return errMsg;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("SubscribeResult{");
            sb.append("subId=").append(subId);
            sb.append(", subRequest=").append(subRequest);
            sb.append(", errMsg='").append(errMsg).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * Сохранение новых подписчиков на получение оповещений о событиях документов
     * @param sid - идентификатор сессии papka24
     * @param observers - список запросов на регестрацию подписки
     * @return список содержащий результат операции регестрации подписки
     */
    public List<SubscribeResult> saveSubscribersList(String sid, List<SubscribeInfoDTO> observers){
        if(sid!=null) {
            List<SubscribeResult> subIds = new ArrayList<>(observers.size());
            Connection c = getConnection();
            try {
                Session session = SessionsPool.find(sid);
                if (session == null) {
                    subIds.add(new SubscribeResult(-401L, null, "session not found"));
                    log.info("session not found");
                } else {
                    boolean allowed = false;
                    UserDTO sessionUser = session.getUser();
                    List<Long> resourceIds = ResourceDAO.getInstance().getUserResourcesIds(sessionUser);
                    PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO subscribers(id,url,time,author,type, event_type) VALUES (?,?,?,?,?,?) RETURNING sub_id");
                    String userLogin = sessionUser.getLogin();
                    Long companyId = sessionUser.getCompanyId();
                    List<String> admins;
                    String stringCompanyId;
                    if(companyId==null){
                        admins = Collections.emptyList();
                        stringCompanyId = "";
                    }else{
                        admins = CompanyDAO.getInstance().getAdmins(companyId);
                        stringCompanyId = String.valueOf(companyId);
                    }
                    for (SubscribeInfoDTO observer : observers) {
                        String observerId = observer.getId();
                        boolean isUserResources = NumberUtils.isCreatable(observerId) && resourceIds.contains(Long.valueOf(observerId));
                        boolean isSelfSubscribe = userLogin.equals(observerId);
                        boolean isAdminSubscribe = false;
                        try {
                            boolean haveEmployee;
                            CompanyDTO companyDTO = sessionUser.getCompanyDTO();
                            if(companyDTO == null){
                                haveEmployee = CompanyDAO.getInstance().haveEmployee(sessionUser.getCompanyId(), observer.getId());
                            }else{
                                haveEmployee = companyDTO.haveEmployee(observer.getId());
                            }
                            isAdminSubscribe = companyId != null && admins.contains(sessionUser.getLogin()) && haveEmployee;
                        }catch (Exception ex){
                            log.warn("check isAdminSubscribe failed admins:{}", admins);
                            log.warn("check isAdminSubscribe failed sessionUser:{}", sessionUser);
                            log.warn("check isAdminSubscribe failed ex:{}", ex);
                        }
                        boolean isAdminGroupSubscribe = (observer.getType() == SubscribeType.GROUP.getType()) && admins.contains(sessionUser.getLogin()) && observer.getId().equals(stringCompanyId);
                        if (isUserResources || isSelfSubscribe ||isAdminSubscribe || isAdminGroupSubscribe) {
                            allowed = true;
                        }
                        if (allowed) {
                            ps.setString(1, observerId);
                            ps.setString(2, observer.getUrl());
                            ps.setLong(3, new Date().getTime());
                            ps.setString(4, userLogin);
                            ps.setInt(5,observer.getType());
                            ps.setArray(6, c.createArrayOf("text", observer.getEventTypes()));
                            ResultSet resultSet = ps.executeQuery();
                            if (resultSet.next()) {
                                subIds.add(new SubscribeResult(resultSet.getLong(1), observer));
                            }
                        } else {
                            subIds.add(new SubscribeResult(-406L, observer, "not associated with a resource"));
                        }
                    }
                }
                c.commit();
            } catch (Exception ex) {
                log.error("error save notifier list", ex);
                try { c.rollback();} catch (SQLException sqe) {sqe.printStackTrace(); }
            } finally {
                try {c.close();} catch (SQLException sqe) {sqe.printStackTrace(); }
            }
            return subIds;
        }else{
            return saveNotifierList(observers);
        }
    }

    /**
     * @deprecated  use {@link #saveSubscribersList(String, List)} instead
     */
    @Deprecated
    private List<SubscribeResult> saveNotifierList(List<SubscribeInfoDTO> observers){
        List<SubscribeResult> subIds = new ArrayList<>(observers.size());
        Connection c = getConnection();
        try {
            PreparedStatement ps = c.prepareStatement(
                    "insert into subscribers(id,url,time) values (?,?,?) RETURNING sub_id");
            for(SubscribeInfoDTO observer : observers){
                ps.setString(1,observer.getId());
                ps.setString(2,observer.getUrl());
                ps.setLong(3, new Date().getTime());
                ResultSet resultSet = ps.executeQuery();
                if(resultSet.next()){
                    subIds.add(new SubscribeResult(resultSet.getLong(1),observer,"warning: you use deprecated API"));
                }
            }
            c.commit();
        }catch (Exception ex){
            log.error("error save notifier list", ex);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return subIds;
    }

    public List<SubscribeInfoDTO> getSubscribersList(SubscribeType subType, String...ids){
        List<SubscribeInfoDTO> observers = new ArrayList<>();
        if(ids==null){
            return observers;
        }
        Connection c = getConnection();
        try {
            PreparedStatement ps;
            for(String id : ids) {
                if (subType.getType() == -1) {
                    ps = c.prepareStatement(
                            "SELECT id, url, time, author, type, event_type FROM subscribers WHERE id = ? ");
                } else {
                    ps = c.prepareStatement(
                            "SELECT id, url, time, author, type, event_type FROM subscribers WHERE id = ? AND type = ? ");
                    ps.setInt(2, subType.getType());
                }
                ps.setString(1, id);
                ResultSet resultSet = ps.executeQuery();
                while (resultSet.next()) {
                    String url = resultSet.getString(2);
                    String author = resultSet.getString(4);
                    int type = resultSet.getInt(5);
                    Array array = resultSet.getArray(6);
                    String[] eventTypes = null;
                    if(array!=null) {
                        eventTypes = (String[]) array.getArray();
                    }
                    observers.add(new SubscribeInfoDTO(id, url, author, type, eventTypes));
                }
            }
            c.commit();
        }catch (Exception ex){
            log.error("error get notifier list", ex);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return observers;
    }

    /**
     * удаление подписок по запросу - удаляется конкретная подписка которая пришла в запросе.
     * @param sid
     * @param subscribers
     * @return
     */
    public List<SubscribeResult> deleteSubscribers(String sid, List<SubscribeInfoDTO> subscribers) {
        if(sid!=null) {
            List<SubscribeResult> subIds = new ArrayList<>(subscribers.size());
            Connection c = getConnection();
            try {
                Session session = SessionsPool.find(sid);
                if(session==null){
                    subIds.add(new SubscribeResult(-401L, null, "session not found"));
                }else {
                    PreparedStatement ps = c.prepareStatement("DELETE FROM subscribers WHERE sub_id = ? and (author = ? or author = ? or author is NULL ) ");
                    for (SubscribeInfoDTO si : subscribers) {
                        ps.setLong(1, Long.valueOf(si.getId()));
                        ps.setString(2, session.getUser().getLogin());
                        ps.setString(3,"SYSTEM");
                        boolean status = ps.executeUpdate() > 0;
                        if (status) {
                            subIds.add(new SubscribeResult(Long.valueOf(si.getId()), si));
                        }
                    }
                }
                c.commit();
            } catch (Exception ex) {
                log.error("error save notifier list", ex);
                try {
                    c.rollback();
                } catch (SQLException sqe) {
                    sqe.printStackTrace();
                }
            } finally {
                try {
                    c.close();
                } catch (SQLException sqe) {
                    sqe.printStackTrace();
                }
            }
            return subIds;
        }else{
            return deleteSubscribers(subscribers);
        }
    }

    /**
     * @deprecated  use {@link #deleteSubscribers(String, List)} instead
     */
    @Deprecated
    private List<SubscribeResult> deleteSubscribers(List<SubscribeInfoDTO> subscribers) {
        List<SubscribeResult> subIds = new ArrayList<>(subscribers.size());
        Connection c = getConnection();
        try {
            PreparedStatement ps = c.prepareStatement("DELETE FROM subscribers WHERE sub_id = ?");
            for(SubscribeInfoDTO si : subscribers) {
                ps.setLong(1, Long.valueOf(si.getId()));
                 boolean status = ps.executeUpdate() > 0;
                if(status) {
                    subIds.add(new SubscribeResult(Long.valueOf(si.getId()), si, "warning: you use deprecated API"));
                }
            }
            c.commit();
        }catch (Exception ex){
            log.error("error save notifier list", ex);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return subIds;
    }

    /**
     * уделение всех оформленных пользователем подписок + отключение админов группы от подписок на удаленного сотрудника
     * @param user
     */
    public boolean removeSubscriber(Long companyId, UserDTO user) {
        boolean res = false;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("DELETE FROM subscribers WHERE author = ? AND id != ? ");
            ps.setString(1, user.getLogin());
            ps.setString(2, user.getLogin());
            ps.executeUpdate();

            List<String> managers = CompanyDAO.getInstance().getAdmins(companyId);
            StringBuilder builder = new StringBuilder();
            for (int i = managers.size(); i > 0; i--) {
                builder.append("?,");
            }
            ps = con.prepareStatement("delete from subscribers where author IN (" + builder.deleteCharAt(builder.length() - 1).toString() + ") and id = ? ");
            for (int i = managers.size(); i > 0; i--) {
                ps.setString(i, managers.get(i - 1));
            }
            ps.setString(managers.size()+1,user.getLogin());
            ps.executeUpdate();

            con.commit();
            res = true;
        }catch (Exception ex){
            log.error("error remove subscriber", ex);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }

    public boolean removeCompanySubscribers(Long companyId){
        boolean res = false;
        if(companyId==null){
            return false;
        }
        Connection con = getConnection();
        try{
            List<String> admins = CompanyDAO.getInstance().getAdmins(companyId);
            List<EmployeeDTO> employees = CompanyDAO.getInstance().getCompanyEmployees(companyId, EmployeeDTO.ROLE_WORKER);

            PreparedStatement ps = con.prepareStatement("delete from subscribers where id = ? and author = ? ");
            for(String admin : admins){
                for(EmployeeDTO employee : employees){
                    ps.setString(1, employee.getLogin());
                    ps.setString(2, admin);
                    ps.executeUpdate();
                }
                ps.setString(1, String.valueOf(companyId));
                ps.setString(2, admin);
                ps.executeUpdate();
            }
            con.commit();
            res = true;
        }catch (Exception ex){
            log.error("error remove subscriber", ex);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }

    public List<SubscribeFullInfoDTO> getSubscriptions(UserDTO user){
        List<SubscribeFullInfoDTO> list = null;
        Connection con = getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("select sub_id, id, url, time, author, type, event_type from subscribers where author = ?");
            ps.setString(1, user.getLogin());
            ResultSet rs = ps.executeQuery();
            list = new ArrayList<>();
            while(rs.next()){
                Array array = rs.getArray(7);
                String[] eventTypes = null;
                if(array!=null) {
                    eventTypes = (String[]) array.getArray();
                }
                SubscribeFullInfoDTO si = new SubscribeFullInfoDTO(rs.getString(2),rs.getString(3),rs.getString(5), rs.getInt(6), eventTypes);
                si.setSubId(rs.getLong(1));
                si.setTime(rs.getLong(4));
                list.add(si);
            }
            con.commit();
        }
        catch (Exception ex){
            log.error("error getSubscriptions", ex);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return list;
    }

    public int deleteUser(Connection con, UserDTO user) throws SQLException {
        PreparedStatement ps = con.prepareStatement("DELETE FROM subscribers WHERE author = ? OR id = ? ");
        ps.setString(1, user.getLogin());
        ps.setString(2, user.getLogin()); //для подписок на него
        return ps.executeUpdate();
    }
}