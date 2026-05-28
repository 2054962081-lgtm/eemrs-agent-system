package com.liu.eemrsserver.service;

import com.liu.eemrsserver.crypto.DataOpCrypto;
import com.liu.eemrsserver.domain.DoctorInfo;
import com.liu.eemrsserver.domain.VisitInfo;
import com.liu.eemrsserver.jsontrans.QueryConditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Scope("prototype")
public class DataOpService {
    @Autowired
    private DataOpCrypto dataOpCrypto;
    public boolean insertInto(VisitInfo patientInfo) {
        return dataOpCrypto.insertInto(patientInfo);
    }
    public List<VisitInfo> query(QueryConditions queryConditions) {
        return dataOpCrypto.query(queryConditions);
    }

    public DoctorInfo sendDocInfo(String id) {
        return dataOpCrypto.getDocInfo(id);
    }
    public List<DoctorInfo> getDocName(String department){
        return dataOpCrypto.getDocName(department);
    }
}
