package com.kob.backend.controller.user.account;

import com.kob.backend.service.user.account.InfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

public class InfoController {
    @Autowired
    private InfoService infoService;

    @GetMapping("/api/user/account/info")
    public Map<String, String> getinfo() {
        return infoService.getinfo();
    }
}
