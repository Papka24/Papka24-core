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

import ua.papka24.server.db.dto.UserDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;


public class DiffDAO extends DAO {

    private DiffDAO(){}

    private static class Singleton {
        private static final DiffDAO HOLDER_INSTANCE = new DiffDAO();
    }

    public static DiffDAO getInstance() {
        return DiffDAO.Singleton.HOLDER_INSTANCE;
    }

    public int[] syncTime(List<SyncItem> syncItems){
        int[] res = {0};
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "WITH upd AS ( " +
                            "UPDATE diff_sync SET date = ? WHERE login = ? RETURNING date " +
                            ")" +
                            "INSERT INTO diff_sync(login, date) SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM upd) ");
            long ldate;
            for(SyncItem item : syncItems) {
                if(item.date!=null){
                    ldate = item.date.getTime();
                }else{
                    ldate = 0L;
                }
                ps.setLong(1,ldate);
                ps.setString(2, item.login);
                ps.setString(3, item.login);
                ps.setLong(4, ldate);
                ps.executeUpdate();
            }
            con.commit();
        }catch (Exception ex) {
            log.error("can't syncTime ", ex);
            try {
                con.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                con.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return res;
    }

    public long getLastSuccessSyncTime(final String login){
        long date = 0;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("select date from diff_sync where login = ? ");
            ps.setString(1,login);
            ResultSet resultSet = ps.executeQuery();
            if(resultSet.next()){
                date = resultSet.getLong(1);
            }
            con.commit();
        }catch (Exception ex) {
            log.error("can't get syncTime ", ex);
            try {
                con.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                con.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return date;
    }

    public static class SyncItem{
        public String login;
        public Date date;
    }

    public int deleteUser(Connection con, UserDTO user) throws SQLException {
        PreparedStatement ps = con.prepareStatement("DELETE FROM diff_sync WHERE login = ? ");
        ps.setString(1, user.getLogin());
        return ps.executeUpdate();
    }
}
