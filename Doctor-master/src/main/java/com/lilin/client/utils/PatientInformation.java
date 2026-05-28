package com.lilin.client.utils;

/**
 * @author lilin
 * @date 2019/10/12  -  2:12 下午
 */


public class PatientInformation {

    private  static  String idNumber;

    private  static String  userName;
    public  String  getIdNumber(){
        return  idNumber;
    }
    public String getUserName(){
        return  userName;
    }
    public String setIdNumber(String idNumber){
        return this.idNumber = idNumber;
    }
    public String setUserName(String userName){
        return this.userName = userName;
    }





}
