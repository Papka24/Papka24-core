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

package ua.papka24.server.api;

import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.api.DTO.LoginDataDTO;
import ua.papka24.server.api.DTO.UserInfoDTO;
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.db.dao.UserDAO;
import ua.papka24.server.db.redis.RedisDAOManager;
import ua.papka24.server.security.SecurityAttributes;
import ua.papka24.server.api.helper.CaptchaHelper;
import ua.papka24.server.api.helper.EmailHelper;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;
import ua.papka24.server.utils.logger.Event;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;


@Path("reg")
public class Registration extends REST {
    private SecureRandom random = new SecureRandom();
    // create - создать первичную структуру базы
    private static ExpiringMap<String, LoginDataDTO> map = ExpiringMap.builder().expirationPolicy(ExpirationPolicy.CREATED).expiration(30, TimeUnit.MINUTES).build();
    private static ExpiringMap<String, String> restoreMap = ExpiringMap.builder().expirationPolicy(ExpirationPolicy.CREATED).expiration(30, TimeUnit.MINUTES).build();
    private static final Logger log = LoggerFactory.getLogger("registration");

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response registration(String jsonRequest) {
        try {
            LoginDataDTO data = LoginDataDTO.gson.fromJson(jsonRequest);
            log.info("registraion:{}",data);
            if (data.getPassword() == null
                    || data.getName() == null
                    || data.getCaptcha() == null
                    || data.getCaptcha().length()==0
                    || data.getLogin() == null
                    || !EmailHelper.validate(data.getLogin())
                    || !CaptchaHelper.checkCaptcha(data.getCaptcha(), null)) {
                return ERROR_BAD_REQUEST;
            } else {
                UserDTO user = UserDAO.getInstance().getUser(data.getLogin());
                if (user == null) {
                    String secret = new BigInteger(128, random).toString(32);
                    // Установка
                    map.put(secret, data);
                    RedisDAOManager.getInstance().saveRegResInfo(secret,jsonRequest);
                    EmailHelper.sendInviteEmail(data.getLogin(), data.getName(), secret);
                    return Response.ok().build();
                } else {
                    return ERROR_CONFLICT;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ERROR_SERVER;
    }

    @PUT
    @Produces(MediaType.TEXT_PLAIN)
    public Response registrationFinish(String secret) {
        LoginDataDTO data = map.get(secret);
        if(data==null){
            try {
                String sData = RedisDAOManager.getInstance().getRegResInfo(secret);
                if (sData != null) {
                    data = LoginDataDTO.gson.fromJson(sData);
                    map.put(secret,data);
                }
            }catch (IOException iox){
                log.warn("error", iox);
            }
        }
        if (data != null) {
            UserDTO user = new UserDTO(data.getLogin(), data.getName(), data.getPassword(), new SecurityAttributes());
            if (UserDAO.getInstance().create(user) > 0) {
                map.remove(secret);
                if(user.getCompanyId()!=null) {
                    UserDAO.getInstance().setCompany(user, user.getCompanyId());
                }
                ResourceDAO.getInstance().collectUserFriends(user);
                Session session = SessionsPool.openSession(user, false);
                if (session == null) {
                    return ERROR_SERVER;
                } else {
                    log.info("register new user:{}:{}",session.getUser(), session.getSessionId(), Event.REGISTRATION);
                    return Response.ok(UserInfoDTO.gson.toJson(new UserInfoDTO(session.getUser(), session.getSessionId(), null))).build();
                }
            } else {
                return ERROR_CONFLICT;
            }
        } else {
            return ERROR_NOT_FOUND;
        }
    }

    @POST
    @Path("/restore/{email}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response restore(@PathParam("email") String email, String captcha) {
        UserDTO user = UserDAO.getInstance().getUser(email);
        if (user == null) {
            return ERROR_NOT_FOUND;
        } else {
            if (!CaptchaHelper.checkCaptcha(captcha, null)) {
                return ERROR_BAD_REQUEST;
            }
            String secret = new BigInteger(128, random).toString(32);
            // Установка
            restoreMap.put(secret, email);
            RedisDAOManager.getInstance().saveRegResInfo(secret,email);
            EmailHelper.sendResetPasswordEmail(email, user.getFullName(), secret);
            return Response.ok().build();
        }
    }

    @POST
    @Path("/restoreFinish/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response restoreFinish(@PathParam("id") String id, String password) {

        String login = restoreMap.get(id);
        if(login==null){
            login = RedisDAOManager.getInstance().getRegResInfo(id);
        }
        if (login != null && UserDAO.getInstance().changePassword(login, password) > 0) {
            UserDAO.getInstance().disableOTP(login);
            map.remove(id);
            return Response.ok().build();
        }
        return ERROR_NOT_FOUND;
    }
}
