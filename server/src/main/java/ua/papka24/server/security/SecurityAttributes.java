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

package ua.papka24.server.security;

import java.io.Serializable;


public class SecurityAttributes implements Serializable {

    private static final long serialVersionUID  = 0;

    public static final long READ_BILLING       = 0b00100000_00000000_00000000_00000000;
    public static final long READ_ADMIN         = 0b01000000_00000000_00000000_00000000;
    public static final long WRITE_ADMIN        = 0b10000000_00000000_00000000_00000000;
    public static final long FULL_ADMIN         = READ_ADMIN | WRITE_ADMIN;

    private long securityDescriptor;

    public SecurityAttributes() {
        this.securityDescriptor = 0;
    }

    public SecurityAttributes(Long securityDescriptor) {
        if (securityDescriptor == null) {
            this.securityDescriptor = 0;
        } else {
            this.securityDescriptor = securityDescriptor;
        }
    }

    public long getSecurityDescriptor() {
        return securityDescriptor;
    }

    public void setSecurityDescriptor(long securityDescriptor) {
        this.securityDescriptor = securityDescriptor;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("SecurityDescriptor (");
        for (int i = 31; i >= 0; --i) {
            ret.append((securityDescriptor & (1 << i)) == 0 ? '0' : '1');
            if (i % 8 == 0 && i > 0) {
                ret.append(' ');
            }
        }
        ret.append(')');
        return ret.toString();
    }

    public String buildJson() {
        StringBuilder sb = new StringBuilder("[");
        if ((securityDescriptor & READ_ADMIN) > 0) {
            sb.append("\"READ_ADMIN\",");
        } else if ((securityDescriptor & WRITE_ADMIN) > 0) {
            sb.append("\"WRITE_ADMIN\",");
        } else if ((securityDescriptor & READ_BILLING) > 0) {
            sb.append("\"READ_BILLING\"");
        }
        if (sb.length() > 1)
            sb.setLength(sb.length() - 1);
        return sb.append(']').toString();
    }

    public boolean can(long action) {
        return (securityDescriptor & action) > 0;
    }

    public boolean canOr(long[] actions){
        for(long action : actions){
            if((securityDescriptor & action) > 0){
                return true;
            }
        }
        return false;
    }
}
