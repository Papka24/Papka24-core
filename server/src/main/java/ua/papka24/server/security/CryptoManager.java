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

import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.crypto.digests.GOST3411Digest;
import ua.papka24.server.Main;
import ua.papka24.server.db.dto.*;
import ua.privatbank.cryptonite.CryptoniteException;
import ua.privatbank.cryptonite.CryptoniteX;
import ua.privatbank.cryptonite.helper.CertificateInfo;
import ua.privatbank.cryptonite.helper.SignInfo;
import ua.privatbank.cryptonite.helper.SignStatus;
import ua.privatbank.cryptonite.helper.SupportedCommonName;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Реализация криптографических интерфейсов для PKIX.
 */
public class CryptoManager {

    @Deprecated
    public static void saveCert(X509Certificate cert) {}

    public static ArrayList<String> getCmsSignsWithoutData(byte[] pdf) {

        ArrayList<String> cmsSignsWithoutData = new ArrayList<>();

        try {
//XXX:
            /* data without cms container */
//            byte[] pdfWithoutSigns = CryptoniteX.cmsGetData(pdf);
//
//            if (pdfWithoutSigns != null) {
//                byte []hash = CryptoniteX.hashData(pdfWithoutSigns);
//                String newName = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
//
//                try {
//                    fos = new FileOutputStream(uploadedFileLocation);
//                    fos.write(pdfWithoutSigns, 0, pdfWithoutSigns.length);
//                    fos.flush();
//                } finally {
//                    if (fos != null) fos.close();
//                }
//            }

            byte[] CMSData = CryptoniteX.cmsTrimData(pdf);
            List<byte[]> cmsSignsWithoutDataBytes = CryptoniteX.cmsSplit(CMSData);
            for (byte[] cmsSignWithoutDataBytes : cmsSignsWithoutDataBytes) {
                cmsSignsWithoutData.add(Base64.getEncoder().encodeToString(cmsSignWithoutDataBytes));
            }
        } catch (Exception ignore) {
        }

        return cmsSignsWithoutData;
    }

    private static Map<String, byte[]> parseTLV(byte[] tlv, int offset) throws IOException {
        HashMap<String, byte[]> ans = new HashMap<>();

        while (offset < tlv.length) {
            int lenOff;
            for (lenOff = offset; lenOff < tlv.length && tlv[lenOff] != 0; lenOff++) ;
            lenOff++;

            if (lenOff + 4 > tlv.length) {
                throw new IOException("Invalid tlv");
            }

            String tag = new String(tlv, offset, lenOff - offset - 1);

            int valueLen = ((tlv[lenOff + 3] & 0xff) << 24) | ((tlv[lenOff + 2] & 0xff) << 16) | ((tlv[lenOff + 1] & 0xff) << 8) | (tlv[lenOff] & 0xff);
            if (lenOff + 4 + valueLen > tlv.length) {
                throw new IOException("Invalid tlv");
            }

            byte[] value = new byte[valueLen];
            System.arraycopy(tlv, lenOff + 4, value, 0, valueLen);

            ans.put(tag, value);

            offset = lenOff + 4 + valueLen;
            if (offset > tlv.length) {
                throw new IOException("Invalid tlv");
            }
        }
        return ans;
    }

    public static byte[] getLastCmsFromMedoc(byte[] origPdf) {
        if (!(new String(origPdf, 0, "UA1_SIGN".length()).equals("UA1_SIGN"))) {
            return null;
        }

        byte[] tlvData = origPdf;
        byte[] cmsSign = null;

        while (true) {
            try {
                Map<String, byte[]> tlvs = parseTLV(tlvData, 0);
                cmsSign = tlvs.get("UA1_SIGN");
                if (cmsSign == null) {
                    break;
                }

                tlvData = CryptoniteX.cmsGetData(cmsSign);

            } catch (IOException e) {
                //this is not medoc format
                break;
            } catch (Exception e) {
                //invalid cmsSign in UA1_SIGN
                return null;
            }
        }
        return cmsSign;
    }

    //TODO:
    public static String getEdrpou(X509Certificate cert) {
//        CryptoniteX.
//        byte[] extValue = cert.getExtensionValue("2.5.29.9");

        return null;
    }

    //TODO:
    public static String getInn(X509Certificate cert) {
        return null;
    }

    //TEST Only
    public static X509Certificate getCertBySubjKeyId(byte[] subjKeyId) {
        return null;
    }

    @Deprecated
    public static X509Certificate getOcspCertByRoot(X509Certificate rootCert) {
        return null;
    }

    //TEST Only
    public static List<X509Certificate> getAllOcspCerts() {
        return null;
    }

    @Deprecated
    public static List<X509Certificate> getAllCACerts() {return null;}

    public static OCSPStatusInfoDTO getOcspStatus(X509Certificate userCert) throws CertificateEncodingException {

        Certificate cert = Certificate.getInstance(userCert.getEncoded());
        return OCSPService.getStatus(cert);
    }

    //TEST Only
    public static byte[] getAuthKeyId(X509Certificate cert) {return null;}

    @Deprecated
    public static byte[] getSubjKeyId(X509Certificate cert) {return null;}

    @Deprecated
    public static boolean isCertStamp(X509Certificate cert) {return false;}

    @Deprecated
    public static List<String> getCertUsage(X509Certificate cert) {return null;}

    @Deprecated
    public static String getOcspUrl(X509Certificate cert) {return null;}

    @Deprecated
    public static X509Certificate getValidOcspCert(List<X509Certificate> certs) {return null;}

    //TEST Only
    public static List<byte[]> splitSignedData(byte [] cms) {
        try {
            return CryptoniteX.cmsSplit(cms);
        } catch (CryptoniteException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static byte[] joinSignedData(byte[] data, String[] cmsList) throws IOException {

        ArrayList<byte []> signs = new ArrayList<>(cmsList.length);
        Base64.Decoder d =Base64.getUrlDecoder();
        byte[] cms = null;
        for(String b64 : cmsList){
            byte [] sign = null;
            try {
                sign = d.decode(b64);
            } catch (IllegalArgumentException e){
                sign = Base64.getDecoder().decode(b64);
            }

            if (cms == null) {
                cms = sign.clone();
            } else {
                try {
                    cms = CryptoniteX.cmsJoin(cms, sign);
                } catch (CryptoniteException e) {
                    throw new IOException(e.getMessage());
                }
            }
        }

        return cms;
    }

    @Deprecated
    public static byte[] joinSignedData(byte[] data, List<byte []> cmsList) throws IOException {return null;}

    //TEST Only
    public static X509Certificate getX509Certificate(byte [] cert) {
        return null;
    }

    //TEST Only
    public static CertificateInfoDTO getCertificateInfo(X509Certificate cert) {
        return null;
    }

    private static byte[] swap(byte[] from) {
        if (from == null) {
            return null;
        }

        byte[] to = new byte[from.length];

        for(int i = 0; i < from.length; ++i) {
            to[to.length - i - 1] = from[i];
        }

        return to;
    }

    private enum TspStatus {
        VALID("valid"), NO_CERT_FOR_VERIFY("noCertForVerify"), INVALID_DATA("invalidData"), INVALID("invalid");
        private String name;
        TspStatus(String stringVal) {
            name=stringVal;
        }
        @Override
        public String toString() {
            return name;
        }
    }

    private static NameDTO getName(HashMap<SupportedCommonName, String> name) {
        HashMap<String, String> nameDTO = new HashMap<String, String>();
        nameDTO.put("C", name.get(SupportedCommonName.COUNTRY_NAME));
        nameDTO.put("SERIALNUMBER", name.get(SupportedCommonName.SERIAL_NUMBER));
        nameDTO.put("KI", name.get(SupportedCommonName.KNOWLDGE_INFORMATION));
        nameDTO.put("CN", name.get(SupportedCommonName.COMMON_NAME));
        nameDTO.put("SURNAME", name.get(SupportedCommonName.SURNAME));
        nameDTO.put("L", name.get(SupportedCommonName.LOCALITY_NAME));
        nameDTO.put("ST", name.get(SupportedCommonName.STATE_OR_PROVINCE_NAME));
        nameDTO.put("STR", name.get(SupportedCommonName.STREET_ADDRESS));
        nameDTO.put("O", name.get(SupportedCommonName.ORGANIZATION_NAME));
        nameDTO.put("OU", name.get(SupportedCommonName.ORGANIZATIONAL_UNIT_NAME));
        nameDTO.put("T", name.get(SupportedCommonName.TELEPHONE_NUMBER));
        nameDTO.put("DE", name.get(SupportedCommonName.DESCRIPTION));
        nameDTO.put("BC", name.get(SupportedCommonName.BUSINESS_CATEGORY));
        nameDTO.put("PC", name.get(SupportedCommonName.POSTAL_CODE));
        nameDTO.put("PB", name.get(SupportedCommonName.POST_OFFICE_BOX));
        nameDTO.put("PDON", name.get(SupportedCommonName.PHYSICAL_DELIVERY_OFFICE_NAME));
        nameDTO.put("GN", name.get(SupportedCommonName.GIVEN_NAME));
        nameDTO.put("E", name.get(SupportedCommonName.EMAIL));

        return new NameDTO(nameDTO);
    }

    private static List<String> getCertUsage(CertificateInfo certificateInfo) {
        ArrayList<String> keyUsageList = new ArrayList<>();
        byte[] encoded = certificateInfo.getEncoded();

        CertificateFactory certFactory = null;
        try {
            certFactory = CertificateFactory.getInstance("X.509");
            InputStream in = new ByteArrayInputStream(encoded);
            X509Certificate cert = (X509Certificate)certFactory.generateCertificate(in);
            boolean[] keyUsage = cert.getKeyUsage();

            if (keyUsage.length >= 1 && keyUsage[0]) {
                keyUsageList.add("digitalSignature");
            }

            if (keyUsage.length >= 2 && keyUsage[1]) {
                keyUsageList.add("nonRepudiation");
            }

            if (keyUsage.length >= 3 && keyUsage[2]) {
                keyUsageList.add("keyEncipherment");
            }

            if (keyUsage.length >= 4 && keyUsage[3]) {
                keyUsageList.add("dataEncipherment");
            }

            if (keyUsage.length >= 5 && keyUsage[4]) {
                keyUsageList.add("keyAgreement");
            }

            if (keyUsage.length >= 6 && keyUsage[5]) {
                keyUsageList.add("keyCertSign");
            }

            if (keyUsage.length >= 7 && keyUsage[6]) {
                keyUsageList.add("crlSign");
            }

            if (keyUsage.length >= 8 && keyUsage[7]) {
                keyUsageList.add("encipherOnly");
            }

            if (keyUsage.length >= 9 && keyUsage[8]) {
                keyUsageList.add("decipherOnly");
            }
        } catch (CertificateException e) {
            e.printStackTrace();
        }

        return keyUsageList;
    }

    private static CertificateInfoDTO getCertificateInfoDTO(CertificateInfo certificateInfo) {
        CertificateInfoDTO certInfo = new CertificateInfoDTO();

        //TODO: boolean isStamp = certificateInfo.getIsStamp();
        boolean isStamp = false;
        certInfo.isStamp = isStamp;
        certInfo.serialNumber = DatatypeConverter.printHexBinary(certificateInfo.getSerialNumber());
        certInfo.keyUsage = getCertUsage(certificateInfo);
        certInfo.notValidAfter = certificateInfo.getNotValidAfter().getTime() / 1000;
        certInfo.notValidBefore = certificateInfo.getNotValidBefore().getTime() / 1000;
        certInfo.subject = getName(certificateInfo.getSubject());
        certInfo.subject.setEDRPOU(certificateInfo.getEgrpou());
        certInfo.subject.setINN(certificateInfo.getInn());
        //TODO:
//        String userCode = certificateInfo.getUserCode();
        String userCode = null;
        certInfo.subject.setUSERCODE(userCode);
        certInfo.issuer = getName(certificateInfo.getIssuer());
        certInfo.publicKey = Base64.getEncoder().encodeToString(certificateInfo.getPublicKey());

        return certInfo;
    }

    public static List<VerifyInfoDTO> verify(byte[] cms) {

        List<VerifyInfoDTO> viList = new ArrayList<>();

        try {
            List<SignInfo> signInfos = CryptoniteX.cmsVerify(cms);
            for (int i = 0; i < signInfos.size(); i++) {
                VerifyInfoDTO vi = new VerifyInfoDTO();
                SignInfo si = signInfos.get(i);
                if (si == null) {
                    break;
                }

                vi.hash = Base64.getEncoder().encodeToString(swap(si.getHash()));

                if (si.getTspStatus() == ua.privatbank.cryptonite.jnr.pkix.TspStatus.TSP_NONE) {
                    vi.tspStatus = null;
                    vi.tspValue = null;
                    vi.tspSignerIdentifier = null;
                } else {
                    if (si.getTspStatus() == ua.privatbank.cryptonite.jnr.pkix.TspStatus.TSP_VALID) {
                        vi.tspStatus = TspStatus.VALID.toString();
                    } else if (si.getTspStatus() == ua.privatbank.cryptonite.jnr.pkix.TspStatus.TSP_NO_CERT_FOR_VERIFY) {
                        vi.tspStatus = TspStatus.NO_CERT_FOR_VERIFY.toString();
                    } else if (si.getTspStatus() == ua.privatbank.cryptonite.jnr.pkix.TspStatus.TSP_INVALID_DATA) {
                        vi.tspStatus = TspStatus.INVALID_DATA.toString();
                    } else if (si.getTspStatus() == ua.privatbank.cryptonite.jnr.pkix.TspStatus.TSP_INVALID) {
                        vi.tspStatus = TspStatus.INVALID.toString();
                    } else {
                        vi.tspStatus = null;
                    }

                    vi.tspValue = si.getTsp().getTime() / 1000;
                    vi.tspSignerIdentifier = Base64.getEncoder().encodeToString(si.getTspSid());
                }

                if (si.getSigningTime() != null) {
                    vi.signingTimeValue = si.getSigningTime().getTime() / 1000;
                } else {
                    vi.signingTimeValue = null;
                }

                vi.signerIdentifier = Base64.getEncoder().encodeToString(si.getSignerId());

                byte[] signCertificate = si.getCertificateInfo().getEncoded();
                if (signCertificate != null) {
                    Certificate certBC = Certificate.getInstance(signCertificate);

                    OCSPStatusInfoDTO ocsp  = OCSPService.getStatus(certBC);
                    if (ocsp.status != null){
                        vi.ocsp = ocsp;
                    } else {
                        vi.ocsp = null;
                    }
                    vi.cert = getCertificateInfoDTO(si.getCertificateInfo());
                    vi.signCertificate = Base64.getEncoder().encodeToString(signCertificate);

                    if (si.getTspStatus() != ua.privatbank.cryptonite.jnr.pkix.TspStatus.TSP_INVALID_DATA) {
                        if (si.getSignStatus() == SignStatus.VALID) {
                            vi.verifyResult = SignInfoDTO.VERIFY_RESULT_VALID;
                        } else if (si.getSignStatus() == SignStatus.VALID_WITHOUT_DATA) {
                            vi.verifyResult = SignInfoDTO.VERIFY_RESULT_VALID_WITHOUT_DATA;
                        } else if (si.getSignStatus() == SignStatus.INVALID_BY_TSP) {
                            vi.verifyResult = SignInfoDTO.VERIFY_RESULT_INVALID_BY_TSP;
                        } else {
                            vi.verifyResult = SignInfoDTO.VERIFY_RESULT_INVALID;
                        }
                    } else {
                        vi.verifyResult = SignInfoDTO.VERIFY_RESULT_INVALID_BY_TSP;
                    }
                } else {
                /* Сертификат подписчика не был найден. */
                    vi.ocsp = null;
                    vi.verifyResult = SignInfoDTO.VERIFY_RESULT_INVALID;
                }
                viList.add(vi);

            }

        } catch (CryptoniteException e) {
            e.printStackTrace();
        }

        return viList;
    }

    public static HashMap<String,X509Certificate> getUniqueCms(List<String> oldCmsB64List, String newCmsB64, byte[] digest)
            throws Exception {

        System.out.printf("getUniqueCms() newCmsB64: %s\n", newCmsB64);

        HashMap<String, X509Certificate> newCmsList = new HashMap<>();
        List<String> allSidList = new ArrayList<>();

        for (String oldCmsB64 : oldCmsB64List) {
            System.out.printf("getUniqueCms() oldCmsB64: %s\n", oldCmsB64);
            List<SignInfo> signInfos = CryptoniteX.cmsVerify(Base64.getDecoder().decode(oldCmsB64));
            for (SignInfo signInfo : signInfos) {
                allSidList.add(Base64.getEncoder().encodeToString(signInfo.getSignerId()));
            }
        }

        List<byte[]> newCmsBytesList = CryptoniteX.cmsSplit(Base64.getDecoder().decode(newCmsB64));
        for (byte[] newCmsBytes : newCmsBytesList) {
            List<SignInfo> signInfos = CryptoniteX.cmsVerify(newCmsBytes);

            if (digest != null && digest.length > 0 && !MessageDigest.isEqual(digest, swap(signInfos.get(0).getHash()))) {
                continue;
            }

            String newSid = Base64.getEncoder().encodeToString(signInfos.get(0).getSignerId());
            if (allSidList.stream().filter(sid -> sid.equals(newSid)).count() == 0) {
                /* Подписи таким ключем еще нет. */
                String cmsB64 = Base64.getEncoder().encodeToString(newCmsBytes);
                byte[] certBytes = signInfos.get(0).getCertificateInfo().getEncoded();
                newCmsList.put(cmsB64, certBytesToX509(certBytes));
                allSidList.add(newSid);

                System.out.printf("getUniqueCms() answer cmsB64: %s, cert: %s\n", cmsB64, DatatypeConverter.printHexBinary(certBytes));
            }
        }

        return newCmsList;
    }

    private static X509Certificate certBytesToX509(byte[] cert) throws Exception {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        InputStream in = new ByteArrayInputStream(cert);
        return (X509Certificate)certFactory.generateCertificate(in);
    }

    @Deprecated
    public static Map<String, String> getOcspCertMap() {return null;}

    //TODO:
    public static String getCompanyName(X509Certificate cert) {
        return null;
    }

    private static class GOST3411Impl implements GOST3411 {
        private GOST3411Digest ctx;

        public GOST3411Impl(byte sbox[]) {
            ctx = (sbox != null) ? new GOST3411Digest(sbox) : new GOST3411Digest();
        }

        public void init() {
        }

        public void init(final byte[] sync) {
        }

        public void update(final byte[] buf, int off, final int len) {
            ctx.update(buf, off, len);
        }

        public byte[] doFinal() {
            byte[] out = new byte [ctx.getDigestSize()];
            ctx.doFinal(out, 0);
            out = swap(out);

            return out;
        }

        public void doFinal(final byte[] dst, final int dstOff) {
            byte []digest = new byte [ctx.getDigestSize()];
            ctx.doFinal(digest, 0);
            digest = swap(digest);
            System.arraycopy(digest, 0, dst, dstOff, digest.length);
        }
    }

    public static GOST3411 getGost34311() {
        byte sboxDKE1[] = {
                0xA,0x9,0xD,0x6,0xE,0xB,0x4,0x5,0xF,0x1,0x3,0xC,0x7,0x0,0x8,0x2,
                0x8,0x0,0xC,0x4,0x9,0x6,0x7,0xB,0x2,0x3,0x1,0xF,0x5,0xE,0xA,0xD,
                0xF,0x6,0x5,0x8,0xE,0xB,0xA,0x4,0xC,0x0,0x3,0x7,0x2,0x9,0x1,0xD,
                0x3,0x8,0xD,0x9,0x6,0xB,0xF,0x0,0x2,0x5,0xC,0xA,0x4,0xE,0x1,0x7,
                0xF,0x8,0xE,0x9,0x7,0x2,0x0,0xD,0xC,0x6,0x1,0x5,0xB,0x4,0x3,0xA,
                0x2,0x8,0x9,0x7,0x5,0xF,0x0,0xB,0xC,0x1,0xD,0xE,0xA,0x3,0x6,0x4,
                0x3,0x8,0xB,0x5,0x6,0x4,0xE,0xA,0x2,0xC,0x1,0x7,0x9,0xF,0xD,0x0,
                0x1,0x2,0x3,0xE,0x6,0xD,0xB,0x8,0xF,0xA,0xC,0x5,0x7,0x9,0x0,0x4
        };

        return new GOST3411Impl(sboxDKE1);
    }
}
