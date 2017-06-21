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
import java.util.List;


public class ResourcesChangeEvent implements Notification {
    private List<Long> docID;
    private String eventType;
    private Long companyId;
    private String userLogin;
    private String initiator;
    private transient String[] shareEmails;
    private long time;
    private List<ResourceDTO> resourceDTO;
    private String eventData;

    public boolean containEvent(Long docid) {
        return docID.contains(docid);
    }

    public ResourcesChangeEvent() {
        this.time = new Date().getTime();
    }

    public List<ResourceDTO> getResourceDTO() {
        return resourceDTO;
    }

    public void setResourceDTO(List<ResourceDTO> resourceDTO) {
        this.resourceDTO = resourceDTO;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getEventData() {
        return eventData;
    }

    public void setEventData(String eventData) {
        this.eventData = eventData;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getUserLogin() {
        return userLogin;
    }

    public void setUserLogin(String userLogin) {
        this.userLogin = userLogin;
    }

    public String[] getShareEmails() {
        return shareEmails;
    }

    public void setShareEmails(String[] shareEmails) {
        this.shareEmails = shareEmails;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public List<Long> getDocID() {
        return docID;
    }

    public void setDocID(List<Long> docID) {
        this.docID = docID;
    }

    public String getInitiator() {
        return initiator;
    }

    public void setInitiator(String initiator) {
        this.initiator = initiator;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ResourcesChangeEvent{");
        sb.append("docID=").append(docID);
        sb.append(", eventType='").append(eventType).append('\'');
        sb.append(", companyId=").append(companyId);
        sb.append(", userLogin='").append(userLogin).append('\'');
        sb.append(", initiator='").append(initiator).append('\'');
        sb.append(", shareEmails=").append(Arrays.toString(shareEmails));
        sb.append(", time=").append(time);
        sb.append(", eventData='").append(eventData).append('\'');
        sb.append('}');
        return sb.toString();
    }
}