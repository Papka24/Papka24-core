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

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.db.redis.model.LoginRedisInfo;
import ua.papka24.server.security.SecurityAttributes;
import ua.papka24.server.db.dao.UserDAO;
import ua.papka24.server.db.redis.RedisDAOManager;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("redis")
public class Redis extends REST{

    private static final Logger log = LoggerFactory.getLogger(Redis.class);
    private static final Gson gson = new Gson();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@HeaderParam("sessionId") String sessionId){
        Session s = SessionsPool.findWithRight(sessionId, SecurityAttributes.READ_ADMIN, SecurityAttributes.WRITE_ADMIN);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            List<LoginRedisInfo> sessions = RedisDAOManager.getInstance().getSessions();
            return Response.ok().entity(new Gson().toJson(sessions)).build();
        }
    }

    @GET
    @Path("/invalidateAll")
    @Produces(MediaType.APPLICATION_JSON)
    public Response invalidateAll(@HeaderParam("sessionid") String sessionId){
        Session s = SessionsPool.findWithRight(sessionId, SecurityAttributes.READ_ADMIN, SecurityAttributes.WRITE_ADMIN);
        if (s == null) {
            return ERROR_FORBIDDEN;
        }
        RedisDAOManager.getInstance().invalidateAllUsersSessions();
        return Response.ok().build();
    }

    @GET
    @Path("/invalidate/{login}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response invalidate(@HeaderParam("sessionid") String sessionId, @PathParam("login") String login){
        Session s = SessionsPool.findWithRight(sessionId, SecurityAttributes.READ_ADMIN, SecurityAttributes.WRITE_ADMIN);
        if (s == null) {
            return ERROR_FORBIDDEN;
        }
        RedisDAOManager.getInstance().markChanged(login);
        return Response.ok().build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/list")
    public Response getUsersInfo(@HeaderParam("sessionid") String sessionId){
        Session s = SessionsPool.findWithRight(sessionId, SecurityAttributes.READ_ADMIN, SecurityAttributes.WRITE_ADMIN);
        if (s == null) {
            return ERROR_FORBIDDEN;
        }
        try {
            List<LoginRedisInfo> sessionsInfo = RedisDAOManager.getInstance().getSessionsInfo();
            return Response.ok().entity(gson.toJson(sessionsInfo)).build();
        }catch (Exception ex) {
            log.error("list error", ex);
        }
        return ERROR_SERVER;
    }

    //carefully!!
//    @GET
//    @Produces(MediaType.APPLICATION_JSON)
//    @Path("flushall")
//    public Response flushAll(@HeaderParam("sessionid") String sessionId){
//        Session s = SessionsPool.findWithRight(sessionId, SecurityAttributes.WRITE_ADMIN);
//        if (s == null) {
//            return ERROR_FORBIDDEN;
//        }
//        RedisDAOManager.getInstance().flushAll();
//        return Response.ok().build();
//    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("resetall")
    public Response reset(@HeaderParam("sessionid") String sessionId){
        Session s = SessionsPool.findWithRight(sessionId, SecurityAttributes.WRITE_ADMIN);
        if (s == null) {
            return ERROR_FORBIDDEN;
        }
        UserDAO.getInstance().list().forEach(RedisDAOManager.getInstance()::markChanged);
        return Response.ok().build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("emails/clean")
    public Response cleanEmailList(@HeaderParam("sessionid") String sessionId){
        Session s = SessionsPool.findWithRight(sessionId, SecurityAttributes.READ_ADMIN, SecurityAttributes.WRITE_ADMIN);
        if (s == null) {
            return ERROR_FORBIDDEN;
        }
        Long aLong = RedisDAOManager.getInstance().cleanEmailList();
        if(aLong == -1){
            return ERROR_SERVER;
        }else {
            return Response.ok().build();
        }
    }
}