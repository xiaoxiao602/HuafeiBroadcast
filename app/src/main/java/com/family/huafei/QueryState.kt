package com.family.huafei

/** 查询状态机:单一来源保存在 SharedPreferences,进程被杀后仍可恢复判断。 */
enum class QueryState { IDLE, SENDING, WAITING, SUCCESS, FAILED }
