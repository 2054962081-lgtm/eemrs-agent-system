package com.liu.eemrsserver.medicalrecord;

import com.liu.eemrsserver.common.BadRequestException;
import com.liu.eemrsserver.common.RequestValidator;
import com.liu.eemrsserver.domain.Waiting;
import com.liu.eemrsserver.medicalrecord.dto.MedicalRecordRequest;
import com.liu.eemrsserver.medicalrecord.dto.MedicalRecordSignatureResponse;
import com.liu.eemrsserver.security.ForbiddenException;
import com.liu.eemrsserver.security.Role;
import com.liu.eemrsserver.security.UserPrincipal;
import com.liu.eemrsserver.service.GuahaoService;
import com.liu.eemrsserver.utils.crypto.OperateKey;
import com.liu.eemrsserver.utils.crypto.SM2;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Service
public class MedicalRecordSignatureService {
    private static final Path DOCTOR_PUBLIC_KEY = Paths.get("..", "Doctor-master", "SM2KeyPair", "ec.x509.pub.der");
    private static final Path DOCTOR_PRIVATE_KEY = Paths.get("..", "Doctor-master", "SM2KeyPair", "ec.pkcs8.pri.der");

    @Autowired
    private GuahaoService guahaoService;

    public MedicalRecordSignatureResponse sign(MedicalRecordRequest request, UserPrincipal currentUser) {
        validateSignRequest(request, currentUser);
        String originalText = MedicalRecordSignatureBuilder.build(request, currentUser.getIdNumber());
        byte[] publicKey = readKey(DOCTOR_PUBLIC_KEY, "doctor public key");
        byte[] privateKey = readKey(DOCTOR_PRIVATE_KEY, "doctor private key");
        BCECPrivateKey sm2PrivateKey = OperateKey.toSM2PrivateKey(privateKey);
        String signature = SM2.sign(originalText, sm2PrivateKey);
        if (signature == null || signature.trim().isEmpty()) {
            throw new BadRequestException("签名生成失败");
        }
        return new MedicalRecordSignatureResponse(Base64.getEncoder().encodeToString(publicKey), signature);
    }

    private void validateSignRequest(MedicalRecordRequest request, UserPrincipal currentUser) {
        RequestValidator.notNull(request, "request");
        RequestValidator.notNull(currentUser, "currentUser");
        if (currentUser.getRole() != Role.DOCTOR) {
            throw new ForbiddenException("Only doctors can sign medical records");
        }
        RequestValidator.notBlank(currentUser.getDepartment(), "doctor department");
        RequestValidator.notBlank(request.getPatientIdNumber(), "patientIdNumber");
        RequestValidator.notBlank(request.getDepartment(), "department");
        RequestValidator.notBlank(request.getConditionDescription(), "conditionDescription");
        if (!currentUser.getDepartment().trim().equals(request.getDepartment().trim())) {
            throw new BadRequestException("病历科室与当前医生科室不一致");
        }
        Waiting waiting = guahaoService.findWaitingAppointment(
                currentUser.getDepartment(),
                currentUser.getIdNumber(),
                request.getPatientIdNumber().trim()
        );
        if (waiting == null || waiting.getIdNumber() == null) {
            throw new BadRequestException("医生尚未接诊该患者，不能生成病历签名");
        }
    }

    private byte[] readKey(Path path, String keyName) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BadRequestException(keyName + " not found");
        }
    }
}
