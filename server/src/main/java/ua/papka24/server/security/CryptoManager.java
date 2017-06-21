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

import ua.papka24.server.db.dto.CertificateInfoDTO;
import ua.papka24.server.db.dto.OCSPStatusInfoDTO;
import ua.papka24.server.db.dto.VerifyInfoDTO;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Реализация криптографических интерфейсов для PKIX. Реализация временно удалена.
 */
public class CryptoManager {

    public static void saveCert(X509Certificate cert) {}

    public static ArrayList<String> getCmsSignsWithoutData(byte[] pdf) {return null;}

    public static byte[] getLastCmsFromMedoc(byte[] origPdf) {return null;}

    public static String getEdrpou(X509Certificate cert) {return null;}

    public static String getInn(X509Certificate cert) {return null;}

    public static X509Certificate getCertBySubjKeyId(byte[] subjKeyId) {return null;}

    public static X509Certificate getOcspCertByRoot(X509Certificate rootCert) {return null;}

    public static List<X509Certificate> getAllOcspCerts() {return null;}

    public static List<X509Certificate> getAllCACerts() {return null;}

    public static OCSPStatusInfoDTO getOcspStatus(X509Certificate userCert) {return null;}

    public static byte[] getAuthKeyId(X509Certificate cert) {return null;}

    public static byte[] getSubjKeyId(X509Certificate cert) {return null;}

    public static boolean isCertStamp(X509Certificate cert) {return false;}

    public static List<String> getCertUsage(X509Certificate cert) {return null;}

    public static String getOcspUrl(X509Certificate cert) {return null;}

    public static X509Certificate getValidOcspCert(List<X509Certificate> certs) {return null;}

    public static List<byte[]> splitSignedData(byte [] cms) {return null;}

    public static byte[] joinSignedData(byte[] data, String[] cmsList) throws IOException {return null;}

    public static byte[] joinSignedData(byte[] data, List<byte []> cmsList) throws IOException {return null;}

    public static X509Certificate getX509Certificate(byte [] cert) {return null;}

    public static CertificateInfoDTO getCertificateInfo(X509Certificate cert) {return null;}

    public static List<VerifyInfoDTO> verify(byte[] cms) throws Exception {return null;}

    public static HashMap<String,X509Certificate> getUniqueCms(List<String> oldCmsB64List, String newCmsB64, byte[] digest1)
            throws Exception {return null;}

    public static Map<String, String> getOcspCertMap() {return null;}

    public static String getCompanyName(X509Certificate cert) {return null;}

    public static GOST3411 getGost34311() {return null;}
}
