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

package ua.papka24.server.db.dto;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;

import java.util.List;

public class SignInfoDTO {
    public static TypeAdapter<SignInfoDTO> gson = new Gson().getAdapter(SignInfoDTO.class);

    public static String VERIFY_RESULT_VALID = "valid";
    public static String VERIFY_RESULT_VALID_WITHOUT_DATA = "validWithoutData";
    public static String VERIFY_RESULT_INVALID_BY_TSP = "invalidByTimestamp";
    public static String VERIFY_RESULT_INVALID = "invalid";

    private String hash;
    private long time;
    private List<VerifyInfoDTO> verifyInfo;

    public SignInfoDTO(String hash, long time, String verifyInfo) {
        this.hash = hash;
        this.time = time;
        this.verifyInfo = new Gson().fromJson(verifyInfo, new TypeToken<List<VerifyInfoDTO>>() {}.getType());
    }

    public SignInfoDTO(String hash, long time, List<VerifyInfoDTO> verifyInfo) {
        this.hash = hash;
        this.time = time;
        this.verifyInfo = verifyInfo;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
