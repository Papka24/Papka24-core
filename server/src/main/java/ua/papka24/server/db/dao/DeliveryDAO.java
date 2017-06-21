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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.db.dto.DeliveryDTO;
import ua.papka24.server.db.dto.UserDTO;

import java.sql.*;
import java.util.*;
import java.util.Date;


public class DeliveryDAO extends DAO{

    public enum DeliveryType{
        TEMP    (1),
        INVITE  (2);

        DeliveryType(int code){
            this.code = code;
        }

        public int code;
    }
    private static final Logger log = LoggerFactory.getLogger(DeliveryDAO.class);

    private DeliveryDAO(){}

    private static class Singleton {
        private static final DeliveryDAO HOLDER_INSTANCE = new DeliveryDAO();
    }

    public static DeliveryDAO getInstance() {
        return DeliveryDAO.Singleton.HOLDER_INSTANCE;
    }

    /**
     * Возвращает список полбзователей которые связанны опросником с определенным егрпоу
     * @param egrpou
     * @param type
     * @return
     */
    public List<DeliveryDTO> getDeliveriesInfo(String egrpou, int type){
        List<DeliveryDTO> deliveries = new ArrayList<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT d.id, d.egrpou, d.login, d.message, d.result, d.time_answer, d.time_send, d.egrpou_list FROM delivery d WHERE ( d.egrpou_list @> ?) AND d.type = ? ");
            ps.setArray(1, con.createArrayOf("TEXT", new String[]{egrpou}));
            ps.setInt(2, type);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                DeliveryDTO dto = new DeliveryDTO();
                dto.id = rs.getLong(1);
                dto.login = rs.getString(3);
                dto.message = rs.getString(4);
                dto.result = rs.getString(5);
                dto.answerTime = rs.getLong(6);
                Array array = rs.getArray(8);
                if(!rs.wasNull()){
                    if(array!=null && array.getArray()!=null) {
                        dto.egrpouList = new HashSet<>();
                        Collections.addAll(dto.egrpouList, (String[])array.getArray());
                    }
                }
                if(rs.wasNull()){
                    dto.answerTime = null;
                }
                dto.sendTime = rs.getLong(7);
                if(rs.wasNull()){
                    dto.sendTime = null;
                }
                deliveries.add(dto);

            }
            con.commit();
        } catch (Exception e) {
            log.error("Can't getDeliveriesInfo delivery ", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return deliveries;
    }

    public boolean saveAnswer(String login, Integer type, Long egrpou, String answer){
        boolean res = false;
        Connection con = getConnection();
        try{
            PreparedStatement ps;
            if(egrpou!=null){
                //отдельно по пользователю и егрпоу
                ps = con.prepareStatement("UPDATE delivery SET time_answer = ?, result = ? WHERE login = ? AND (egrpou_list @> ?) AND type = ? ");
                ps.setLong(1, new Date().getTime());
                ps.setString(2, answer);
                ps.setString(3, login);
                ps.setArray(4, con.createArrayOf("TEXT",new String[]{String.valueOf(egrpou)}));
                ps.setInt(5, type);
                res = ps.executeUpdate() > 0;
            }else {
                //по пользователю и всем его егрпоу
                ps = con.prepareStatement("UPDATE delivery SET time_answer = ?, result = ? WHERE login = ? AND type = ? ");
                ps.setLong(1, new Date().getTime());
                ps.setString(2, answer);
                ps.setString(3, login);
                ps.setInt(4, type);
                res = ps.executeUpdate() > 0;
            }
            con.commit();
        } catch (Exception e) {
            log.error("Can't get user saveAnswer ", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }

    public boolean deleteDelivery(int type, String login){
        boolean res = false;
        Connection con = getConnection();
        try{
            PreparedStatement ps;
            if(login!=null) {
                ps = con.prepareStatement("DELETE FROM delivery WHERE type = ? AND login = ?");
                ps.setInt(1, type);
                ps.setString(2, login);
            }else{
                ps = con.prepareStatement("DELETE FROM delivery WHERE type = ? ");
                ps.setInt(1, type);
            }
            res = ps.executeUpdate() > 0;
            con.commit();
        } catch (Exception e) {
            log.error("Can't get user deleteDeliveryItem ", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }

    public int deleteUser(Connection con, UserDTO user) throws SQLException {
        PreparedStatement ps = con.prepareStatement("DELETE FROM delivery WHERE login = ? ");
        ps.setString(1, user.getLogin());
        return ps.executeUpdate();
    }

    public long saveDelivery(String login, String message, Set<String> egrpouList, Integer type){
        long id = -1;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT id FROM delivery WHERE login = ? and type = ? ");
            ps.setString(1, login);
            ps.setInt(2, type);
            ResultSet rs = ps.executeQuery();
            if(rs.next()){
                id = rs.getLong(1);
                ps = con.prepareStatement(
                        "UPDATE delivery SET egrpou_list = array_append(egrpou_list, ?::TEXT) WHERE login = ? AND type = ? AND ( NOT egrpou_list && ? OR egrpou_list ISNULL ) "
                );
                for(String egrpou : egrpouList){
                    ps.setString(1, egrpou);
                    ps.setString(2, login);
                    ps.setInt(3, type);
                    ps.setArray(4, con.createArrayOf("TEXT", new String[]{egrpou}));
                    ps.executeUpdate();
                }
                ps.executeBatch();
            }else{
                ps = con.prepareStatement(
                    "INSERT INTO delivery(login, type, message, time_send, egrpou_list) values(?,?,?,?,?) RETURNING id "
                );
                ps.setString(1, login);
                ps.setInt(2, type);
                ps.setString(3, message);
                ps.setLong(4, new Date().getTime());
                if(egrpouList==null){
                    ps.setNull(5, Types.ARRAY);
                }else{
                    ps.setArray(5, con.createArrayOf("TEXT", egrpouList.stream().filter(e->!"0".equals(e)).toArray()));
                }
                rs = ps.executeQuery();
                if(rs.next()){
                    id = rs.getLong(1);
                }
            }
            con.commit();
        } catch (Exception e) {
            log.error("Can't saveDelivery ", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return id;
    }

    public void saveDeliveryAnswer(long id, String answer, int type) {
        Connection con = getConnection();
        try{
            //если это ответ на опрос - запись должна существовать
            long time = new Date().getTime();
            PreparedStatement ps = con.prepareStatement(
                    "UPDATE delivery SET result = ?, time_answer = ? WHERE id = ? RETURNING login, egrpou ");
            ps.setString(1, answer);
            ps.setLong(2, time);
            ps.setLong(3, id);
            ResultSet rs = ps.executeQuery();
            String login = null;
            if(rs.next()){
                login = rs.getString(1);
            }
            if(login!=null && "sendInvitationEmails".equals(answer)){
                ps = con.prepareStatement(
                        "WITH upd AS ( " +
                        " SELECT 1 FROM partner_winfo WHERE login = ? " +
                        " ) " +
                        "INSERT INTO partner_winfo(login, time) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM upd) ");
                ps.setString(1, login);
                ps.setString(2, login);
                ps.setLong(3, time);
                ps.executeUpdate();
            }
            con.commit();
        } catch (Exception e) {
            log.error("Can't saveDeliveryAnswer ", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
    }

    public DeliveryDTO getDelivery(String login, int type) {
        DeliveryDTO dto = null;
        Connection con = getConnection();
        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT login, egrpou, time_send, time_answer, result, id FROM delivery WHERE login = ? AND type = ? ");
            ps.setString(1, login);
            ps.setInt(2, type);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                dto = new DeliveryDTO();
                dto.login = rs.getString(1);
                dto.sendTime = rs.getLong(3);
                dto.answerTime = rs.getLong(4);
                dto.result = rs.getString(5);
                dto.id = rs.getLong(6);
            }
            con.commit();
        } catch (Exception e) {
            log.error("Can't getDelivery ", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return dto;
    }
}
