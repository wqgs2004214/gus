package com.example.bluetoothfound;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.os.Vibrator;

public class BluetoothService extends Service {
	private static final String DEVICE_NAME = "123";
	public static final String ACTION_STOP_PLAY_RINGTONE = "stopPlayringtoneAction";
	// discovery thread
	private static final Executor disCoveryThread = Executors
			.newCachedThreadPool();
	// ringtone player
	private MediaPlayer mPlayer;

	private boolean isDiscovery = false;
	private List<String> deviceList = new ArrayList<String>();
	private BluetoothAdapter mBluetoothAdapter;
	private AudioManager mAudioManager;
	private Vibrator mVibrator;
	private SharedPreferences mSharedPreferences;
	@Override
	public void onCreate() {
		super.onCreate();
		mPlayer = new MediaPlayer();
		mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		mVibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		mSharedPreferences = getSharedPreferences(
				AlarmSettingActivity.SETTING, Context.MODE_PRIVATE);
		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		registerReceiver(mReceiver, filter);
		// connect status
		if (mBluetoothAdapter.isDiscovering()) {
			mBluetoothAdapter.cancelDiscovery();
		}
		
		//register music boradcast
		IntentFilter playRingtoneFilter = new IntentFilter();
		playRingtoneFilter.addAction(ACTION_STOP_PLAY_RINGTONE);
		registerReceiver(stopPlayRingtoneReceiver, playRingtoneFilter);
		
		// discovery is running.
		isDiscovery = true;
		disCoveryThread.execute(new Runnable() {

			@Override
			public void run() {
				while (isDiscovery) {
					// Request discover from BluetoothAdapter
					mBluetoothAdapter.startDiscovery();
					try {
						Thread.sleep(12 * 1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					boolean isFound = false;
					while (true) {
						if (!mBluetoothAdapter.isDiscovering()) {
							for (String deviceName : deviceList) {
								if (deviceName != null
										&& deviceName.equals(DEVICE_NAME)) {
									isFound = true;
								}
							}
							break;
						}
					}
					if (!isFound && isDiscovery) {
						// play ringtone
						Notifier notifier = new Notifier(BluetoothService.this);
			            notifier.notify(isDiscovery, "通知","未发现设备");
						playRingtone();
					} else if(isDiscovery){
						stopPlayRingtone();
					}
					// clear bluetooth device
					deviceList.clear();
				}
			}
		});
		
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// Make sure we're not doing discovery anymore
		if (mBluetoothAdapter != null) {
			mBluetoothAdapter.cancelDiscovery();
		}
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
		int ringerMode = mSharedPreferences.getInt("ringerMode", AlarmSettingActivity.RINGTONE_ENABLE);
		if (mAudioManager.getStreamVolume(AudioManager.STREAM_ALARM) != 0) {
			if (ringerMode == AlarmSettingActivity.VIBRATE_ENABLE
					|| ringerMode == AlarmSettingActivity.VIBRATE_RINGTONE_ENABLE) {
				long[] pattern = { 500, 200 };
				mVibrator.vibrate(pattern, 0);
			}
			if (ringerMode != AlarmSettingActivity.VIBRATE_ENABLE) {
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
			mVibrator.cancel();
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}

	// found device broadcast receiver
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			// When discovery finds a device
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				// Get the BluetoothDevice object from the Intent
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				// System.out.println("found devices:" + device.getName());
				short rssi = intent.getExtras().getShort(
						BluetoothDevice.EXTRA_RSSI);
				String deviceName = device.getName();
				// If it's already paired, skip it, because it's been listed
				// already
				if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
					deviceList.add(deviceName);
				}
				//send to BluetoothFoundActivity
				if (deviceName.equals(DEVICE_NAME)) {
					Intent uiIntent = new Intent();
					uiIntent.setAction(BluetoothFoundActivity.ACTION_UI_UPDATE);
					uiIntent.putExtra(BluetoothFoundActivity.UPDATE_STRING, "发现设备:" + deviceName +", rssi:"+ rssi);
					sendBroadcast(uiIntent);
				}
				// When discovery is finished, change the Activity title
			}
		}
	};
	
	/**
	 *  stop ringtone broadcast receiver
	 */
	private final BroadcastReceiver stopPlayRingtoneReceiver = new BroadcastReceiver() {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			isDiscovery = false;
			stopPlayRingtone();
			stopSelf();
		}
	};
	
}
