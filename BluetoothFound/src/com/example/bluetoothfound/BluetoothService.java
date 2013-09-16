package com.example.bluetoothfound;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;

public class BluetoothService extends Service {
	public static final String DEVICE_NAME = "IS96-0815-68";
	public static final String ACTION_STOP_PLAY_RINGTONE = "stopPlayringtoneAction";
	public static final String ACTION_START_PLAY_RINGTONE = "startPlayingtoneAction";
	// ringtone player
	private MediaPlayer mPlayer;
	private AudioManager mAudioManager;
	private Vibrator mVibrator;
	private SharedPreferences mSharedPreferences;
	private final Timer stopPlayRingtoneTimer = new Timer();
	private TimerTask stopPlayRingtoneTask;
	@Override
	public void onCreate() {
		super.onCreate();
		
		mPlayer = new MediaPlayer();
		mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		mVibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
		mSharedPreferences = getSharedPreferences(
				BluetoothFoundActivity.SETTING, Context.MODE_PRIVATE);
		
		//register bluetooth Headset boradcast
		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
		filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
		registerReceiver(mReceiver, filter);
		
		//register music boradcast
		IntentFilter playRingtoneFilter = new IntentFilter();
		playRingtoneFilter.addAction(ACTION_STOP_PLAY_RINGTONE);
		playRingtoneFilter.addAction(ACTION_START_PLAY_RINGTONE);
		registerReceiver(playRingtoneReceiver, playRingtoneFilter);
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// Make sure we're not doing discovery anymore
		if (mPlayer != null && mPlayer.isPlaying()) {
			mPlayer.stop();
			mPlayer.reset();
		}
		if (mPlayer != null) {
			mPlayer.release();
		}
		mVibrator.cancel();
		// Unregister broadcast listeners
		unregisterReceiver(mReceiver);
		unregisterReceiver(playRingtoneReceiver);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	/**
	 * play setting ringtone
	 */
	private void playRingtone() {
		if (stopPlayRingtoneTask != null) {
			stopPlayRingtoneTask.cancel();
			stopPlayRingtoneTimer.purge();
		}
		stopPlayRingtone();
		String pickRingtoneUrl = mSharedPreferences.getString("ringtoneUri", "");
		int durationValue = mSharedPreferences.getInt(BluetoothFoundActivity.SETTING_DURATION, 5);
		int ringerMode = mSharedPreferences.getInt("ringerMode", BluetoothFoundActivity.RINGTONE_ENABLE);
		if (ringerMode == BluetoothFoundActivity.VIBRATE_ENABLE
				|| ringerMode == BluetoothFoundActivity.VIBRATE_RINGTONE_ENABLE) {
			long[] pattern = { 500, 200 };
			mVibrator.vibrate(pattern, 0);
		}
		if (mAudioManager.getStreamVolume(AudioManager.STREAM_ALARM) != 0) {
			if (ringerMode != BluetoothFoundActivity.VIBRATE_ENABLE) {
				try {
					Uri pickUri = Uri.parse(pickRingtoneUrl);
					mPlayer.reset();
					mPlayer.setDataSource(this, pickUri);
					mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
					mPlayer.setLooping(true);
					mPlayer.prepare();
				} catch (Exception e) {
					return;
				}
				mPlayer.start();
			}
		}
		
		/**
		 * stop play ringtone task.
		 */
		stopPlayRingtoneTask = new TimerTask() {
			
			@Override
			public void run() {
				stopPlayRingtone();
			}
		};
		
		stopPlayRingtoneTimer.schedule(stopPlayRingtoneTask, durationValue * 1000);
	}

	/**
	 * stop play ringtone
	 */
	private void stopPlayRingtone() {
		if (mPlayer != null && mPlayer.isPlaying()) {
			try {
			mPlayer.stop();
			mPlayer.reset();
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		mVibrator.cancel();
	}

	// found device broadcast receiver
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			
			//when bluetooth headset state changed
			if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
				Bundle b = intent.getExtras();
				int currentState = b.getInt(BluetoothProfile.EXTRA_STATE);
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				String deviceName = device.getName();
				//int prevState = b.getInt(BluetoothProfile.EXTRA_PREVIOUS_STATE);
				if (currentState == BluetoothProfile.STATE_CONNECTED) {
					if (deviceName.equals(DEVICE_NAME)) {
						sendTextUpdateBroadcast("发现TGK设备:" + deviceName);
						stopPlayRingtone();
					}
				} else if (currentState == BluetoothProfile.STATE_DISCONNECTED) {
					sendTextUpdateBroadcast("TGK设备已断开连接!");
					Notifier notifier = new Notifier(BluetoothService.this);
		            notifier.notify(true, "通知","TGK设备已断开连接!");
					playRingtone();
				}
			}
			
			// When discovery finds a device
//			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
//				// Get the BluetoothDevice object from the Intent
//				BluetoothDevice device = intent
//						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//				// System.out.println("found devices:" + device.getName());
//				short rssi = intent.getExtras().getShort(
//						BluetoothDevice.EXTRA_RSSI);
//				String deviceName = device.getName();
//				// If it's already paired, skip it, because it's been listed
//				// already
//				if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
//					deviceList.add(deviceName);
//				}
//				//send to BluetoothFoundActivity
//				if (deviceName.equals(DEVICE_NAME)) {
//					Intent uiIntent = new Intent();
//					uiIntent.setAction(BluetoothFoundActivity.ACTION_UI_UPDATE);
//					uiIntent.putExtra(BluetoothFoundActivity.UPDATE_STRING, "发现设备:" + deviceName +", rssi:"+ rssi);
//					sendBroadcast(uiIntent);
//				}
//				// When discovery is finished, change the Activity title
//			}
		}
	};
	
	
	private void sendTextUpdateBroadcast(String text) {
		Intent uiIntent = new Intent();
		uiIntent.setAction(BluetoothFoundActivity.ACTION_UI_UPDATE);
		uiIntent.putExtra(BluetoothFoundActivity.UPDATE_STRING, text);
		sendBroadcast(uiIntent);
	}
	
	/**
	 *  stop ringtone broadcast receiver
	 */
	private final BroadcastReceiver playRingtoneReceiver = new BroadcastReceiver() {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(ACTION_STOP_PLAY_RINGTONE)) {
				stopPlayRingtone();
				stopSelf();
			} else if (action.equals(ACTION_START_PLAY_RINGTONE)) {
				playRingtone();
			}
			
		}
	};
	
}
