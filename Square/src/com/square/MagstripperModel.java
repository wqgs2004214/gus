package com.square;

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

import android.annotation.SuppressLint;
//import java.security.MessageDigest;
//import java.security.NoSuchAlgorithmException;
//import java.text.SimpleDateFormat;
//import java.util.Date;
import java.util.HashMap;
import java.util.Observable;



public class MagstripperModel extends Observable {
	private boolean listening = false;
	private Boolean locked = true;
	private String comport = "COM1";
	private static ErrorLog el = new ErrorLog();
	private HashMap<String,Swipe> openSwipes = new HashMap<String,Swipe>();
	private short thresdelta = 600; //pref, if changed from default, you should also change MicIn.java when it detects a swipe over 5 seconds
	private int lockTime = 4000; //pref
	private int zerolvl = 0; //pref not possible via short = 'automatic'
	private boolean zeroautomatic = true; //pref
	private short numswipes = 0;//pref
	private char spaceChar = ' '; //pref, specifies how the space character should be displayed
	private boolean secLog = true; //pref, dont display plain text data in the system log
	private int saltRange = 256; //pref the number of possibilities for the salt
	//private UserTableModel utm;
	//private CardTableModel ctm;
	private String version = "0.3a";
	
	/**
	 * Constructor that creates the user and card tablemodels
	 */
	public MagstripperModel(){
	//	utm = new UserTableModel(this);
	//	ctm = new CardTableModel();
	}
	
	/**
	 * Sets the usertablemodel, used when loading the table
	 * @param u the usertablemodel
	 */
	//public void setUserTableModel(UserTableModel u){
	//	utm = u;
	//}
	/**
	 * Sets the cardtablemodel, used when loading the table
	 * @param c
	 */
	//public void setCardTableModel(CardTableModel c){
	//	ctm = c;
	//}
	
	/**
	 * Returns the usertablemodel used by this model
	 * @return the usertablemodel
	 */
	//public UserTableModel getTableModel(){
	//	return utm;
	//}
	
	/**
	 * Returns the cardtablemodel userd by this model
	 * @return the cardtablemodel
	 */
	//public CardTableModel getCardTableModel(){
	//	return ctm;
	//}
	
	/**
	 * Sets the space character that should replace all spaces in the ASCII decoding output
	 * @param c the new space character
	 */
	public void setSpaceChar(char c){
		spaceChar = c;
		setChanged();
		el.addMsg("Info: Setting Space Character To "+c);
		this.notifyObservers("space");
	}
	/**
	 * Returns the space character used in all the ASCII decoding output
	 * @return the space character
	 */
	public char getSpaceChar(){
		return spaceChar;
	}
	
	/**
	 * Sets the number of swipes that should be stored
	 * @param i the number of swipes
	 */
	public void setNumSwipes(short i){
		numswipes = i;
		el.addMsg("Info: Setting Number Of Swipes In Memory To "+i);
	}
	
	/**
	 * Returns the number of swipes that should be stored
	 * @return the number of swipes
	 */
	public short getNumSwipes(){
		return numswipes;
	}
	
	/**
	 * Sets if the zero level should be detected automatically or not 
	 * @param b true for automatic false for static
	 */
	public void setZeroAutomatic(boolean b){
		zeroautomatic = b;
		setChanged();
		this.notifyObservers("zerolevelautomatic");
	}
	
	/**
	 * Returns if the zero level should be determined automatically
	 * @return true for automatic, false for static
	 */
	public boolean getZeroAutomatic(){
		return zeroautomatic;
	}
	
	/**
	 * Statically sets the zero level
	 * @param i the new zero level
	 */
	public void setZeroLevel(int i){
		zerolvl = i;
		setChanged();
		this.notifyObservers("zerolevel");
	}
	
	/**
	 * Statically sets the zero level
	 * @param i the new zero level
	 * @param b boolean if a notification should be sent out
	 */
	public void setZeroLevel(int i, boolean b){
		if(b)
			setZeroLevel(i);
		else
			zerolvl = i;
	}
	
	/**
	 * Returns the current zero level
	 * @return the zero level
	 */
	public int getZeroLevel(){
		return zerolvl;
	}
	
	/**
	 * Sets the noise thresholds statically
	 * @param d the new thresholds
	 */
	public void setDelta(short d){ //delta used for +/- thresholds for mic in
		thresdelta=d;
		setChanged();
		this.notifyObservers("thresholds");
	}
	
	/**
	 * Returns the noise thresholds
	 * @return the noise thresholds
	 */
	public short getDelta(){
		return thresdelta;
	}
	
	/**
	 * Sets the default time a lock should be opened
	 * @param i the time in miliseconds
	 */
	public void setLockTime(int i){
		lockTime=i;
		el.addMsg("Info: Setting Default Unlock Time To "+i);
	}
	
	/**
	 * Returns the default unlock time 
	 * @return unlock time in miliseconds
	 */
	public int getLockTime(){
		return lockTime;
	}
	
	/**
	 * Sets whether the microphone should be reading data
	 * @param tl true is listen, false is pause
	 */
	public void setListening(boolean tl){
		listening = tl;
	}
	
	/**
	 * Returns whether the microphone is listening to input
	 * @return true is listening, false is paused
	 */
	public boolean getListeningState(){
		return listening;
	}
	
	/**
	 * Sets the sate of the lock
	 * @param l true is locked, false is unlocked
	 */
	public void setLockState(boolean l){
		locked = l;
		el.addMsg("Info: Setting Strike To "+(l ? "Locked" : "Unlocked"));
		setChanged();
		this.notifyObservers("lock");
	}
	
	/**
	 * Return the lock state
	 * @return true is locked, false is unlocked
	 */
	public boolean getLockState(){
		return locked;
	}
	
	/**
	 * Adds a swipe to the model
	 * @param s the swipe to add
	 */
	public void addSwipe(Swipe s){
		s.decodeSwipe();
		openSwipes.put(s.getTimestamp(),s);
		setChanged();
		this.notifyObservers(s);
	}
	
	/**
	 * Remove a swipe from the list based on its' timestamp
	 * @param id the swipe's id
	 */
	public void removeSwipe(String id){
		openSwipes.remove(id);
	}
	
	/**
	 * Checks if the lock should be unlocked based on the permission table
	 * @param s the swipe to check the permission on
	 */
	@SuppressLint("UseValueOf")
	/*public void checkPermissions(Swipe s){
		int maxtime = 0;
		int usedcardrow = 0;
		Integer userID = new Integer(-1);
		Date currDate = new Date();
		SimpleDateFormat currhoursf = new SimpleDateFormat("H");
		SimpleDateFormat currminf = new SimpleDateFormat("m");
		Time currTime = new Time( new Integer(currhoursf.format(currDate)).intValue(), new Integer(currminf.format(currDate)).intValue() );
		
		byte[] suffix = s.getDecodedString().getBytes();//plaintext data
		byte[] plain = new byte[suffix.length+2];//add two places in the array for the salt
		System.arraycopy(suffix, 0, plain, 2, suffix.length); //copy the array with two salt addresses @ 0-1
		MessageDigest digest = null; //calculate the hash
		try {
			digest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e1) {
			el.addMsg("Error: Could Not Load MD5 Hashing Function");
			return;
		}
		
		for(int i=0;i<utm.getRowsDefined();i++){//loop through each user
			el.addMsg("Debug: Testing User: "+((Integer)utm.getValueAt(i, UserTableModel.ID)).intValue()+" Enabled: "+((Boolean)utm.getValueAt(i, UserTableModel.ENABLED)));
			if( ((Boolean)utm.getValueAt(i, UserTableModel.ENABLED)) && maxtime < ((Integer)utm.getValueAt(i, UserTableModel.UNLOCKTIME)).intValue() ){//if this user is enabled
				Cards c = ctm.getCards( ((Integer)utm.getValueAt(i, UserTableModel.ID)) );
				for(int j=0;j<c.getNumCards();j++){//loop through each card
					Object[] cardRow = c.getCard(j);
					
					if( ((Boolean)cardRow[CardTableModel.ENABLED]) && ( ((String)cardRow[CardTableModel.DIRECTION]).equals("Both") || //if the card is enabled, and the hash matches, and direction is both
							s.getSwipedReverse().equals("Yes") && ((String)cardRow[CardTableModel.DIRECTION]).equals("Backward") || s.getSwipedReverse().equals("No") && ((String)cardRow[CardTableModel.DIRECTION]).equals("Forward") ) &&  //if the direction matches, 
							currTime.withinRange((Time)cardRow[CardTableModel.STARTTIME], (Time)cardRow[CardTableModel.ENDTIME]) ){	//if the time is within range

						el.addMsg("Debug: Testing Card: "+((Integer)ctm.getValueAt(j, CardTableModel.ID)).intValue());

						byte[] hashed;
						String cardHash = (String)(cardRow[CardTableModel.HASHEDDATA]);
						char char1, char2;
						for(int salt=0;salt<=saltRange;salt++){
							plain[0] = (byte)((salt & 0xff00)>>>8);//high order byte of salt, shift down to a byte and add it in the first position
							plain[1] = (byte)(salt & 0xff);//low order byte of salt, cut out 
							hashed = digest.digest(plain);
							for(int k=0;k<16;k++){
								char1 = (Integer.toString( ((hashed[k] & 0xf0)>>>4), 16)).charAt(0);//high order char
								char2 = (Integer.toString( (hashed[k] & 0x0f), 16)).charAt(0);//low order char
								if(char1 != cardHash.charAt(2*k) || char2 != cardHash.charAt(2*k+1)){//short circuit the checking
									break;
								}else if(k==15){//passed
									salt=saltRange+1;
									maxtime = ((Integer)utm.getValueAt(i, UserTableModel.UNLOCKTIME)).intValue();
									usedcardrow = j;
									userID = (Integer)utm.getValueAt(i, UserTableModel.ID);	
								}
							}
						}
					}
				}//end looping through each card
			}
		}//end looping through each user
		if(maxtime > 0){
			Strike unlock = new Strike(this, maxtime);
			unlock.start();
			ctm.getCards(userID).incrementUse(usedcardrow);
			ctm.updateUses(usedcardrow);
		}
	}
	*/
	
	/**
	 * Gets the swipe object that matches the decode time
	 * @param time the string containing the timestamp of the swipe to find
	 * @return the swipe matching the timestamp
	 */
	public Swipe getSwipe(String time){ //return the swipe that is based on this timestamp
		return openSwipes.get(time);
	}
	
	/**
	 * Returns the number of swipes stored in memory
	 * @return the number of swipes
	 */
	public int getSwipeCount(){
		return openSwipes.size();
	}

	/**
	 * Returns the errorlog object for the model
	 * @return the errorlog
	 */
	public ErrorLog getLog(){
		return el;
	}
	
	/**
	 * Sets the comport
	 * @param s The string representation of the comport ex. "COM1"
	 */
	public void setComPort(String s){
		comport = s;
		el.addMsg("Info: Setting Com Port to "+s);
	}
	
	/**
	 * returns the string representation of the comport currently in use
	 * @return the comport
	 */
	public String getComPort(){ return comport; }
	
	/**
	 * Sets whether the system log sould be secure
	 * @param b yes or no
	 */
	public void setSecLog(boolean b){
		secLog = b;
	}
	
	/**
	 * Returns if the log is secure
	 * @return if the log is secure
	 */
	public boolean getSecLog(){
		return secLog;
	}
	
	public int getSaltRange(){
		return saltRange;
	}
	
	public void setSaltRange(int i){
		saltRange = i;
	}
	
	public String getVersion(){
		return version;
	}
	
}