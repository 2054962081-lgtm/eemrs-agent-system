package com.lilin.client.pojo_contr;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author L
 * @date 2019-09-24 17:18
 * @desc
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientData {
    private int opCode;
    private String userOP;
}
