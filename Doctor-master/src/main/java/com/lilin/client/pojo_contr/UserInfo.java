package com.lilin.client.pojo_contr;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author L
 * @date 2019-10-07 16:57
 * @desc 用于一个科室挂号的病人信息
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserInfo {
    private String idNumber;
    private String userName;
}
