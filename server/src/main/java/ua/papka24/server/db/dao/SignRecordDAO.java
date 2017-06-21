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

import ua.papka24.server.api.DTO.EgrpouUsers;
import ua.papka24.server.db.dto.UserDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


//собрать работу с sign_record
public class SignRecordDAO extends DAO{

    private SignRecordDAO(){}

    private static class Singleton {
        private static final SignRecordDAO HOLDER_INSTANCE = new SignRecordDAO();
    }

    public static SignRecordDAO getInstance() {
        return SignRecordDAO.Singleton.HOLDER_INSTANCE;
    }

    public List<Long> checkEgrpousExists(final List<Long> egrpouList){
        List<Long> result = null;
        Connection con = getConnection();
        try{
            String whr = egrpouList.stream().map(e->"?").collect(Collectors.joining(","));
            PreparedStatement ps = con.prepareStatement(
                    "SELECT DISTINCT egrpou  FROM sign_record WHERE egrpou IN ( "+whr+" ) ");
            int i = 1;
            for(Long egrpou : egrpouList){
                ps.setLong(i++, egrpou);
            }
            result = new ArrayList<>();
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                result.add(rs.getLong(1));
            }
            con.commit();
        } catch (Exception e) {
            log.error("Can't checkEgrpousExists", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return result;
    }

    int deleteUser(Connection con, UserDTO user) throws SQLException {
        PreparedStatement ps = con.prepareStatement("DELETE FROM sign_record WHERE login = ? ");
        ps.setString(1, user.getLogin());
        return ps.executeUpdate();
    }

    public long getSignedDocumentCount(String login) {
        long res = -1;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT count(DISTINCT resource_id) " +
                            "FROM sign_record " +
                            "WHERE login = ? ");
            ps.setString(1, login);
            ResultSet rs = ps.executeQuery();
            if(rs.next()){
               res = rs.getLong(1);
            }
            con.commit();
        } catch (Exception e) {
            log.error("Can't getSignedDocumentCount", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }

    public List<EgrpouUsers> getEgrpouLogins(List<Long> egrpous){
        List<EgrpouUsers> egrpouUsers = new ArrayList<>();
        Connection con = getConnection();
        try{
            String where = egrpous.stream().map(e->"?").collect(Collectors.joining(",","(",")"));
            String req = "SELECT egrpou, array_agg(DISTINCT login) " +
                    " FROM sign_record " +
                    " WHERE egrpou IN " + where +
                    " GROUP BY egrpou";
            PreparedStatement ps = con.prepareStatement(req);
            int i = 1;
            for (Long egrpou : egrpous) {
                ps.setLong(i++, egrpou);
            }
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                long egrpou = rs.getLong(1);
                List<String> logins;
                Array arr = rs.getArray(2);
                if(arr!=null){
                    logins = Arrays.asList((String[]) arr.getArray());
                    egrpouUsers.add(new EgrpouUsers(egrpou, logins));
                }
            }
            con.commit();
        }catch (Exception ex){
            log.error("Can't getEgrpouLogins", ex);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return egrpouUsers;
    }
}
