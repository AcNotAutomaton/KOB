package com.kob.backend.service.impl.user.account;

import com.kob.backend.mapper.ArticleMapper;
import com.kob.backend.pojo.Article;
import com.kob.backend.service.user.account.MarkdownService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/***
 * @author 睡醒继续做梦
 * @date 2023/3/23 16:35
 */
@Service
public class MarkdownImpl implements MarkdownService {

    @Autowired
    private ArticleMapper articleMapper;

    @Override
    public Integer updateMarkdown(String markdown) {
        return articleMapper.updateById(new Article(1, markdown));

    }

    @Override
    public String showMarkdown() {
        return articleMapper.selectById(1).getMarkdown();
    }
}
