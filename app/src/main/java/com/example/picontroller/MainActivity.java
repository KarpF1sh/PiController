package com.example.picontroller;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.webkit.ConsoleMessage.MessageLevel.LOG;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // Target device name
    private static final String TARGET_DEVICE = "DESKTOP-G207V2K";

    // Permission id
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PICK_PHOTO_REQUEST = 1;
    BluetoothDevice targetDevice = null;

    // For timed function
    private Handler handler;
    private Runnable runnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d("Main", "started");

        // Reciever for discovery
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

        // Get by id
        final Button button = findViewById(R.id.sendButton);
        //final Button imageButton = findViewById(R.id.button2);
        final TextView textInput = findViewById(R.id.tagTextInput);
        final ImageView statusIcon = findViewById(R.id.indicator);
        //final ImageView previewImage = findViewById(R.id.imageView2);

        /*
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPhotoPicker();
            }
        });
        */

        // Test and ask permissions
        if ((ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissionLauncher.launch( Manifest.permission.BLUETOOTH_SCAN );
        }

        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch( Manifest.permission.BLUETOOTH_CONNECT );
        }

        // TODO: Better denying permission checking. Currently the app crashes if the user doesn't allow nearby permission
        // IkIK I'm baaaaaaad and too lazy to fix this rn

        // Get device Bluetooth manager
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            // Bluetooth is not supported on this device
            Log.e("BLUETOOTH", "Bluetooth not supported");
            Toast.makeText(getApplicationContext(), "Bluetooth not supported", Toast.LENGTH_SHORT).show();

        } else if (!bluetoothAdapter.isEnabled()) {
            // Check if bluetooth is on
            Log.w("BLUETOOTH", "Bluetooth not enabled");
            //Toast.makeText(getApplicationContext(), "Bluetooth not enabled", Toast.LENGTH_LONG).show();

            // Bluetooth is not enabled, prompt the user to enable it
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // Create a handler
        handler = new Handler();

        // Create a runnable to define the task you want to execute
        runnable = new Runnable() {
            @Override
            @SuppressLint("MissingPermission")
            public void run() {

                // Perform the desired callback action
                // Find the target device by name
                Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

                for (BluetoothDevice device : pairedDevices) {
                    if (device.getName().equals(TARGET_DEVICE)) {
                        //Log.d("BLUETOOTH", "Found device: " + device.getName());

                        // Enable the button
                        button.setEnabled(true);

                        targetDevice = device;
                        break;
                    } else {

                        Log.d("BLUETOOTH", "Discovery started");
                        // Start Bluetooth discovery
                        bluetoothAdapter.startDiscovery();

                        // Disable the button
                        button.setEnabled(false);
                        // Set indicator color
                        statusIcon.setColorFilter(Color.argb(0, 0, 0, 0));

                    }
                }

                // Schedule the next execution after 2 seconds
                handler.postDelayed(this, 2000);
            }
        };

        // Start the timer with a delay of 2 seconds
        handler.postDelayed(runnable, 2000);

        button.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("MissingPermission")
            public void onClick(View v) {
                Log.d("BUTTON", "Button clicked");

                // Defaults
                BluetoothSocket socket = null;
                OutputStream outputStream;

                // Get string from text field
                String message = textInput.getText().toString();

                // Making the connection
                if (targetDevice != null) {
                    // Get device MAC
                    //String deviceAddress = targetDevice.getAddress();

                    //UUID MY_UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb");
                    UUID MY_UUID = UUID.randomUUID();

                    // Try connecting
                    try {
                        socket = targetDevice.createRfcommSocketToServiceRecord(MY_UUID);

                        // This will fail most likely due to a bug/weird behaviour
                        // On older android devices the bluetooth port gets assigned to -1 but that will break the connection on modern devices
                        socket.connect();

                        Log.d("BLUETOOTH", "Connected");

                        // Enable the button
                        button.setEnabled(true);
                        // Set indicator color
                        statusIcon.setColorFilter(Color.argb(200, 0, 255, 0));

                    } catch (IOException e) {
                        Log.e("BLUETOOTH", e.getMessage());

                        // So we use this fallback method
                        try {
                            Log.w("BLUETOOTH", "trying fallback...");

                            // Hacky way to set the mPort to 1
                            socket = (BluetoothSocket) targetDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(targetDevice, 1);
                            socket.connect();

                            Log.d("BLUETOOTH", "Connected");

                            // Set indicator color
                            statusIcon.setColorFilter(Color.argb(200, 0, 255, 0));

                        } catch (Exception e2) {
                            // Everything else failed for some reason
                            Log.e("BLUETOOTH", "Couldn't establish Bluetooth connection!");

                            // Set indicator color
                            statusIcon.setColorFilter(Color.argb(0, 0, 0, 0));
                        }
                    }
                } else {
                    Log.w("BLUETOOTH", "Bluetooth device not found nor connected");
                    // The target device with name "pidisplay" is not paired or not found
                }

                // Sending the message
                if (socket != null) {
                    try {
                        Log.d("BLUETOOTH", "Getting socket stream");
                        outputStream = socket.getOutputStream();

                        Log.d("BLUETOOTH", "Sending message: " + message);
                        outputStream.write(message.getBytes());

                        Log.d("BLUETOOTH", "Closing socket stream");
                        outputStream.close();

                        // Disconnecting so other devices can use this as well
                        Log.d("BLUETOOTH", "Closing socket");
                        socket.close();

                        Toast.makeText(getApplicationContext(), "Sent", Toast.LENGTH_SHORT).show();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } else {
                    Log.w("BLUETOOTH", "Failed to send, not connected");
                    Toast.makeText(getApplicationContext(), "Out of range or not connected", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    // Create a BroadcastReceiver for device discovery.
    @SuppressLint("MissingPermission")
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();

                Log.d("BLUETOOTH", "Device found " + deviceName);

                // Store the name
                if (deviceName != null && deviceName.equals(TARGET_DEVICE)) {
                    // Found the device with the specific name, initiate the pairing process
                    Log.d("BLUETOOTH", "Target device found");

                    // Bond with the device
                    device.createBond();

                    // Cancel discovery to stop discovering new devices
                    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.cancelDiscovery();
                    }

                    ImageView statusIcon = findViewById(R.id.indicator);
                    // Set indicator color
                    statusIcon.setColorFilter(Color.argb(200, 0, 255, 0));
                }
            }
        }
    };

    /*

    private void openPhotoPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_PHOTO_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_PHOTO_REQUEST && resultCode == RESULT_OK && data != null) {
            // Handle the selected photo here
            // You can get the photo URI using data.getData() method
            // Get the selected photo URI

            Uri selectedPhotoUri = data.getData();

            // Set the selected photo to the ImageView
            ImageView imageView = findViewById(R.id.imageView2);
            imageView.setImageURI(selectedPhotoUri);
        }
    }

    */

    // Permission callback
    private ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        if (isGranted) {
            // Permission is granted. Continue the action or workflow in your
            // Restart app
            restart();
        }
    });

    // Restart method
    public void restart(){
        Intent intent = new Intent(this, MainActivity.class);
        this.startActivity(intent);
        this.finishAffinity();
    }

    // Cleanup
    @Override
    protected void onDestroy() {
        super.onDestroy();

        handler.removeCallbacks(runnable);
        unregisterReceiver(receiver);
    }
}

