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

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle.asn1.cms.SignerIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.RespID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CertificateUtils {

    static public SubjectKeyIdentifier getSKID(Certificate cert) {
         return SubjectKeyIdentifier.fromExtensions(cert.getTBSCertificate().getExtensions());
    }

    public static byte[] getAuthKeyId(Certificate cert) {
        return AuthorityKeyIdentifier.fromExtensions(cert.getTBSCertificate().getExtensions()).getKeyIdentifier();
    }

    public static byte[] getSubjKeyId(Certificate cert) {
        return getSKID(cert).getKeyIdentifier();
    }

    public static String getOcspUrl(Certificate certificate) {
        AuthorityInformationAccess aia = AuthorityInformationAccess.fromExtensions(certificate.getTBSCertificate().getExtensions());
        AccessDescription[] ads = aia.getAccessDescriptions();

        for (AccessDescription ad : ads) {
            if (ad.getAccessLocation().getTagNo() == GeneralName.uniformResourceIdentifier) {
                return ad.getAccessLocation().getName().toString();
            }
        }

        return null;
    }

    public static  List<Certificate> getCertByResponderID(RespID responderId, X509CertificateHolder[]certs) {
        X500Name subject = responderId.toASN1Primitive().getName();
        List<Certificate> certsList = new ArrayList<Certificate>();
        List<X509CertificateHolder> certHoldersList;
        if (subject != null) {
            certHoldersList = Arrays.stream(certs).filter(cert -> cert.getSubject().equals(subject)).collect(Collectors.toList());
        } else {
            byte []subjKeyId = responderId.toASN1Primitive().getKeyHash();
            certHoldersList = Arrays.stream(certs).filter(certHolder -> Arrays.equals(getSubjKeyId(certHolder.toASN1Structure()), subjKeyId)).collect(Collectors.toList());
        }

        certHoldersList.forEach(cert -> certsList.add(cert.toASN1Structure()));
        return certsList;
    }

    public static SignerIdentifier getSignerIdentifierWithSubjKeyId(Certificate x509Cert) {
        SubjectKeyIdentifier subjectKeyIdentifier =  getSKID(x509Cert);

        return new SignerIdentifier((ASN1OctetString)subjectKeyIdentifier.toASN1Primitive());
    }

    public static SignerIdentifier getSignerIdentifierWithIssuerAndSerial(Certificate x509Cert) {
        IssuerAndSerialNumber id = getIssuerAndSerialNumber(x509Cert);

        return new SignerIdentifier(id);
    }

    public static IssuerAndSerialNumber getIssuerAndSerialNumber(Certificate cert) {
        return new IssuerAndSerialNumber(cert);
    }
}
