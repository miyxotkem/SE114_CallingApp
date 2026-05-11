package com.example.se114_callingsystem.Activity.Page;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.Util.ThemeHelper;

import java.net.URLEncoder;

public class DocumentViewerActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private String fileUrl;
    private String fileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_viewer);

        webView = findViewById(R.id.webViewDoc);
        progressBar = findViewById(R.id.progressBar);
        TextView tvDocTitle = findViewById(R.id.tvDocTitle);
        ImageButton btnBack = findViewById(R.id.btnBackFromDoc);
        ImageButton btnDownload = findViewById(R.id.btnDownloadDoc);

        // Get data passed from Chat_adapter
        fileUrl = getIntent().getStringExtra("FILE_URL");
        fileName = getIntent().getStringExtra("FILE_NAME");

        if (fileName != null) {
            tvDocTitle.setText(fileName);
        }

        btnBack.setOnClickListener(v -> finish());
        btnDownload.setOnClickListener(v -> downloadFile());

        setupWebView();
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        // Keep navigation inside the WebView
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }
        });

        // Use Google Docs Viewer to render the Cloudinary URL
        try {
            String encodedUrl = URLEncoder.encode(fileUrl, "UTF-8");
            String docUrl = "https://docs.google.com/gview?embedded=true&url=" + encodedUrl;
            webView.loadUrl(docUrl);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi khi tải tài liệu", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadFile() {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(fileUrl));
            request.setTitle(fileName);
            request.setDescription("Đang tải tệp tin...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(this, "Đang tải xuống...", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Không thể tải xuống tệp tin", Toast.LENGTH_SHORT).show();
        }
    }
}