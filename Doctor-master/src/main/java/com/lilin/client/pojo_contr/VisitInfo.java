package com.lilin.client.pojo_contr;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.math.BigInteger;

/**
 * 病人信息的实体类
 */
@Data
public class VisitInfo {
    private String department;
    private String medication;
    private String conditionDescription;
    private String cost;
    private BigInteger visitTime;
    private String patientName;
    private String patientIdNumber;
    private String patientIdHashCode;
    private BigInteger age;
    private String doctorName;
    private String doctorIdNumber;
    private String doctorIdHashCode;
    private String dPk;
    private String signature;
    private String gender;
    @JSONField(serialize =false)
    private String time;
}
