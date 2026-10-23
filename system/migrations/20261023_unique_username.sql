-- 执行前检查：SELECT username, COUNT(*) FROM `user` GROUP BY username HAVING COUNT(*) > 1;
-- 如已有仅包含 username 的唯一索引则无需重复执行；本迁移不会删除或合并任何账号。
ALTER TABLE `user` ADD UNIQUE INDEX `uk_user_username` (`username`);
