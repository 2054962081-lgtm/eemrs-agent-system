package com.liu.eemrsserver.domain;

import lombok.Data;

/**
 * @author L
 * @date 2019-10-15 17:42
 * @desc
 **/
@Data
public class DoctorInfo {
    private String idNumber;
    private String userName;
    private String idHashCode;
    private String gender;
    private String department;
}
