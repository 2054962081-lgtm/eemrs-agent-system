package com.lilin.client.pojo_contr;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author lilin
 * @date 2019/10/14  -  9:17 下午
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PasswordModify {
    private String type;
    private String idNumber;
    private String oldPassword;
    private String newPassword;
}
