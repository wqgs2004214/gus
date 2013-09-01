/*
This file is part of Magstripper, Copyright 2006, 2007.

Magstripper is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or
(at your option) any later version.

Magstripper is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package com.square;
import java.io.Serializable;

public class Time implements Comparable<Object>, Serializable{
	static final long serialVersionUID = 4362480612758L; //to make extending Serializable happy
	private int hour, minute;
	
	/**
	 * Constructor to build a time object given hour/min
	 * @param hr the hour
	 * @param min the minute
	 */
	public Time(int hr, int min){
		hour = hr;
		minute = min;
	}

	/**
	 * CompareTo method implemented for comparable
	 * @param obj the object to compare this to
	 * @return 1 if greater, 0 if equal, -1 if less
	 */
	public int compareTo(Object obj) throws ClassCastException{
		if(!(obj instanceof Time)){
			throw new ClassCastException("Must compare two time objects");
		}
		Time t = (Time)obj;
		if( hour > t.getHour() || hour == t.getHour() && minute > t.getMinute())
			return 1;
		else if(hour < t.getHour() || hour == t.getHour() && minute < t.getMinute())
			return -1;
		else
			return 0;
	}
	
	/**
	 * Determine if the current time is within the range of these two times
	 * @param s start time
	 * @param e end time
	 * @return true if it is within range, false otherwise
	 */
	public boolean withinRange(Time s, Time e){
		if(s.compareTo(e) == 0 || s.compareTo(this) < 0 && e.compareTo(this) > 0 )
			return true;
		return false;
	}
	
	/**
	 * Convert the time into a displayable string
	 * @return string of the format HH:MM
	 */
	public String toString(){
		String retval = "";
		if(hour < 10)
			retval += "0";
		retval += hour+":";
		if(minute < 10)
			retval += "0";
		retval += minute;
		return retval;
	}
	
	/**
	 * Return the hour of this time object
	 * @return hour
	 */
	public int getHour(){
		return hour;
	}
	
	/**
	 * Return the minute of this time object
	 * @return minute
	 * @return
	 */
	public int getMinute(){
		return minute;
	}
	
}