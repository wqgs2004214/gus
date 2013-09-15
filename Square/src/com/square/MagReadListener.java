package com.square;

/**
 * 
 * @author gus
 *
 */
public interface MagReadListener {
	/**
	 * notification user get the stripped binary.
	 * @param bits Returns the stripped binary.
	 */
	public void updateBits(String bits);
	
	/**
	 * notification user get the decode ASCII string.
	 * @param bytes Returns the decoded ASCII string
	 */
	public void updateBytes(String bytes);
}
