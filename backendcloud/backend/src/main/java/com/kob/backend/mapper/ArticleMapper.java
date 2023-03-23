package com.kob.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kob.backend.pojo.Article;
import org.apache.ibatis.annotations.Mapper;

/***
 * @author 睡醒继续做梦
 * @date 2023/3/23 16:33
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {
}
