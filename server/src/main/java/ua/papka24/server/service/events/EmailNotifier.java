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

package ua.papka24.server.service.events;

import ua.papka24.server.service.events.event.EmailEvent;
import ua.papka24.server.service.events.main.EventType;
import ua.papka24.server.api.DTO.ShareTemplate;


public class EmailNotifier {

    private EmailNotifier(){
    }

    private static class Singleton {
        private static final EmailNotifier HOLDER_INSTANCE = new EmailNotifier();
    }

    public static EmailNotifier getInstance() {
        return EmailNotifier.Singleton.HOLDER_INSTANCE;
    }

    //share email
    public void notifyEmail(EventType eventType, String user, String toEmail, long docId, String docName, String authorName, boolean sendInvite, String comment, ShareTemplate template){
        EmailEvent emailEvent = new EmailEvent(eventType, user, toEmail, docId, docName, authorName, sendInvite, comment, template);
        EventManager.getInstance().addNotification(emailEvent);
    }

    //sign email
    public void notifyEmail(EventType sign, String login, String docAuthor, Long id, String docName, String fullName) {
        notifyEmail(sign, login, docAuthor, id, docName, fullName, false, null, null);
    }
}