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
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.db.dto.ChatMessage;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;
import ua.papka24.server.service.chat.ChatManager;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/chat")
public class Chat extends REST {

    private static final Logger log = LoggerFactory.getLogger(Chat.class);
    private static final Gson gson = new Gson();
    private static final int MESSAGE_MAX_LENGTH = 1000;

    @POST
    @Path("/{roomId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveMessages(@HeaderParam("sessionid") String sessionId, @PathParam("roomId") Long roomId, String text){
        log.info("sessionid:{}, jsonContent:{}", sessionId, text);
        Session session = SessionsPool.find(sessionId);
        if(session==null){
            return ERROR_FORBIDDEN;
        }
        if(roomId==null || text==null || text.isEmpty() || text.length()>MESSAGE_MAX_LENGTH){
            return ERROR_BAD_REQUEST;
        }
        try {
            ChatMessage chatMessage = new ChatMessage(session.getUser().getLogin(), roomId, text);
            chatMessage = ChatManager.getInstance().saveMessage(chatMessage);
            return Response.ok().entity(ChatMessage.gson.toJson(chatMessage)).build();
        }catch (JsonSyntaxException jse){
            log.error("error save messages", jse);
            return ERROR_BAD_REQUEST;
        }catch (Exception ex){
            log.error("error save messages", ex);
        }
        return ERROR_SERVER;
    }

    @PUT
    @Path("/{roomId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateLastRead(@HeaderParam("sessionid") String sessionId, @PathParam("roomId") Long roomId){
        Session session = SessionsPool.find(sessionId);
        if(session==null){
            return ERROR_FORBIDDEN;
        }
        if(roomId==null){
            return ERROR_BAD_REQUEST;
        }
        try {
            ChatManager.getInstance().setRoomStatusRead(roomId, session.getUser().getLogin());
            return Response.ok().build();
        }catch (JsonSyntaxException jse){
            log.error("error updateLastRead", jse);
            return ERROR_BAD_REQUEST;
        }catch (Exception ex){
            log.error("error get save messages", ex);
        }
        return ERROR_SERVER;
    }

    @GET
    @Path("/{roomId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMessages(@HeaderParam("sessionid") String sessionId, @PathParam("roomId") Long roomId, @QueryParam("t") Long time) {
        Session session = SessionsPool.find(sessionId);
        if(session==null){
            return ERROR_FORBIDDEN;
        }
        if(roomId==null){
            return ERROR_BAD_REQUEST;
        }
        try {
            List<ChatMessage> messages = ChatManager.getInstance().getMessages(session.getUser(), roomId, time);
            if(messages==null){
                return ERROR_NOT_FOUND;
            }
            return Response.ok().entity(gson.toJson(messages, ChatMessage.itemsListType)).build();
        }catch (Exception ex){
            log.error("error get chat messages", ex);
        }
        return ERROR_SERVER;
    }

    @DELETE
    @Path("/{roomId}/{time}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteMessage(@HeaderParam("sessionid") String sessionId, @PathParam("roomId") Long roomId,@PathParam("time") Long time) {
        Session session = SessionsPool.find(sessionId);
        if(session==null){
            return ERROR_FORBIDDEN;
        }
        if(roomId==null || time == null){
            return ERROR_BAD_REQUEST;
        }
        try {
            ChatManager.getInstance().deleteMessage(session.getUser().getLogin(), roomId, time);
            return Response.ok().build();
        }catch (Exception ex){
            log.error("error get chat messages", ex);
        }
        return ERROR_SERVER;
    }
}
