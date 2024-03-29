package com.kob.backend.controller.user.bot;

import com.alibaba.fastjson.JSONObject;
import com.kob.backend.pojo.Bot;
import com.kob.backend.service.user.bot.GetListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GetListController {
    @Autowired
    private GetListService getListService;

    @GetMapping("/api/user/bot/getlist/")
    public List<Bot> getList() {
        return getListService.getList();
    }

    @GetMapping("/api/user/bot/getlist/user/")
    public List<Bot> getUserList(@RequestParam Integer user_id){

        return getListService.getUserList(user_id);
    }

    @GetMapping("/api/user/bot/getlist/alluser/")
    public JSONObject getAllUserList(){
        return getListService.getAllUserList();
    }

}