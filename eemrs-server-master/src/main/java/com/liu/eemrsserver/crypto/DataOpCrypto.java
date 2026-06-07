package com.liu.eemrsserver.crypto;

import com.liu.eemrsserver.config.SMServerKey;
import com.liu.eemrsserver.common.BadRequestException;
import com.liu.eemrsserver.domain.DoctorInfo;
import com.liu.eemrsserver.domain.VisitInfo;
import com.liu.eemrsserver.jsontrans.QueryConditions;
import com.liu.eemrsserver.mapper.DataOpMappper;
import com.liu.eemrsserver.medicalrecord.MedicalRecordSignatureBuilder;
import com.liu.eemrsserver.utils.crypto.JavaBeanEnc;
import com.liu.eemrsserver.utils.crypto.OperateKey;
import com.liu.eemrsserver.utils.crypto.SM2;
import com.liu.eemrsserver.utils.crypto.SM3;
import com.liu.eemrsserver.utils.crypto.homomorphic.boldyreva.BoldyrevaCipher;
import com.liu.eemrsserver.utils.crypto.sm4.SM4_String;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Scope("prototype")
public class DataOpCrypto {
    @Autowired
    private DataOpMappper dataOpMappper;
    @Autowired
    private SMServerKey smServerKey;
    @Autowired
    private BoldyrevaCipher boldyrevaCipher;

    public boolean insertInto(VisitInfo patientInfo) {
        boolean verify;
        try {
            verify = verifyMedicalRecordSignature(patientInfo);
        } catch (Exception e) {
            throw new BadRequestException("SM2 signature format is invalid");
        }
        if (!verify) {
            throw new BadRequestException("SM2 signature verification failed");
        }
        if (patientInfo.getPatientIdNumber() == null) {
            throw new BadRequestException("patientIdNumber must not be blank");
        }
        ensureVisitTime(patientInfo);
        String hash = SM3.hash(patientInfo.getPatientIdNumber());
        Integer sum = resolvePatientCounter(hash);
        if (sum == null) {
            throw new BadRequestException("Patient counter not found, please register the patient before writing medical record");
        }
        VisitInfo enPI = JavaBeanEnc.encVisitInfo((sum+1)+"",patientInfo, smServerKey.getSm4Key(), boldyrevaCipher, smServerKey.getOpeKey());
        if (enPI.getVisitTime() == null) {
            throw new BadRequestException("visitTime encryption failed");
        }
        boolean bool =dataOpMappper.insert(enPI);
        boolean bool1 = dataOpMappper.updateCounter(hash,SM4_String.encWithIV((sum+1)+"",smServerKey.getSm4Key()));
        if (!bool || !bool1) {
            throw new BadRequestException("Medical record database write failed");
        }
        return true;
    }

    private boolean verifyMedicalRecordSignature(VisitInfo patientInfo) {
        String signatureText = MedicalRecordSignatureBuilder.build(patientInfo);
        String legacyText = patientInfo.getConditionDescription();
        return SM2.verify(signatureText, patientInfo.getSignature(), OperateKey.toSM2PublicKey(Base64.getDecoder().decode(patientInfo.getDPk())))
                || SM2.verify(legacyText, patientInfo.getSignature(), OperateKey.toSM2PublicKey(Base64.getDecoder().decode(patientInfo.getDPk())));
    }

    private void ensureVisitTime(VisitInfo patientInfo) {
        if (patientInfo.getVisitTime() == null) {
            patientInfo.setVisitTime(BigInteger.valueOf(System.currentTimeMillis()));
        }
    }

    public List<VisitInfo> query(QueryConditions queryConditions) {
        QueryConditions qc = JavaBeanEnc.enQueryCondition(queryConditions, smServerKey.getSm4Key(), boldyrevaCipher, smServerKey.getOpeKey());
        if (queryConditions.getPatientIdNumber()!=null){
            String hash = SM3.hash(queryConditions.getPatientIdNumber());
            Integer sum;
            sum = resolvePatientCounter(hash);
            if (sum!=null){
                if (sum <= 0) {
                    return new ArrayList<VisitInfo>();
                }
                List<String> list = new ArrayList<>();
                for (int i=1;i<=sum;i++){
                    list.add(SM3.hash(queryConditions.getPatientIdNumber()+i));
                }
                if (list.isEmpty()) {
                    return new ArrayList<VisitInfo>();
                }
                qc.setPids(list);
            }else {
                return new ArrayList<VisitInfo>();
            }
        }


        List<VisitInfo> patientInfos = dataOpMappper.queryByCondition(qc);

        List<VisitInfo> collect = patientInfos.stream()
                .map(p -> {
                    return JavaBeanEnc.decVisitInfo(p, smServerKey.getSm4Key(), boldyrevaCipher, smServerKey.getOpeKey());
                }).collect(Collectors.toList());
        return collect;
    }

    private Integer resolvePatientCounter(String patientIdHash) {
        List<String> encryptedCounters = dataOpMappper.getCountersByIdHash(patientIdHash);
        if (encryptedCounters == null || encryptedCounters.isEmpty()) {
            return null;
        }
        Integer maxCounter = null;
        for (String encryptedCounter : encryptedCounters) {
            if (encryptedCounter == null) {
                continue;
            }
            try {
                int counter = Integer.parseInt(SM4_String.decWithIV(encryptedCounter, smServerKey.getSm4Key()));
                if (maxCounter == null || counter > maxCounter) {
                    maxCounter = counter;
                }
            } catch (RuntimeException e) {
                // Historical duplicate accounts can contain counters encrypted with an obsolete key.
            }
        }
        return maxCounter;
    }

    public DoctorInfo getDocInfo(String id) {
        String hash = SM3.hash(id);
        DoctorInfo d = dataOpMappper.getDocInfoByHashCode(hash);
        if (d!=null){
            DoctorInfo out = JavaBeanEnc.decDocInfoInfo(d,smServerKey.getSm4Key());
            System.out.println(out);
            return out;
        }else {
            return new DoctorInfo() ;
        }
    }

    public List<DoctorInfo> getDocName(String department) {
        if (department == null || department.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String normalizedDepartment = department.trim();
        String enDept = SM4_String.encWithoutIV(normalizedDepartment, smServerKey.getSm4Key());
        List<DoctorInfo> doctorInfos = dataOpMappper.getDocNameByDepartment(enDept, normalizedDepartment);
        List<DoctorInfo> collect = doctorInfos.stream()
                .map(this::decryptDoctorInfoForDepartmentQuery)
                .collect(Collectors.toList());
        return collect;
    }

    private DoctorInfo decryptDoctorInfoForDepartmentQuery(DoctorInfo doctorInfo) {
        try {
            return JavaBeanEnc.decDocInfoInfo(doctorInfo, smServerKey.getSm4Key());
        } catch (RuntimeException e) {
            DoctorInfo out = new DoctorInfo();
            out.setDepartment(doctorInfo.getDepartment());
            out.setGender(decryptWithIvOrOriginal(doctorInfo.getGender()));
            out.setIdHashCode(null);
            out.setIdNumber(decryptWithIvOrOriginal(doctorInfo.getIdNumber()));
            out.setUserName(decryptWithIvOrOriginal(doctorInfo.getUserName()));
            return out;
        }
    }

    private String decryptWithIvOrOriginal(String value) {
        if (value == null) {
            return null;
        }
        try {
            return SM4_String.decWithIV(value, smServerKey.getSm4Key());
        } catch (RuntimeException e) {
            return value;
        }
    }
}
