package com.kob.backend.service.impl.user.bot;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kob.backend.mapper.BotMapper;
import com.kob.backend.mapper.UserMapper;
import com.kob.backend.pojo.Bot;
import com.kob.backend.pojo.User;
import com.kob.backend.service.impl.utils.UserDetailsImpl;
import com.kob.backend.service.user.bot.GetListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Service
public class GetListServiceImpl implements GetListService {
    @Autowired
    private BotMapper botMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public List<Bot> getList() {
        UsernamePasswordAuthenticationToken authenticationToken =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl loginUser = (UserDetailsImpl) authenticationToken.getPrincipal();
        User user = loginUser.getUser();

        QueryWrapper<Bot> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", user.getId());

        return botMapper.selectList(queryWrapper);
    }

    @Override
    public List<Bot> getUserList(Integer id) {
        LambdaQueryWrapper<Bot> q = new LambdaQueryWrapper<>();
        q.eq(Bot::getUserId,id);
        List<Bot> bots = botMapper.selectList(q);
        for(Bot i : bots){
            i.setContent("");
        }
        return bots;
    }

    @Override
    public JSONObject getAllUserList() {

        JSONObject jsonObject = new JSONObject();
        QueryWrapper<Bot> q = new QueryWrapper<>();
        q.orderByDesc("rating");
        List<Bot> bots = botMapper.selectList(q);
        List<JSONObject> items = new LinkedList<>();
        for(Bot bot : bots){
            JSONObject res = new JSONObject();
            res.put("bot_title", bot.getTitle());
            res.put("user_id", bot.getUserId());
            res.put("bot_rating", bot.getRating());
            res.put("user_photo",userMapper.selectById(bot.getUserId()).getPhoto() );
//            res.put("game_time",)
            //TODO 次数
            res.put("user_name", userMapper.selectById(bot.getUserId()).getUsername());
            items.add(res);
        }
        jsonObject.put("bot",items);
        return jsonObject;
    }
}