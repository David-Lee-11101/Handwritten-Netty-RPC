package com.seclab.service;

import com.seclab.pojo.User;
import java.util.Map;

public interface UserService {

    String getUser(String userId);
    Map<String, User> getUser();
}

