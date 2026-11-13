package com.q.bpmonitor;

public class Record {
    public long id;
    public long ts;        // 测量时间戳（毫秒）
    public int sys;        // 收缩压
    public int dia;        // 舒张压
    public int hr;         // 心率
    public boolean irregular; // 脉搏不规律
    public boolean afib;      // 疑似房颤
    public String note;
    public long userId;       // 所属用户
}
