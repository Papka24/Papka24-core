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

package ua.papka24.server.db.dto;

import org.apache.commons.codec.digest.DigestUtils;
import ua.papka24.server.api.DTO.LoginDataDTO;
import ua.papka24.server.db.dao.UserDAO;
import ua.papka24.server.security.SecurityAttributes;
import ua.papka24.server.Main;
import ua.papka24.server.api.DTO.CompanyDTO;
import ua.papka24.server.security.SessionsPool;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;


public class UserDTO implements Serializable {

    private String login;
    private String description;
    private String fullName;
    private String passwordDigest;
    private String authData;
    private SecurityAttributes privileges;
    private Map<String,String> friends;
    private boolean heavyWeight = false;
    private CompanyDTO companyDTO;
    private Long companyId;
    private Long blocked;

    public UserDTO(String login, String fullName, String password, SecurityAttributes privileges) {
        this.login = login.toLowerCase();
        this.fullName = fullName.replace("<", "").replace(">", "");
        this.passwordDigest = hash(password);
        this.privileges = privileges;
    }

    public UserDTO(String login) {
        this.login = login.toLowerCase();
    }

    public UserDTO(ResultSet rs) throws SQLException {
        this.login = rs.getString(1);
        this.description = rs.getString(2);
        this.fullName = rs.getString(3);
        this.passwordDigest = rs.getString(4);
        this.privileges = new SecurityAttributes(rs.getLong(5));
        this.heavyWeight = rs.getBoolean(7);
        this.authData = rs.getString(8);
        this.companyId = rs.getLong(9);
        if(rs.wasNull()){
            this.companyId = null;
        }
        this.blocked = rs.getLong(10);
    }

    public UserDTO(String login, String passwordDigest, long security_descr, String authData, long blocked) {
        this.login = login;
        this.passwordDigest = passwordDigest;
        this.privileges = new SecurityAttributes(security_descr);
        this.authData = authData;
        this.blocked = blocked;
        this.description = "";
        this.fullName = "";
        this.friends = null;
        this.companyId = null;
    }

    public static UserDTO load(String userLogin){
        return UserDAO.getInstance().getUser(userLogin);
    }

    public static UserDTO loadFromPool(String userLogin) {
        return SessionsPool.getUserDTO(userLogin);
    }

    public void invalidate(){
        UserDTO user = load(this.getLogin());
        this.login = user.login;
        this.description = user.description;
        this.fullName = user.fullName;
        this.passwordDigest = user.passwordDigest;
        this.privileges = user.privileges;
        this.heavyWeight = user.heavyWeight;
        this.authData = user.authData;
        this.companyId = user.companyId;
        this.friends = user.friends;
        this.companyDTO = user.companyDTO;
        this.blocked = user.blocked;
    }

    public static String hash(String password) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(DigestUtils.getSha256Digest().digest((password + Main.passwordSalt).getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "";
    }

    public String getLogin() {
        return login.toLowerCase();
    }

    public boolean checkPassword(String password) {
        password = hash(password);
        return password.equals(this.passwordDigest);
    }

    public SecurityAttributes getPrivileges() {
        return privileges;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPasswordDigest() {
        return passwordDigest;
    }

    public Map<String, String> getFriends() {
        if(friends==null){
            friends = new HashMap<>();
        }
        return friends;
    }

    public void setFriends(Map<String, String> friends) {
        this.friends = friends;
    }

    public void setHeavyWeight(boolean b) {
        this.heavyWeight = b;
    }

    public boolean isHeavyWeight() {
        return heavyWeight;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("UserDTO{");
        sb.append("login='").append(login).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", fullName='").append(fullName).append('\'');
        sb.append(", authData='").append(authData).append('\'');
        sb.append(", privileges=").append(privileges);
        sb.append(", friends=").append(friends);
        sb.append(", heavyWeight=").append(heavyWeight);
        sb.append(", companyDTO=").append(companyDTO);
        sb.append(", companyId=").append(companyId);
        sb.append(", blocked=").append(blocked);
        sb.append('}');
        return sb.toString();
    }

    public boolean checkOTP(LoginDataDTO data) {
        return this.authData == null || data.getOTP() != null && Main.gAuth.authorize(authData, data.getOTP());
    }

    public String getAuthData(){
        return  authData;
    }

    public void setAuthData(String authData){
        this.authData = authData;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public CompanyDTO getCompanyDTO() {
        if(companyDTO==null){
            CompanyDTO.mockCompany();
        }
        return companyDTO;
    }

    public void setCompanyDTO(CompanyDTO companyDTO) {
        this.companyDTO = companyDTO;
    }

    public Long getBlocked() {
        return blocked;
    }

    public void setBlocked(Long blocked) {
        this.blocked = blocked;
    }

    public boolean theSameCompanyId(Long companyId){
        return this.companyId==null && companyId == 0 || companyId!=0 && (companyId.equals(this.companyId));
    }
}
