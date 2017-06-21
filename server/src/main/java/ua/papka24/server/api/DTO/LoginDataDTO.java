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

public class LoginDataDTO {
    public static TypeAdapter<LoginDataDTO> gson = new Gson().getAdapter(LoginDataDTO.class);
    private String name;
    private String login;
    private String password;
    private String captcha;
    private Integer otp;

    public String getLogin() {
        if(login == null){
            return null;
        }
        return login.toLowerCase();
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public String getCaptcha() {
        return captcha;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("LoginDataDTO{");
        sb.append("name='").append(name).append('\'');
        sb.append(", login='").append(login).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public Integer getOTP() {
        return otp;
    }

    public void trim() {
        this.login = login.trim();
        this.password = password.trim();
    }
}
