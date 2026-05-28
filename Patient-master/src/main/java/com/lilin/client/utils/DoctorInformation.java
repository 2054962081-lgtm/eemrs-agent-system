package com.lilin.client.utils;

/**
 * @author lilin
 * @date 2019/10/12  -  3:05 下午
 */
public class DoctorInformation {
    private static String idNumber;
    private  static String userName;
    private  static  String department;


    public String getIdNumber(){
        return idNumber;
    }
    public  String setIdNumber(String idNumber){
        return this.idNumber = idNumber;
    }
    public String getUserName(){
        return userName;
    }
    public String setUserName(String userName){
        return this.userName = userName;
    }
    public String getDepartment(){
        return department;
    }
    public String setDepartment(String department){
        return this.department = department;
    }
}
