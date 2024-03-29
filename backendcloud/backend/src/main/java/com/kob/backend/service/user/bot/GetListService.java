package com.kob.backend.service.user.bot;

import com.alibaba.fastjson.JSONObject;
import com.kob.backend.pojo.Bot;

import java.util.List;

public interface GetListService {
    List<Bot> getList();

    List<Bot> getUserList(Integer id);

    JSONObject getAllUserList();
}