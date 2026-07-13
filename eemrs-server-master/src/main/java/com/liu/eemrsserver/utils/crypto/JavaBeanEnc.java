package com.liu.eemrsserver.utils.crypto;

import com.liu.eemrsserver.domain.DocLog;
import com.liu.eemrsserver.domain.DoctorInfo;
import com.liu.eemrsserver.domain.GuahaoInfo;
import com.liu.eemrsserver.domain.PatLog;
import com.liu.eemrsserver.domain.PatientInfo;
import com.liu.eemrsserver.domain.VisitInfo;
import com.liu.eemrsserver.domain.Waiting;
import com.liu.eemrsserver.jsontrans.QueryConditions;
import com.liu.eemrsserver.jsontrans.UserLog;
import com.liu.eemrsserver.utils.Pair;
import com.liu.eemrsserver.utils.crypto.homomorphic.boldyreva.BoldyrevaCipher;
import com.liu.eemrsserver.utils.crypto.sm4.SM4_String;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

public class JavaBeanEnc {
    public static PatientInfo encVisitInfo(PatientInfo patientInfo, byte[] key) {
        PatientInfo out = new PatientInfo();
        out.setGender(patientInfo.getGender() == null ? null : SM4_String.encWithIV(patientInfo.getGender(), key));
        out.setAge(patientInfo.getAge() == null ? null : SM4_String.encWithIV(patientInfo.getAge(), key));
        out.setBirthDay(patientInfo.getBirthDay() == null ? null : SM4_String.encWithIV(patientInfo.getBirthDay(), key));
        out.setIdHashCode(SM3.hash(patientInfo.getIdNumber()));
        out.setMedicareCard(patientInfo.getMedicareCard() == null ? null : SM4_String.encWithIV(patientInfo.getMedicareCard(), key));
        out.setUserName(patientInfo.getUserName() == null ? null : SM4_String.encWithIV(patientInfo.getUserName(), key));
        out.setIdNumber(patientInfo.getIdNumber());
        out.setAddress(patientInfo.getAddress() == null ? null : SM4_String.encWithIV(patientInfo.getAddress(), key));
        out.setMail(patientInfo.getMail() == null ? null : SM4_String.encWithIV(patientInfo.getMail(), key));
        out.setNation(patientInfo.getNation() == null ? null : SM4_String.encWithIV(patientInfo.getNation(), key));
        out.setTelephone(patientInfo.getTelephone() == null ? null : SM4_String.encWithIV(patientInfo.getTelephone(), key));
        return out;
    }

    public static PatientInfo decPatientInfo(PatientInfo patientInfo, byte[] key) {
        PatientInfo out = new PatientInfo();
        out.setGender(patientInfo.getGender() == null ? null : SM4_String.decWithIV(patientInfo.getGender(), key));
        out.setAge(patientInfo.getAge() == null ? null : SM4_String.decWithIV(patientInfo.getAge(), key));
        out.setBirthDay(patientInfo.getBirthDay() == null ? null : SM4_String.decWithIV(patientInfo.getBirthDay(), key));
        out.setIdHashCode(patientInfo.getIdHashCode());
        out.setMedicareCard(patientInfo.getMedicareCard() == null ? null : SM4_String.decWithIV(patientInfo.getMedicareCard(), key));
        out.setUserName(patientInfo.getUserName() == null ? null : SM4_String.decWithIV(patientInfo.getUserName(), key));
        out.setIdNumber(patientInfo.getIdNumber() == null ? null : SM4_String.decWithIV(patientInfo.getIdNumber(), key));
        out.setAddress(patientInfo.getAddress() == null ? null : SM4_String.decWithIV(patientInfo.getAddress(), key));
        out.setMail(patientInfo.getMail() == null ? null : SM4_String.decWithIV(patientInfo.getMail(), key));
        out.setNation(patientInfo.getNation() == null ? null : SM4_String.decWithIV(patientInfo.getNation(), key));
        out.setTelephone(patientInfo.getTelephone() == null ? null : SM4_String.decWithIV(patientInfo.getTelephone(), key));
        return out;
    }

    public static QueryConditions enQueryCondition(@NotNull QueryConditions queryConditions, @NotNull byte[] SM4Key, @NotNull BoldyrevaCipher boldyrevaCipher, @NotNull byte[] OPEKey) {
        QueryConditions out = new QueryConditions();
        try {
            if (queryConditions.getAgeInterval() != null) {
                Pair<BigInteger, BigInteger> pair = queryConditions.getAgeInterval();
                out.setAgeInterval(new Pair<>(
                        boldyrevaCipher.encrypt(pair.getKey(), OPEKey),
                        boldyrevaCipher.encrypt(pair.getValue(), OPEKey)
                ));
            }
            if (queryConditions.getTimeInterval() != null) {
                Pair<BigInteger, BigInteger> pair = queryConditions.getTimeInterval();
                out.setTimeInterval(new Pair<>(
                        boldyrevaCipher.encrypt(pair.getKey(), OPEKey),
                        boldyrevaCipher.encrypt(pair.getValue(), OPEKey)
                ));
            }
            if (queryConditions.getDoctorIdNumber() != null) {
                out.setDoctorIdNumber(SM3.hash(queryConditions.getDoctorIdNumber()));
            }
            if (queryConditions.getDepartment() != null) {
                out.setDepartment(SM4_String.encWithoutIV(queryConditions.getDepartment(), SM4Key));
            }
            if (queryConditions.getDoctorName() != null) {
                out.setDoctorName(SM4_String.encWithoutIV(queryConditions.getDoctorName(), SM4Key));
            }
            return out;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    public static VisitInfo encVisitInfo(String s, @NotNull VisitInfo visitInfo, @NotNull byte[] SM4Key, @NotNull BoldyrevaCipher boldyrevaCipher, @NotNull byte[] OPEKey) {
        VisitInfo out = new VisitInfo();
        try {
            out.setAge(visitInfo.getAge() == null ? null : boldyrevaCipher.encrypt(visitInfo.getAge(), OPEKey));
            out.setConditionDescription(SM4_String.encWithIV(visitInfo.getConditionDescription(), SM4Key));
            out.setCost(visitInfo.getCost() == null ? null : SM4_String.encWithIV(visitInfo.getCost(), SM4Key));
            out.setDepartment(SM4_String.encWithoutIV(visitInfo.getDepartment(), SM4Key));
            out.setDoctorIdHashCode(SM3.hash(visitInfo.getDoctorIdNumber()));
            out.setDoctorIdNumber(SM4_String.encWithIV(visitInfo.getDoctorIdNumber(), SM4Key));
            out.setDoctorName(SM4_String.encWithoutIV(visitInfo.getDoctorName(), SM4Key));
            out.setDPk(visitInfo.getDPk());
            out.setMedication(visitInfo.getMedication() == null ? null : SM4_String.encWithIV(visitInfo.getMedication(), SM4Key));
            out.setPatientIdHashCode(SM3.hash(visitInfo.getPatientIdNumber() + s));
            out.setPatientIdNumber(SM4_String.encWithIV(visitInfo.getPatientIdNumber(), SM4Key));
            out.setPatientName(SM4_String.encWithIV(visitInfo.getPatientName(), SM4Key));
            out.setSignature(visitInfo.getSignature());
            out.setVisitTime(boldyrevaCipher.encrypt(visitInfo.getVisitTime(), OPEKey));
            out.setGender(visitInfo.getGender() == null ? null : SM4_String.encWithIV(visitInfo.getGender(), SM4Key));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    public static VisitInfo decVisitInfo(@NotNull VisitInfo visitInfo, @NotNull byte[] SM4Key, @NotNull BoldyrevaCipher boldyrevaCipher, @NotNull byte[] OPEKey) {
        VisitInfo out = new VisitInfo();
        try {
            out.setAge(visitInfo.getAge() == null ? null : boldyrevaCipher.decrypted(visitInfo.getAge(), OPEKey));
            out.setConditionDescription(SM4_String.decWithIV(visitInfo.getConditionDescription(), SM4Key));
            out.setCost(visitInfo.getCost() == null ? null : SM4_String.decWithIV(visitInfo.getCost(), SM4Key));
            out.setDepartment(SM4_String.decWithoutIV(visitInfo.getDepartment(), SM4Key));
            out.setDoctorIdHashCode(null);
            out.setDoctorIdNumber(null);
            out.setDoctorName(SM4_String.decWithoutIV(visitInfo.getDoctorName(), SM4Key));
            out.setDPk(visitInfo.getDPk());
            out.setMedication(visitInfo.getMedication() == null ? null : SM4_String.decWithIV(visitInfo.getMedication(), SM4Key));
            out.setPatientIdHashCode(null);
            out.setPatientIdNumber(SM4_String.decWithIV(visitInfo.getPatientIdNumber(), SM4Key));
            out.setPatientName(SM4_String.decWithIV(visitInfo.getPatientName(), SM4Key));
            out.setSignature(visitInfo.getSignature());
            out.setVisitTime(boldyrevaCipher.decrypted(visitInfo.getVisitTime(), OPEKey));
            out.setGender(visitInfo.getGender() == null ? null : SM4_String.decWithIV(visitInfo.getGender(), SM4Key));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    public static UserLog encUserOp(@NotNull UserLog userOP, @NotNull byte[] sm4Key) throws Exception {
        UserLog out = new UserLog();
        out.setType(userOP.getType());
        out.setHashCode(userOP.getHashCode());
        out.setIdNumber(SM4_String.encWithIV(userOP.getIdNumber(), sm4Key));
        out.setPassword(SM4_String.encWithIV(userOP.getPassword(), sm4Key));
        out.setUserName(userOP.getUserName() == null ? null : SM4_String.encWithIV(userOP.getUserName(), sm4Key));
        if (userOP.getDepartment() != null) {
            out.setDepartment(SM4_String.encWithoutIV(userOP.getDepartment(), sm4Key));
        }
        out.setCounter(SM4_String.encWithIV(userOP.getCounter(), sm4Key));
        return out;
    }

    public static PatLog decPat(@NotNull PatLog encUser, @NotNull byte[] sm4Key) {
        String idNumber = SM4_String.decWithIV(encUser.getIdNumber(), sm4Key);
        String userName = encUser.getUserName() == null ? null : SM4_String.decWithIV(encUser.getUserName(), sm4Key);
        String password = SM4_String.decWithIV(encUser.getPassword(), sm4Key);
        return new PatLog(null, idNumber, userName, password, encUser.getIdHashCode());
    }

    public static GuahaoInfo encGuahaoInfo(@NotNull GuahaoInfo guahaoInfo, @NotNull byte[] sm4Key) {
        GuahaoInfo out = new GuahaoInfo();
        out.setHashCode(guahaoInfo.getHashCode());
        out.setIdNumber(SM4_String.encWithIV(guahaoInfo.getIdNumber(), sm4Key));
        out.setUserName(SM4_String.encWithIV(guahaoInfo.getUserName(), sm4Key));
        out.setDepartment(SM4_String.encWithoutIV(guahaoInfo.getDepartment(), sm4Key));
        out.setDoctorIdHashCode(guahaoInfo.getDoctorIdHashCode() == null ? null : SM3.hash(guahaoInfo.getDoctorIdHashCode()));
        return out;
    }

    public static Waiting decUserInfo(@NotNull Waiting userInfo, @NotNull byte[] sm4Key) {
        String idNumber = SM4_String.decWithIV(userInfo.getIdNumber(), sm4Key);
        String userName = SM4_String.decWithIV(userInfo.getUserName(), sm4Key);
        return new Waiting(idNumber, userName);
    }

    public static DoctorInfo decDocInfoInfo(DoctorInfo d, byte[] sm4Key) {
        DoctorInfo doctorInfo = new DoctorInfo();
        doctorInfo.setDepartment(SM4_String.decWithoutIV(d.getDepartment(), sm4Key));
        doctorInfo.setGender(d.getGender() == null ? null : SM4_String.decWithIV(d.getGender(), sm4Key));
        doctorInfo.setIdHashCode(null);
        doctorInfo.setIdNumber(SM4_String.decWithIV(d.getIdNumber(), sm4Key));
        doctorInfo.setUserName(SM4_String.decWithIV(d.getUserName(), sm4Key));
        return doctorInfo;
    }

    public static DoctorInfo encDocInfoInfo(DoctorInfo d, byte[] sm4Key) {
        DoctorInfo doctorInfo = new DoctorInfo();
        doctorInfo.setDepartment(SM4_String.encWithoutIV(d.getDepartment(), sm4Key));
        doctorInfo.setGender(d.getGender() == null ? null : SM4_String.encWithIV(d.getGender(), sm4Key));
        doctorInfo.setIdHashCode(SM3.hash(d.getIdNumber()));
        doctorInfo.setIdNumber(SM4_String.encWithIV(d.getIdNumber(), sm4Key));
        doctorInfo.setUserName(SM4_String.encWithIV(d.getUserName(), sm4Key));
        return doctorInfo;
    }

    public static DocLog decDoc(DocLog docLog, byte[] sm4Key) {
        String idNumber = SM4_String.decWithIV(docLog.getIdNumber(), sm4Key);
        String userName = docLog.getUserName() == null ? null : SM4_String.decWithIV(docLog.getUserName(), sm4Key);
        String password = SM4_String.decWithIV(docLog.getPassword(), sm4Key);
        String dept = docLog.getDepartment() == null ? null : SM4_String.decWithoutIV(docLog.getDepartment(), sm4Key);
        return new DocLog(null, idNumber, userName, dept, password, null);
    }
}
