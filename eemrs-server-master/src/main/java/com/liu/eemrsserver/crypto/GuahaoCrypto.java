package com.liu.eemrsserver.crypto;

import com.liu.eemrsserver.config.SMServerKey;
import com.liu.eemrsserver.domain.GuahaoInfo;
import com.liu.eemrsserver.domain.PatientInfo;
import com.liu.eemrsserver.mapper.GuahaoMapper;
import com.liu.eemrsserver.domain.Waiting;
import com.liu.eemrsserver.utils.NewPair;
import com.liu.eemrsserver.utils.crypto.JavaBeanEnc;
import com.liu.eemrsserver.utils.crypto.SM3;
import com.liu.eemrsserver.utils.crypto.sm4.SM4_String;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author L
 * @date 2019-10-06 21:22
 * @desc 挂号操作的加解密中间层
 **/
@Component("guahaoCrypto")
@Scope("prototype")
public class GuahaoCrypto {
    @Autowired
    private GuahaoMapper guahaoMapper;
    @Autowired private SMServerKey smServerKey;
    public boolean add(GuahaoInfo guahaoInfo) {
        guahaoInfo.setHashCode(//设置哈希值
                SM3.hash(guahaoInfo.getIdNumber())
        );
        GuahaoInfo insert = JavaBeanEnc.encGuahaoInfo(guahaoInfo, smServerKey.getSm4Key());
        boolean bool = guahaoMapper.insertInfo(insert);

        return bool;
    }

    public List<Waiting> query(NewPair newPair) {
        String enDept = SM4_String.encWithoutIV(newPair.getDepartment(), smServerKey.getSm4Key());
        String hash = newPair.getIdNumber()==null?null:SM3.hash(newPair.getIdNumber());
        List<Waiting> enUsers = guahaoMapper.queryByDept(enDept,hash);
        List<Waiting> collect = enUsers.stream()
                .map(u -> {
                    return JavaBeanEnc.decUserInfo(u, smServerKey.getSm4Key());
                }).collect(Collectors.toList());
        return collect;
    }

    public PatientInfo getPatientInfo(String idNumber) {
        String hashCode =SM3.hash(idNumber);
        PatientInfo p = guahaoMapper.getPatientByHashCode(hashCode);
        if (p!=null){
            PatientInfo out = JavaBeanEnc.decPatientInfo(p,smServerKey.getSm4Key());
            return out;
        }else {
            return new PatientInfo() ;
        }
    }

    public PatientInfo sendInfo(String id) {
        return getPatientInfo(id);
    }

    public boolean delectGuahaoByHash(String idNumber) {
        String hash = SM3.hash(idNumber);
        return guahaoMapper.deleteGuaHaoByHash(hash);
    }
}
