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

package ua.papka24.server.service.events.main.data;

import ua.papka24.server.api.DTO.ShareResDTO;
import ua.papka24.server.db.dto.ChatMessage;
import ua.papka24.server.db.dto.ResourceDTO;
import ua.papka24.server.service.events.event.ResourceChangeEvent;
import ua.papka24.server.service.events.event.ResourcesChangeEvent;
import ua.papka24.server.service.events.main.EventType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


public interface Notification {

    static Builder builder(){
        return new Builder();
    }

    class Builder {
        private EventType eventType;
        private String userLogin;
        private String eventData;
        private ResourceDTO resource;
        private String[] ids;
        private List<ShareResDTO> sharedEmails;
        private Long companyId;
        private ChatMessage chatMessage;
        private String egrpou;
        private String companyName;
        private List<ShareResDTO> shareResDTOList;

        public Builder shareResDTO(List<ShareResDTO> shareResDTOList) {
            this.shareResDTOList = shareResDTOList;
            return this;
        }

        public Builder chatMessage(ChatMessage chatMessage) {
            this.chatMessage = chatMessage;
            return this;
        }

        public Builder companyName(String companyName) {
            this.companyName = companyName;
            return this;
        }

        public Builder egrpou(String egrpou) {
            this.egrpou = egrpou;
            return this;
        }

        public Builder companyId(Long companyId) {
            this.companyId = companyId;
            return this;
        }

        public Builder eventType(EventType eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder userLogin(String userLogin) {
            this.userLogin = userLogin;
            return this;
        }

        public Builder id(Long id) {
            if(this.ids==null){
                String[] ids = new String[1];
                ids[0] = String.valueOf(id);
                this.ids = ids;
            }else{
                String[] newIds = Arrays.copyOf(this.ids, this.ids.length + 1);
                newIds[newIds.length-1] = String.valueOf(id);
                this.ids = newIds;
            }
            return this;
        }

        public Builder eventData(String eventData) {
            this.eventData = eventData;
            return this;
        }

        public Builder resource(ResourceDTO resource) {
            this.resource = resource;
            return this;
        }

        public Builder ids(String[] ids) {
            this.ids = ids;
            return this;
        }

        public Builder sharedEmails(List<ShareResDTO> sharedEmails) {
            this.sharedEmails = sharedEmails;
            return this;
        }

        public ResourcesChangeEvent createWSSNotification(){
            ResourcesChangeEvent event = new ResourcesChangeEvent();
            event.setEventType(this.eventType.name());
            event.setUserLogin(this.userLogin);
            if(this.resource!=null) {
                event.setResourceDTO(Collections.singletonList(this.resource));
            }
            event.setDocID(Arrays.stream(this.ids).map(Long::valueOf).collect(Collectors.toList()));
            if(this.sharedEmails!=null) {
                event.setShareEmails(this.sharedEmails.stream().map(ShareResDTO::getEmail).distinct().toArray(String[]::new));
            }
            if (this.eventData != null) {
                event.setEventData(this.eventData);
            }
            return event;
        }

        public ResourcesChangeEvent createCompanyChangeNotification(){
            ResourcesChangeEvent msg = new ResourcesChangeEvent();
            msg.setEventType(this.eventType.name());
            msg.setUserLogin(this.userLogin);
            msg.setEventData(this.eventData);
            msg.setCompanyId(this.companyId);
            msg.setDocID(Collections.singletonList(this.companyId));
            return msg;
        }

        public ChatMessage createChatNotification(){
            return chatMessage;
        }

        public ResourceChangeEvent convertChatToResourceChange(){
            ResourceChangeEvent rce = new ResourceChangeEvent();
            rce.setDocID(chatMessage.roomId);
            rce.setEventType(EventType.NEW_CHAT_MESSAGE.name());
            rce.setEventData(chatMessage.getText());
            rce.setUserLogin(chatMessage.login);
            return rce;
        }

        public ResourceChangeEvent createResourceChangeNotification(){
            ResourceChangeEvent msg = new ResourceChangeEvent();
            msg.setDocID(Long.valueOf(this.ids[0]));
            msg.setEventType(this.eventType.name());
            msg.setEventData(this.eventData);
            msg.setUserLogin(this.userLogin);
            msg.setResourceDTO(this.resource);
            if(this.shareResDTOList!=null) {
                msg.setExtraData(this.shareResDTOList.stream().map(ShareResDTO::getEmail).toArray(String[]::new));
            }
            return msg;
        }
    }
}
