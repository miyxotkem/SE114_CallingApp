package com.example.se114_callingsystem;

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

import com.google.android.material.card.MaterialCardView;

import java.util.List;

import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.video.VideoCanvas;

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
        View view = LayoutInflater.from(context).inflate(R.layout.activity_item_call_participant, parent, false);
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        layoutParams.height = parent.getHeight() / getRowsCount(participantList.size());
        view.setLayoutParams(layoutParams);
        return new CallViewHolder(view);
    }
//    @NonNull
//    @Override
//    public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//        View view = LayoutInflater.from(context).inflate(R.layout.activity_item_call_participant, parent, false);
//
//        // Convert 220dp into exact screen pixels so it looks the same on all phones
//        int boxHeight = (int) (220 * context.getResources().getDisplayMetrics().density);
//
//        // Force the height to stay small
//        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
//        layoutParams.height = boxHeight;
//        view.setLayoutParams(layoutParams);
//
//        return new CallViewHolder(view);
//    }

    // --- PAYLOAD HANDLER: Prevents video from freezing when clicking mute/camera ---
    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            String payload = payloads.get(0).toString();
            Participant participant = participantList.get(position);

            if (payload.equals("border_update")) {
                updateSpeakingBorder(holder, participant.isSpeaking);
                return; // Stop here so video doesn't rebuild
            }
            else if (payload.equals("state_update")) {
                // Hides video container completely so the profile picture can show
                holder.videoContainer.setVisibility(participant.isVideoOff ? View.GONE : View.VISIBLE);
                holder.ivUserProfile.setVisibility(participant.isVideoOff ? View.VISIBLE : View.GONE);
                holder.ivMuteStatus.setVisibility(participant.isMuted ? View.VISIBLE : View.GONE);
                return; // Stop here so video doesn't rebuild
            }
        }
        // If no payload, do a full heavy bind
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
        Participant participant = participantList.get(position);
        holder.tvUserName.setText(participant.name);

        // Heavy video setup
        holder.videoContainer.removeAllViews();
        SurfaceView surfaceView = new SurfaceView(context);
        surfaceView.setZOrderMediaOverlay(true);
        holder.videoContainer.addView(surfaceView);

        if (rtcEngine != null) {
            if (participant.uid == 0) {
                rtcEngine.setupLocalVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0));
            } else {
                rtcEngine.setupRemoteVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, participant.uid));
            }
        }

        // Set initial visibility states
        holder.videoContainer.setVisibility(participant.isVideoOff ? View.GONE : View.VISIBLE);
        holder.ivUserProfile.setVisibility(participant.isVideoOff ? View.VISIBLE : View.GONE);
        holder.ivMuteStatus.setVisibility(participant.isMuted ? View.VISIBLE : View.GONE);

        updateSpeakingBorder(holder, participant.isSpeaking);
    }

    private void updateSpeakingBorder(CallViewHolder holder, boolean isSpeaking) {
        if (isSpeaking) {
            holder.cardView.setStrokeColor(Color.parseColor("#4CAF50")); // Bright Green
            holder.cardView.setStrokeWidth(15); // <--- INCREASED TO 15 PIXELS
        } else {
            holder.cardView.setStrokeColor(Color.parseColor("#3A3A3A")); // Dark Grey
            holder.cardView.setStrokeWidth(2);
        }
    }

    @Override
    public int getItemCount() {
        return participantList.size();
    }

    private int getRowsCount(int totalItems) {
        if (totalItems <= 2) return totalItems;
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