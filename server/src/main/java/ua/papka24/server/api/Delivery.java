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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.db.dao.DeliveryDAO;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("delivery")
public class Delivery extends REST{

    private static final Logger log = LoggerFactory.getLogger(Delivery.class);


    @GET
    @Path("check/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response check(@HeaderParam("sessionid") String sessionId, @PathParam("id") Integer id){
        Session session = SessionsPool.find(sessionId);
        if(session == null){
            return ERROR_NOT_FOUND;
        }
        try {
            return ERROR_NOT_FOUND;
        }catch (Exception ex){
            log.error("Delivery check",ex);
        }
        return ERROR_SERVER;
    }

    @POST
    @Path("ans/{id}/{egrpou}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveAnswer(@HeaderParam("sessionid") String sessionId,
                               @PathParam("id") Integer id,
                               @PathParam("egrpou") Long egrpou,
                               String request){
        Session session = SessionsPool.find(sessionId);
        if(session == null){
            return ERROR_NOT_FOUND;
        }
        boolean b = DeliveryDAO.getInstance().saveAnswer(session.getUser().getLogin(), id, egrpou, request);
        if(b){
            return Response.ok().build();
        }else{
            return ERROR_SERVER;
        }
    }
}
