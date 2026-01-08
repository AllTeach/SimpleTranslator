package com.example.translator;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import java.util.HashSet;
import java.util.Set;

public class DownloadModelsActivity extends AppCompatActivity {

    private TextView tvEnglishStatus, tvGreekStatus, tvManageStatus;
    private ProgressBar progEnglish, progGreek;
    private Button btnDownloadEnglish, btnDeleteEnglish, btnDownloadGreek, btnDeleteGreek;
    private Switch switchAllowCellular;

    private RemoteModelManager modelManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_models);

        tvEnglishStatus = findViewById(R.id.tvEnglishStatus);
        tvGreekStatus = findViewById(R.id.tvGreekStatus);
        tvManageStatus = findViewById(R.id.tvManageStatus);
        progEnglish = findViewById(R.id.progEnglish);
        progGreek = findViewById(R.id.progGreek);
        btnDownloadEnglish = findViewById(R.id.btnDownloadEnglish);
        btnDeleteEnglish = findViewById(R.id.btnDeleteEnglish);
        btnDownloadGreek = findViewById(R.id.btnDownloadGreek);
        btnDeleteGreek = findViewById(R.id.btnDeleteGreek);
        switchAllowCellular = findViewById(R.id.switchAllowCellular);

        modelManager = RemoteModelManager.getInstance();

        btnDownloadEnglish.setOnClickListener(v -> downloadSingleLanguage(TranslateLanguage.ENGLISH, progEnglish, tvEnglishStatus));
        btnDownloadGreek.setOnClickListener(v -> downloadSingleLanguage(TranslateLanguage.GREEK, progGreek, tvGreekStatus));

        btnDeleteEnglish.setOnClickListener(v -> deleteSingleLanguage(TranslateLanguage.ENGLISH, tvEnglishStatus));
        btnDeleteGreek.setOnClickListener(v -> deleteSingleLanguage(TranslateLanguage.GREEK, tvGreekStatus));

        updateModelStatuses();
    }

    private void updateModelStatuses() {
        modelManager.getDownloadedModels(TranslateRemoteModel.class)
            .addOnSuccessListener(models -> {
                Set<String> downloadedLangs = new HashSet<>();
                for (TranslateRemoteModel m : models) {
                    try {
                        downloadedLangs.add(m.getLanguage());
                    } catch (NoSuchMethodError e) {
                        downloadedLangs.add(m.toString());
                    }
                }

                if (downloadedLangs.contains(TranslateLanguage.ENGLISH)) {
                    tvEnglishStatus.setText("Status: downloaded");
                } else {
                    tvEnglishStatus.setText("Status: not downloaded");
                }

                if (downloadedLangs.contains(TranslateLanguage.GREEK)) {
                    tvGreekStatus.setText("Status: downloaded");
                } else {
                    tvGreekStatus.setText("Status: not downloaded");
                }
            })
            .addOnFailureListener(e -> {
                tvManageStatus.setText("Could not fetch downloaded models: " + e.getMessage());
            });
    }

    private void downloadSingleLanguage(String languageCode, ProgressBar progressBar, TextView statusView) {
        progressBar.setVisibility(View.VISIBLE);
        statusView.setText("Status: downloading...");

        boolean allowCellular = switchAllowCellular.isChecked();
        DownloadConditions conditions = new DownloadConditions.Builder()
            .requireWifi()
            .build();

        if (allowCellular) {
            conditions = new DownloadConditions.Builder().build();
        }

        TranslateRemoteModel model = new TranslateRemoteModel.Builder(languageCode).build();

        modelManager.download(model, conditions)
            .addOnSuccessListener(aVoid -> {
                progressBar.setVisibility(View.GONE);
                statusView.setText("Status: downloaded");
                tvManageStatus.setText("Downloaded " + languageCode);
            })
            .addOnFailureListener(e -> {
                progressBar.setVisibility(View.GONE);
                statusView.setText("Status: download failed: " + e.getMessage());
                tvManageStatus.setText("Download failed for " + languageCode);
            });
    }

    private void deleteSingleLanguage(String languageCode, TextView statusView) {
        TranslateRemoteModel model = new TranslateRemoteModel.Builder(languageCode).build();

        modelManager.deleteDownloadedModel(model)
            .addOnSuccessListener(aVoid -> {
                statusView.setText("Status: not downloaded");
                tvManageStatus.setText("Deleted model " + languageCode);
            })
            .addOnFailureListener(e -> {
                tvManageStatus.setText("Delete failed: " + e.getMessage());
            });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateModelStatuses();
    }
}
