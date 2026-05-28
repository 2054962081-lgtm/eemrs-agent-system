package com.lilin.client.pojo_contr;

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
    private String department;//科室
    private String idNumber;//NotNull
    private String userName;//姓名
    private String hashCode;//idNumber的哈希值
}
