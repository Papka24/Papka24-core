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

/**
 * Class for code verify info.
 */
public class OCSPStatusInfoDTO {
    public final class RespStatus {
        public static final String SUCCESSFUL = "successful";
        public static final String MALFORMED_REQUEST = "malformedRequest";
        public static final String INTERNAL_ERROR = "internalError";
        public static final String TRY_LATER = "tryLater";
        public static final String SIG_REQUIRED = "sigRequired";
        public static final String UNAUTHORIZED = "unauthorized";
        public static final String UNKNOWN = "unknown";
        private RespStatus() { }
    }

    public final class RespSign {
        public static final String VALID = "valid";
        public static final String INVALID = "invalid";
        public static final String NO_CERT_FOR_VERIFY = "noCertForVerify";
        public static final String UNKNOWN = "unknown";
        private RespSign() {}
    }

    public final class CertStatus {
        public static final String GOOD = "good";
        public static final String REVOKED = "revoked";
        public static final String UNKNOWN = "unknown";
        private CertStatus() {}
    }

    public final class RevocationReason {
        public static final String UNSPECIFIED = "unspecified";
        public static final String KEY_COMPROMISE = "keyCompromise";
        public static final String CA_COMPROMISE = "cACompromise";
        public static final String AFFILIATION_CHANGED = "affiliationChanged";
        public static final String SUPERSEDED = "superseded";
        public static final String CESSATION_OF_OPERATION = "cessationOfOperation";
        public static final String CERTIFICATE_HOLD = "certificateHold";
        public static final String REMOVE_FROM_CRL = "removeFromCRL";
        public static final String PRIVILEGE_WITHDRAWN = "privilegeWithdrawn";
        public static final String AA_COMPROMISE = "aACompromise";
        public static final String UNKNOWN = "unknown";
        private RevocationReason() {}
    }

    public static TypeAdapter<OCSPStatusInfoDTO> gson = new Gson().getAdapter(OCSPStatusInfoDTO.class);
    public String OCSPRespStatus; // "successful", "malformedRequest", "internalError", "tryLater", "sigRequired", "unauthorized"
    public String OCSPRespSign; // "valid", "invalid", "unknown"
    public String status;
    public Long revocationTime;
    public String revocationReason;

    public OCSPStatusInfoDTO(String OCSPRespStatus) {
        this.OCSPRespStatus = OCSPRespStatus;
        OCSPRespSign = null;
        status = null;
        revocationTime = null;
        revocationReason = null;
    }

    public OCSPStatusInfoDTO(String OCSPRespSign, String status, Long revocationTime, String revocationReason) {
        this.OCSPRespStatus = RespStatus.SUCCESSFUL;
        this.OCSPRespSign = OCSPRespSign;
        this.status = status;
        this.revocationTime = revocationTime;
        this.revocationReason = revocationReason;
    }
}
