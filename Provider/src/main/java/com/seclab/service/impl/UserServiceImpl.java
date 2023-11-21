package com.seclab.service.impl;

import com.seclab.pojo.User;
import com.seclab.service.UserService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service("userService")
public class UserServiceImpl implements UserService {
    @Override
    public User getUser(String userId) {
        User user = new User("1001", "xiaochen", "7");
        User user2 = new User("1002", "xiaozhao", "8");
        Map<String, User> allUserMap = new HashMap<String, User>();
        allUserMap.put(user.getUserId(), user);
        allUserMap.put(user2.getUserId(), user2);
        return allUserMap.get(userId);
    }

    @Override
    public Map<String, User> getUser() {
        User user = new User("1001", "xiaochen", "7");
        User user2 = new User("1002", "xiaozhao", "8");
        Map<String, User> allUserMap = new HashMap<String, User>();
        allUserMap.put(user.getUserId(), user);
        allUserMap.put(user2.getUserId(), user2);
        return allUserMap;
    }
}