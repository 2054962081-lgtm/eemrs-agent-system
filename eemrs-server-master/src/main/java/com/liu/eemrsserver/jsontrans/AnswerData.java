package com.liu.eemrsserver.jsontrans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author L
 * @date 2019-09-25 4:55
 * @desc 服务器的响应数据，包含响应码和响应数据的JSON格式
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnswerData {
    private Integer answerCode;
    private boolean success;
    private String answerInfo;
}
