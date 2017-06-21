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

package ua.papka24.server.service.events.event;

import ua.papka24.server.db.dto.ResourceDTO;
import ua.papka24.server.service.events.main.data.Notification;

import java.util.Arrays;
import java.util.Date;


//!является основой для оповещение subscribers
public class ResourceChangeEvent implements Notification {
    private Long docID;
    private String eventType;
    private String eventData;
    private String userLogin;
    private transient String[] extraData;
    private ResourceDTO resourceDTO;
    private final long time;

    public ResourceChangeEvent() {
        this.time = new Date().getTime();
    }

    private ResourceChangeEvent(Long docID, String eventType, String eventData, String userLogin) {
        this();
        this.docID = docID;
        this.eventType = eventType;
        this.eventData = eventData;
        this.userLogin = userLogin;
    }

    public Long getDocID() {
        return docID;
    }

    public void setDocID(Long id) {
        this.docID = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventData() {
        return eventData;
    }

    public void setEventData(String eventData) {
        this.eventData = eventData;
    }

    public String getUserLogin() {
        return userLogin.toLowerCase();
    }

    public void setUserLogin(String userLogin) {
        this.userLogin = userLogin;
    }

    public String[] getExtraData() {
        return extraData;
    }

    public void setExtraData(String[] extraData) {
        this.extraData = extraData;
    }

    public ResourceChangeEvent copyWithoutExtra() {
        return new ResourceChangeEvent(this.docID,this.eventType,this.eventData,this.userLogin);
    }

    public ResourceDTO getResourceDTO() {
        return resourceDTO;
    }

    public void setResourceDTO(ResourceDTO resourceDTO) {
        this.resourceDTO = resourceDTO;
    }

    public long getTime() {
        return time;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ResourceChangeEvent{");
        sb.append("docID='").append(docID).append('\'');
        sb.append(", eventType='").append(eventType).append('\'');
        sb.append(", userLogin='").append(userLogin).append('\'');
        sb.append(", extraData=").append(Arrays.toString(extraData));
        sb.append(", resourceDTO=").append(resourceDTO);
        sb.append(", time=").append(time);
        sb.append('}');
        return sb.toString();
    }
}
