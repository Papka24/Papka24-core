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

package ua.papka24.server.api.DTO;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import ua.papka24.server.db.dto.FriendDTO;
import ua.papka24.server.db.dto.UserDTO;

import java.util.List;
import java.util.Map;

public class UserInfoDTO {
    public static TypeAdapter<UserInfoDTO> gson = new Gson().getAdapter(UserInfoDTO.class);
    private String login;
    private String fullName;
    private String description;
    private String security;
    private String sessionId;
    private CompanyDTO company;
    private Map<String,String> friends;
    private List<FriendDTO> friendsCompany;
    private int newPartner;
    private boolean enableOTP;

    public UserInfoDTO(UserDTO user){
        login = user.getLogin();
        fullName = user.getFullName();
        description =user.getDescription();
        security = user.getPrivileges().buildJson();
        friends = user.getFriends();
        company = user.getCompanyDTO();
        this.enableOTP = user.getAuthData()!=null && user.getAuthData().length()>0;
    }


    public UserInfoDTO(UserDTO user, String sessionId, List<FriendDTO> friendsCompany) {
        this(user);
        this.sessionId = sessionId;
        this.friendsCompany = friendsCompany;
    }

    public void setNewPartner(int newPartner) {
        this.newPartner = newPartner;
    }

    public String getFullName() {
        return fullName;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("UserInfoDTO{");
        sb.append("login='").append(login).append('\'');
        sb.append(", fullName='").append(fullName).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", security='").append(security).append('\'');
        sb.append(", sessionId='").append(sessionId).append('\'');
        sb.append(", company=").append(company);
        sb.append(", friends=").append(friends);
        sb.append(", enableOTP=").append(enableOTP);
        sb.append('}');
        return sb.toString();
    }
}
