package com.kob.backend.service.impl.record;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kob.backend.mapper.BotMapper;
import com.kob.backend.mapper.GameBotMapper;
import com.kob.backend.mapper.RecordMapper;
import com.kob.backend.mapper.UserMapper;
import com.kob.backend.pojo.Bot;
import com.kob.backend.pojo.GameBot;
import com.kob.backend.pojo.Record;
import com.kob.backend.pojo.User;
import com.kob.backend.service.record.GetRecordListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Service
public class GetRecordListServiceImpl implements GetRecordListService {
    @Autowired
    private RecordMapper recordMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private GameBotMapper gameBotMapper;
    @Autowired
    private BotMapper botMapper;


    @Override
    public JSONObject getList(Integer page) {
        IPage<Record> recordIPage = new Page<>(page, 10);
        QueryWrapper<Record> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("createtime");
        List<Record> records = recordMapper.selectPage(recordIPage, queryWrapper).getRecords();
        JSONObject resp = new JSONObject();
        List<JSONObject> items = new LinkedList<>();
        for (Record record: records) {
            User userA = userMapper.selectById(record.getAId());
            User userB = userMapper.selectById(record.getBId());
            JSONObject item = new JSONObject();
            item.put("a_photo", userA.getPhoto());
            item.put("a_username", userA.getUsername());
            item.put("b_photo", userB.getPhoto());
            item.put("b_username", userB.getUsername());
            String result = "平局";
            if ("A".equals(record.getLoser())) result = "B胜";
            else if ("B".equals(record.getLoser())) result = "A胜";
            item.put("result", result);
            item.put("record", record);
            items.add(item);
        }
        resp.put("records", items);
        resp.put("records_count", recordMapper.selectCount(null));

        return resp;
    }

    @Override
    public JSONObject getUserList(Integer id) {
        QueryWrapper<Record> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("a_id",id).or().eq("b_id",id);
        queryWrapper.orderByDesc("createtime");
        List<Record> records = recordMapper.selectList(queryWrapper);
        JSONObject resp = new JSONObject();
        List<JSONObject> items = new LinkedList<>();
        for (Record record: records) {
            User userA = userMapper.selectById(record.getAId());
            User userB = userMapper.selectById(record.getBId());
            JSONObject item = new JSONObject();
            item.put("a_photo", userA.getPhoto());
            item.put("a_username", userA.getUsername());
            item.put("b_photo", userB.getPhoto());
            item.put("b_username", userB.getUsername());
            String result = "平局";
            if ("A".equals(record.getLoser())){
                if(userB.getId().equals(id))
                    result = "胜利";
                else
                    result = "失败";
            }
            else if ("B".equals(record.getLoser())){
                if(userB.getId().equals(id))
                    result = "失败";
                else
                    result = "胜利";
            }
            QueryWrapper<GameBot> qqqq = new QueryWrapper<>();
            System.out.println("re = " + record.getId());
            qqqq.eq("game_id",record.getId());
            System.out.println("qqqq = " + qqqq);
            GameBot gameBot = gameBotMapper.selectOne(qqqq);
            String titleA = "手打", titleB = "手打";
            if(gameBot!=null){
                System.out.println("gameBot = " + gameBot);
                Bot bot = botMapper.selectById(gameBot.getABotId());
                if(bot!=null)
                    titleA = bot.getTitle();
                Bot bot1 = botMapper.selectById(gameBot.getBBotId());
                if(bot1!=null)
                    titleB = bot1.getTitle();
                item.put("a_bot_title", titleA);
                item.put("b_bot_title", titleB);
            }else{
                item.put("a_bot_title", titleA);
                item.put("b_bot_title", titleB);
            }

            item.put("result", result);
            item.put("record", record);
            items.add(item);
        }
        resp.put("records", items);

        return resp;
    }
}