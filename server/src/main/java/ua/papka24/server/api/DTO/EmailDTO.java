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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.activation.DataSource;

public class EmailDTO implements Comparable<EmailDTO>{
    private String user;
    private String to;
    private String subject;
    private String message;
    private String htmlMessage;
    private Priority priority;
    private Template tepmplate;
    private DataSource dataSource;
    private ShareTemplate shareTemplate;

    public EmailDTO(String user, String to, String subject, String messages, String htmlMessage, Priority priority, Template template) {
       this(user, to, subject, messages, htmlMessage, priority, template, null);
    }

    public EmailDTO(String user, String to, String subject, String messages, String htmlMessage, Priority priority, Template template, ShareTemplate shareTemplate) {
        this.user = user;
        this.to = to;
        this.subject = subject;
        this.message = messages;
        this.htmlMessage = htmlMessage;
        this.priority = priority;
        this.tepmplate = template;
        this.shareTemplate = shareTemplate;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getHtmlMessage() {
        return htmlMessage;
    }

    public void setHtmlMessage(String htmlMessage) {
        this.htmlMessage = htmlMessage;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Template getTepmplate() {
        return tepmplate;
    }

    public void setTepmplate(Template tepmplate) {
        this.tepmplate = tepmplate;
    }

    @Override
    public int compareTo(EmailDTO emailDTO) {
        return this.priority.compareTo(emailDTO.getPriority());
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public ShareTemplate getShareTemplate() {
        return shareTemplate;
    }

    public enum Priority{
        LOW,
        NORMAL,
        HIGH
    }

    public enum Template{
        SHARE,
        SIGN,
        INVITE_TO_GROUP,
        ACCEPT_GROUP_INVITE,
        INVITE,
        RESET_PASSWORD,
        DISABLE_SHARE,
        SPAM1,
        SPAM2,
        SPAM5,
        SPAM34,
        NEWSONE,
        NEW_CHAT_MESSAGE,
        TEMPLATE_SHARE
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof EmailDTO)) return false;

        EmailDTO emailDTO = (EmailDTO) o;

        return new EqualsBuilder()
                .append(user, emailDTO.user)
                .append(to, emailDTO.to)
                .append(subject, emailDTO.subject)
                .append(message, emailDTO.message)
                .append(htmlMessage, emailDTO.htmlMessage)
                .append(priority, emailDTO.priority)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(user)
                .append(to)
                .append(subject)
                .append(message)
                .append(htmlMessage)
                .append(priority)
                .toHashCode();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("EmailDTO{");
        sb.append("user='").append(user).append('\'');
        sb.append(", to='").append(to).append('\'');
        sb.append(", subject='").append(subject).append('\'');
        sb.append(", message='").append(message).append('\'');
        sb.append(", htmlMessage='").append(htmlMessage).append('\'');
        sb.append(", priority=").append(priority);
        sb.append(", tepmplate=").append(tepmplate);
        sb.append(", dataSource=").append(dataSource);
        sb.append(", shareTemplate=").append(shareTemplate);
        sb.append('}');
        return sb.toString();
    }
}
