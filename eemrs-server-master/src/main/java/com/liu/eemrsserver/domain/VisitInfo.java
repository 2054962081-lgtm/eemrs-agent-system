package com.liu.eemrsserver.domain;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.Date;

/**
 * @author L
 * @date 2019-10-06 20:17
 * @desc 用于接收数据库中病人就诊信息，和给用户返回的就诊信息类
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VisitInfo {
    private String department;
    private String medication;
    private String conditionDescription;
    private String cost;//可以为空
    private BigInteger visitTime;
    private String patientName;
    private String patientIdNumber;
    private String patientIdHashCode;
    private BigInteger age;//可以为空
    private String doctorName;
    private String doctorIdNumber;
    private String doctorIdHashCode;
    private String dPk;
    private String signature;
    private String gender;//可以为空
}
