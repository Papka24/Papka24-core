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

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

import java.util.ArrayList;
import java.util.List;

public class ShareResRequest {
    public static TypeAdapter<ShareResRequest> gson = new Gson().getAdapter(ShareResRequest.class);
    private ArrayList<ShareResDTO> requestList;
    private String[] emails;
    private String comment;
    private boolean empty;
    private Integer mode;

    public String getComment() {
        return comment;
    }

    public String[] getEmails() {
        return emails;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public List<ShareResDTO> getRequestList() {
        if (requestList == null){
            requestList = new ArrayList<>(emails.length);
            for (String email: emails){
                requestList.add(new ShareResDTO(email.trim(), comment.replaceAll("<[^>]*>",""), 0));
            }
        } else {
            String mainComment = null;
            Integer mode = null;
            if (this.comment != null) {
                mainComment = this.comment;
            }
            if (this.mode != null) {
                mode = this.mode;
            }
            for (ShareResDTO r : requestList){
                String shareComment = r.getComment();
                Integer shareMode = r.getMode();
                if (shareComment == null && mainComment != null) {
                    r.setComment(mainComment);
                }
                if (r.getComment() != null) {
                    r.setComment(r.getComment().replaceAll("<[^>]*>",""));
                }
                if (shareMode == null && mode != null) {
                    r.setMode(mode);
                }
            }
        }
        return requestList;
    }

    public boolean isEmpty() {
        return emails==null && requestList == null
                || requestList == null && emails.length == 0
                || emails == null && requestList.size() == 0
                || requestList!= null && emails != null && requestList.size() == 0 && emails.length == 0;
    }
}
