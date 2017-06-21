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

import ua.papka24.server.api.DTO.UserEgrpou;
import ua.papka24.server.db.dto.AdditionalAgreementDTO;
import ua.papka24.server.db.dto.ShareDTO;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.db.dto.reginfo.FirstShareInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class ShareDAO extends DAO{
    private ShareDAO(){}

    private static class Singleton {
        private static final ShareDAO HOLDER_INSTANCE = new ShareDAO();
    }

    public static ShareDAO getInstance() {
        return ShareDAO.Singleton.HOLDER_INSTANCE;
    }

    /**
     * удаление шаринга пользователя (шаринг где пользователь является получателем или инициатором)
     * предназначено для использования в рамках одной транзакции. единый
     * @param user
     * @param isInitiator - признак необходимо ли удалять и шаринги инициированные пользователем
     * @return
     */
    public int deleteUserShare(Connection con, final UserDTO user, boolean isInitiator) throws SQLException {
        int res;
        PreparedStatement ps;
        if(isInitiator){
            ps = con.prepareStatement("DELETE FROM share where user_login = ? OR initiator = ? ");
            ps.setString(1, user.getLogin());
            ps.setString(2, user.getLogin());
        }else {
            ps = con.prepareStatement("DELETE FROM share WHERE user_login = ? ");
            ps.setString(1, user.getLogin());
        }
        res = ps.executeUpdate();
        return res;
    }

    Map<Long,List<ShareDTO>> getShare(Connection con, String user, List<Long> ids) throws SQLException {
        Map<Long,List<ShareDTO>> result = new HashMap<>();
        String wher = ids.stream().map(e -> "?").collect(Collectors.joining(","));
        String select = "SELECT s.user_login, s.status, s.time, s.tags, s.resource_id FROM share AS s WHERE s.resource_id in "
                + " ( " + wher + " ) ";
        PreparedStatement ps;
        ResultSet rs;
        ps = con.prepareStatement(select);
        int i = 1;
        for (Long id : ids) {
            ps.setLong(i++, id);
        }
        rs = ps.executeQuery();
        while (rs.next()) {
            long resourceId = rs.getLong(5);
            if(!rs.wasNull()){
                List<ShareDTO> shareDTOs = result.computeIfAbsent(resourceId, k -> new ArrayList<>());
                ShareDTO share = new ShareDTO(resourceId, rs.getString(1), rs.getInt(2), rs.getLong(3), rs.getLong(4));
                shareDTOs.add(share);
            }
        }
        return result;
    }

    Map<Long,List<ShareDTO>> getShare(String user, List<Long> ids) {
        Map<Long,List<ShareDTO>> result = new HashMap<>();
        Connection c = getConnection();
        assert c != null;
        try {
            result = getShare(c, user, ids);
            c.commit();
        } catch (Exception e) {
            log.error("Can't get sharing resources {}", ids, e);
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
        return result;
    }

    public FirstShareInfo getFirstShareInfo(String login){
        FirstShareInfo res = null;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT s.time, r.author " +
                    "FROM share s, resource r " +
                    "WHERE s.user_login = ? AND s.resource_id = r.id " +
                    "AND s.time = (SELECT min(ss.time) FROM share ss WHERE ss.user_login = ?) ");
            ps.setString(1, login);
            ps.setString(2, login);
            ResultSet rs = ps.executeQuery();
            if(rs.next()){
                res = new FirstShareInfo();
                res.time = rs.getLong(1);
                res.author = rs.getString(2);
            }
            con.commit();
        }catch (Exception ex){
            log.error("error", ex);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }


    public List<UserEgrpou> getAdditionalAgreementStatus(List<AdditionalAgreementDTO> additionalAgreements) {
        List<UserEgrpou> userEgrpous = new ArrayList<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT s.user_login, sr.egrpou, ? " +
                            "FROM share s " +
                            "  LEFT JOIN sign_record sr ON (s.user_login = sr.login) " +
                            "WHERE s.initiator = ? AND s.status IN (3,13) " +
                            "      AND s.resource_id IN ( " +
                            "                SELECT id " +
                            "                FROM resource " +
                            "                WHERE author = ? AND name = ? " +
                            ") " +
                            "GROUP BY s.user_login, sr.egrpou ");
            for (AdditionalAgreementDTO additionalAgreement : additionalAgreements) {
                ps.setString(1, additionalAgreement.companyName);
                ps.setString(2, additionalAgreement.primaryLogin);
                ps.setString(3, additionalAgreement.primaryLogin);
                ps.setString(4, additionalAgreement.agreementName);
                ResultSet rs = ps.executeQuery();
                while(rs.next()){
                    userEgrpous.add(new UserEgrpou(rs.getString(1), rs.getString(2), rs.getString(3)));
                }
                rs.close();
            }
            ps.close();
            con.commit();
        }catch (Exception ex){
            log.error("error", ex);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return userEgrpous;
    }
}
