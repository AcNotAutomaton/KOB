package com.kob.backend.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 * @author 睡醒继续做梦
 * @date 2023/4/21 15:47
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("game_bot")
public class GameBot {
    private Integer id;
    private Integer gameId;
    private Integer aBotId;
    private Integer aUserId;
    private Integer bBotId;
    private Integer bUserId;
}
