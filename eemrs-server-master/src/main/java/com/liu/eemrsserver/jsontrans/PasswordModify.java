package com.liu.eemrsserver.jsontrans;

import lombok.Data;

/**
 * @author L
 * @date 2019-10-14 17:04
 * @desc 封装修改密码的数据
 **/
@Data
public class PasswordModify {
    private String type;
    private String idNumber;
    private String oldPassword;
    private String newPassword;
}
