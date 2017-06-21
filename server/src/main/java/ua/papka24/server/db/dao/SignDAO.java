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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ua.papka24.server.db.dto.SignInfoDTO;
import ua.papka24.server.db.dto.VerifyInfoDTO;
import ua.papka24.server.db.dto.SignRecordDTO;
import ua.papka24.server.security.CryptoManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;


public class SignDAO extends DAO {

    private SignDAO(){}

    private static class Singleton {
        private static final SignDAO HOLDER_INSTANCE = new SignDAO();
    }

    public static SignDAO getInstance() {
        return SignDAO.Singleton.HOLDER_INSTANCE;
    }

    public SignInfoDTO addSign(String hash, String base64) {
        SignInfoDTO result = null;
        List<VerifyInfoDTO> verifyResults;
        try {
            try {
                verifyResults = CryptoManager.verify(Base64.getDecoder().decode(base64));
            } catch (IllegalArgumentException e ){
                verifyResults = CryptoManager.verify(Base64.getUrlDecoder().decode(base64));
            }
        } catch (Exception e) {
            log.error("Can't check sign = {}", base64, e);
            return null;
        }
        Connection c = getConnection();
        assert c != null;
        String vrStr = new Gson().toJson(verifyResults, new TypeToken<ArrayList<VerifyInfoDTO>>() {}.getType());

        try {
            PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM sign_info AS s WHERE s.hash = ?");
            ps.setString(1, hash);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1)>0) {
                ps = c.prepareStatement("UPDATE sign_info s SET sign_info = ?, time = ? WHERE hash = ?");
                ps.setString(1, vrStr);
                ps.setLong(2, new Date().getTime());
                ps.setString(3, hash);
                if (ps.executeUpdate() > 0) {
                    result = new SignInfoDTO(hash, new Date().getTime(), verifyResults);
                }
            } else {
                ps = c.prepareStatement("INSERT INTO sign_info (hash, sign_info, time, ocsp_info) VALUES (?,?,?,?)");
                ps.setString(1, hash);
                ps.setString(2, vrStr);
                ps.setLong(3, new Date().getTime());
                ps.setString(4, "");
                if (ps.executeUpdate() > 0) {
                    result = new SignInfoDTO(hash, new Date().getTime(), verifyResults);
                }
            }
            c.commit();
        } catch (Exception e) {
            result = null;
            log.error("Can't check sign = {}", base64, e);
            log.error("Can't check sign = {}", e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return result;
    }

    /**
     * Get sign info without check right
     * @param resourceId - id
     * @return
     */
    public List<SignRecordDTO> getSign(Long resourceId) {
        List<SignRecordDTO> result = new ArrayList<>();
        Connection c = getConnection();
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement("SELECT s.egrpou, s.inn, s.login, s.time, s.company FROM sign_record as s WHERE s.resource_id = ?");
            ps.setLong(1, resourceId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new SignRecordDTO(rs.getLong(1),rs.getLong(2), rs.getString(3), resourceId, rs.getLong(4), rs.getString(5)));
            }
            c.commit();
        } catch (Exception e) {
            log.error("Can't get sign for resource {}", resourceId, e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return result;
    }

    public SignInfoDTO getSign(String hash) {
        SignInfoDTO result = null;
        Connection c = getConnection();
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement("SELECT s.time, s.sign_info FROM sign_info AS s WHERE s.hash = ?");
            ps.setString(1, hash);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                result = new SignInfoDTO(hash, rs.getLong(1), rs.getString(2));
            }
            c.commit();
        } catch (Exception e) {
            log.error("Can't get sign with hash = {}", hash);
            log.error("Can't get sign", e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return result;
    }

    public Set<String> getUserEgrpouList(String login){
        Set<String> res = new HashSet<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT egrpou FROM sign_record WHERE login = ? ");
            ps.setString(1, login);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                res.add(rs.getString(1));
            }
            con.commit();
        } catch (Exception e) {
            log.error("Can't getUserEgrpouList", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }
}
