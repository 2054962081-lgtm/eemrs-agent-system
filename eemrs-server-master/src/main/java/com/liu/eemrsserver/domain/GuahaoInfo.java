package com.liu.eemrsserver.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author L
 * @date 2019-10-06 20:17
 * @desc 挂号信息
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuahaoInfo {
    private String department;//科室,使用确定性加密
    private String idNumber;//NotNull
    private String userName;//姓名
    private String hashCode;//idNumber的哈希值，在医生接诊时关联查询已注册病人的详细信息
    private String doctorIdHashCode;
}
