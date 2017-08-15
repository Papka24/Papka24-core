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

import org.bouncycastle.asn1.cms.SignerIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.cert.ocsp.RespID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.Main;
import ua.privatbank.cryptonite.CryptoniteException;
import ua.privatbank.cryptonite.CryptoniteX;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CertificateCache {
    public static final Logger log = LoggerFactory.getLogger("CertificateCache");
    private static String certificateCacheDir = "";
    private static CertificateCache instance;
    private ConcurrentHashMap<String, Certificate> map;
    private ConcurrentHashMap<SignerIdentifier, Certificate> siMap;
    private ConcurrentHashMap<String, String> subjectAuthorityKeyIdMap;

    private CertificateCache() {

    }

    public static CertificateCache getInstance() {
        if (instance == null) {
            instance = new CertificateCache();
            instance.map = new ConcurrentHashMap<>();
            instance.siMap = new ConcurrentHashMap<>();
            instance.subjectAuthorityKeyIdMap = new ConcurrentHashMap<>();
        } else {
            return instance;
        }
        try {
            if (Main.certificateCacheDir!=null){
                certificateCacheDir = Main.certificateCacheDir;
            }

            Files.walk(Paths.get(certificateCacheDir))
                    .filter(Files::isRegularFile)
                    .forEach(filePath -> {
                        try {
                            Certificate cert = Certificate.getInstance(Files.readAllBytes(filePath));
                            addCert(cert);
                        } catch (IOException e) {
                            log.error("Can't read certificate from file {}", filePath, e);
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return instance;
    }

    private static void addCert(Certificate cert) {
        byte[] subjKeyId = CertificateUtils.getSubjKeyId(cert);
        if (subjKeyId == null){
            log.error("Can't get subject key id from certificate with serial number {}", cert.getSerialNumber());
        } else {
            instance.map.put(Base64.getUrlEncoder().withoutPadding().encodeToString(subjKeyId), cert);
        }

        SignerIdentifier si1 = CertificateUtils.getSignerIdentifierWithSubjKeyId(cert);
        if (si1 == null){
            log.error("Can't get SignerIdentifier from certificate with serial number {}", cert.getSerialNumber());
        } else {
            instance.siMap.put(si1, cert);
        }

        SignerIdentifier si2 = CertificateUtils.getSignerIdentifierWithIssuerAndSerial(cert);
        instance.siMap.put(si2, cert);

        byte[] authKeyId;
        authKeyId = CertificateUtils.getAuthKeyId(cert);
        if (authKeyId == null){
            log.error("Can't get authority key id from certificate with serial number {}", cert.getSerialNumber());
        } else {
            instance.subjectAuthorityKeyIdMap.put(Base64.getUrlEncoder().withoutPadding().encodeToString(subjKeyId), Base64.getUrlEncoder().withoutPadding().encodeToString(authKeyId));
        }
    }

    public static void saveCert(Certificate cert) {
        addCert(cert);
        try {
            byte[] subjKeyId = CertificateUtils.getSubjKeyId(cert);
            byte[] certBytes = cert.getEncoded();

            if (Main.certificateCacheDir != null){
                certificateCacheDir = Main.certificateCacheDir;
            }

            Path certFile = Paths.get(certificateCacheDir, Base64.getUrlEncoder().withoutPadding().encodeToString(subjKeyId));
            if (Files.notExists(certFile)){
                try {
                    Files.write(certFile, certBytes);
                } catch (IOException e) {
                    log.error("Can't write certificate to file {}", certFile, e);
                }
            }

        } catch (IOException e) {
            log.error("Can't write certificate to file.", e);
        }

    }

    List<Certificate> getCertByResponderID(RespID responderId) {
        X500Name ocspSubject = responderId.toASN1Primitive().getName();
        if (ocspSubject != null) {
            return getCertByName(ocspSubject);
        } else {
            byte []subjKeyId = responderId.toASN1Primitive().getKeyHash();
            Certificate cert = getCertBySubjKeyId(subjKeyId);
            if (cert != null) {
                ArrayList<Certificate> certs = new ArrayList<>();
                certs.add(cert);
                return certs;
            }
        }

        return null;
    }

    private List<Certificate> getCertByName(X500Name name) {
        List<Certificate> certList = new ArrayList<Certificate>();
        for (Certificate cert: map.values()) {
            if (cert.getSubject().equals(name)) {
                certList.add(cert);
            }
        }
        return certList;
    }

    public Certificate getCertBySubjKeyId(byte[] subjKeyId) {
        return map.get(Base64.getUrlEncoder().withoutPadding().encodeToString(subjKeyId));
    }

    public Certificate getOcspCertByRoot(Certificate rootCert) throws IOException {
        byte[] subjKeyIdRootCert = CertificateUtils.getSubjKeyId(rootCert);
        return getOcspCertByRoot(subjKeyIdRootCert);
    }

    private Certificate getOcspCertByRoot(byte[] subjKeyIdRootCert) {
        Set<String> childrenSubjectKeyIdSet = subjectAuthorityKeyIdMap.entrySet().stream()
                .filter(entry -> Objects.equals(entry.getValue(), Base64.getUrlEncoder().withoutPadding().encodeToString(subjKeyIdRootCert)))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        for (String subjectKeyIdB64 : childrenSubjectKeyIdSet) {
            Certificate cert = map.get(subjectKeyIdB64);
            byte []certEncoded = null;
            try {
                certEncoded = cert.getEncoded();
            } catch (IOException e) {
                continue;
            }

            try {
                if (CryptoniteX.certificateIsOcspExtKeyUsage(certEncoded)) {
                    Calendar cal = Calendar.getInstance();
                    cal.set(14, 0);
                    Date now = cal.getTime();

                    if (now.compareTo(cert.getStartDate().getDate()) >= 0
                            && now.compareTo(cert.getEndDate().getDate()) < 0) {
                        return cert;
                    }
                }
            } catch (CryptoniteException ignored) {
            }
        }

        return null;
    }

    public List<Certificate> getAllOcspCerts() {
        return map.entrySet().stream().filter(entry -> {
            try {
                return CryptoniteX.certificateIsOcspExtKeyUsage(entry.getValue().getEncoded());
            } catch (CryptoniteException e) {
                return false;
            } catch (IOException e) {
                return false;
            }
        }).map(Map.Entry::getValue).collect(Collectors.toCollection(ArrayList::new));
    }

//    public List<Certificate> getAllCACerts() {
//        return map.entrySet().stream().filter(entry ->
//                entry.getValue().getKeyUsage() != null
//                        && entry.getValue().getKeyUsage().length > UsageBits.KEY_CERT_SIGN.getIndex()
//                        && entry.getValue().getKeyUsage()[UsageBits.KEY_CERT_SIGN.getIndex()]).
//                map(Map.Entry::getValue).collect(Collectors.toCollection(ArrayList::new));
//    }

    Certificate getCertBySignerIdentifier(SignerIdentifier signerIdentifier) {
        return siMap.get(signerIdentifier);
    }
}
