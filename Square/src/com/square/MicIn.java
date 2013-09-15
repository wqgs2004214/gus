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
import java.util.Iterator;
import java.util.LinkedList;
import android.media.*;



/* PRINT OUT MIXER INFO		
Mixer.Info[] mixers = AudioSystem.getMixerInfo(); 
System.out.println("Found " + mixers.length + " mixers");
for(int i=0;i<mixers.length;i++){
	System.out.println(mixers[i].getName());
	System.out.println("     " + mixers[i].getDescription());
	System.out.println("     " + mixers[i].getVendor());
	System.out.println("     " + mixers[i].getVersion());
}
System.out.println("==============================\n");
*/

public class MicIn extends Thread{
	private short zerolvl = 0;
	private short posthres, negthres, delta;
	private MagstripperModel model;
	private volatile boolean suspend = true;
	//private TargetDataLine mic;
	private byte[] lastRead;
	private AudioRecord ar;
	private int bs;
	private static int SAMPLE_RATE_IN_HZ = 44100;
	/**
	 * Constructor to create the 
	 * @param m The underlying model
	 */
	public MicIn(MagstripperModel m){
		this.model=m;
		//delta = 600;
		delta = m.getDelta();
	}
	
	/**
	 * Sets the zero level and recalculates the nose thresholds
	 * @param i zero level
	 */
	public void setZeroLevel(short i){
		zerolvl = i;
		posthres = (short)(delta+zerolvl); 
		negthres = (short)(zerolvl-delta);
		model.getLog().addMsg("Info: Setting Microphone Zero Level To: "+zerolvl+" Setting Thresholds To: "+posthres+" - "+negthres);
	}
	
	/**
	 * Recalculates and sets the zero level and noise thresholds automatically based on the 
	 * @return the new zero level
	 */
	public short recalculateZeroLevel(){
		int retval = 0;
		for(int i=200;i<1200;i+=2){ //offset a few frames, calc the avg zero level
			retval += (short)((((short)lastRead[i+1])<<8) + ( ((short)lastRead[i]) & 255));
		}
		retval = (short)(retval/500);
		model.getLog().addMsg("Info: Recalculating Microphone Zero Level To: "+retval);
		return (short)retval;
	}
	/**
	 * Sets the noise thresholds, generally after a zerolevel or threshold update
	 *
	 */
	public void setThresholds(){
		delta = model.getDelta();
		posthres = (short)(delta+zerolvl); 
		negthres = (short)(zerolvl-delta);
		model.getLog().addMsg("Info: Setting Thresholds To: "+posthres+" - "+negthres);
	}
	
	/**
	 * Method called when the thread is started. listens for input breaks each card read into a swipe.
	 */
	public void run(){
		bs = AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT);
		ar = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE_IN_HZ,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT, bs*10);
		
		// READ IN FROM MIC
		//audiofmt = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,44100.0F, 16, 1, 2, 44100.0F, false);
		//DataLine.Info info = new DataLine.Info(TargetDataLine.class, audiofmt);
		//mic = null;
	
		byte[] buffer = new byte[bs*10];
		try{
			super.run();
			ar.startRecording();
			
	                //ar.read(buffer, 0, buffer.length);
			
		}//catch(LineUnavailableException e){
			//model.getLog().addMsg("Error: LineUnavailableException Caught When Trying To Access Microphone");
			//model.setListening(false);
			//return;
		//}
	catch(IllegalArgumentException iae){
			model.getLog().addMsg("Error: IllegalArgumentException Caught When Trying To Initialize Microphone Input");
			model.setListening(false);
			return;
		}
		lastRead = new byte[SAMPLE_RATE_IN_HZ / 10]; //used to backtrack if we're at the end of the buffer (100ms)
		int read = 0; //stores the actual number of bytes we read from the buffer
		if( 81 > ( ar.read(lastRead,0,lastRead.length)) ){
			zerolvl = 0;
			model.getLog().addMsg("Warning: We Couldn't Set The Zero Level Because We Didn't Read Enough Bytes");
		} else {
			//zerolvl = recalculateZeroLevel(); 
			//setThresholds();
		}
		
		boolean trackDetected = false; //are we in the middle of recording a swipe?
		int inNoiseLevel = 0; //the number of samples recorded inside the noise level
		LinkedList<Byte> currAudioData = new LinkedList<Byte>(); //linked list to temporarily store a swipe
		int min = 65535; int max = -65535; int quartersecs = 0; //used to output the min/max noise level, quartersecs records the number of loops through reading the buffer
		while( 0 < (read = ar.read(buffer,0,buffer.length)) ){ //read from the targetdataline's buffer
			synchronized(this){
				try {
					while(suspend){//if we should suspend execution
						wait();
						if(suspend == false){
							zerolvl = recalculateZeroLevel();
							model.setZeroLevel(zerolvl, false);
						}
						setThresholds();
					}
				} catch (InterruptedException e) {
					model.getLog().addMsg("Error: InterruptedException Thrown When Trying To Suspend Microphone Input");
					return;
				}
			}
			
			for(int i=0;i<read;i+=2){ //step through the buffer we just read
				int val = (short)((((short)buffer[i+1])<<8) + ( ((short)buffer[i]) & 255)); //take the two bytes and create a short value
				if(!trackDetected && val > posthres || !trackDetected && val < negthres)
				{ //outside thresholds, start of track found
					trackDetected = true; 
					min = 65535; max = -65535; quartersecs = 0; //reset the min/max output
					model.getLog().addMsg("Info: Detected Start Of Swipe From Microphone");
					if( i < lastRead.length ){//we dont have 100ms on this array, use the readback buffer
						currAudioData.add(new Byte(buffer[i+1])); //since we will be reading backward filling in reverse, store this value (for loop steps by two)
						for(int j=0;j<lastRead.length-1;j++){
							if(i-j >= 0) //use the current buf
								currAudioData.add(0, new Byte(buffer[i-j]));
							else //use the old buffer
								currAudioData.add(0, new Byte(lastRead[i+lastRead.length-j]));
						}
					}else{ //we dont need the backtracking array
						currAudioData.add(new Byte(buffer[i+1])); //since we will be reading backward filling in reverse, store this value (for loop steps by two)
						for(int j=0;j<lastRead.length-1;j++){
							currAudioData.add(0, new Byte(buffer[i-j])); //work back from where we are, adding 100ms to the front of the linked list
						}
					}
				}//outside threshold
				else if(trackDetected){ //we are mid swipe
					if(currAudioData.size() > SAMPLE_RATE_IN_HZ)
					{//something's wrong with our thresholds we shouldnt have a swipe 5 seconds long
						//discard this swipe, reset the zerolevel/thresholds
						currAudioData.clear();
						model.getLog().addMsg("Warning: The Zero Level / Thresholds Are Way Off Causing A Detected Swipe Over 5 Seconds, Discarding Swipe And Resetting Levels");
						zerolvl = recalculateZeroLevel();
						model.setZeroLevel(zerolvl, false);
						if(model.getDelta() < 600)
						{
							model.getLog().addMsg("Info: Thresholds Reset To Default Value");
							model.setDelta((short)600); //setthresholds is called from observer
						}else{
							model.getLog().addMsg("Info: Thresholds Increased beyond Default Value, User Should Check Microphone Levels And Manually Set Thresholds");
							model.setDelta((short) (model.getDelta()+300) );
						}
						trackDetected = false;
					}//delay 5s.
					else
					{//trackDetect=1 and time bellow 5S.
						currAudioData.add(new Byte(buffer[i]));
						currAudioData.add(new Byte(buffer[i+1]));
						if(val < posthres && val > negthres){ //within the noise level
							inNoiseLevel+=2;
						}else
						{
							inNoiseLevel=0; //reset
						}
						if(inNoiseLevel >= lastRead.length){ //end the swipe, 100ms at the end of the swipe
							trackDetected = false;
							inNoiseLevel = 0; //reset noise level
							if(currAudioData.size()%2 != 0)
							{ //we're dealing with 16 bit samples, we should have an even number of bytes in our data struct
								model.getLog().addMsg("Error: Read Odd Amount Of Data From Microphone, Cannot Decode Swipe");
							}else
							{
								model.getLog().addMsg("Info: Detected End Of Swipe From Microphone");
								byte[] sdata = new byte[currAudioData.size()];
								Iterator<Byte> it = currAudioData.iterator();
								int pos = 0;
								while(it.hasNext()){//convert back to byte[] to create swipe
									sdata[pos] = ((Byte)it.next()).byteValue();
									pos++;
								}
								//ByteArrayInputStream bais = new ByteArrayInputStream(sdata);
								//AudioInputStream ais = new AudioInputStream(bais, audiofmt, currAudioData.size()/2);
								currAudioData.clear(); //clear this data get ready for next
								Swipe s = new Swipe(sdata);
								model.addSwipe(s);
//								model.checkPermissions(s); //should we unlock the lock?
							}
						}
					}//trackdetect=1,time bellow 5s.
					
				}else{//not part of a swipe, update the min/max
					if(val > max) max = val;
					if(val < min) min = val;
				}//end mid swipe
			}//end reading current data buffer
			quartersecs++;
			if(quartersecs == 80 && !trackDetected){ //print out a debug msg every 20 seconds
				model.getLog().addMsg("Debug: Min/Max Values Of Noise From Mic "+min+" - "+max+"  Zerolevel Off By "+((max-min)/2 - zerolvl));
				quartersecs = 0;min = 65535; max = -65535;
			}
			for(int i=0;i<buffer.length && i<lastRead.length;i++){ //fill the old buffer
				lastRead[i] = buffer[buffer.length-lastRead.length+i];
			}
		}//end reading from target dataline
	}
	
	/**
	 * Sets whether the microphone should pause listening, or restart listening
	 * @param b true to pause listening, false to restart listening
	 */
	public synchronized void suspendListening(boolean b){
		suspend = b;
		if(!suspend)
			notifyAll();
	}
	/**
	 * Returns the TargetDataLine object
	 * @return current TargetDataLine
	 */
	public AudioRecord getMicIn(){
		return ar;
	}
}