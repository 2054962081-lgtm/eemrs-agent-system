package com.liu.eemrsserver.jsontrans;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author L
 * @date 2019-09-21 8:26
 * @desc 用于接收用户发送的登录、注册、注销的用户数据
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserLog {
    private String type;//dt pt
    private String idNumber;
    private String userName;
    private String password;
    @JSONField(serialize = false)
    private String hashCode;
    private String department;
    @JSONField(serialize = false)
    private String counter="0";
}
