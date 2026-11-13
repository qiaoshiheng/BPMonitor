package com.q.bpmonitor;

public class User {
    public long id;
    public String name;
    public int color;   // 头像/徽章颜色
    public long createdAt;

    // 基础资料（0/空 表示未填写）
    public int sex = -1;        // 0=女 1=男 -1=未填写
    public double age;          // 岁
    public double height;       // cm
    public double weight;       // kg

    /** BMI，资料不全返回 0 */
    public double bmi() {
        if (height <= 0 || weight <= 0) return 0;
        return weight / Math.pow(height / 100.0, 2);
    }
}
