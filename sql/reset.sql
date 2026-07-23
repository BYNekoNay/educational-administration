-- ============================================================
-- 艺术培训机构全流程教务管理平台 - 数据库一键重置（v2.0）
-- 
-- 说明：本项目 SQL 拆分为建表（schema.sql）和填充（data.sql）
--   schema.sql: DDL - 建库建表、索引、约束
--   data.sql:   DML - 角色、用户、课程、班级、学员、排课、考勤等演示数据
--   reset.sql:  本文件。三个文件完全独立，可分别执行
--
-- 用法（三选一）：
--   PowerShell:  Get-Content sql\reset.sql | mysql -uroot -p123456
--   完整重置:     (Get-Content sql\schema.sql; Get-Content sql\data.sql) | mysql -uroot -p123456
--   Bash:        cat sql/reset.sql | mysql -uroot -p123456
--                或者: cat sql/schema.sql sql/data.sql | mysql -uroot -p123456
--
-- 数据规模：22用户 12课程 12班级 12学员 15报名 232排课(含period时段) 24考勤 10时段
-- 版本：v2.0
-- 更新：2026-07-23 整合 schema.sql + data.sql，新增 period 时段系统和完整排课
-- ============================================================

DROP DATABASE IF EXISTS edu_admin;
CREATE DATABASE edu_admin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE edu_admin;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SOURCE schema.sql;
SOURCE data.sql;

SET FOREIGN_KEY_CHECKS = 1;
