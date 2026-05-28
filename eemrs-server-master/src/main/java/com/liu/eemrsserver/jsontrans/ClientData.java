package com.liu.eemrsserver.jsontrans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author L
 * @date 2019-09-24 17:18
 * @desc 用于接收用户发来的数据
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientData {
    private int opCode;
    private String userOP;
}
