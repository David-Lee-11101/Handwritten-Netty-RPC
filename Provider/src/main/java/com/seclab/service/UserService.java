package com.seclab.service;

import com.seclab.pojo.User;

import java.util.Map;

public interface UserService {
    User getUser(String userId);
    Map<String, User> getUser();
}
