package com.example.customerapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CustomerListActivity extends AppCompatActivity {

    private DBHelper db;
    private EditText etSearch;
    private TextView tvCount;
    private ListView listView;
    private CustomerAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_list);

        db = new DBHelper(this);
        etSearch = findViewById(R.id.etSearch);
        tvCount = findViewById(R.id.tvCount);
        listView = findViewById(R.id.listView);

        adapter = new CustomerAdapter(new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((p, v, pos, id) -> showEdit(adapter.getItem(pos)));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        refresh();
    }

    private void refresh() {
        String q = etSearch.getText().toString().trim();
        List<Customer> list = db.search(q);
        adapter.clear();
        adapter.addAll(list);
        adapter.notifyDataSetChanged();
        tvCount.setText((q.isEmpty() ? "कुल कस्टमर: " : "मिले कस्टमर: ") + list.size());
    }

    // ---------- View / Edit ----------
    private void showEdit(Customer c) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_edit, null);
        EditText eName = v.findViewById(R.id.eName);
        EditText eFather = v.findViewById(R.id.eFather);
        EditText eMobile = v.findViewById(R.id.eMobile);
        EditText eLocation = v.findViewById(R.id.eLocation);
        EditText eAddress = v.findViewById(R.id.eAddress);
        TextView tvDate = v.findViewById(R.id.tvDate);
        ImageView ivFront = v.findViewById(R.id.ivFront);
        ImageView ivBack = v.findViewById(R.id.ivBack);

        eName.setText(c.name);
        eFather.setText(c.father);
        eMobile.setText(c.mobile);
        eLocation.setText(c.location);
        eAddress.setText(c.address);
        tvDate.setText("दिनांक: " + c.date + "   समय: " + c.time);

        loadThumb(c.front, ivFront);
        loadThumb(c.back, ivBack);
        ivFront.setOnClickListener(x -> openImage(c.front));
        ivBack.setOnClickListener(x -> openImage(c.back));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(v)
                .setPositiveButton("सेव", null)
                .setNegativeButton("डिलीट", (d, w) -> confirmDelete(c))
                .setNeutralButton("बंद", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(x -> {
                    String name = eName.getText().toString().trim();
                    String mobile = eMobile.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "नाम भरें", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!mobile.isEmpty() && mobile.length() != 10) {
                        Toast.makeText(this, "मोबाइल 10 अंक का होना चाहिए", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    c.name = name;
                    c.father = eFather.getText().toString().trim();
                    c.mobile = mobile;
                    c.location = eLocation.getText().toString().trim();
                    c.address = eAddress.getText().toString().trim();
                    db.update(c);
                    Toast.makeText(this, "अपडेट हो गया", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    refresh();
                }));
        dialog.show();
    }

    private void confirmDelete(Customer c) {
        new AlertDialog.Builder(this)
                .setMessage(c.name + " को डिलीट करें?")
                .setPositiveButton("हाँ, डिलीट", (d, w) -> {
                    db.delete(c.id);
                    refresh();
                })
                .setNegativeButton("नहीं", null)
                .show();
    }

    private void loadThumb(String path, ImageView iv) {
        if (path == null || path.isEmpty() || !new File(path).exists()) return;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 8;   // छोटा करके दिखाएँ, मेमोरी बचाने के लिए
        Bitmap bmp = BitmapFactory.decodeFile(path, o);
        if (bmp != null) iv.setImageBitmap(bmp);
    }

    private void openImage(String path) {
        if (path == null || path.isEmpty() || !new File(path).exists()) {
            Toast.makeText(this, "फ़ोटो नहीं मिली", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", new File(path));
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "image/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "फ़ोटो नहीं खुल पाई", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- List adapter ----------
    private class CustomerAdapter extends ArrayAdapter<Customer> {
        CustomerAdapter(List<Customer> items) {
            super(CustomerListActivity.this, 0, items);
        }

        @NonNull
        @Override
        public View getView(int pos, View convert, @NonNull ViewGroup parent) {
            if (convert == null) {
                convert = LayoutInflater.from(getContext()).inflate(R.layout.item_customer, parent, false);
            }
            Customer c = getItem(pos);
            ((TextView) convert.findViewById(R.id.tvName)).setText(
                    c.father.isEmpty() ? c.name : c.name + "  (पिता: " + c.father + ")");
            ((TextView) convert.findViewById(R.id.tvMobile)).setText(
                    c.mobile.isEmpty() ? "मोबाइल: —" : "मोबाइल: " + c.mobile);
            ((TextView) convert.findViewById(R.id.tvAddress)).setText(
                    c.address.isEmpty() ? "पता: —" : "पता: " + c.address);
            ((TextView) convert.findViewById(R.id.tvLocation)).setText(
                    c.location.isEmpty() ? "लोकेशन: —" : "लोकेशन: " + c.location);
            return convert;
        }
    }
}
