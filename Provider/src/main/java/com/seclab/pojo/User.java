package com.seclab.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class User {
    private String userId;
    private String userName;
    private String age;
}