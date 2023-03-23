package com.kob.backend.controller.user.account;

import com.kob.backend.service.user.account.MarkdownService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/***
 * @author 睡醒继续做梦
 * @date 2023/3/23 16:39
 */
@RestController
public class MarkdownController {

    @Autowired
    private MarkdownService markdownService;

    @PostMapping("/api/user/account/updatemarkdown/")
    public Integer  update(@RequestParam String markdown){
        return markdownService.updateMarkdown(markdown);
    }

    @PostMapping("/api/user/account/showmarkdown/")
    public String show(){
        return markdownService.showMarkdown();
    }
}
