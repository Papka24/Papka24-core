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

import ua.papka24.server.service.events.main.EventType;
import ua.papka24.server.service.events.main.data.Notification;
import ua.papka24.server.api.DTO.ShareTemplate;

public class EmailEvent implements Notification {

    private EventType eventType;
    private String user;
    private String toEmail;
    private long docId;
    private String docName;
    private String authorName;
    private boolean sendInvite;
    private String comment;
    private final ShareTemplate template;

    public EmailEvent(EventType eventType, String user, String toEmail, long docId, String docName, String authorName, boolean sendInvite, String comment, ShareTemplate template){
        this.eventType = eventType;
        this.user = user;
        this.toEmail = toEmail;
        this.docId = docId;
        this.docName = docName;
        this.authorName = authorName;
        this.sendInvite = sendInvite;
        this.comment = comment;
        this.template = template;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getToEmail() {
        return toEmail;
    }

    public void setToEmail(String toEmail) {
        this.toEmail = toEmail;
    }

    public long getDocId() {
        return docId;
    }

    public void setDocId(long docId) {
        this.docId = docId;
    }

    public String getDocName() {
        return docName;
    }

    public void setDocName(String docName) {
        this.docName = docName;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public boolean isSendInvite() {
        return sendInvite;
    }

    public void setSendInvite(boolean sendInvite) {
        this.sendInvite = sendInvite;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public ShareTemplate getTemplate() {
        return template;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("EmailEvent{");
        sb.append("eventType=").append(eventType);
        sb.append(", user='").append(user).append('\'');
        sb.append(", toEmail='").append(toEmail).append('\'');
        sb.append(", docId=").append(docId);
        sb.append(", docName='").append(docName).append('\'');
        sb.append(", authorName='").append(authorName).append('\'');
        sb.append(", sendInvite=").append(sendInvite);
        sb.append(", comment='").append(comment).append('\'');
        sb.append(", template=").append(template);
        sb.append('}');
        return sb.toString();
    }
}
