package com.liu.eemrsserver.service;

import com.liu.eemrsserver.crypto.GuahaoCrypto;
import com.liu.eemrsserver.domain.GuahaoInfo;
import com.liu.eemrsserver.domain.PatientInfo;
import com.liu.eemrsserver.domain.Waiting;
import com.liu.eemrsserver.utils.NewPair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author L
 * @date 2019-10-06 21:12
 * @desc 挂号相关的操作服务
 **/
@Service
@Scope("prototype")
public class GuahaoService {
    @Autowired private GuahaoCrypto guahaoCrypto;
    public boolean add(GuahaoInfo guahaoInfo) {
        return guahaoCrypto.add(guahaoInfo);
    }
    public List<Waiting> query(NewPair newPair) {
        return guahaoCrypto.query(newPair);
    }

    public PatientInfo getPatientInfo(String idNumber) {
        return guahaoCrypto.getPatientInfo(idNumber) ;
    }

    public Waiting findWaitingAppointment(String department, String doctorIdNumber, String patientIdNumber) {
        return guahaoCrypto.findWaitingAppointment(department, doctorIdNumber, patientIdNumber);
    }

    public PatientInfo sendInfo(String id) {
        return guahaoCrypto.sendInfo(id);
    }

    public boolean delectGuaHao(String idNumber) {
        return guahaoCrypto.delectGuahaoByHash(idNumber);
    }
}
