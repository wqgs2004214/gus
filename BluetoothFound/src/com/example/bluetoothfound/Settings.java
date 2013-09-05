/*
 * Copyright (C) 2012 Shenzhen SMT online Co., Ltd.
 * 
 * 项目名称:SMT移动信息化解决方案系统
 * 创建日期:2013年9月5日
 */
package com.example.bluetoothfound;
/**
 * setting
 * 修改日期:2013年9月5日
 * @author gus@sinomaster.com
 * @version 1.0.0
 */
public class Settings {
	private int duration;
	private int alramMode;
	private String ringtone;
	public int getDuration() {
		return duration;
	}
	public void setDuration(int duration) {
		this.duration = duration;
	}
	public int getAlramMode() {
		return alramMode;
	}
	public void setAlramMode(int alramMode) {
		this.alramMode = alramMode;
	}
	public String getRingtone() {
		return ringtone;
	}
	public void setRingtone(String ringtone) {
		this.ringtone = ringtone;
	}
	
	
}
