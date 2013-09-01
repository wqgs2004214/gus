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
import java.util.ArrayList;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import android.annotation.SuppressLint;


public class Swipe {
	
	private byte[] bis; //required for mark/reset
	private int bytesperframe; //1=8bit or 2=16bit 
	private int channels; //stereo or mono
	private float samplerate; //samples per second (ex 44100hz)
	private int bitspersample; //bitspersample of the audio stream (8 bit or 16 bit)
	private boolean bitorder; //bit order of the audiostream
	private String aformat;
	private long framelength;
	private short totalnegpeaks = -1, totalpospeaks = -1; //total negative/positive peaks detected
	private short zerolvl; //zero level to shift the thresholds, set in findPeaks
	private short currposthres, currnegthres; //positive and negative thresholds, set in findPeaks
	private ArrayList<int[]> peaks = new ArrayList<int[]>(); //the arraylist that holds peak data
	private String rawbinary = "", strippedbinary = ""; //raw binary decoded from the peaks
	private boolean swipedreverse = false;
	private boolean leadingone = false;
	private byte bitsperchar = 0; //used to determine where the CRC bit is and where to divide the chars (standards are 5 and 7)
	private String lrc;
	private ArrayList<Integer> crcerr = new ArrayList<Integer>();
	private String decodedString = "";
	private ArrayList<Integer> lrcerr= new ArrayList<Integer>();;
	private short leadingzeros = 0;
	private short trailingzeros = 0;
	private short rawlength;
	private ArrayList<Integer> crccorrections = new ArrayList<Integer>();
	private static ErrorLog errorLog = new ErrorLog();
	private Date decodeTime;
	
	/**
	 * Class constructor specifying the sample data
	 * @param ais	The AudioInputStream that contains the sample data
	 */
	public Swipe(byte[] sdata){
		
		bis = new byte[(int) sdata.length];
		this.bis=sdata;
		this.bytesperframe =2;
		this.channels = 1;
		this.samplerate =44100;
		this.bitspersample =16;
		//this.bitorder = ais.getFormat().isBigEndian();
		//this.aformat = (ais.getFormat().getEncoding()).toString();
		this.framelength = sdata.length/2;
	}
	
	/**
	 * The method that does all the processing of this swipe. All other needed methods are called from here. 
	 */
	public void decodeSwipe(){
		decodeTime = new Date(); //set the time when decode was started
		errorLog.addMsg("Info: Decode Time "+getTimestamp());
		peaks = findPeaks();
		if(peaks.size() > 1){ //we found peaks to decode
			rawbinary = dumpRawBinary();
			
			strippedbinary = decodeChars();
			System.out.println("strippedbinary:" + strippedbinary);
			//crcerr = verifyCRC();
			if(bitsperchar == 5){decodedString = decodeABA(strippedbinary, bitsperchar, errorLog);}
			else if(bitsperchar == 7){decodedString = decodeIATA(strippedbinary, bitsperchar, errorLog);}
			System.out.println("ASCII:" + decodedString);
		}
	}
	
	//limitation: we can only read through ais once due to reset()/mark() not supported and BIR requring a buffer length
	/**
	 * Parses the sample data and builds an ArrayList of all the peaks. It also sets the zero level, and positive and negative thresholds.
	 * <p>
	 * The noise thresholds continually get larger and are 20% of the current largest peak. After all peaks are found the ArrayList of peaks
	 * is reparsed and peaks under the larger thresholds are dropped. This allows only one loop through the sample data.
	 * @return	The ArrayList of peaks. Each element contains an int[current frame, sample value, distance to last peak] 
	 */
	private ArrayList<int[]> findPeaks(){
		
		double thresper = .2; //threshold percentage of maximum peak
		currposthres = (bytesperframe == 2) ? (short)400 : (short)2; 
		currnegthres = (short)-currposthres;
		zerolvl = 0; //the current zero level to adjust for peak shifting
		int lastpeakframe = 0; //the last frame # where a peak was detected
		short lastpeakval = 0; //the value last sample where a peak was found
		short lastval = 0; //the value of the last sample
		boolean peakhit=false; //state used to know if we're coming off a peak
		totalnegpeaks = 0; totalpospeaks = 0; //total negative/positive peaks detected
		ArrayList<int[]> apeaks = new ArrayList<int[]>(); //the arraylist that holds peak data

		for(int j=0;j<bis.length;j+=bytesperframe){//loop through reading the audio data
			if(j == 0){ //if this is the first buffer we're reading, try and set the zerolevel and thresholds
				int zerolvlsum = 0; //sum of values so far
				for(int i=0;i<50 && i<bytesperframe*10;i+=bytesperframe){ //only read the first 10 possible points
					short val = 0; //curr frame val
					if(bytesperframe == 2) //is it 16bit or 8bit?
						//dealing with 16bit sample size, loworder is the first byte, highorder is second
						//by shifting high up 8 and stripping low order sign, end up with 16byte short
						val = (short)((((short)bis[i+1])<<8) + ( ((short)bis[i]) & 255));
					else if(bytesperframe == 1)
						//LAME! 8 bit samples are unsigned... java? not so much
						val=(short)(((byte)0x80) ^ bis[i]);
					if(val < currposthres && val > currnegthres){ //if the value is in the noise level
						zerolvlsum += val; //we'll lose percision if we dont keep a running sum
						zerolvl = (short)(zerolvlsum/((i/bytesperframe)+1));
						errorLog.addMsg("Debug: Setting zerolevel frame: "+(i/bytesperframe)+" val "+val+" curr sum "+zerolvlsum+" curr zerolvl "+zerolvl);
					}else{ //stop looping 
						if(i==0){zerolvl = val;} //the zero level is way outside the range, we've either hit a peak and need to stop avging or it's just really high/low and the first frame should be good enough
						errorLog.addMsg("Warning: The noise level is shifted outside the normal range. Setting to "+val);
						break;
					} 
				}
				currposthres += zerolvl; currnegthres += zerolvl; //shift the thresholds by the new zero level
				errorLog.addMsg("Debug: Initial thresholds: "+currposthres+" - "+currnegthres+" zerolevel: "+zerolvl);
			}//end initial zerolevel/threshold setting

			short val = 0;
			if(bytesperframe == 2) //is it 16bit or 8bit?
				//dealing with 16bit sample size, loworder is the first byte, highorder is second
				//by shifting high up 8 and stripping low order sign, end up with 16byte short
				val = (short)( (((short)bis[j+1])<<8) + ( ((short)bis[j]) & 255 ) );
			else if(bytesperframe == 1)
				//LAME! 8 bit samples are unsigned... java? not so much
				val=(short)(((byte)0x80) ^ bis[j]);
			else{//uh it has to be a 8 or 16bit wav 
				errorLog.addMsg("Error: This Recording Did Not Have 8 Or 16 Bit Sample Sizes, Cannot Process");
				return null;
			}
			//System.out.println(val);

			int frame = j/bytesperframe ;// + looped*(buf.length/bytesperframe); //calculate the frame number
			if(val > currposthres && !peakhit && val < lastval){ //high peak found
				//System.out.println("Hig Peak, frame: "+frame+" val: "+lastval+" spacing from last "+(frame-lastpeakframe)+" "+currposthres+" "+currnegthres);//+peakhit);
				apeaks.add(new int[]{(frame),lastval,(frame-lastpeakframe)});
				totalpospeaks++; peakhit = true;
				lastpeakval = lastval;
				if ((((val - zerolvl) * thresper) - zerolvl) > currposthres) {
					currposthres = (short) (((val - zerolvl) * thresper) - zerolvl);
				} // set the new threshold		
			}else if(val < currnegthres && !peakhit && val > lastval){ //low peak found
				//System.out.println("Low Peak, frame: "+frame+" val: "+lastval+" spacing from last "+(frame-lastpeakframe)+" "+currposthres+" "+currnegthres);//+peakhit);
				apeaks.add(new int[]{(frame),lastval,(frame-lastpeakframe)});
				totalnegpeaks++; peakhit = true;
				lastpeakval = lastval;
				if ((zerolvl - ((zerolvl - val) * thresper)) < currnegthres) {
					currnegthres = (short) (zerolvl - ((zerolvl - val) * thresper));
				} // set the new threshold
			}else if(peakhit && lastpeakval > currposthres && val > lastpeakval){ //higher than last high peak, hasnt hit silence threshold yet
				apeaks.add(new int[]{(frame),val,(frame-lastpeakframe)});
				apeaks.remove(apeaks.size()-2); //remove what we thought was the high peak
				lastpeakval = val;
				errorLog.addMsg("Debug: Found Second Higher Peak Before Reaching Noise Level, Frame: "+frame+" Gap: "+(frame-lastpeakframe)+" Val: "+val);
				if ((((val - zerolvl) * thresper) - zerolvl) > currposthres) {
					currposthres = (short) (((val - zerolvl) * thresper) - zerolvl);
				} // set the new threshold	
			}else if(peakhit && lastpeakval < currnegthres && val < lastpeakval ){ //lower than last low peak, hasnt hit silence threshold yet
				apeaks.add(new int[]{(frame),val,(frame-lastpeakframe)});
				apeaks.remove(apeaks.size()-2); //remove what we thought was the low peak
				lastpeakval = val;
				errorLog.addMsg("Debug: Found Second Lower Peak Before Reaching Noise Level, Frame: "+frame+" Gap: "+(frame-lastpeakframe)+" Val: "+val);
				if ((zerolvl - ((zerolvl - val) * thresper)) < currnegthres) {
					currnegthres = (short) (zerolvl - ((zerolvl - val) * thresper));
				} // set the new threshold
			}else if( peakhit && val < currposthres && val > currnegthres){ //back inside the noise level
				peakhit = false;
				lastpeakframe = apeaks.get(apeaks.size()-1)[0];
			}
			lastval=val;
		}//end loop for reading waveform

		//drop the peaks that might have been above the initial noise level, but dont after scanning the entire track.
		errorLog.addMsg("Debug: Threshold Before Dropping Peak Rescan: "+currposthres+" - "+currnegthres+" Zerolevel: "+zerolvl);
		for(int i=0;i<apeaks.size()-1;i++){ //+1 since we aread ahead once
			if(apeaks.get(i)[1] > currnegthres && apeaks.get(i)[1] < currposthres ){ //peak is within the current noise level
				errorLog.addMsg("Info: Dropping Peak: "+i+" At Frame: "+apeaks.get(i)[0]+" Val: "+apeaks.get(i)[1]+" Gap: "+apeaks.get(i)[2]);
				apeaks.get(i+1)[2]+=apeaks.get(i)[2]; //add the gaps together since it wasnt a real peak
				apeaks.remove(i); //drop the peak
				apeaks.trimToSize();
				i--; //have to rescan the current position because everything moved forward
			}
		}
		
		if(apeaks.size() <= 1){
			errorLog.addMsg("Error: No Peaks Detected, Cannot Decode");
			return apeaks;
		}
			
		errorLog.addMsg("Debug: Number Of Peaks: "+apeaks.size()+" Frames Of Peaks: " + ((apeaks.get((apeaks.size()-1))[0])-apeaks.get(0)[0]) + " First Frame: " + apeaks.get(0)[0] + " Last Frame: " + apeaks.get((apeaks.size()-1))[0]);
		if(apeaks.size()<2){
			errorLog.addMsg("Error: Less Than 2 Peaks Found, Cannot Decode");
			return apeaks;
		}
		
		errorLog.addMsg("Debug: Dropping First Peak, Frame: "+apeaks.get(0)[0]+" Val: "+apeaks.get(0)[1]);
		apeaks.remove(0); //drop first peak because it's gap is from the start of the track
		apeaks.trimToSize();
		return apeaks;
	}
	
	
	/**
	 * After the peaks are found, that array can be decoded into binary.
	 * <p>
	 * This is done by analyzing the gap between the peaks. Some more advanced checking is done if the gaps do not fall in the initial range.
	 * @return A string of binary data
	 */
	private String dumpRawBinary(){
		//if we expect a 1 bit, which should have an extra peak in half the normal time
		boolean expectshort = false;
		//binary data that we return
		String binary = "";
		//low / high percent for expected peaks
		double rangeper = .2;
		
		//store the last 5 peak seperation values, limitation: can only correct for length/2 non standard 1's before zeros
		int[] avglasta = new int[5];
		//the oldest data to be overwritten in the array
		int avglastpos = 0, peaksdecoded = 0;		
		
		//this should decode a start 1 bit(up to avglasta.length/2 1 bits)
		for(int i=0;i<avglasta.length;i++)//fill the array with the first value incase we dont have enough peaks to otherwise fill it, that way we wont end up with zeros
			avglasta[i] = peaks.get(0)[2]; //put the first seperation value in the array
		
		for(int i=1;i<avglasta.length && i<peaks.size();i++){
			
			//decode the first n peaks without expected data
			int[] curr = peaks.get(i); //get the current peak information
			double avglast = 0;
			for(int j=0;j<i;j++) //average the number of peaks we've read so far to use as a prediction
				//weight each prediction linearly based on how many we're averaging
				avglast += avglasta[j] * ((double)(j+1))/(i);

			avglast = avglast / (((double)i)/2+.5);
			double range = rangeper*avglast;
			
			//System.out.println("double range "+((avglast*2)+range)+" "+((avglast*2)-range));
			if(avglast+range >= curr[2] && avglast-range <= curr[2]){ //if the current peak is within range of the high peak avg it's a long gap
				avglasta[i] = curr[2]; //add the current spacing to the array
				if(expectshort){ //we had one short peak but now a long, possible library stripe
					errorLog.addMsg("Warning: When calculating the initial average, we were expecting a short gap but detected a long");
				}
				expectshort = false;
			}else if((avglast/2)+range >= curr[2]){ //if the current peak is within half the range of the high peak avg it's a short gap
				if((avglast/2)-range > curr[2]){ //gap below the short range, warn and continue
					errorLog.addMsg("Warning: When calculating the initial average, we found a gap below the short range");
				}
				avglasta[i] = curr[2]*2;
				expectshort = !expectshort; //flip it no matter if this is the first or second short
			}else if((avglast*2)+range >= curr[2] && (avglast*2)-range <= curr[2]){ //crap we might have thought 1's were 0's, if we hit a peak that's twice what we're expecting
				if(i%2 != 0){
					//wii have a problem, short peaks should always come in pairs, perhaps we have a F3F frequency, could also be library cards
					errorLog.addMsg("Warning: When calculating the initial average, possible initial one bits, first zero was found after an odd number of peaks");
				}
				for(int j=0;j<i/2;j++){ //go back through the array and collapse the short gaps together
					avglasta[j] = avglasta[j*2] + avglasta[j*2+1];
				}
				//reset i and add the current peak seperation to the array
				i = i/2;
				avglasta[i] = curr[2];
				errorLog.addMsg("Info: This track started with a 1 bit (non standard)");
			}else if(avglast+range < curr[2]){ //wow this peak is above the max, just put it in the array
				errorLog.addMsg("Warning: When calculating the initial average, we found a gap above high range");
				avglasta[i] = curr[2];
			}else if(avglast-range > curr[2] && (avglast/2)+range < curr[2]){ //we're between peaks, should we go high or low?
				if(avglast-curr[2] < curr[2]-(avglast/2)){ //are we closer to the full gap or half gap?
					avglasta[i] = curr[2];
					errorLog.addMsg("Warning: When calculating the initial average, we found a gap between ranges, closer to high");
				}else{
					errorLog.addMsg("Warning: When calculating the initial average, we found a gap between ranges, closer to low");
					avglasta[i] = curr[2]*2;
					expectshort = !expectshort; //flip it no matter if this is the first or second short
				}
			}else{ //wow we really cant put this in just skip it i guess?
				errorLog.addMsg("Warning: Can't calculate the initial average for peak "+i+", skipping");
			}
			String tmp = "Debug: Initial average prediction, frame: "+curr[0]+" gap: "+curr[2]+" avg gap: "+(((int)((avglast+range)*100))/(float)100)+" - "+(((int)((avglast-range)*100))/(float)100)+" "+(((int)((avglast/2-range)*100))/(float)100)+" - "+(((int)((avglast/2+range)*100))/(float)100)+" array: ";
			for(int k=0;k<avglasta.length;k++)
				tmp+=avglasta[k]+" ";
			errorLog.addMsg(tmp);
		}//decode the first 1 bit
			
		expectshort = false;
		for(int j=0;j<peaks.size();j++){ //loop through each peak that we detected
			//pull out the data for this peak
			int peakframe = peaks.get(j)[0], peakvalue = peaks.get(j)[1], distfromlast = peaks.get(j)[2];
			//find the avg of the last n peaks, weight the array linearly, compensate for where the oldest data is (avglastpos)
			double avglast = 0;
			for(int i=avglastpos;i<avglastpos+avglasta.length;i++){
				avglast = avglast + avglasta[i%avglasta.length] * ((double)(i-avglastpos)+1) / avglasta.length;
			}
			
			avglast = avglast/(((double)avglasta.length)/2 + .5);
			double range = .15*avglast;
			//System.out.println(avglast);
			//System.out.print(j+" sep "+distfromlast+" avg "+avglast+" range "+avglast*lowper+"-"+avglast*highper+" "+avglast/2*lowper+"-"+avglast/2*highper+" old "+avglastpos+" ");
			//for(int i=0;i<avglasta.length;i++)
			//	System.out.print(avglasta[i]+" ");
			//System.out.println();
			//System.out.println("peak: "+(peaksdecoded+1)+" dist from last: "+distfromlast+" avg: "+avglast+" high: "+avglast*.8+"-"+avglast*1.2+"  low: "+(avglast/2)*.8+"-"+(avglast/2)*1.2+" expectshort: "+expectshort);
			
			//if the current peak is within the range of the high peak avg it's a long gap
			if(avglast+range >= distfromlast && avglast-range <= distfromlast){
				//long space, recalc the last 10 avg
				avglasta[avglastpos] = distfromlast;
				binary+="0";
				expectshort = false;
			}else if((avglast/2)+range >= distfromlast && (avglast/2)-range <= distfromlast){
				//second peak of the 1 bit found, add to the binary string
				if(expectshort){
					binary+="1";
					avglasta[avglastpos] = distfromlast + peaks.get(j-1)[2];
				}else
					avglastpos = (avglastpos-1)%avglasta.length; //we're not adding anything to the avglasta yet, dont increment
				
				//(re)set if we are expecting a peak in 1/2 the time
				expectshort = !expectshort;
				
			}else{ //we failed at detecting the peak, above all else we must maintain correct bit clocking or the rest of the swipe is toast.
				//is there a peak "avg" distance from last decoded peak (no? fail) was there a peak between current and curr-avgdist (meaning a 1)? is it double the distance meaning a 0?
				
				errorLog.addMsg("Warning: Peak didn't fit in range, binary decoded: "+binary.length()+" frame: "+peakframe+" val: "+peaks.get(j)[1]+" gap: "+distfromlast+" expecting small gap: "+expectshort+" ranges: "+
						(((int)((avglast-range)*100))/(float)100)+"-"+(((int)((avglast+range)*100))/(float)100)+" "+(((int)(((avglast/2)-range)*100))/(float)100)+"-"+(((int)(((avglast/2)+range)*100))/(float)100));
				//we should have any peak detection errors corrected for us by now, meaning we should have opposite sinage for each peak
				//extended window for detecting peaks
				double erange = .2*avglast;
				if(j == 0){ //cant seek back to decode the first peak, drop if erange fails
					if(avglast+erange >= distfromlast && avglast-erange <= distfromlast){
						errorLog.addMsg("Debug: Corrected bit "+(peaksdecoded+1)+" extending ranges, first peak of track, decoded: 0");
						binary+="0";
						avglasta[avglastpos] = distfromlast;
					}else if((avglast/2)+erange >= distfromlast && (avglast/2)-erange <= distfromlast && !expectshort){ //if it's the first part of a 1
						errorLog.addMsg("Debug: Corrected peak "+(peaksdecoded+1)+" extending ranges, first peak of track, decoded: 1");
						expectshort = !expectshort; //expect a short peak for the next bit
						avglastpos = (avglastpos-1)%avglasta.length; //we're not adding anything to the avglasta yet, dont increment
					}else{
						errorLog.addMsg("Warning: Can't decode first peak, dropping");
					}
				}else if(peaks.size() == j+1){ //we're on the last peak, we cant read ahead
					if(avglast+erange >= distfromlast && avglast-erange <= distfromlast){
						errorLog.addMsg("Debug: Corrected bit "+(peaksdecoded+1)+" extending ranges, last peak of track, decoded: 0");
						binary+="0";
						avglasta[avglastpos] = distfromlast;
					}else if((avglast/2)+erange >= distfromlast && (avglast/2)-erange <= distfromlast && expectshort){ //if it's the first part of a 1
						errorLog.addMsg("Debug: Corrected peak "+(peaksdecoded+1)+" extending ranges, last peak of track, decoded: 1");
						binary+="1";
						avglasta[avglastpos] = distfromlast + peaks.get(j-1)[2];
						expectshort = !expectshort;
					}else{					
						errorLog.addMsg("Warning: Can't decode last peak, dropping");
					}
				}else{
					int lastvalue = peaks.get(j-1)[1], lastdist = peaks.get(j-1)[2];				
					int /*nextvalue = peaks.get(j+1)[1],*/ nextdist = peaks.get(j+1)[2];
					
					if(distfromlast > avglast){ //if the peak was higher than our avg
						//jump 2*avglast, peak there?, peak signage?
						if(expectshort){
							if((lastvalue-zerolvl) * (peakvalue-zerolvl) < 0){ //are the peak's sinage reversed
								//the peaks are reversed but the spacing is more than a bit, could be a 1 and the first peak of a second 1 (missing 2 peaks)
							}else{//peaks arent reversed
								
							}
						}else{
							if((lastvalue-zerolvl) * (peakvalue-zerolvl) < 0){ //double check the two peaks reversed from each other
								if(avglast+erange >= distfromlast && avglast-erange <= distfromlast || //this peak space within erange
										avglast+erange >= nextdist && avglast-erange <= nextdist || //if the next bit is a 0 and signage matches, current could still be a 0 if false, but is probably a 0 if true
										avglasta[(avglastpos+avglasta.length-1)%avglasta.length]+erange >= distfromlast && avglasta[(avglastpos+avglasta.length-1)%avglasta.length]-erange <= distfromlast){ //if spacing is within the range of the last bit
									errorLog.addMsg("Warning: Corrected bit "+(peaksdecoded+1)+" above ranges, first bit, decoded: 0");
									binary+="0";
									avglasta[avglastpos] = distfromlast;
								}
							}
						}
					
					}else if(distfromlast > avglast/2){ //the peak was between our low/high ranges
						//check signage to see if we missed a peak, read ahead to see if it's a 1, else it's a quick 0?
						if(expectshort){ //expecting the second part of a 1 bit
							if((lastvalue-zerolvl) * (peakvalue-zerolvl) < 0){ //double check the two peaks reversed from each other
								if(avglast+erange >= distfromlast+lastdist && avglast-erange <= distfromlast+lastdist || //this peak space + the last is within the avg
										(avglast/2)+erange >= distfromlast && (avglast/2)-erange <= distfromlast || //peak space is within half the avg
										avglasta[(avglastpos+avglasta.length-1)%avglasta.length]+erange >= distfromlast+lastdist && avglasta[(avglastpos+avglasta.length-1)%avglasta.length]-erange <= distfromlast+lastdist || //peak space + last is within the last value in the array
										(avglasta[(avglastpos+avglasta.length-1)%avglasta.length]/2)+erange >= distfromlast && (avglasta[(avglastpos+avglasta.length-1)%avglasta.length]/2)-erange <= distfromlast){
									errorLog.addMsg("Warning: Corrected peak "+(peaksdecoded+1)+" between ranges, second bit, decoded: 0");
									binary+="0";
									avglasta[avglastpos] = distfromlast;
								}else{//didnt detect it in any extended range
									//wow this peak must be between our predictions, but we were expecting a short peak so it's probably a 1
									//double check the next bit and make sure we haven't messed up our bit seperation (wait till there's a 0)
									errorLog.addMsg("Warning: Corrected peak "+(peaksdecoded+1)+" between ranges (even extended), second bit, decoded 1");
									if(avglast-distfromlast < distfromlast-(avglast/2)){ //are we closer to the full gap or half gap?
										errorLog.addMsg("Warning: Corrected peak "+(peaksdecoded+1)+" between ranges, first bit, decoded: 0, based on closest calculation");
										binary+="0";
										avglasta[avglastpos] = distfromlast;
									}else{
										errorLog.addMsg("Warning: Corrected peak "+(peaksdecoded+1)+" between ranges, first bit, decoded: 1, based on closest calculation");
										binary+="1";
										avglasta[avglastpos] = distfromlast + lastdist;
										expectshort = !expectshort;
									}
								}
							}else{
								//uh the peaks were the same sign we must've missed a peak
								//set unknown bit state / look for next zero?
								errorLog.addMsg("Warning: Corrected peak "+(peaksdecoded+1)+" between ranges, second bit, NOT opposite signage, decoded: 1");
								binary+="1";
								avglasta[avglastpos] = distfromlast + lastdist;
								expectshort = !expectshort;
							}
						}else{//not expecting the second part of a 1 bit
							//check if it's a 0
							if(avglast+erange >= distfromlast && avglast-erange <= distfromlast || //this peak space within erange
									avglast+erange >= nextdist && avglast-erange <= nextdist || //if the next bit is a 0 and signage matches, current could still be a 0 if false, but is probably a 0 if true
									avglasta[(avglastpos+avglasta.length-1)%avglasta.length]+erange >= distfromlast && avglasta[(avglastpos+avglasta.length-1)%avglasta.length]-erange <= distfromlast){ //if spacing is within the range of the last bit
								if((lastvalue-zerolvl) * (peakvalue-zerolvl) < 0){
									//double check our bit spacing isnt off
									errorLog.addMsg("Warning: Corrected peak "+(peaksdecoded+1)+" between ranges, first bit, decoded: 0");
									binary+="0";
									avglasta[avglastpos] = distfromlast;
								}else{
									//we detected a 0 but we're missing a peak, must've been a one
									errorLog.addMsg("Warning: Corrected peak "+(peaksdecoded+1)+" between ranges, first bit, NOT opposite signage, decoded: 1");
									binary+="1";
									avglasta[avglastpos] = distfromlast + lastdist;
									expectshort = !expectshort;
								}
							//check if it's a 1
							}else if((avglast/2)+erange >= distfromlast && (avglast/2)-erange <= distfromlast || //this peak space within erange
									avglast+erange >= distfromlast+nextdist && avglast-erange <= distfromlast+nextdist || //check next bit to see if both are in range
									avglast/2+erange >= nextdist && avglast/2-erange <= nextdist || //check next bit to see if it's the second part of a 1
									1.5*avglast+erange >= distfromlast+avglasta[(avglastpos+avglasta.length-1)%avglasta.length] && 1.5*avglast-erange <= distfromlast+avglasta[(avglastpos+avglasta.length-1)%avglasta.length]){ //current gap+last bit within 1.5 of the avg
								errorLog.addMsg("Warning: Corrected peak "+(peaksdecoded+1)+" between ranges, first bit, decoded: 1");
								expectshort = !expectshort; //expect a short peak for the next bit
								avglastpos = (avglastpos-1)%avglasta.length; //we're not adding anything to the avglasta yet, dont increment
							}else{
								//wow this peak must be between our predictions
								//double check the next bit and make sure we haven't messed up our bit seperation, unknown bit state read ahead?
								if(avglast-distfromlast < distfromlast-(avglast/2)){ //are we closer to the full gap or half gap?
									errorLog.addMsg("Warning: Corrected peak "+(peaksdecoded+1)+" between ranges, first bit, decoded: 0, based on closest calculation");
									binary+="0";
									avglasta[avglastpos] = distfromlast;
								}else{
									errorLog.addMsg("Warning: Corrected peak "+(peaksdecoded+1)+" between ranges, first bit, decoded: 1, based on closest calculation");
									expectshort = !expectshort; //expect a short peak for the next bit
									avglastpos = (avglastpos-1)%avglasta.length; //we're not adding anything to the avglasta yet, dont increment
								}
							}	
						}
					}else{ //the peak was below our low range
						if(expectshort){ //expecting the second part of a 1
							if( avglast+erange >= distfromlast+lastdist && avglast-erange <= distfromlast+lastdist || //this peak space + the last is within the avg
									(avglast/2)+erange >= distfromlast && (avglast/2)-erange <= distfromlast || //peak space is within half the avg
									avglasta[(avglastpos+avglasta.length-1)%avglasta.length]+erange >= distfromlast+lastdist && avglasta[(avglastpos+avglasta.length-1)%avglasta.length]-erange <= distfromlast+lastdist || //peak space + last is within the last value in the array
									(avglasta[(avglastpos+avglasta.length-1)%avglasta.length]/2)+erange >= distfromlast && (avglasta[(avglastpos+avglasta.length-1)%avglasta.length]/2)-erange <= distfromlast ){ //peak space is within half the last val
								if((lastvalue-zerolvl) * (peakvalue-zerolvl) < 0){ //double check the two peaks reversed from each other
									errorLog.addMsg("Warning: Corrected peak "+(peaksdecoded+1)+"below ranges, second bit, decoded: 1");
									binary+="1";
									avglasta[avglastpos] = distfromlast + lastdist;
									expectshort = !expectshort;
								}else{
									//uh the peaks were the same sign we must've missed a peak, but it's a shorter peak than expected
									//set unknown bit state / look for next zero?
									errorLog.addMsg("Warning: Corrected peak "+(peaksdecoded+1)+"below ranges, second bit, NOT opposite signage, decoded: 1");
									binary+="1";
									avglasta[avglastpos] = distfromlast + lastdist;
									expectshort = !expectshort;
								}
							}else{
								//wow this peak was really really short, but we were expecting a short peak so it's probably a 1
								//double check the next bit and make sure we haven't messed up our bit seperation (wait till there's a 0)
								errorLog.addMsg("Warning: Corrected peak "+(peaksdecoded+1)+"below ranges(even extended), second bit, decoded: 1");
								binary+="1";
								avglasta[avglastpos] = distfromlast + lastdist;
								expectshort = !expectshort;
							}
						}else{//first peak of a one, that's below the low range
							if( (avglast/2)+erange >= distfromlast && (avglast/2)-erange <= distfromlast || //check current gap within extended range
									1.5*avglast+erange >= distfromlast+avglasta[(avglastpos+avglasta.length-1)%avglasta.length] && 1.5*avglast-erange <= distfromlast+avglasta[(avglastpos+avglasta.length-1)%avglasta.length] || //current gap + last known within 1.5 array val range
									avglast+erange >= distfromlast+nextdist && avglast-erange <= distfromlast+nextdist || //current gap + next gap within range
									(avglast/2)+erange >= nextdist && (avglast/2)-erange <= nextdist ){ //next gap within 1/2 range
								if((lastvalue-zerolvl) * (peakvalue-zerolvl) < 0){ //double check the two peaks reversed from each other
									errorLog.addMsg("Warning: Corrected peak "+(peaksdecoded+1)+" below ranges, first bit, decoded: 0");
									expectshort = !expectshort; //expect a short peak for the next bit
									avglastpos = (avglastpos-1)%avglasta.length; //we're not adding anything to the avglasta yet, dont increment
								}else{
									//uh the peaks were the same sign we must've missed a peak
									//set unknown bit state / look for next zero?
									errorLog.addMsg("Warning: Corrected peak "+(peaksdecoded+1)+" below ranges, first bit, NOT opposite signage, decoded: 1");
									binary+="1";
									avglasta[avglastpos] = distfromlast + lastdist;
									expectshort = !expectshort;
								}			
							}else{
								errorLog.addMsg("Warning: Corrected peak "+(peaksdecoded+1)+" below ranges (even extended), first bit, decoded: 1");
								binary+="1";
								avglasta[avglastpos] = distfromlast + lastdist;
								expectshort = !expectshort;
							}
						}
					}
				}//end zero check
			}//end error detection
			//increment to the oldest position in the array
			avglastpos = (avglastpos+1)%avglasta.length;
			peaksdecoded++;
		}
		rawlength = (short)binary.length();
		return binary;
	}
	
	// find the start sentinel and strip leading bits
	// return the striped binary from SS to LRC
	// sets bitsperchar for further decoding/crc/lrc checking
	/**
	 * Finds the start and stop sentinel of the track for both character sets. Checks if the swipe was swiped backwards.
	 * Also verifies the CRC and LRC, and corrects if possible. Strips a non standard leading one bit if found. 
	 * @return An ASCII decoded string
	 */
	@SuppressLint("UseValueOf")
	private String decodeChars(){
		//strip non standard leading one, no char contains 5 0's with valid crc
		String tempstripped = rawbinary;
		if(tempstripped.length() > 6 && tempstripped.startsWith("1") && tempstripped.substring(1,6).equals("00000")){
			errorLog.addMsg("Info: Chopping Off Nonstandard Leading One");
			tempstripped = tempstripped.substring(1);
			leadingone = true;
		}
		
		int one = tempstripped.indexOf("1"); //first 1 bit, used to ignore leading zeros
		int endsentpos = -1; //end sentinal position
		//TODO must have 0's before start sentinel otherwise when reversing it, can find 5 bit ss in the middle when track is 7 bit
		if(one != -1 && tempstripped.length() >= one+5 && tempstripped.substring(one,one+5).equals("11010")){ //found start sentinel 5 bits per char
			errorLog.addMsg("Info: 5 Bit Start Sentinal Found At Raw Position: "+one);
			tempstripped = tempstripped.substring(one);
			leadingzeros = (short)one;
			bitsperchar = 5;
			//find end sentinal
			do{
				endsentpos = tempstripped.indexOf("11111", endsentpos+1); //cant use lastindexof because that could pick up the lrc, lrc can also be 11111!
			}while(endsentpos%5 != 0 && endsentpos != -1);
			if(endsentpos == -1 ){
				errorLog.addMsg("Warning: No 5 Bit End Sentinal Found");
			}else{
				errorLog.addMsg("Info: 5 Bit End Sentinal Found At Raw Position: "+endsentpos);
			}
		}else if(one != -1 && tempstripped.length() >= one+7 && tempstripped.substring(one,one+7).equals("1010001")){ //found start sentinel 7 bits per char
			errorLog.addMsg("Info: 7 Bit Start Sentinal Found At Raw Position: "+one);
			tempstripped = tempstripped.substring(one);
			leadingzeros = (short)one;
			bitsperchar = 7;
			//find end sentinal
			do{
				endsentpos = tempstripped.indexOf("1111100", endsentpos+1); //cant use lastindexof because that could pick up the lrc
			}while(endsentpos%7 != 0 && endsentpos != -1);
			if(endsentpos == -1 ){
				errorLog.addMsg("Warning: No 7 Bit End Sentinal Found");
			}else{
				errorLog.addMsg("Info: 7 Bit End Sentinal Found At Raw Position: "+endsentpos);
			}
		}else if(one == -1){
			errorLog.addMsg("Error: There Was No '1' Bit Found In The Track. Can't Decode");
			return "";
		}else if(!swipedreverse){ //cant find start sentinel
			//serach in reverse
			if(tempstripped.lastIndexOf("01011") != -1 && tempstripped.indexOf("1",tempstripped.lastIndexOf("01011")+5) == -1 || //if we found the reverse SS and there's no 1's after it (only leading zeros), 5 bits/char
					tempstripped.lastIndexOf("01011") != -1 && tempstripped.endsWith("1") && tempstripped.lastIndexOf("1",tempstripped.length()-1) != -1 || //or we found the reverse and there's a leading 1, and no 1's between SS and leading 1, 5 bits/char
					tempstripped.lastIndexOf("1000101") != -1 && tempstripped.indexOf("1",tempstripped.lastIndexOf("1000101")+7) == -1 || //if we found the reverse SS and there's no 1's after it (only leading zeros), 7 bits/char
					tempstripped.lastIndexOf("1000101") != -1 && tempstripped.endsWith("1") && tempstripped.lastIndexOf("1",tempstripped.length()-1) != -1 ){ //or we found the reverse and there's a leading 1, and no 1's between SS and leading 1, 7 bits/char){ 
				errorLog.addMsg("Info: Probably Swiped The Wrong Way, Reversing To Decode");
				swipedreverse = true;
				rawbinary = (new StringBuffer(tempstripped)).reverse().toString();
				return decodeChars();
			}else{
				leadingzeros = (short) tempstripped.indexOf("1");
				trailingzeros = (short) (tempstripped.length() - tempstripped.lastIndexOf("1")-1);
				errorLog.addMsg("Warning: No Start Sentinel Found, We Can't Decode Any Chars");
				return "";
			}
		}

		//determine if there is a LRC to not truncate
		if(endsentpos != -1 && bitsperchar == 5 && tempstripped.length() >= endsentpos+2*bitsperchar && !tempstripped.substring(endsentpos,endsentpos+bitsperchar).equals("00000")|| //ES found, room for lrc, 5 bits, and not all 0's
				endsentpos != -1 && bitsperchar == 7 && tempstripped.length() >= endsentpos+2*bitsperchar && !tempstripped.substring(endsentpos,endsentpos+bitsperchar).equals("0000000")){ //ES found, room for lrc, 7 bits, and not all 0's
			trailingzeros = (short)(tempstripped.length()-endsentpos-2*bitsperchar); 
			tempstripped = tempstripped.substring(0,endsentpos+2*bitsperchar); //strip 2 chars from the start of the end sent (to keep lrc)
			lrcerr = verifyLRC(tempstripped);
			lrc = tempstripped.substring(endsentpos+bitsperchar,endsentpos+2*bitsperchar);
			tempstripped = tempstripped.substring(0,endsentpos+bitsperchar); //cut off the lrc
			
		}else if(endsentpos != -1){ //strip bits after the ES only if it was found
			errorLog.addMsg("Warning: LRC Not Found");
			tempstripped = tempstripped.substring(0,endsentpos+5);
			trailingzeros = (short)(tempstripped.length()-endsentpos-bitsperchar);
		}else if(endsentpos == -1){
			errorLog.addMsg("Info: Can't Find LRC Without End Sentinel");
		}
		
		if(bitsperchar > 0){ //to verify CRC we needed to find the SS to determine bits/char
			crcerr = verifyCRC(tempstripped);
			if(crcerr.size() == 1 && !lrcerr.contains(new Integer(bitsperchar)) && lrcerr.size() == 1 ){ //CRC errors found, no CRC errors on LRC, LRC errors found  == single bit error
				int chartoreplace = (crcerr.get(0).intValue())*bitsperchar+(lrcerr.get(0).intValue());
				if(chartoreplace >= tempstripped.length() || chartoreplace < 0){ 
					errorLog.addMsg("Error: Tried to CRC correct a bit off the end of the string");
				}else{
					errorLog.addMsg("Warning: Trying To Correct Char "+(crcerr.get(0).intValue())+" Position "+(lrcerr.get(0).intValue())+" Bit "+chartoreplace);
					//errorLog.addMsg("Warning: Trying To Correct Char "+(crcerr.get(0).intValue())+" Position "+(lrcerr.get(0).intValue())+" Bit "+chartoreplace);
					tempstripped = (tempstripped.substring(0,chartoreplace) + //parse the string back together flipping the one middle bit
							((Integer.valueOf(tempstripped.substring(chartoreplace,chartoreplace+1)).byteValue()) ^ ((byte)0x01)) + 
							(chartoreplace == tempstripped.length()-1 ? "" : tempstripped.substring(chartoreplace+1,tempstripped.length())) ); //make sure it wasnt the last bit
					crccorrections.add(new Integer(chartoreplace));
				}
			}else if(crcerr.size() == 1 && lrcerr.size() == 0){ //1 CRC error found, LRC CRC passes, LRC passes = the CRC bit is wrong, cant say this 100%
				errorLog.addMsg("Warning: LRC Passes, CRC Error At Char "+crcerr.get(0)+" Probably Due To CRC Being Wrong");
				int chartoreplace = crcerr.get(0)*bitsperchar+bitsperchar-1;
				tempstripped = (tempstripped.substring(0,chartoreplace) + //parse the string back together flipping the one middle bit
						((Integer.valueOf(tempstripped.substring(chartoreplace,chartoreplace+1)).byteValue()) ^ ((byte)0x01)) + 
						(chartoreplace == tempstripped.length()-1 ? "" : tempstripped.substring(chartoreplace+1,tempstripped.length()))); //make sure it wasnt the last bit
				crccorrections.add(new Integer(chartoreplace));
			}
		}
		return tempstripped;
	}
	
    public static String decodeBin(String binary) {
        Pattern[] encodings = new Pattern[] {
        	//5 bit: possible initial 1, leading zeros, start sentinel, 5 bit characters, end sentinel, possible lrc, trailing zeros
            Pattern.compile("^1?0*(11010)(([01]{5})*)(11111)([01]{5})?0*$"),
            //7 bit: possible initial 1, leading zeros, start sentinel
            Pattern.compile("^1?0*(1010001)(([01]{7})*)(1111100)([01]{7})?0*$"),
        };
        
        String ss = null;
        String payload = null;
        String es = null;
        String lrc = null;

        for(int i = 0; i < encodings.length && ss == null; i++) {
            Matcher m = encodings[i].matcher(binary);

            if(m.matches()) {
                ss = m.group(1);
                payload = m.group(2);
                es = m.group(4);
                lrc = m.group(5);
            }
        }

		String decoded = null;

		if(ss != null) {
	    	if(ss.equals("11010")) {
	    		decoded = decodeABA(ss + payload + es, 5, errorLog);
	    	}else if(ss.equals("1010001")) {
	    		decoded = decodeIATA(ss + payload + es, 7, errorLog);
	    	}
		}

		return decoded;
    }
	
	/**
	 * This is a helper method called by decodeChars() to verify each character's CRC bit.
	 * @param binary the binary string of characters to check the CRC
	 * @return ArrayList of character
	 */
	@SuppressLint("UseValueOf")
	private ArrayList<Integer> verifyCRC(String binary){
		//System.out.println("verifyCRC "+bitsperchar+" "+binary.length()+" "+binary);
		if(binary.length() % bitsperchar != 0){ //we dont have the perfect length of bits for character length
			errorLog.addMsg("Warning: We Have An Odd Amount Of Bits To Verify The CRC. Dropping "+(binary.length()%bitsperchar)+" Bits");
			binary = binary.substring(0,binary.length()-binary.length()%bitsperchar);
		}
		
		ArrayList<Integer> crcerr = new ArrayList<Integer>();
		for(int i=0;i<binary.length();i+=bitsperchar){ //step through each character
			byte par = 0,val = 0;
			for(int j=0;j<bitsperchar-1;j++){ //step through each bit
				par+=Integer.valueOf(binary.substring(i+j,i+j+1));
				val+=Math.pow(2,j)*Integer.valueOf(binary.substring(i+j,i+j+1));
			}
			
			//check the parity
			if((par&1) == Integer.valueOf(binary.substring(i+bitsperchar-1,i+bitsperchar))){
				errorLog.addMsg("Warning: Parity Failure at char: "+(i/bitsperchar)+" "+binary.substring(i,i+bitsperchar));
				crcerr.add(new Integer((i/bitsperchar)));
			}
		}
		return crcerr;
	}
	
	/**
	 * This is a helper method called by decodeChars() to convert binary into ABA standard ASCII. It is static because it is also used by some frontend elements.
	 * @param bin the binary to decode
	 * @param abpc the bits per character
	 * @param el the error log
	 * @return a ASCII string of the decoded binary
	 */
	public static String decodeABA(String bin, int abpc, ErrorLog el){
		//decoded string to return
		String decoded = "";
		for(int i=0;i<bin.length()-abpc+1;i+=abpc){
			int val = 0;
			for(int j=0;j<abpc-1 && j<bin.length();j++){
				val+=Math.pow(2,j)*Integer.valueOf(bin.substring(i+j,i+j+1));
			}
			//decode the value, Shift up by 48 to match ASCII table
			decoded+=(char)(val+48);
		}
		if(decoded.length() > 40 && el != null ){
			el.addMsg("Info: Decoded More Characters Than Is Specified By The Track 2 ANSI/ISO Standards");
		}else if(decoded.length() > 107 && el != null){
			el.addMsg("Info: Decoded More Characters Than Is Specified By The Track 3 ANSI/ISO Standards");
		}
		return decoded;
	}
	
	/**
	 * This is a helper method called by decodeChars() to convert binary into IATA standard ASCII. It is static because it is also used by some frontend elements.
	 * @param bin the binary to decode
	 * @param abpc the bits per character
	 * @param el the error log
	 * @return a ASCII string of the decoded binary
	 */
	public static String decodeIATA(String bin, int abpc, ErrorLog el){
		//decoded string to return
		String decoded = "";
		for(int i=0;i<bin.length()+1-abpc;i+=abpc){
			int val = 0;
			for(int j=0;j<abpc-1 && j<bin.length();j++){
				val+=Math.pow(2,j)*Integer.valueOf(bin.substring(i+j,i+j+1));
			}
			//decode the value, shift up by 32 to match ASCII table
			decoded+=(char)(val+32);
		}
		if(decoded.length() > 79 && el != null){
			el.addMsg("Warning: Decoded More Characters Than Is Specified By The Track 1 ANSI/ISO Standards");
		}
		return decoded;
	}
	
	//binary input string from ss->lrc, bits per character

	/**
	 * Verifies the LRC character after the end sentinel. Records any errors in an ArrayList.
	 * @param binary the cropped binary string from start sentinel to the end of the LRC
	 * @return An ArrayList containing the bit positions of LRC errors
	 */
	@SuppressLint("UseValueOf")
	private ArrayList<Integer> verifyLRC(String binary){
		
		if(binary.length() % bitsperchar != 0){ //we dont have the perfect length of bits for character length
			errorLog.addMsg("Error: We Have An Odd Amount Of Bits To Verify The LRC. Can Not Check LRC");
			binary = binary.substring(0,binary.length()-binary.length()%bitsperchar);
		}else{
			for(int i=0;i<bitsperchar-1;i++){ //step through the i'th bit of each char, not including the crc column
				byte par = 0;
				for(int j=0;j<(binary.length()/bitsperchar)-1;j++){ //step through each bit, but not the lrc
					par+=Integer.valueOf(binary.substring(i+bitsperchar*j,i+bitsperchar*j+1));
				}
				//System.out.println(i+" "+par);
				if((par&1) != Integer.valueOf(binary.substring(binary.length()-bitsperchar+i,binary.length()-bitsperchar+1+i))){
					errorLog.addMsg("Warning: LRC Failure At Bit: "+(i));
					lrcerr.add(new Integer(i));
				}
			}
			//now check the crc on the lrc
			byte par = 0;
			for(int i=binary.length()-bitsperchar;i<binary.length()-1;i++){
				par += Integer.valueOf(binary.substring(i,i+1));
			}
			if((par&1) == Integer.valueOf(binary.length()-1)){
				errorLog.addMsg("Warning: LRC CRC Failure, LRC Corrections Can Not Be Calculated");
				lrcerr.add(new Integer(bitsperchar));
			}
		}
		return lrcerr;
	}
	
	/**
	 * Accessor to determine if the track was swiped in reverse
	 * @return "Yes" if it was in reverse, "No" if it wasn't
	 */
	public String getSwipedReverse(){
		return swipedreverse ? "Yes" : "No";
	}
	/**
	 * Accessor to get the array of CRC errors
	 * @return ArrayList of characters that had CRC errors
	 */
	public ArrayList<Integer> getCRCErrors(){
		return crcerr;
	}
	/**
	 * Accessor to get the array of LRC errors
	 * @return ArrayList of the bit position errors of the LRC
	 */
	public ArrayList<Integer> getLRCErrors(){
		return lrcerr;
	}
	/**
	 * Returns the decoded ASCII string
	 * @return decoded string
	 */
	public String getDecodedString(){
		return decodedString;
	}
	/**
	 * Returns the bits used per character, used to decode the binary into ASCII, and determine which bits are used for CRC
	 * @return the bits used per character
	 */
	public byte getBitsPerChar(){
		return bitsperchar;
	}
	/**
	 * Returns the raw binary that is not cropped or truncated
	 * @return string containing the raw binary
	 */
	public String getRawBinary(){
		return rawbinary;
	}
	/**
	 * Returns the stripped binary. Starts from the start sentinel to the end sentinel
	 * @return binary string cropping leading/trailing zeros
	 */
	public String getStrippedBinary(){
		return strippedbinary;
	}
	/**
	 * Returns the total number of positive peaks found in this track
	 * @return positive peaks in this track
	 */
	public short getTotalPosPeaks(){
		return totalpospeaks;
	}
	/**
	 * Returns the total number of negative peaks found in this track
	 * @return negative peaks in this track
	 */
	public short getTotalNegPeaks(){
		return totalnegpeaks;
	}
	/**
	 * Returns the calculated zero level of this track, used to shift the data before detecting peaks
	 * @return the zero level
	 */
	public short getZeroLevel(){
		return zerolvl;
	}
	/**
	 * Returns a string of the LRC in binary, including the crc bit
	 * @return the LRC
	 */
	public String getLRC(){
		return lrc;
	}
	/**
	 * Returns a yes/no string depending on if we found a non standard leading one bit.
	 * @return "Yes" if there was a leading one bit, "No" otherwise
	 */
	public String getLeadingOne(){
		return (leadingone ? "Yes" : "No");
	}
	/**
	 * Returns the number of leading zeros before the start sentinel
	 * @return the number of leading zeros
	 */
	public short getLeadingZeros(){
		return leadingzeros;
	}
	/**
	 * Returns the number of trailing zeros after the LRC, or end sentinel if there is no LRC
	 * @return the number of trailing zeros
	 */
	public short getTrailingZeros(){
		return trailingzeros;
	}
	/**
	 * Returns the length of the stripped binary
	 * @return length of the stripped binary
	 */
	public short getStrippedLength(){
		return (short)strippedbinary.length();
	}
	/**
	 * Returns the length of the raw binary
	 * @return length of the raw binary
	 */
	public short getRawLength(){
		return rawlength;
	}
	/**
	 * Returns the error log object for this swipe, containing all the error/warning/info/debug messages
	 * @return error log object for this swipe
	 */
	public ErrorLog getErrorLog(){
		return errorLog;
	}
	/**
	 * Returns the string of timestamp of when the swipe was decoded. uses format month/day/year(2 digit) hour:minute:second.milisecond
	 * @return a string of the timestamp when the decoding was done
	 */
	@SuppressLint("SimpleDateFormat")
	public String getTimestamp(){
		SimpleDateFormat sdf = new SimpleDateFormat("M/d/yy HH:mm:ss.SSS ");
		return sdf.format(decodeTime);
	}
	/**
	 * Returns a string representation of the number of channels in this swipe's recording
	 * @return "Mono" for one channel, "Stereo" otherwise
	 */
	public String getChannels(){
		return channels == 1 ? "Mono" : "Stereo";
	}
	/**
	 * Returns the string representation of the format used for this audio stream
	 * @return the audio format
	 */
	public String getFormat(){
		return aformat;
	}
	/**
	 * Returns the sample rate of this audio stream
	 * @return the sample rate
	 */
	public float getSampleRate(){
		return samplerate;
	}
	/**
	 * Returns the frame length of this audio stream
	 * @return the frame length
	 */
	public long getFrameLength(){
		return framelength;
	}
	/**
	 * Returns the number of bits used for each sample
	 * @return bits per sample
	 */
	public int getBitsPerSample(){
		return bitspersample;
	}
	/**
	 * Returns the bit order of the audio stream
	 * @return "Big Endian" or "Little Endian"
	 */
	public String getBitOrder(){
		return (bitorder) ? "Big Endian" : "Little Endian";
	}
	/**
	 * Returns the current positive noise threshold used to find peaks
	 * @return positive threshold
	 */
	public short getPosThres(){
		return currposthres;
	}
	/**
	 * Returns the current negative noise threshold used to find peaks
	 * @return negative threshold
	 */
	public short getNegThres(){
		return currnegthres;
	}
	/**
	 * Returns the audio stream in a byte array
	 * @return byte array of the audio samples
	 */
	public byte[] getStream(){
		return bis;
	}
	/**
	 * Returns the format object of this audio stream
	 * @return the AudioFormat object of this stream
	 */
	//public AudioFormat getRawFormat(){
	//	return ais.getFormat();
	//}
}