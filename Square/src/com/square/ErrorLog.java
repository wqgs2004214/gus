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
import java.util.Observable;
import java.util.Vector;
//filter levels
//0 - Error
//1 - Warning
//2 - Info
//3 - Debug

public class ErrorLog extends Observable{
	private Vector<String> log; //the object of error messages, one string per entry

	/**
	 * constructor for the error log, initializes the object that holds the error messages.
	 */
	public ErrorLog(){
		log = new Vector<String>();
	}
	
	//returns true if added
	/**
	 * Appends a status message to the errorlog object
	 * @return true if the messages was added successfully, false otherwise
	 */
	public boolean addMsg(String s){
		boolean retval = false;
		//synchronized not needed
		if(s.startsWith("Error:")){ retval=true;}
		else if(s.startsWith("Warning:")){ retval=true;}
		else if(s.startsWith("Info:")){ retval=true;}
		else if(s.startsWith("Debug:")){ retval=true;}

		if(retval){log.add(s);setChanged();notifyObservers("log");}
		else{ addMsg("Warning: New Log Entry Lacks Proper Prefix; "+s);}
		return retval;
		}
}