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

import java.util.LinkedHashMap;
import java.util.Map;



public class AdminDashboardDTO {
    public static TypeAdapter<AdminDashboardDTO> gson = new Gson().getAdapter(AdminDashboardDTO.class);
    private long sessionCount;
    private long userCount;
    private long docsCount;
    private long shareCount;
    private long signCount;
    private Map<String,Long> userTrends;
    private Map<String,Long> docsTrends;
    private Map<String,Long> shareTrends;
    private Map<String,Long> signTrends;
    private RatioDTO signRatio;
    private RatioDTO uploadRatio;
    private RatioDTO loginRatio;

    public void setSessionCount(long sessionCount) {
        this.sessionCount = sessionCount;
    }

    public void setUserCount(long userCount) {
        this.userCount = userCount;
    }

    public void setDocsCount(long docsCount) {
        this.docsCount = docsCount;
    }

    public void setShareCount(long shareCount) {
        this.shareCount = shareCount;
    }

    public void setSignCount(long signCount) {
        this.signCount = signCount;
    }

    public Map<String, Long> getUserTrends() {
        if(userTrends==null){
            userTrends = new LinkedHashMap<>();
        }
        return userTrends;
    }

    public Map<String, Long> getDocsTrends() {
        if(docsTrends==null){
            docsTrends = new LinkedHashMap<>();
        }
        return docsTrends;
    }

    public Map<String, Long> getShareTrends() {
        if(shareTrends==null){
            shareTrends = new LinkedHashMap<>();
        }
        return shareTrends;
    }

    public Map<String, Long> getSignTrends() {
        if(signTrends == null){
            signTrends = new LinkedHashMap<>();
        }
        return signTrends;
    }

    public void setSignRatio(RatioDTO signRatio) {
        this.signRatio = signRatio;
    }

    public void setUploadRatio(RatioDTO uploadRatio) {
        this.uploadRatio = uploadRatio;
    }

    public void setLoginRatio(RatioDTO loginRatio) {
        this.loginRatio = loginRatio;
    }

    @Override
    public String toString() {
        return "AdminDashboardDTO{" + "sessionCount=" + sessionCount +
                ", userCount=" + userCount +
                ", docsCount=" + docsCount +
                ", shareCount=" + shareCount +
                ", signCount=" + signCount +
                ", userTrends=" + userTrends +
                ", docsTrends=" + docsTrends +
                ", shareTrends=" + shareTrends +
                ", signTrends=" + signTrends +
                ", signRatio=" + signRatio +
                ", uploadRatio=" + uploadRatio +
                ", loginRatio=" + loginRatio +
                '}';
    }
}
