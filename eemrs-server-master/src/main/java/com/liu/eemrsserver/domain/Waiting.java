package com.liu.eemrsserver.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author L
 * @date 2019-10-07 16:57
 * @desc 返回医生查询的当前科室的等待接诊的病人信息
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Waiting {
    private String idNumber;
    private String userName;
}
