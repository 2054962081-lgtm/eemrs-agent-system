package com.liu.eemrsserver.domain;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

/**
 * @author L
 * @date 2019-10-14 15:04
 * @desc 完整的病人信息表
 **/
@Data
public class PatientInfo {
    private String userName;
    private String gender;
    private String age;
    private String birthDay;
    private String idNumber;
    @JSONField(serialize =false)
    private String idHashCode;
    private String password;
    private String medicareCard;
    private String nation;
    private String telephone;
    private String address;
    private String mail;

}
