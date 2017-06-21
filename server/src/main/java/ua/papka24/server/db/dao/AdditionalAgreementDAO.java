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

import ua.papka24.server.db.dto.AdditionalAgreementDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AdditionalAgreementDAO extends DAO {

    private AdditionalAgreementDAO(){}

    private static class Singleton {
        private static final AdditionalAgreementDAO HOLDER_INSTANCE = new AdditionalAgreementDAO();
    }

    public static AdditionalAgreementDAO getInstance() {
        return AdditionalAgreementDAO.Singleton.HOLDER_INSTANCE;
    }

    public List<AdditionalAgreementDTO> getAdditionalAgreements(){
        List<AdditionalAgreementDTO> additionalAgreements = new ArrayList<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT company_name, primary_login, agreement_name from additional_agreement ");
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                additionalAgreements.add(new AdditionalAgreementDTO(rs.getString(1), rs.getString(2), rs.getString(3)));
            }
            con.commit();
        }catch (Exception ex){
            log.error("error", ex);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return additionalAgreements;
    }

    public void addAdditionalAgreements(AdditionalAgreementDTO agreement) {
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO additional_agreement(company_name, primary_login, agreement_name) VALUES (?,?,?)");
            ps.setString(1, agreement.companyName);
            ps.setString(2, agreement.primaryLogin);
            ps.setString(3, agreement.agreementName);
            ps.executeUpdate();
            con.commit();
        }catch (Exception ex){
            log.error("error", ex);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
    }
}
