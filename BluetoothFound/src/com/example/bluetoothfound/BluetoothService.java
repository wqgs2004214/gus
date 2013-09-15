package com.example.bluetoothfound;

import java.util.List;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
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
	// ringtone player
	private MediaPlayer mPlayer;
	private BluetoothAdapter mBluetoothAdapter;
	private AudioManager mAudioManager;
	private Vibrator mVibrator;
	private SharedPreferences mSharedPreferences;
	private BluetoothHeadset mBluetoothHeadset;
	@Override
	public void onCreate() {
		super.onCreate();
		
		mPlayer = new MediaPlayer();
		mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		mVibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
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
		registerReceiver(stopPlayRingtoneReceiver, playRingtoneFilter);
		getProfileProxy();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// Make sure we're not doing discovery anymore
		closeProfileProxy();
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
		unregisterReceiver(stopPlayRingtoneReceiver);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	/**
	 * play setting ringtone
	 */
	private void playRingtone() {
		stopPlayRingtone();
		String pickRingtoneUrl = mSharedPreferences.getString("ringtoneUri", "");
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
	
	
	private void closeProfileProxy() {
		mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
	}
	
	private void getProfileProxy() {
		
		mBluetoothAdapter.getProfileProxy(this, new ServiceListener() {

			@Override
			public void onServiceDisconnected(int profile) {
				if (profile == BluetoothProfile.HEADSET) {
					mBluetoothHeadset = null;
				}
			}

			@Override
			public void onServiceConnected(int profile, BluetoothProfile proxy) {
				if (profile == BluetoothProfile.HEADSET) {
					boolean isTGKConnected = false;
					mBluetoothHeadset = (BluetoothHeadset) proxy;
					List<BluetoothDevice> devices = mBluetoothHeadset
							.getConnectedDevices();
					for (final BluetoothDevice dev : devices) {
						String deviceName = dev.getName();
						if (deviceName.equals(BluetoothService.DEVICE_NAME)) {
							sendTextUpdateBroadcast("TGK设备已连接:"
										+ dev.getName());
							isTGKConnected = true;
						}
					}
					if (!isTGKConnected) {
						sendTextUpdateBroadcast("TGK设备已断开连接!");
						playRingtone();
					}

				}
			}
		}, BluetoothProfile.HEADSET);
	}
	
	
	private void sendTextUpdateBroadcast(String text) {
		Intent uiIntent = new Intent();
		uiIntent.setAction(BluetoothFoundActivity.ACTION_UI_UPDATE);
		uiIntent.putExtra(BluetoothFoundActivity.UPDATE_STRING, text);
		sendBroadcast(uiIntent);
	}
	
	/**
	 *  stop ringtone broadcast receiver
	 */
	private final BroadcastReceiver stopPlayRingtoneReceiver = new BroadcastReceiver() {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			stopPlayRingtone();
			stopSelf();
		}
	};
	
}
