package com.example.customerapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMS = 100;
    private static final int REQ_FRONT = 1;
    private static final int REQ_BACK = 2;
    private static final int REQ_BACKUP = 3;
    private static final int REQ_RESTORE = 4;

    private EditText etName, etFather, etMobile, etLocation, etAddress;
    private DBHelper db;
    private LocationManager lm;
    private String lastAutoLocation = "";
    private File frontFile, backFile;

    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location loc) {
            String s = String.format(Locale.US, "%.6f, %.6f", loc.getLatitude(), loc.getLongitude());
            String cur = etLocation.getText().toString();
            // सिर्फ़ तब भरें जब खाना खाली हो या पिछली ऑटो लोकेशन ही हो
            if (cur.isEmpty() || cur.equals(lastAutoLocation)) etLocation.setText(s);
            lastAutoLocation = s;
        }

        @Override public void onStatusChanged(String p, int s, Bundle e) { }
        @Override public void onProviderEnabled(@NonNull String p) { }
        @Override public void onProviderDisabled(@NonNull String p) { }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        etName = findViewById(R.id.etName);
        etFather = findViewById(R.id.etFather);
        etMobile = findViewById(R.id.etMobile);
        etLocation = findViewById(R.id.etLocation);
        etAddress = findViewById(R.id.etAddress);

        db = new DBHelper(this);
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        askPermissions();

        // गोल कैमरा बटन: आधार आगे -> आधार पीछे -> अपने-आप सेव
        findViewById(R.id.btnCamera).setOnClickListener(v -> startAadhaarFlow());

        findViewById(R.id.btnSave).setOnClickListener(v -> saveCustomer());

        findViewById(R.id.btnList).setOnClickListener(v ->
                startActivity(new Intent(this, CustomerListActivity.class)));

        ImageButton btnBackup = findViewById(R.id.btnBackup);
        // एक बार दबाने पर: बैकअप
        btnBackup.setOnClickListener(v -> {
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/zip");
            i.putExtra(Intent.EXTRA_TITLE, "customer_backup_" + stamp + ".zip");
            startActivityForResult(i, REQ_BACKUP);
        });
        // दबाकर रखने पर: रीस्टोर
        btnBackup.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("रीस्टोर")
                    .setMessage("बैकअप से रीस्टोर करने पर अभी का सारा डेटा बदल जाएगा। जारी रखें?")
                    .setPositiveButton("हाँ", (d, w) -> {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("application/zip");
                        startActivityForResult(i, REQ_RESTORE);
                    })
                    .setNegativeButton("नहीं", null)
                    .show();
            return true;
        });
    }

    // ---------- Permissions ----------
    private void askPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CAMERA}, REQ_PERMS);
        } else {
            startLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ_PERMS && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocation();
        }
    }

    // ---------- Location ----------
    private void startLocation() {
        try {
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (lm.isProviderEnabled(provider)) {
                    Location last = lm.getLastKnownLocation(provider);
                    if (last != null) listener.onLocationChanged(last);
                    lm.requestLocationUpdates(provider, 2000, 1, listener);
                }
            }
        } catch (SecurityException ignored) { }
    }

    // ---------- Aadhaar photo flow ----------
    private void startAadhaarFlow() {
        if (!validateForm()) return;
        if (db.isDuplicate(text(etName), text(etFather), text(etMobile))) {
            Toast.makeText(this, "यह कस्टमर पहले से ऐड है!", Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            askPermissions();
            return;
        }
        frontFile = null;
        backFile = null;
        askThen("आधार कार्ड की आगे की (Front) फ़ोटो खींचें", () -> openCamera(REQ_FRONT));
    }

    private void askThen(String msg, Runnable action) {
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("कैमरा खोलें", (d, w) -> action.run())
                .setNegativeButton("रद्द", (d, w) -> {
                    frontFile = null;
                    backFile = null;
                })
                .show();
    }

    private void openCamera(int req) {
        try {
            File dir = getExternalFilesDir("Pictures");
            File f = new File(dir, (req == REQ_FRONT ? "aadhaar_front_" : "aadhaar_back_")
                    + System.currentTimeMillis() + ".jpg");
            if (req == REQ_FRONT) frontFile = f; else backFile = f;

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            startActivityForResult(i, req);
        } catch (Exception e) {
            Toast.makeText(this, "कैमरा नहीं खुल पाया", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);

        if (req == REQ_FRONT) {
            if (res == RESULT_OK && frontFile != null && frontFile.length() > 0) {
                askThen("अब आधार कार्ड की पीछे की (Back) फ़ोटो खींचें", () -> openCamera(REQ_BACK));
            } else {
                frontFile = null;
                Toast.makeText(this, "फ़ोटो रद्द हुई, कस्टमर सेव नहीं हुआ", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (req == REQ_BACK) {
            if (res == RESULT_OK && backFile != null && backFile.length() > 0) {
                saveCustomer();   // दोनों फ़ोटो आ गईं -> सेव
            } else {
                if (frontFile != null) frontFile.delete();
                frontFile = null;
                backFile = null;
                Toast.makeText(this, "फ़ोटो रद्द हुई, कस्टमर सेव नहीं हुआ", Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (res != RESULT_OK || data == null || data.getData() == null) return;
        if (req == REQ_BACKUP) {
            try {
                BackupHelper.backup(this, db, data.getData());
                Toast.makeText(this, "बैकअप हो गया", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "बैकअप फेल: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else if (req == REQ_RESTORE) {
            try {
                BackupHelper.restore(this, db, data.getData());
                Toast.makeText(this, "रीस्टोर हो गया", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "रीस्टोर फेल: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------- Save ----------
    private String text(EditText e) {
        return e.getText().toString().trim();
    }

    private boolean validateForm() {
        if (text(etName).isEmpty()) {
            Toast.makeText(this, "कस्टमर का नाम भरें", Toast.LENGTH_SHORT).show();
            return false;
        }
        String m = text(etMobile);
        if (!m.isEmpty() && m.length() != 10) {
            Toast.makeText(this, "मोबाइल नंबर 10 अंक का होना चाहिए", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void saveCustomer() {
        if (!validateForm()) return;
        if (db.isDuplicate(text(etName), text(etFather), text(etMobile))) {
            Toast.makeText(this, "यह कस्टमर पहले से ऐड है!", Toast.LENGTH_LONG).show();
            return;
        }

        Customer c = new Customer();
        c.name = text(etName);
        c.father = text(etFather);
        c.mobile = text(etMobile);
        c.location = text(etLocation);
        c.address = text(etAddress);
        c.date = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        c.time = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(new Date());
        c.front = frontFile != null ? frontFile.getAbsolutePath() : "";
        c.back = backFile != null ? backFile.getAbsolutePath() : "";

        if (db.insert(c)) {
            Toast.makeText(this, "कस्टमर सेव हो गया", Toast.LENGTH_SHORT).show();
            etName.setText("");
            etFather.setText("");
            etMobile.setText("");
            etAddress.setText("");
            etLocation.setText(lastAutoLocation);
            frontFile = null;
            backFile = null;
        } else {
            Toast.makeText(this, "सेव नहीं हो पाया", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        lm.removeUpdates(listener);
    }
}
