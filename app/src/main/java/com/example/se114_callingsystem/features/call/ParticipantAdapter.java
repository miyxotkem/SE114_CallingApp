package com.example.se114_callingsystem.features.call;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.Participant;
import com.google.android.material.card.MaterialCardView;
import io.agora.rtc2.Constants;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.video.VideoCanvas;
import java.util.List;

public class ParticipantAdapter extends RecyclerView.Adapter<ParticipantAdapter.CallViewHolder> {

    private Context context;
    private List<Participant> participantList;
    private RtcEngine rtcEngine;

    public ParticipantAdapter(Context context, List<Participant> participantList, RtcEngine rtcEngine) {
        this.context = context;
        this.participantList = participantList;
        this.rtcEngine = rtcEngine;
    }

    @NonNull
    @Override
    public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_call_participant, parent, false);

        // TÃ­nh toÃ¡n chiá»u cao Ä‘á»ƒ cÃ¡c Ã´ video chia Ä‘á»u mÃ n hÃ¬nh
        int totalItems = participantList.size();
        int rows = getRowsCount(totalItems);

        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (parent.getHeight() > 0) {
            layoutParams.height = parent.getHeight() / rows;
        } else {
            // Backup náº¿u parent chÆ°a ká»‹p tÃ­nh height
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        view.setLayoutParams(layoutParams);
        return new CallViewHolder(view);
    }

    // --- 1. HÃ m há»— trá»£ cáº­p nháº­t nhanh khi click nÃºt (Payload) ---
    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            String payload = payloads.get(0).toString();
            Participant participant = participantList.get(position);

            if (payload.equals("border_update")) {
                updateSpeakingBorder(holder, participant.isSpeaking);
                return; // Chá»‰ cáº­p nháº­t viá»n, khÃ´ng váº½ láº¡i video
            }
            else if (payload.equals("state_update")) {
                // Cáº­p nháº­t ngay láº­p tá»©c tráº¡ng thÃ¡i áº©n/hiá»‡n cam vÃ  mic mÃ  khÃ´ng lÃ m giáº­t hÃ¬nh
                holder.videoContainer.setVisibility(participant.isVideoOff ? View.GONE : View.VISIBLE);
                holder.ivUserProfile.setVisibility(participant.isVideoOff ? View.VISIBLE : View.GONE);
                holder.ivMuteStatus.setVisibility(participant.isMuted ? View.VISIBLE : View.GONE);
                return;
            }
        }
        // Náº¿u khÃ´ng cÃ³ payload, thá»±c hiá»‡n bind Ä‘áº§y Ä‘á»§ nhÆ° bÃªn dÆ°á»›i
        super.onBindViewHolder(holder, position, payloads);
    }

    // --- 2. HÃ m Bind Ä‘áº§y Ä‘á»§ (Cháº¡y khi má»›i vÃ o phÃ²ng hoáº·c lÆ°á»›t danh sÃ¡ch) ---
    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
        Participant participant = participantList.get(position);

        // 1. Dá»n dáº¹p container Ä‘á»ƒ trÃ¡nh chá»“ng chÃ©o khi cuá»™n RecyclerView
        holder.videoContainer.removeAllViews();

        // 2. Táº¡o SurfaceView má»›i
        SurfaceView surfaceView = new SurfaceView(context);
        holder.videoContainer.addView(surfaceView);

        // 3. Thiáº¿t láº­p video tá»« Agora
        if (rtcEngine != null) {
            if (participant.name.equals("MÃ n hÃ¬nh cá»§a tÃ´i")) {
                // ÄÃ‚Y LÃ€ Ã” Cá»¦A SCREEN SHARE (Cá»¥c bá»™): Cáº§n chá»‰ Ä‘á»‹nh nguá»“n lÃ  mÃ n hÃ¬nh thay vÃ¬ camera
                // KhÃ´ng set ZOrderMediaOverlay cho screen share Ä‘á»ƒ trÃ¡nh xung Ä‘á»™t render
                VideoCanvas canvas = new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, 0);
                canvas.sourceType = Constants.VIDEO_SOURCE_SCREEN_PRIMARY;
                rtcEngine.setupLocalVideo(canvas);
                // QUAN TRá»ŒNG: Pháº£i gá»i startPreview vá»›i nguá»“n SCREEN Ä‘á»ƒ SDK báº¯t Ä‘áº§u váº½ khung hÃ¬nh
                rtcEngine.startPreview(Constants.VideoSourceType.VIDEO_SOURCE_SCREEN_PRIMARY);

            } else if (participant.name.contains("Me")) {
                // ÄÃ‚Y LÃ€ Ã” CAMERA Cá»¦A Báº N (Cá»¥c bá»™)
                surfaceView.setZOrderMediaOverlay(true);
                rtcEngine.setupLocalVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0));

            } else {
                // ÄÃ‚Y LÃ€ Ã” Cá»¦A NGÆ¯á»œI KHÃC (Bao gá»“m cáº£ camera ngÆ°á»i khÃ¡c vÃ  mÃ n hÃ¬nh ngÆ°á»i khÃ¡c)
                surfaceView.setZOrderMediaOverlay(true);
                // DÃ¹ng RENDER_MODE_FIT cho luá»“ng screen share (UID > 1000), RENDER_MODE_HIDDEN cho camera
                int renderMode = (participant.uid >= 1000) ? VideoCanvas.RENDER_MODE_FIT : VideoCanvas.RENDER_MODE_HIDDEN;
                rtcEngine.setupRemoteVideo(new VideoCanvas(surfaceView, renderMode, participant.uid));
            }
        }

        holder.tvUserName.setText(participant.name);

        // 4. Thiáº¿t láº­p tráº¡ng thÃ¡i hiá»ƒn thá»‹ ban Ä‘áº§u (áº©n/hiá»‡n mic, cam)
        holder.videoContainer.setVisibility(participant.isVideoOff ? View.GONE : View.VISIBLE);
        holder.ivUserProfile.setVisibility(participant.isVideoOff ? View.VISIBLE : View.GONE);
        holder.ivMuteStatus.setVisibility(participant.isMuted ? View.VISIBLE : View.GONE);

        updateSpeakingBorder(holder, participant.isSpeaking);
    }

    private void updateSpeakingBorder(CallViewHolder holder, boolean isSpeaking) {
        if (isSpeaking) {
            holder.cardView.setStrokeColor(Color.parseColor("#4CAF50")); // MÃ u xanh lÃ¡ sÃ¡ng
            holder.cardView.setStrokeWidth(12);
        } else {
            holder.cardView.setStrokeColor(Color.parseColor("#3A3A3A")); // MÃ u xÃ¡m tá»‘i
            holder.cardView.setStrokeWidth(2);
        }
    }

    @Override
    public int getItemCount() {
        return participantList.size();
    }

    private int getRowsCount(int totalItems) {
        if (totalItems <= 1) return 1;
        if (totalItems <= 2) return 2;
        if (totalItems <= 4) return 2;
        return 3;
    }

    public static class CallViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        FrameLayout videoContainer;
        ImageView ivUserProfile;
        TextView tvUserName;
        ImageView ivMuteStatus;

        public CallViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            videoContainer = itemView.findViewById(R.id.videoContainer);
            ivUserProfile = itemView.findViewById(R.id.ivUserProfile);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            ivMuteStatus = itemView.findViewById(R.id.ivMuteStatus);
        }
    }
}

