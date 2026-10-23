package com.kob.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.kob.backend.consumer.utils.Player;
import com.kob.backend.mapper.*;
import com.kob.backend.pojo.Bot;
import com.kob.backend.pojo.User;
import com.kob.backend.service.impl.pk.GameResultService;
import com.kob.backend.service.impl.user.account.RegisterServiceImpl;
import com.kob.backend.service.impl.user.bot.UpdateServiceImpl;
import com.kob.backend.service.impl.utils.UserDetailsImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** 使用真实 SQL、MyBatis Mapper 和 Spring 事务代理，数据库完全独立于项目 MySQL。 */
@SpringJUnitConfig(PersistenceConsistencyTest.Config.class)
class PersistenceConsistencyTest {
    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.kob.backend.mapper")
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                    + ";MODE=MySQL;NON_KEYWORDS=USER,RECORD,COUNT;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean GameResultService resultService(UserMapper users, BotMapper bots, RecordMapper records, GameBotMapper gameBots) {
            return new GameResultService(users, bots, records, gameBots);
        }
    }

    @Autowired DataSource source;
    @Autowired GameResultService results;
    @Autowired UserMapper users;
    @Autowired BotMapper bots;
    private JdbcTemplate jdbc;

    @BeforeEach
    void schema() {
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE user (id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100) UNIQUE, "
                + "password VARCHAR(100), photo VARCHAR(1000), rating INT, times INT)");
        jdbc.execute("CREATE TABLE bot (id INT AUTO_INCREMENT PRIMARY KEY, user_id INT, title VARCHAR(100), "
                + "description VARCHAR(300), content VARCHAR(10000), rating INT, count INT, createtime TIMESTAMP, modifytime TIMESTAMP)");
        jdbc.execute("CREATE TABLE record (id INT AUTO_INCREMENT PRIMARY KEY, a_id INT, a_sx INT, a_sy INT, "
                + "b_id INT, b_sx INT, b_sy INT, a_steps VARCHAR(1000), b_steps VARCHAR(1000), map VARCHAR(1000), loser VARCHAR(10), createtime TIMESTAMP)");
        jdbc.execute("CREATE TABLE game_bot (id INT AUTO_INCREMENT PRIMARY KEY, game_id INT, a_bot_id INT, "
                + "a_user_id INT, b_bot_id INT, b_user_id INT)");
        jdbc.update("INSERT INTO user(id,username,rating,times) VALUES(1,'one',1500,0),(2,'two',1500,0)");
        jdbc.update("INSERT INTO bot(id,user_id,title,content,rating,count) VALUES(11,1,'old','code',1600,42),(12,2,'other','code',1500,0)");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private Player player(int id, int bot) {
        return new Player(id, bot, "", id == 1 ? 11 : 1, id == 1 ? 1 : 12, new ArrayList<>(List.of(id == 1 ? 0 : 2)));
    }

    @Test
    void failureOnLastWriteRollsBackRatingsCountsAndRecord() {
        jdbc.execute("ALTER TABLE game_bot ADD CONSTRAINT reject_test_result CHECK (b_user_id <> 2)");
        assertThrows(RuntimeException.class, () -> results.save(player(1, 11), player(2, 12), "map", "B"));
        assertEquals(1500, jdbc.queryForObject("SELECT rating FROM user WHERE id=1", Integer.class));
        assertEquals(1500, jdbc.queryForObject("SELECT rating FROM user WHERE id=2", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT times FROM user WHERE id=1", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT times FROM user WHERE id=2", Integer.class));
        assertEquals(1600, jdbc.queryForObject("SELECT rating FROM bot WHERE id=11", Integer.class));
        assertEquals(1500, jdbc.queryForObject("SELECT rating FROM bot WHERE id=12", Integer.class));
        assertEquals(42, jdbc.queryForObject("SELECT count FROM bot WHERE id=11", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count FROM bot WHERE id=12", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM record", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM game_bot", Integer.class));
    }

    @Test
    void successfulResultCommitsAllChangesAndGeneratedRecordId() {
        results.save(player(1, 11), player(2, 12), "map", "B");
        assertEquals(1505, jdbc.queryForObject("SELECT rating FROM user WHERE id=1", Integer.class));
        assertEquals(1498, jdbc.queryForObject("SELECT rating FROM user WHERE id=2", Integer.class));
        assertEquals(1605, jdbc.queryForObject("SELECT rating FROM bot WHERE id=11", Integer.class));
        assertEquals(43, jdbc.queryForObject("SELECT count FROM bot WHERE id=11", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM record r JOIN game_bot g ON g.game_id=r.id", Integer.class));
    }

    @Test
    void drawCountsAsAGameAndNullHistoricCountersCanBeIncremented() {
        jdbc.update("UPDATE user SET times=NULL WHERE id=1");
        jdbc.update("UPDATE bot SET count=NULL WHERE id=11");
        results.save(player(1, 11), player(2, 12), "map", "all");
        assertEquals(1, jdbc.queryForObject("SELECT times FROM user WHERE id=1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count FROM bot WHERE id=11", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count FROM bot WHERE id=12", Integer.class));
        assertEquals(1600, jdbc.queryForObject("SELECT rating FROM bot WHERE id=11", Integer.class));
    }

    @Test
    void titleEditDoesNotOverwriteAConcurrentRatingAndCountIncrement() {
        BotMapper raced = mock(BotMapper.class, delegatesTo(bots));
        when(raced.selectById(11)).thenAnswer(inv -> {
            Bot stale = bots.selectById(11);
            jdbc.update("UPDATE bot SET rating=rating+5,count=count+1 WHERE id=11");
            return stale;
        });
        UpdateServiceImpl edit = new UpdateServiceImpl();
        ReflectionTestUtils.setField(edit, "botMapper", raced);
        User user = users.selectById(1);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserDetailsImpl(user), null, List.of()));
        assertEquals("success", edit.update(Map.of("bot_id", "11", "title", "renamed", "description", "description", "content", "code")).get("error_message"));
        assertEquals("renamed", jdbc.queryForObject("SELECT title FROM bot WHERE id=11", String.class));
        assertEquals(1605, jdbc.queryForObject("SELECT rating FROM bot WHERE id=11", Integer.class));
        assertEquals(43, jdbc.queryForObject("SELECT count FROM bot WHERE id=11", Integer.class));
    }

    @Test
    void simultaneousRegistrationsCreateOnlyOneAccount() throws Exception {
        CyclicBarrier checkedAvailability = new CyclicBarrier(2);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any(CharSequence.class))).thenAnswer(inv -> {
            checkedAvailability.await(3, TimeUnit.SECONDS);
            return new BCryptPasswordEncoder(4).encode(inv.getArgument(0));
        });
        RegisterServiceImpl registration = new RegisterServiceImpl();
        ReflectionTestUtils.setField(registration, "userMapper", users);
        ReflectionTestUtils.setField(registration, "passwordEncoder", encoder);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Map<String, String>> a = executor.submit(() -> registration.register("same", "password", "password"));
            Future<Map<String, String>> b = executor.submit(() -> registration.register("same", "password", "password"));
            List<String> responses = List.of(a.get(5, TimeUnit.SECONDS).get("error_message"), b.get(5, TimeUnit.SECONDS).get("error_message"));
            assertTrue(responses.contains("success"));
            assertTrue(responses.contains("用户名已存在"));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE username='same'", Integer.class));
        } finally {
            executor.shutdownNow();
        }
    }
}
