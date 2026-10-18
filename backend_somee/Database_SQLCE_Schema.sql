-- ==============================================================================
-- PROJECT: Samgyupsal Ordering System
-- COURSE: CC106 Application Development & Emerging Technologies
-- DATABASE ENGINE: Microsoft SQL Server Compact Edition (MS SQL CE)
-- STORAGE FILE: Database.sdf
-- CONNECTION STRING: Data Source=Database.sdf;Persist Security Info=False;
-- ==============================================================================

-- 1. Orders Table: Tracks customer tickets, meals, meats, banchan, and statuses
CREATE TABLE [Orders] (
    [order_id] NVARCHAR(50) NOT NULL PRIMARY KEY,
    [table_code] NVARCHAR(50) NOT NULL,
    [head_count] INT NOT NULL DEFAULT 2,
    [set_meal] NVARCHAR(50) NOT NULL,
    [items_json] NTEXT NOT NULL,
    [is_addon] INT NOT NULL DEFAULT 0,
    [status] NVARCHAR(50) NOT NULL DEFAULT 'PREPARING',
    [created_at] BIGINT NOT NULL
);
GO

-- 2. TableSessions Table: Tracks 1-hour dining countdowns and time extensions
CREATE TABLE [TableSessions] (
    [table_code] NVARCHAR(50) NOT NULL PRIMARY KEY,
    [timer_started] INT NOT NULL DEFAULT 0,
    [start_time] BIGINT NOT NULL DEFAULT 0,
    [expiry_time] BIGINT NOT NULL DEFAULT 0
);
GO

-- Verification Queries
SELECT * FROM [Orders];
GO

SELECT * FROM [TableSessions];
GO
