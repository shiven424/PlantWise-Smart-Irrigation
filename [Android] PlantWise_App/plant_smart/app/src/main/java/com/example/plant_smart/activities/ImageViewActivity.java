package com.example.plant_smart.activities;

import android.graphics.Bitmap; // Import Bitmap
import android.graphics.BitmapFactory; // Import BitmapFactory
import android.os.Bundle;
import android.util.Base64; // Import Base64
import android.util.Log; // Import Log
import android.view.MenuItem; // Import MenuItem
import android.widget.ImageView; // Import ImageView
import android.widget.Toast; // Import Toast
import android.content.Intent; // Import Intent

import androidx.annotation.NonNull; // Keep if needed
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Import Toolbar

import com.example.plant_smart.R; // Import R

import java.io.BufferedReader; // Import BufferedReader
import java.io.File; // Import File
import java.io.FileReader; // Import FileReader
import java.io.IOException; // Import IOException
import java.io.UnsupportedEncodingException; // Import UnsupportedEncodingException


public class ImageViewActivity extends AppCompatActivity {

    private static final String TAG = "ImageViewActivity";
    public static final String EXTRA_IMAGE_FILE_PATH = "com.example.plant_smart.IMAGE_FILE_PATH"; // Define a constant for the Intent extra key

    private ImageView imageViewFull; // ImageView to display the full image

    // Store the file path passed via Intent
    private String imageFilePath = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_view); // Ensure this matches your layout file name

        Log.d(TAG, "ImageViewActivity onCreate"); // Log onCreate

        // Set up the toolbar
        Toolbar toolbar = findViewById(R.id.toolbar); // Assuming your layout includes a Toolbar with ID 'toolbar'
        setSupportActionBar(toolbar);
        // Enable the Up button (back arrow)
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Plant Image"); // Set toolbar title
        }

        // Find the ImageView in the layout
        imageViewFull = findViewById(R.id.imageViewFull); // Assuming your layout includes an ImageView with ID 'imageViewFull'


        // Get the image file path from the Intent extras
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_IMAGE_FILE_PATH)) {
            imageFilePath = intent.getStringExtra(EXTRA_IMAGE_FILE_PATH);
            Log.d(TAG, "Received image file path: " + imageFilePath); // Log the received path
            displayImageFromFile(imageFilePath); // Display the image
        } else {
            Log.e(TAG, "No image file path received in Intent extras."); // Log error if no path
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
            // Optionally finish the activity if no image to display
            // finish();
        }
    }

    // Handle the Up button (back arrow) click in the Toolbar
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed(); // Go back when the Up button is clicked
        return true;
    }

    // Method to read the base64 data from the file, decode it, and display the image
    private void displayImageFromFile(String filePath) {
        Bitmap imageBitmap = null;
        String b64 = null;
        BufferedReader reader = null;
        File imageFile = new File(filePath);

        if (!imageFile.exists()) {
            Log.e(TAG, "Image file does not exist at path: " + filePath);
            Toast.makeText(this, "Error loading image file", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Read the base64 string from the temporary file
            reader = new BufferedReader(new FileReader(imageFile));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            b64 = stringBuilder.toString();
            Log.d(TAG, "Read base64 string from file. Length: " + b64.length());

            // Decode the base64 image data
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            imageBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

            if (imageBitmap != null) {
                Log.d(TAG, "Image decoded successfully from file. Displaying."); // Log successful decoding
                imageViewFull.setImageBitmap(imageBitmap); // Set the bitmap to the ImageView
            } else {
                Log.e(TAG, "Failed to decode image data from file."); // Log decoding failure
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }

        } catch (IOException e) {
            Log.e(TAG, "Error reading image file", e); // Log file reading error
            Toast.makeText(this, "Error reading image data", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error decoding base64 string from file", e); // Log decoding error
            Toast.makeText(this, "Error decoding image data", Toast.LENGTH_SHORT).show();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing file reader", e); // Log closing error
                }
            }
        }
        // Note: The temporary file is cleaned up in onDestroy of DashboardActivity
        // and also in onRequestPermissionsResult if permission is denied.
        // Consider adding cleanup here as well if the image is no longer needed after display.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ImageViewActivity onDestroy"); // Log onDestroy
        // You might add cleanup logic here if the file is only needed while this activity is open
        // However, the current cleanup is handled in DashboardActivity and onRequestPermissionsResult.
    }
}
