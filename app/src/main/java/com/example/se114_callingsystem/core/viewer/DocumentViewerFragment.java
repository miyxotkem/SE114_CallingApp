package com.example.se114_callingsystem.core.viewer;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.example.se114_callingsystem.R;
import java.net.URLEncoder;

public class DocumentViewerFragment extends Fragment {

    private WebView webView;
    private ProgressBar progressBar;
    private String fileUrl;
    private String fileName;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_core_document_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        webView = view.findViewById(R.id.webViewDoc);
        progressBar = view.findViewById(R.id.progressBar);
        TextView tvDocTitle = view.findViewById(R.id.tvDocTitle);
        ImageButton btnBack = view.findViewById(R.id.btnBackFromDoc);
        ImageButton btnDownload = view.findViewById(R.id.btnDownloadDoc);

        if (getArguments() != null) {
            fileUrl = getArguments().getString("DOC_URL");
            fileName = getArguments().getString("FILE_NAME");
        }

        if (fileName != null) {
            tvDocTitle.setText(fileName);
        }

        btnBack.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());
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
            if (getContext() != null) {
                Toast.makeText(requireContext(), "Lỗi khi tải tài liệu", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void downloadFile() {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(fileUrl));
            request.setTitle(fileName);
            request.setDescription("Đang tải tệp tin...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            if (getContext() != null) {
                DownloadManager manager = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager != null) {
                    manager.enqueue(request);
                    Toast.makeText(requireContext(), "Đang tải xuống...", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            if (getContext() != null) {
                Toast.makeText(requireContext(), "Không thể tải xuống tệp tin", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
